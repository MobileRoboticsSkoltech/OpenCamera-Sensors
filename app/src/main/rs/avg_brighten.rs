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
float gamma;

//float tonemap_scale;
//float linear_scale;

/* Simplified brighten algorithm for gain only.
 */
uchar4 __attribute__((kernel)) avg_brighten_gain(uchar4 in) {
    float3 value = gain*convert_float3(in.rgb);

	uchar4 out;
    out.rgb = convert_uchar3(clamp(value+0.5f, 0.f, 255.f));
    out.a = 255;
    return out;
}

uchar4 __attribute__((kernel)) avg_brighten_f(float3 rgb, uint32_t x, uint32_t y) {
    /*{
    	uchar4 out;
        out.rgb = convert_uchar3(clamp(rgb + 0.5f, 0.f, 255.f));
        out.a = 255;
        return out;
    }*/
    //if( false )
    {
        // spatial noise reduction filter
        /*float old_value = fmax(rgb.r, rgb.g);
        old_value = fmax(old_value, rgb.b);*/
        float3 sum = 0.0;
        // if changing the radius, may also want to change the value returned by HDRProcessor.getAvgSampleSize()
        int radius = 4;
        //int radius = 2;
        int width = rsAllocationGetDimX(bitmap);
        int height = rsAllocationGetDimY(bitmap);
        int count = 0;
        //float C = 0.1f*rgb.g*rgb.g;
        for(int cy=y-radius;cy<=y+radius;cy++) {
            for(int cx=x-radius;cx<=x+radius;cx++) {
                if( cx >= 0 && cx < width && cy >= 0 && y < height ) {
                    float3 this_pixel = rsGetElementAt_float3(bitmap, cx, cy);
                    {
                        /*float this_value = fmax(this_pixel.r, this_pixel.g);
                        this_value = fmax(this_value, this_pixel.b);*/
                        // use a wiener filter, so that more similar pixels have greater contribution
                        //const float C = 64.0f*64.0f;
                        //const float C = 32.0f*32.0f;
                        //const float C = 32.0f*32.0f/8.0f;
                        //const float C = 64.0f;
                        const float C = 16.0f*16.0f/8.0f;
                        float3 diff = rgb - this_pixel;
                        float L = dot(diff, diff);
                        /*float diff = this_value - old_value;
                        float L = diff*diff;*/
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

        /*float new_value = fmax(rgb.r, rgb.g);
        new_value = fmax(new_value, rgb.b);
        // preserve value - we want denoise only for chrominance
        rgb *= old_value/new_value;*/
    }

    rgb = rgb - black_level;
    rgb = rgb * white_level;
    rgb = clamp(rgb, 0.0f, 255.0f);

    /*float3 hdr = gain*rgb;

	uchar4 out;
    {
        float value = fmax(hdr.r, hdr.g);
        value = fmax(value, hdr.b);
        float scale = 255.0f / ( tonemap_scale + value );
        scale *= linear_scale;

        hdr *= scale;

        // reduce saturation
        const float saturation_factor = 0.7f;
        float grey = (hdr.r + hdr.g + hdr.b)/3.0f;
        hdr = grey + saturation_factor*(hdr - grey);
        out.rgb = convert_uchar3(clamp(hdr + 0.5f, 0.f, 255.f));
        out.a = 255;
    }*/

    /*float3 hdr = rgb;

	uchar4 out;
    {
        float value = fmax(hdr.r, hdr.g);
        value = fmax(value, hdr.b);
        float scale = 255.0f / ( tonemap_scale + value );
        scale *= linear_scale;

        hdr *= scale;

        // shouldn't need to clamp - linear_scale should be such that values don't map to more than 255
        out.r = (uchar)(hdr.r + 0.5f);
        out.g = (uchar)(hdr.g + 0.5f);
        out.b = (uchar)(hdr.b + 0.5f);
        out.a = 255;
    }*/

    /*rgb *= gain;
    float3 hdr = powr(rgb/255.0f, gamma) * 255.0f;
	uchar4 out;
    out.rgb = convert_uchar3(clamp(hdr+0.5f, 0.f, 255.f));
    out.a = 255;*/

    rgb *= gain;
    float3 hdr = rgb;
    float value = fmax(hdr.r, hdr.g);
    value = fmax(value, hdr.b);
    if( value >= 0.5f ) {
        float new_value = powr(value/255.0f, gamma) * 255.0f;
        float gamma_scale = new_value / value;
        hdr *= gamma_scale;
    }
	uchar4 out;
    out.rgb = convert_uchar3(clamp(hdr+0.5f, 0.f, 255.f));
    out.a = 255;

    return out;
}
