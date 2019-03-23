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
            out.r = 255;
            out.g = 255;
            out.b = 255;
        }
        else {
            out.r = 0;
            out.g = 0;
            out.b = 0;
        }
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
