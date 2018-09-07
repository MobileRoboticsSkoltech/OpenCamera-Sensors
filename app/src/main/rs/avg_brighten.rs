#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap;

float median_filter_strength = 1.0f; // from 0 to 1

static float black_level;
static float white_level;

void setBlackLevel(float value) {
    black_level = value;
    white_level = 255.0f / (255.0f - black_level);
}

float gain;
float gain_A, gain_B; // see comments below
float gamma;
float low_x;
float mid_x;
float max_x;

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
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);
    //if( false )
    if( x > 0 && x < width-1 && y > 0 && y < height-1 )
    {
        // median filter for noise reduction
        // performs better than spatial filter; reduces black/white speckles in: testAvg23,
        // testAvg28, testAvg31, testAvg33
        // note that one has to typically zoom to 400% to see the improvement
        float4 p0 = rsGetElementAt_float4(bitmap, x, y-1);
        float4 p1 = rsGetElementAt_float4(bitmap, x-1, y);
        float4 p2 = 0.0;
        p2.rgb = rgb;
        float4 p3 = rsGetElementAt_float4(bitmap, x+1, y);
        float4 p4 = rsGetElementAt_float4(bitmap, x, y+1);

        // use alpha channel to store luminance
        p0.a = max(max(p0.r, p0.g), p0.b);
        p1.a = max(max(p1.r, p1.g), p1.b);
        p2.a = max(max(p2.r, p2.g), p2.b);
        p3.a = max(max(p3.r, p3.g), p3.b);
        p4.a = max(max(p4.r, p4.g), p4.b);

        // if changing this code, see if the test code in UnitTest.findMedian() should be updated
        if( p0.a > p1.a ) {
            float4 temp_p = p0;
            p0 = p1;
            p1 = temp_p;
        }
        if( p0.a > p2.a ) {
            float4 temp_p = p0;
            p0 = p2;
            p2 = temp_p;
        }
        if( p0.a > p3.a ) {
            float4 temp_p = p0;
            p0 = p3;
            p3 = temp_p;
        }
        if( p0.a > p4.a ) {
            float4 temp_p = p0;
            p0 = p4;
            p4 = temp_p;
        }
        //
        if( p1.a > p2.a ) {
            float4 temp_p = p1;
            p1 = p2;
            p2 = temp_p;
        }
        if( p1.a > p3.a ) {
            float4 temp_p = p1;
            p1 = p3;
            p3 = temp_p;
        }
        if( p1.a > p4.a ) {
            float4 temp_p = p1;
            p1 = p4;
            p4 = temp_p;
        }
        //
        if( p2.a > p3.a ) {
            float4 temp_p = p2;
            p2 = p3;
            p3 = temp_p;
        }
        if( p2.a > p4.a ) {
            float4 temp_p = p2;
            p2 = p4;
            p4 = temp_p;
        }
        // don't care about sorting p3 and p4
        //rgb = p2.rgb;
        rgb = (1.0f - median_filter_strength) * rgb + median_filter_strength * p2.rgb;
    }
    if( false )
    {
        // spatial noise reduction filter
        // if making canges to this (especially radius, C), run AvgTests - in particular, pay close
        // attention to:
        // testAvg6: don't want to make the postcard too blurry
        // testAvg8: zoom in to 60%, ensure still appears reasonably sharp
        // testAvg23: ensure we do reduce the noise, e.g., view around "vicks", without making the
        // text blurry
        // testAvg24: want to reduce the colour noise near the wall, but don't blur out detail, e.g.
        // at the flowers
        // Also need to be careful of performance.
        /*float old_value = fmax(rgb.r, rgb.g);
        old_value = fmax(old_value, rgb.b);*/
        float3 sum = 0.0;
        //float3 colour_sum = 0.0;
        // if changing the radius, may also want to change the value returned by HDRProcessor.getAvgSampleSize()
        //int radius = 16;
        //int radius = 4;
        //int radius = 3;
        //int radius = 2;
        int radius = 1;
        int count = 0;
        //float C = 0.1f*rgb.g*rgb.g;
        int sx = (x >= radius) ? x-radius : 0;
        int ex = (x < width-radius) ? x+radius : width-1;
        int sy = (y >= radius) ? y-radius : 0;
        int ey = (y < height-radius) ? y+radius : height-1;
        for(int cy=sy;cy<=ey;cy++) {
            for(int cx=sx;cx<=ex;cx++) {
                //if( cx >= 0 && cx < width && cy >= 0 && cy < height )
                {
                    float3 this_pixel = rsGetElementAt_float3(bitmap, cx, cy);
                    //colour_sum += this_pixel;
                    {
                        /*float this_value = fmax(this_pixel.r, this_pixel.g);
                        this_value = fmax(this_value, this_pixel.b);
                        if( this_value > 0.5f )
                            this_pixel *= old_value/this_value;*/
                        // use a wiener filter, so that more similar pixels have greater contribution
                        // smaller value of C means stronger filter (i.e., less averaging)
                        // see note above for details on the choice of this value
                        // if changing this, consider if we also want to change the value for the
                        // colour only spatial filtering, below
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
                        //L = 0.0f; // test no wiener filter
                        float weight = L/(L+C);
                        this_pixel = weight * rgb + (1.0-weight) * this_pixel;
                    }
                    sum += this_pixel;
                    count++;
                }
            }
        }

        rgb = sum / count;

        /*float3 rgb_luminance = sum / count; // luminance average
        rgb = colour_sum / count; // colour average

        // we apply stronger denoise to colour than for luminance
        // we shift rgb (the colour average) to match the desired luminance
        float luminance_value = fmax(rgb_luminance.r, rgb_luminance.g);
        luminance_value = fmax(luminance_value, rgb_luminance.b);
        float new_value = fmax(rgb.r, rgb.g);
        new_value = fmax(new_value, rgb.b);
        if( new_value > 0.5f )
            rgb *= luminance_value/new_value;
        //rgb += luminance_value - new_value;
        //rgb = clamp(rgb, 0.0f, 255.0f);
        // preserve value - we want denoise only for chrominance
        //if( new_value > 0.5f )
        //    rgb *= old_value/new_value;
        //rgb += old_value - new_value;
        //rgb = clamp(rgb, 0.0f, 255.0f);
        */
    }
    //if( false )
    {
        // spatial noise reduction filter, colour only
        // if changing this, see list of tests under standard spatial noise reduction above
        float old_value = fmax(rgb.r, rgb.g);
        old_value = fmax(old_value, rgb.b);
        float3 sum = 0.0;
        int radius = 3;
        int count = 0;
        int sx = (x >= radius) ? x-radius : 0;
        int ex = (x < width-radius) ? x+radius : width-1;
        int sy = (y >= radius) ? y-radius : 0;
        int ey = (y < height-radius) ? y+radius : height-1;
        for(int cy=sy;cy<=ey;cy++) {
            for(int cx=sx;cx<=ex;cx++) {
                //if( cx >= 0 && cx < width && cy >= 0 && cy < height )
                {
                    float3 this_pixel = rsGetElementAt_float3(bitmap, cx, cy);
                    {
                        float this_value = fmax(this_pixel.r, this_pixel.g);
                        this_value = fmax(this_value, this_pixel.b);
                        if( this_value > 0.5f )
                            this_pixel *= old_value/this_value;
                        // use a wiener filter, so that more similar pixels have greater contribution
                        // smaller value of C means stronger filter (i.e., less averaging)
                        // for now set at same value as standard spatial filter above
                        const float C = 64.0f*64.0f/8.0f;
                        float3 diff = rgb - this_pixel;
                        float L = dot(diff, diff);
                        //L = 0.0f; // test no wiener filter
                        float weight = L/(L+C);

                        /*{
                            int ix = (int)x;
                            int iy = (int)y;
                            int dx = cx - ix;
                            int dy = cy - iy;
                            // also take distance into account
                            float factor_c = 32.0;
                            float dist2 = dx*dx + dy*dy;
                            dist2 = 32.0;
                            float radius_weight = exp(-dist2/factor_c);
                            weight = 1.0 - weight;
                            weight *= radius_weight;
                            weight = 1.0 - weight;
                        }*/

                        this_pixel = weight * rgb + (1.0-weight) * this_pixel;
                    }
                    sum += this_pixel;
                    count++;
                }
            }
        }

        rgb = sum / count;
    }

    //if( false )
    {
        // sharpen
        // helps: testAvg12, testAvg16, testAvg23, testAvg30, testAvg32
        if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
            float3 p00 = rsGetElementAt_float3(bitmap, x-1, y-1);
            float3 p10 = rsGetElementAt_float3(bitmap, x, y-1);
            float3 p20 = rsGetElementAt_float3(bitmap, x+1, y-1);

            float3 p01 = rsGetElementAt_float3(bitmap, x-1, y);
            float3 p21 = rsGetElementAt_float3(bitmap, x+1, y);

            float3 p02 = rsGetElementAt_float3(bitmap, x-1, y+1);
            float3 p12 = rsGetElementAt_float3(bitmap, x, y+1);
            float3 p22 = rsGetElementAt_float3(bitmap, x+1, y+1);

            float3 blurred = (p00 + p10 + p20 + p01 + 8.0f*rgb + p21 + p02 + p12 + p22)/16.0f;
            float3 shift = 1.5f * (rgb-blurred);
            const float threshold2 = 8*8;
            if( dot(shift, shift) > threshold2 )
            {
                rgb += shift;
            }

            //float3 smooth = p00 + 2.0f*p10 + p20 + 2.0f*p01 + 4.0f*rgb + 2.0f*p21 + p02 + 2.0f*p12 + p22;
            //rgb += 1.0f * ( rgb - smooth/16.0f );

            rgb = clamp(rgb, 0.0f, 255.0f);
        }
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

    // apply combination of gain and gamma
    /*
    rgb *= gain;
    float3 hdr = rgb;
    float value = fmax(hdr.r, hdr.g);
    value = fmax(value, hdr.b);
    if( value >= 0.5f ) {
        float new_value = powr(value/255.0f, gamma) * 255.0f;
        float gamma_scale = new_value / value;
        hdr *= gamma_scale;
    }
    */

    // apply piecewise function of gain vs gamma
    float3 hdr = rgb;
    float value = fmax(hdr.r, hdr.g);
    value = fmax(value, hdr.b);
    //hdr *= gain;
    //hdr *= 2.0f;
    //const float alpha = 0.7f;
    //const saturation_level = 10;
    if( value <= low_x ) {
        // don't scale
    }
    else if( value <= mid_x ) {
        //float alpha = (value-low_x)/(mid_x-low_x);
        //float new_value = (1.0-alpha)*low_x + alpha*gain*mid_x;
        // gain_A and gain_B should be set so that new_value meets the commented out code above
        // This code is critical for performance!
        //float new_value = gain_A * value + gain_B;
        //hdr *= new_value/value;

        hdr *= (gain_A + gain_B/value);

        //float new_value = gain_A * value + gain_B;
        //float shift = new_value - value;
        //hdr += shift;
        //hdr = (1.0f-alpha)*(hdr+shift) + alpha*hdr*new_value/value;
        //hdr = (hdr+saturation_level)*new_value/(value+saturation_level);
    }
    else {
        float new_value = powr(value/max_x, gamma) * 255.0f;

        float gamma_scale = new_value / value;
        hdr *= gamma_scale;

        //float shift = new_value - value;
        //hdr += shift;
        //hdr = (1.0f-alpha)*(hdr+shift) + alpha*hdr*new_value/value;
        //hdr = (hdr+saturation_level)*new_value/(value+saturation_level);
    }

    // apply gamma correction
    //hdr = powr(hdr/255.0f, 0.454545454545f) * 255.0f;

	uchar4 out;
    out.rgb = convert_uchar3(clamp(hdr+0.5f, 0.f, 255.f));
    out.a = 255;

    return out;
}
