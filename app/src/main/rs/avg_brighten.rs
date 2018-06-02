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

/*static void sort(float *comp, int n_pixels_c) {
   for(int i=1;i<n_pixels_c;i++) {
       int key = comp[i];
       int j = i-1;

       // move elements of comp[0,...,i-1], that are greater than key, to one position ahead of
       // their current position
       while(j >= 0 && comp[j] > key) {
           comp[j+1] = comp[j];
           j--;
       }
       comp[j+1] = key;
   }
}*/

uchar4 __attribute__((kernel)) avg_brighten_f(float3 rgb, uint32_t x, uint32_t y) {
    /*{
    	uchar4 out;
        out.rgb = convert_uchar3(clamp(rgb + 0.5f, 0.f, 255.f));
        out.a = 255;
        return out;
    }*/
    /*if( false )
    {
        // median filter for noise reduction
        int width = rsAllocationGetDimX(bitmap);
        int height = rsAllocationGetDimY(bitmap);
        int x_l = x, x_r = x, y_l = y, y_r = y;
        if( x > 0 ) {
            x_l--;
        }
        if( x < width-1 ) {
            x_r++;
        }
        if( y > 0 ) {
            y_l--;
        }
        if( y < height-1 ) {
            y_r++;
        }
        float value = fmax(rgb.r, rgb.g);
        value = fmax(value, rgb.b);

        //const int n_pixels_c = 9;
        //float3 pixels[n_pixels_c];
        //pixels[0] = rsGetElementAt_float3(bitmap, x_l, y_l);
        //pixels[1] = rsGetElementAt_float3(bitmap, x, y_l);
        //pixels[2] = rsGetElementAt_float3(bitmap, x_r, y_l);
        //pixels[3] = rsGetElementAt_float3(bitmap, x_l, y);
        //pixels[4] = rgb;
        //pixels[5] = rsGetElementAt_float3(bitmap, x_r, y);
        //pixels[6] = rsGetElementAt_float3(bitmap, x_l, y_r);
        //pixels[7] = rsGetElementAt_float3(bitmap, x, y_r);
        //pixels[8] = rsGetElementAt_float3(bitmap, x_r, y_r);
        const int n_pixels_c = 5;
        const int mid_pixel_c = (n_pixels_c-1)/2;
        float3 pixels[n_pixels_c];
        pixels[0] = rsGetElementAt_float3(bitmap, x, y_l);
        pixels[1] = rsGetElementAt_float3(bitmap, x_l, y);
        pixels[2] = rgb;
        pixels[3] = rsGetElementAt_float3(bitmap, x_r, y);
        pixels[4] = rsGetElementAt_float3(bitmap, x, y_r);

        float comp[n_pixels_c];
        // red
        for(int i=0;i<n_pixels_c;i++)
            comp[i] = pixels[i].r;
        sort(comp, n_pixels_c);
        rgb.r = comp[mid_pixel_c];

        // green
        for(int i=0;i<n_pixels_c;i++)
            comp[i] = pixels[i].g;
        sort(comp, n_pixels_c);
        rgb.g = comp[mid_pixel_c];

        // blue
        for(int i=0;i<n_pixels_c;i++)
            comp[i] = pixels[i].b;
        sort(comp, n_pixels_c);
        rgb.b = comp[mid_pixel_c];

        // convert from RGB to YUV
        if( false ) {
            for(int i=0;i<n_pixels_c;i++) {
                float Y = 0.299 * pixels[i].r + 0.587 * pixels[i].g + 0.114 * pixels[i].b;
                float U = -0.147 * pixels[i].r - 0.289 * pixels[i].g + 0.436 * pixels[i].b;
                float V = 0.615 * pixels[i].r - 0.515 * pixels[i].g - 0.100 * pixels[i].b;
                pixels[i].x = Y;
                pixels[i].y = U;
                pixels[i].z = V;
            }

            // Y
            //for(int i=0;i<n_pixels_c;i++)
            //    comp[i] = 0.299 * pixels[i].r + 0.587 * pixels[i].g + 0.114 * pixels[i].b;
            //sort(comp, n_pixels_c);
            //rgb.x = comp[mid_pixel_c];
            rgb.x = 0.299 * pixels[mid_pixel_c].r + 0.587 * pixels[mid_pixel_c].g + 0.114 * pixels[mid_pixel_c].b;

            // U
            for(int i=0;i<n_pixels_c;i++)
                comp[i] = -0.147 * pixels[i].r - 0.289 * pixels[i].g + 0.436 * pixels[i].b;
            sort(comp, n_pixels_c);
            rgb.y = comp[mid_pixel_c];

            // V
            for(int i=0;i<n_pixels_c;i++)
                comp[i] = 0.615 * pixels[i].r - 0.515 * pixels[i].g - 0.100 * pixels[i].b;
            sort(comp, n_pixels_c);
            rgb.z = comp[mid_pixel_c];

            // convert back from YUV to RGB
            //rgb = rsYuvToRGBA_float4((uchar)(255.0f*rgb.x), (uchar)(255.0f*rgb.y), (uchar)(255.0f*rgb.z)).rgb;
            float r = rgb.x + 1.140f * rgb.z;
            float g = rgb.x - 0.395f * rgb.y - 0.581f * rgb.z;
            float b = rgb.x + 2.032f * rgb.y;
            rgb.r = r;
            rgb.g = g;
            rgb.b = b;
            rgb = clamp(rgb, 0.0f, 255.0f);
        }

        float new_value = fmax(rgb.r, rgb.g);
        new_value = fmax(new_value, rgb.b);
        float diff = value - new_value;
        rgb.r += diff;
        rgb.g += diff;
        rgb.b += diff;
        rgb = clamp(rgb, 0.0f, 255.0f);
    }*/
    //if( false )
    {
        // spatial noise reduction filter
        // if making canges to this (especially radius, C), run AvgTests - in particular, pay close
        // attention to:
        // testAvg6: don't want to make the postcard too blurry
        // testAvg8: zoom in to 60%, ensure still appears reasonably sharp
        // testAvg23: ensure we do reduce the noise, e.g., view around "vicks", without making the
        // text blurry
        /*float old_value = fmax(rgb.r, rgb.g);
        old_value = fmax(old_value, rgb.b);*/
        float3 sum = 0.0;
        // if changing the radius, may also want to change the value returned by HDRProcessor.getAvgSampleSize()
        //int radius = 4;
        //int radius = 2;
        int radius = 1;
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
                        // smaller value of C means stronger filter (i.e., less averaging)
                        // see note above for details on the choice of this value
                        //const float C = 64.0f*64.0f;
                        //const float C = 32.0f*32.0f;
                        const float C = 64.0f*64.0f/8.0f;
                        //const float C = 256.0f;
                        //const float C = 32.0f*32.0f/8.0f;
                        //const float C = 64.0f;
                        //const float C = 16.0f*16.0f/8.0f;
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
