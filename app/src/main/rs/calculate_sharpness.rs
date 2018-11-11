#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap;

// Rather than a single sum, we calculate a sum for each column of pixels. This is for two reasons:
// - This should help performance/parallelism by meaning we're not always locking on a single
//   variable (a reduction renderscript kernel may help, though this requires Android 7).
// - This avoids risk of overflow (when we sum the sums array in Java, we convert to float, but
//   we can't have a float here as floats aren't supported for renderscript atomic operations).
int32_t *sums;
int width;

void init_sums() {
    for(int i=0;i<width;i++)
        sums[i] = 0;
}

void __attribute__((kernel)) calculate_sharpness(uchar4 in, uint32_t x, uint32_t y) {
    int centre = in.g;
    int left = centre;
    int right = centre;
    int top = centre;
    int bottom = centre;

    if( x > 0 ) {
        left = rsGetElementAt_uchar4(bitmap, x-1, y).g;
    }
    if( x < width-1 ) {
        right = rsGetElementAt_uchar4(bitmap, x+1, y).g;
    }
    if( y > 0 ) {
        left = rsGetElementAt_uchar4(bitmap, x, y-1).g;
    }
    if( y < rsAllocationGetDimY(bitmap)-1 ) {
        right = rsGetElementAt_uchar4(bitmap, x, y+1).g;
    }

    // uses a laplacian filter https://en.wikipedia.org/wiki/Discrete_Laplace_operator
    int this_sum = abs((left + right + top + bottom - 4 * centre)/4);

    rsAtomicAdd(&sums[x], this_sum);
}
