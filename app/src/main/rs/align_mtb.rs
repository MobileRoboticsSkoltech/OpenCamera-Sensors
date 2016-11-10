#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap0;
rs_allocation bitmap1;
int width, height;

int step_size = 1;
int off_x = 0, off_y = 0;

int32_t *errors;

void init_errors() {
	for(int i=0;i<9;i++)
		errors[i] = 0;
}

void __attribute__((kernel)) align_mtb(uchar in, uint32_t x, uint32_t y) {
    /* We want to sample every step_size'th pixel. Because renderscript can't do this directly, instead
       we fake it by sampling over an input allocation of size (width/step_size, height/step_size), and
       then scaling the coordinates by step_size.

       The reason we want to sample every step_size'th pixel is it's good enough for the algorithm to work,
       and is much faster.
       */
    x *= step_size;
    y *= step_size;
    if( x+off_x >= step_size && x+off_x < width-step_size && y+off_y >= step_size && y+off_y < height-step_size ) {
        uchar pixel0 = rsGetElementAt_uchar(bitmap0, x, y);
        int c=0;
        for(int dy=-1;dy<=1;dy++) {
            for(int dx=-1;dx<=1;dx++) {
            	uchar pixel1 = rsGetElementAt_uchar(bitmap1, x+off_x+dx*step_size, y+off_y+dy*step_size);
            	if( pixel0 != pixel1 ) {
                	rsAtomicInc(&errors[c]);
            	}
                c++;
            }
        }
    }
}
