#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

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
    if( x+off_x >= step_size && x+off_x < width-step_size && y+off_y >= step_size && y+off_y < height-step_size ) {
        int c=0;
        for(int dy=-1;dy<=1;dy++) {
            for(int dx=-1;dx<=1;dx++) {
            	uchar pixel1 = rsGetElementAt_uchar(bitmap1, x+off_x+dx*step_size, y+off_y+dy*step_size);
            	if( in != pixel1 ) {
                	rsAtomicInc(&errors[c]);
            	}
                c++;
            }
        }
    }
}
