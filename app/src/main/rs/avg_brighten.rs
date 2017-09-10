#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap;

static float black_level;
static float white_level;

void setBlackLevel(float value) {
    black_level = value;
    white_level = 255.0f / (255.0f - black_level);
}

float gain;
//float gamma;

float tonemap_scale;
float linear_scale;

/*uchar4 __attribute__((kernel)) avg_brighten(uchar4 in) {
    //float3 value = powr(convert_float3(in.rgb)/255.0f, gamma) * 255.0f;
    float3 value = gain*convert_float3(in.rgb);
    //float3 value = gain * powr(convert_float3(in.rgb)/255.0f, gamma) * 255.0f;

	uchar4 out;
    out.rgb = convert_uchar3(clamp(value+0.5f, 0.f, 255.f));
    return out;
}*/

uchar4 __attribute__((kernel)) avg_brighten(float3 rgb, uint32_t x, uint32_t y) {
    {
        // spatial noise reduction filter
        float3 sum = 0.0;
        int radius = 4;
        int width = rsAllocationGetDimX(bitmap);
        int height = rsAllocationGetDimY(bitmap);
        int count = 0;
        //float C = 0.1f*rgb.g*rgb.g;
        for(int cy=y-radius;cy<=y+radius;cy++) {
            for(int cx=x-radius;cx<=x+radius;cx++) {
                if( cx >= 0 && cx < width && cy >= 0 && y < height ) {
                    float3 this_pixel = rsGetElementAt_float3(bitmap, cx, cy);
                    {
                        // use a wiener filter, so that more similar pixels have greater contribution
                        //const float C = 32.0f*32.0f;
                        //const float C = 32.0f*32.0f/8.0f;
                        //const float C = 64.0f;
                        const float C = 16.0f*16.0f/8.0f;
                        float3 diff = rgb - this_pixel;
                        float L = dot(diff, diff);
                        float weight = L/(L+C);
                        //weight = 0.0;
                        this_pixel = weight * rgb + (1.0-weight) * this_pixel;
                    }
                    sum += this_pixel;
                    count++;
                }
            }
        }

        rgb = sum / count;
    }

    rgb = rgb - black_level;
    rgb = rgb * white_level;
    rgb = clamp(rgb, 0.0f, 255.0f);
    float3 hdr = gain*rgb;

	uchar4 out;
    {
        float value = fmax(hdr.r, hdr.g);
        value = fmax(value, hdr.b);
        float scale = 255.0f / ( tonemap_scale + value );
        scale *= linear_scale;
        // shouldn't need to clamp - linear_scale should be such that values don't map to more than 255
        out.r = (uchar)(scale * hdr.r + 0.5f);
        out.g = (uchar)(scale * hdr.g + 0.5f);
        out.b = (uchar)(scale * hdr.b + 0.5f);
        out.a = 255;
    }
    return out;
}
