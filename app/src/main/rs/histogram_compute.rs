#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

int32_t *histogram;
int32_t *histogram_r;
int32_t *histogram_g;
int32_t *histogram_b;

void init_histogram() {
    for(int i=0;i<256;i++)
        histogram[i] = 0;
}

void init_histogram_rgb() {
    for(int i=0;i<256;i++) {
        histogram_r[i] = 0;
        histogram_g[i] = 0;
        histogram_b[i] = 0;
    }
}

void __attribute__((kernel)) histogram_compute_by_luminance(uchar4 in, uint32_t x, uint32_t y) {
    float3 in_f = convert_float3(in.rgb);
    float avg = (0.299f*in_f.r + 0.587f*in_f.g + 0.114f*in_f.b);
    uchar value = (int)(avg+0.5); // round to nearest
    value = clamp(value, (uchar)0, (uchar)255); // just in case

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_by_value(uchar4 in, uint32_t x, uint32_t y) {
    uchar value = max(in.r, in.g);
    value = max(value, in.b);

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_by_value_f(float3 in_f, uint32_t x, uint32_t y) {
    uchar3 in;
    in.r = (uchar)clamp(in_f.r+0.5f, 0.0f, 255.0f);
    in.g = (uchar)clamp(in_f.g+0.5f, 0.0f, 255.0f);
    in.b = (uchar)clamp(in_f.b+0.5f, 0.0f, 255.0f);

    uchar value = max(in.r, in.g);
    value = max(value, in.b);

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_by_intensity(uchar4 in, uint32_t x, uint32_t y) {
    float3 in_f = convert_float3(in.rgb);
    float avg = (in_f.r + in_f.g + in_f.b)/3.0;
    uchar value = (int)(avg+0.5); // round to nearest
    value = min(value, (uchar)255); // just in case

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_by_intensity_f(float3 in_f, uint32_t x, uint32_t y) {
    float avg = (in_f.r + in_f.g + in_f.b)/3.0;
    uchar value = (int)(avg+0.5); // round to nearest
    value = min(value, (uchar)255); // just in case

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_by_lightness(uchar4 in, uint32_t x, uint32_t y) {
    uchar max_value = max(in.r, in.g);
    max_value = max(max_value, in.b);
    uchar min_value = min(in.r, in.g);
    min_value = min(min_value, in.b);

    float avg = (min_value + max_value)/2.0;
    uchar value = (int)(avg+0.5); // round to nearest
    value = min(value, (uchar)255); // just in case

    rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_rgb(uchar4 in, uint32_t x, uint32_t y) {
    rsAtomicInc(&histogram_r[in.r]);
    rsAtomicInc(&histogram_g[in.g]);
    rsAtomicInc(&histogram_b[in.b]);
}

int zebra_stripes_threshold = 255;
int zebra_stripes_foreground_r = 0;
int zebra_stripes_foreground_g = 0;
int zebra_stripes_foreground_b = 0;
int zebra_stripes_foreground_a = 255;
int zebra_stripes_background_r = 255;
int zebra_stripes_background_g = 255;
int zebra_stripes_background_b = 255;
int zebra_stripes_background_a = 255;
int zebra_stripes_width = 40;

uchar4 __attribute__((kernel)) generate_zebra_stripes(uchar4 in, uint32_t x, uint32_t y) {
    uchar value = max(in.r, in.g);
    value = max(value, in.b);
    uchar4 out;
    if( value >= zebra_stripes_threshold ) {
        /*out.r = 255;
        out.g = 0;
        out.b = 255;
        out.a = 255;*/
        int stripe = (x+y)/zebra_stripes_width;
        if( stripe % 2 == 0 ) {
            out.r = zebra_stripes_background_r;
            out.g = zebra_stripes_background_g;
            out.b = zebra_stripes_background_b;
            out.a = zebra_stripes_background_a;
        }
        else {
            out.r = zebra_stripes_foreground_r;
            out.g = zebra_stripes_foreground_g;
            out.b = zebra_stripes_foreground_b;
            out.a = zebra_stripes_foreground_a;
        }
    }
    else {
        out.r = 0;
        out.g = 0;
        out.b = 0;
        out.a = 0;
        //out = in;
    }

    return out;
}

rs_allocation bitmap; // input bitmap

uchar4 __attribute__((kernel)) generate_focus_peaking(uchar4 in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    int strength = 0;
    /*if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
        uchar4 pixel0c = rsGetElementAt_uchar4(bitmap, x-1, y-1);
        int pixel0 = max(pixel0c.r, pixel0c.g);
        pixel0 = max(pixel0, (int)pixel0c.b);
        uchar4 pixel1c = rsGetElementAt_uchar4(bitmap, x, y-1);
        int pixel1 = max(pixel1c.r, pixel1c.g);
        pixel1 = max(pixel1, (int)pixel1c.b);
        uchar4 pixel2c = rsGetElementAt_uchar4(bitmap, x+1, y-1);
        int pixel2 = max(pixel2c.r, pixel2c.g);
        pixel2 = max(pixel2, (int)pixel2c.b);
        uchar4 pixel3c = rsGetElementAt_uchar4(bitmap, x-1, y);
        int pixel3 = max(pixel3c.r, pixel3c.g);
        pixel3 = max(pixel3, (int)pixel3c.b);
        uchar4 pixel4c = in;
        int pixel4 = max(pixel4c.r, pixel4c.g);
        pixel4 = max(pixel4, (int)pixel4c.b);
        uchar4 pixel5c = rsGetElementAt_uchar4(bitmap, x+1, y);
        int pixel5 = max(pixel5c.r, pixel5c.g);
        pixel5 = max(pixel5, (int)pixel5c.b);
        uchar4 pixel6c = rsGetElementAt_uchar4(bitmap, x-1, y+1);
        int pixel6 = max(pixel6c.r, pixel6c.g);
        pixel6 = max(pixel6, (int)pixel6c.b);
        uchar4 pixel7c = rsGetElementAt_uchar4(bitmap, x, y+1);
        int pixel7 = max(pixel7c.r, pixel7c.g);
        pixel7 = max(pixel7, (int)pixel7c.b);
        uchar4 pixel8c = rsGetElementAt_uchar4(bitmap, x+1, y+1);
        int pixel8 = max(pixel8c.r, pixel8c.g);
        pixel8 = max(pixel8, (int)pixel8c.b);

        int value = ( 8*pixel4 - pixel0 - pixel1 - pixel2 - pixel3 - pixel5 - pixel6 - pixel7 - pixel8 );
        strength = value*value;
    }*/
    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
        float3 pixel0c = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y-1).rgb);
        float3 pixel1c = convert_float3(rsGetElementAt_uchar4(bitmap, x, y-1).rgb);
        float3 pixel2c = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y-1).rgb);
        float3 pixel3c = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y).rgb);
        float3 pixel4c = convert_float3(in.rgb);
        float3 pixel5c = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y).rgb);
        float3 pixel6c = convert_float3(rsGetElementAt_uchar4(bitmap, x-1, y+1).rgb);
        float3 pixel7c = convert_float3(rsGetElementAt_uchar4(bitmap, x, y+1).rgb);
        float3 pixel8c = convert_float3(rsGetElementAt_uchar4(bitmap, x+1, y+1).rgb);

        float3 value = ( 8*pixel4c - pixel0c - pixel1c - pixel2c - pixel3c - pixel5c - pixel6c - pixel7c - pixel8c );
        strength = dot(value, value);
    }

    uchar4 out;
    if( strength > 256*256 ) {
        out.r = 255;
        out.g = 255;
        out.b = 255;
        out.a = 255;
    }
    else {
        out.r = 0;
        out.g = 0;
        out.b = 0;
        out.a = 0;
        //out = in;
    }
    return out;
}

uchar4 __attribute__((kernel)) generate_focus_peaking_filtered(uchar4 in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    uchar4 out = in;

    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
        // only need to read one component, as input image is now greyscale
        int pixel1 = rsGetElementAt_uchar4(bitmap, x, y-1).r;
        int pixel3 = rsGetElementAt_uchar4(bitmap, x-1, y).r;
        int pixel4 = in.r;
        int pixel5 = rsGetElementAt_uchar4(bitmap, x+1, y).r;
        int pixel7 = rsGetElementAt_uchar4(bitmap, x, y+1).r;

        int count = 0;
        if( pixel1 == 255 )
            count++;
        if( pixel3 == 255 )
            count++;
        if( pixel4 == 255 )
            count++;
        if( pixel5 == 255 )
            count++;
        if( pixel7 == 255 )
            count++;

        if( count >= 3 ) {
            out.r = 255;
            out.g = 255;
            out.b = 255;
            out.a = 255;
        }
        else {
            out.r = 0;
            out.g = 0;
            out.b = 0;
            out.a = 0;
        }
    }

    return out;
}
