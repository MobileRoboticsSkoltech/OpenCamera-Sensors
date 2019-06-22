#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)

// Setting rs_fp_relaxed causes problems (often in an unstable manner) where blended images
// come out too bright, seems to be problem with the rhs laplacian, specifically subtractBitmap()
// sometimes producing wrong results. This has no noticeable effect on performance.
//#pragma rs_fp_relaxed

rs_allocation bitmap;

const float g0 = 0.05, g1 = 0.25, g2 = 0.4;

uchar4 __attribute__((kernel)) reduce(uchar4 in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);
    int sx = 2*x;
    int sy = 2*y;

    uchar4 out;

    if( sx >= 2 && sx < width-2 && sy >= 2 & sy < height-2 ) {
        float3 pixel00 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-2, sy-2).rgb) * g0 * g0;
        float3 pixel10 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-1, sy-2).rgb) * g1 * g0;
        float3 pixel20 = convert_float3(rsGetElementAt_uchar4(bitmap, sx, sy-2).rgb) * g2 * g0;
        float3 pixel30 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+1, sy-2).rgb) * g1 * g0;
        float3 pixel40 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+2, sy-2).rgb) * g0 * g0;

        float3 pixel01 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-2, sy-1).rgb) * g0 * g1;
        float3 pixel11 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-1, sy-1).rgb) * g1 * g1;
        float3 pixel21 = convert_float3(rsGetElementAt_uchar4(bitmap, sx, sy-1).rgb) * g2 * g1;
        float3 pixel31 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+1, sy-1).rgb) * g1 * g1;
        float3 pixel41 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+2, sy-1).rgb) * g0 * g1;

        float3 pixel02 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-2, sy).rgb) * g0 * g2;
        float3 pixel12 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-1, sy).rgb) * g1 * g2;
        float3 pixel22 = convert_float3(rsGetElementAt_uchar4(bitmap, sx, sy).rgb) * g2 * g2;
        float3 pixel32 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+1, sy).rgb) * g1 * g2;
        float3 pixel42 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+2, sy).rgb) * g0 * g2;

        float3 pixel03 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-2, sy+1).rgb) * g0 * g1;
        float3 pixel13 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-1, sy+1).rgb) * g1 * g1;
        float3 pixel23 = convert_float3(rsGetElementAt_uchar4(bitmap, sx, sy+1).rgb) * g2 * g1;
        float3 pixel33 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+1, sy+1).rgb) * g1 * g1;
        float3 pixel43 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+2, sy+1).rgb) * g0 * g1;

        float3 pixel04 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-2, sy+2).rgb) * g0 * g0;
        float3 pixel14 = convert_float3(rsGetElementAt_uchar4(bitmap, sx-1, sy+2).rgb) * g1 * g0;
        float3 pixel24 = convert_float3(rsGetElementAt_uchar4(bitmap, sx, sy+2).rgb) * g2 * g0;
        float3 pixel34 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+1, sy+2).rgb) * g1 * g0;
        float3 pixel44 = convert_float3(rsGetElementAt_uchar4(bitmap, sx+2, sy+2).rgb) * g0 * g0;

        float3 result = pixel00 + pixel10 + pixel20 + pixel30 + pixel40 +
            pixel01 + pixel11 + pixel21 + pixel31 + pixel41 +
            pixel02 + pixel12 + pixel22 + pixel32 + pixel42 +
            pixel03 + pixel13 + pixel23 + pixel33 + pixel43 +
            pixel04 + pixel14 + pixel24 + pixel34 + pixel44;

        out.rgb = convert_uchar3(clamp(result+0.5f, 0.f, 255.f));
    }
    else {
        out.rgb = rsGetElementAt_uchar4(bitmap, sx, sy).rgb;
    }
    out.a = 255;

    return out;
}

uchar4 __attribute__((kernel)) expand(uchar4 in, uint32_t x, uint32_t y) {
    uchar4 out;

    if( x % 2 == 0 && y % 2 == 0 ) {
        out.rgb = rsGetElementAt_uchar4(bitmap, x/2, y/2).rgb;
    }
    else {
        out.r = 0;
        out.g = 0;
        out.b = 0;
    }
    out.a = 255;

    return out;
}

uchar4 __attribute__((kernel)) blur(uchar4 in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    uchar4 out;

    if( x >= 2 && x < width-2 && y >= 2 & y < height-2 ) {
        float3 pixel00 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y-2).rgb) * g0 * g0;
        float3 pixel10 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y-2).rgb) * g1 * g0;
        float3 pixel20 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y-2).rgb) * g2 * g0;
        float3 pixel30 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y-2).rgb) * g1 * g0;
        float3 pixel40 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y-2).rgb) * g0 * g0;

        float3 pixel01 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y-1).rgb) * g0 * g1;
        float3 pixel11 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y-1).rgb) * g1 * g1;
        float3 pixel21 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y-1).rgb) * g2 * g1;
        float3 pixel31 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y-1).rgb) * g1 * g1;
        float3 pixel41 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y-1).rgb) * g0 * g1;

        float3 pixel02 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y).rgb) * g0 * g2;
        float3 pixel12 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y).rgb) * g1 * g2;
        float3 pixel22 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y).rgb) * g2 * g2;
        float3 pixel32 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y).rgb) * g1 * g2;
        float3 pixel42 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y).rgb) * g0 * g2;

        float3 pixel03 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y+1).rgb) * g0 * g1;
        float3 pixel13 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y+1).rgb) * g1 * g1;
        float3 pixel23 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y+1).rgb) * g2 * g1;
        float3 pixel33 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y+1).rgb) * g1 * g1;
        float3 pixel43 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y+1).rgb) * g0 * g1;

        float3 pixel04 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y+2).rgb) * g0 * g0;
        float3 pixel14 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y+2).rgb) * g1 * g0;
        float3 pixel24 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y+2).rgb) * g2 * g0;
        float3 pixel34 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y+2).rgb) * g1 * g0;
        float3 pixel44 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y+2).rgb) * g0 * g0;

        float3 result = pixel00 + pixel10 + pixel20 + pixel30 + pixel40 +
            pixel01 + pixel11 + pixel21 + pixel31 + pixel41 +
            pixel02 + pixel12 + pixel22 + pixel32 + pixel42 +
            pixel03 + pixel13 + pixel23 + pixel33 + pixel43 +
            pixel04 + pixel14 + pixel24 + pixel34 + pixel44;
        result *= 4;

        out.r = (uchar)clamp(result.r+0.5f, 0.0f, 255.0f);
        out.g = (uchar)clamp(result.g+0.5f, 0.0f, 255.0f);
        out.b = (uchar)clamp(result.b+0.5f, 0.0f, 255.0f);
    }
    else {
        out.rgb = rsGetElementAt_uchar4(bitmap, x, y).rgb;
    }
    out.a = 255;

    return out;
}

uchar4 __attribute__((kernel)) blur1dX(uchar4 in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);

    uchar4 out;

    if( x >= 2 && x < width-2 ) {
        float3 pixel0 = convert_float3(rsGetElementAt_uchar4(bitmap, x-2, y).rgb) * g0;
        float3 pixel1 = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y).rgb) * g1;
        float3 pixel2 = convert_float3(in.rgb) * g2;
        float3 pixel3 = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y).rgb) * g1;
        float3 pixel4 = convert_float3(rsGetElementAt_uchar4(bitmap, x+2, y).rgb) * g0;

        float3 result = pixel0 + pixel1 + pixel2 + pixel3 + pixel4;
        result *= 2;

        out.r = (uchar)clamp(result.r+0.5f, 0.0f, 255.0f);
        out.g = (uchar)clamp(result.g+0.5f, 0.0f, 255.0f);
        out.b = (uchar)clamp(result.b+0.5f, 0.0f, 255.0f);
        out.a = 255;
    }
    else {
        out = in;
    }

    return out;
}

uchar4 __attribute__((kernel)) blur1dY(uchar4 in, uint32_t x, uint32_t y) {
    int height = rsAllocationGetDimY(bitmap);

    uchar4 out;

    if( y >= 2 && y < height-2 ) {
        float3 pixel0 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y-2).rgb) * g0;
        float3 pixel1 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y-1).rgb) * g1;
        float3 pixel2 = convert_float3(in.rgb) * g2;
        float3 pixel3 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y+1).rgb) * g1;
        float3 pixel4 = convert_float3(rsGetElementAt_uchar4(bitmap, x, y+2).rgb) * g0;

        float3 result = pixel0 + pixel1 + pixel2 + pixel3 + pixel4;
        result *= 2;

        out.r = (uchar)clamp(result.r+0.5f, 0.0f, 255.0f);
        out.g = (uchar)clamp(result.g+0.5f, 0.0f, 255.0f);
        out.b = (uchar)clamp(result.b+0.5f, 0.0f, 255.0f);
        out.a = 255;
    }
    else {
        out = in;
    }

    return out;
}

/* Subtracts bitmap from the input.
 */
float3 __attribute__((kernel)) subtract(uchar4 in, uint32_t x, uint32_t y) {

    uchar4 in1 = rsGetElementAt_uchar4(bitmap, x, y);

    float3 in_f = convert_float3(in.rgb);
    float3 in1_f = convert_float3(in1.rgb);

    float3 out = in_f - in1_f;

    return out;
}

/* Adds bitmap to the input.
 */
uchar4 __attribute__((kernel)) add(uchar4 in, uint32_t x, uint32_t y) {

    float3 in_f = convert_float3(in.rgb);
    float3 in1_f = rsGetElementAt_float3(bitmap, x, y);

    float3 result = in_f + in1_f;

    uchar4 out;
    out.r = (uchar)clamp(result.r+0.5f, 0.0f, 255.0f);
    out.g = (uchar)clamp(result.g+0.5f, 0.0f, 255.0f);
    out.b = (uchar)clamp(result.b+0.5f, 0.0f, 255.0f);
    out.a = 255;

    return out;
}

static int merge_blend_width;
static int start_blend_x;

void setBlendWidth(int blend_width, int full_width) {
    merge_blend_width = blend_width;
    start_blend_x = (full_width - merge_blend_width)/2;
}

//int32_t *best_path;
int32_t *interpolated_best_path; // the centre of the blend window, for each y coordinate
//float best_path_x_width;
//float best_path_y_scale;

static float3 merge_core(float3 in0, float3 in1, uint32_t x, uint32_t y) {
    //int width = rsAllocationGetDimX(bitmap);
    //float alpha = ((float)(x-start_blend_x))/(float)merge_blend_width;
    /*float alpha = 0.0f;
    if( x > start_blend_x ) {
        alpha = ((float)(x-start_blend_x))/(float)merge_blend_width;
    }*/

    /*int best_path_y_index = (int)((y+0.5f)*best_path_y_scale);
    int best_path_value = best_path[best_path_y_index];
    int mid_x = (int)((best_path_value+1) * best_path_x_width + 0.5f);*/
    int mid_x = interpolated_best_path[y];

    int32_t ix = x;
    //float alpha = ((float)(ix-start_blend_x))/(float)merge_blend_width;
    float alpha = ((float)( ix-(mid_x-merge_blend_width/2) )) / (float)merge_blend_width;
    alpha = clamp(alpha, 0.0f, 1.0f);

    /*float alpha;
    if( ix < mid_x )
        alpha = 0.0f;
    else
        alpha = 1.0f;*/

    float3 out = (1.0f-alpha)*in0 + alpha*in1;
    return out;
}

/** Overwrites the right hand side with the right hand side of bitmap.
 */
uchar4 __attribute__((kernel)) merge(uchar4 in, uint32_t x, uint32_t y) {
    /*
    int hwidth = rsAllocationGetDimX(bitmap)/2;
    uchar4 out = in;
    if( x >= hwidth )
    {
        out = rsGetElementAt_uchar4(bitmap, x, y);
    }
    */

    /*
    int width = rsAllocationGetDimX(bitmap);
    float alpha = ((float)x)/(float)width;

    uchar4 in1 = rsGetElementAt_uchar4(bitmap, x, y);

    float3 in0_f = convert_float3(in.rgb);
    float3 in1_f = convert_float3(in1.rgb);
    float3 result = (1.0f-alpha)*in0_f + alpha*in1_f;

    uchar4 out;
    out.rgb = convert_uchar3(clamp(result+0.5f, 0.f, 255.f));
    out.a = 255;
    */

    uchar4 in1 = rsGetElementAt_uchar4(bitmap, x, y);

    float3 in0_f = convert_float3(in.rgb);
    float3 in1_f = convert_float3(in1.rgb);

    float3 result = merge_core(in0_f, in1_f, x, y);

    uchar4 out;
    out.rgb = convert_uchar3(clamp(result+0.5f, 0.f, 255.f));
    out.a = 255;

    return out;
}

/** Overwrites the right hand side with the right hand side of bitmap.
 */
float3 __attribute__((kernel)) merge_f(float3 in, uint32_t x, uint32_t y) {
    /*
    int hwidth = rsAllocationGetDimX(bitmap)/2;
    float3 out = in;
    if( x >= hwidth )
    {
        out = rsGetElementAt_float3(bitmap, x, y);
    }
    */

    /*int width = rsAllocationGetDimX(bitmap);
    float alpha = ((float)x)/(float)width;

    float3 in1 = rsGetElementAt_float3(bitmap, x, y);

    float3 out = (1.0f-alpha)*in + alpha*in1;*/

    float3 in1 = rsGetElementAt_float3(bitmap, x, y);
    float3 out = merge_core(in, in1, x, y);

    return out;
}

int32_t *errors;

void init_errors() {
    for(int i=0;i<1;i++)
        errors[i] = 0;
}

void __attribute__((kernel)) compute_error(uchar4 in, uint32_t x, uint32_t y) {
    float3 in0_f = convert_float3(in.rgb);
    float3 in1_f = convert_float3(rsGetElementAt_uchar4(bitmap, x, y).rgb);
    float3 diff = in0_f - in1_f;
    float diff2 = dot(diff, diff);

    if( errors[0] < 2000000000 ) { // avoid risk of overflow
        rsAtomicAdd(&errors[0], diff2);
    }
}
