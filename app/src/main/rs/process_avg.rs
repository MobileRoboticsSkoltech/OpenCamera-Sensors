#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap_new;

int offset_x_new = 0, offset_y_new = 0;
float avg_factor = 1.0f;
float wiener_C = 1024.0f;

float3 __attribute__((kernel)) avg_f(float3 pixel_avg_f, uint32_t x, uint32_t y) {
    int32_t ix = x;
    int32_t iy = y;
    uchar4 pixel_new;

	if( ix+offset_x_new >= 0 && iy+offset_y_new >= 0 && ix+offset_x_new < rsAllocationGetDimX(bitmap_new) && iy+offset_y_new < rsAllocationGetDimY(bitmap_new) ) {
    	pixel_new = rsGetElementAt_uchar4(bitmap_new, x+offset_x_new, y+offset_y_new);
	}
	else {
	    return pixel_avg_f;
	    //return convert_float3(pixel_avg.rgb);
	    //return convert_uchar4(pixel_avg);
	}

    float3 pixel_new_f = convert_float3(pixel_new.rgb);

    {
        // temporal merging
        //const float C = 32.0f*32.0f;
        float3 diff = pixel_avg_f - pixel_new_f;
        float L = dot(diff, diff);
        /*if( L > C ) {
            // error too large, so no contribution for new image pixel
            return pixel_avg_f;
        }*/
        float weight = L/(L+wiener_C);
        pixel_new_f = weight * pixel_avg_f + (1.0-weight) * pixel_new_f;
    }

    pixel_avg_f = (avg_factor*pixel_avg_f + pixel_new_f)/(avg_factor+1.0f);

	/*uchar4 out;
    out.r = (uchar)clamp(pixel_avg_f.r+0.5f, 0.0f, 255.0f);
    out.g = (uchar)clamp(pixel_avg_f.g+0.5f, 0.0f, 255.0f);
    out.b = (uchar)clamp(pixel_avg_f.b+0.5f, 0.0f, 255.0f);
    out.a = 255;

	return out;*/
	return pixel_avg_f;
}

float3 __attribute__((kernel)) avg(uchar4 pixel_avg, uint32_t x, uint32_t y) {
    float3 pixel_avg_f = convert_float3(pixel_avg.rgb);
    return avg_f(pixel_avg_f, x, y);
}

rs_allocation bitmap1;
rs_allocation bitmap2;
rs_allocation bitmap3;
rs_allocation bitmap4;
rs_allocation bitmap5;
rs_allocation bitmap6;
rs_allocation bitmap7;

int offset_x1 = 0, offset_y1 = 0;
int offset_x2 = 0, offset_y2 = 0;
int offset_x3 = 0, offset_y3 = 0;
int offset_x4 = 0, offset_y4 = 0;
int offset_x5 = 0, offset_y5 = 0;
int offset_x6 = 0, offset_y6 = 0;
int offset_x7 = 0, offset_y7 = 0;

static uchar4 read_aligned_pixel(rs_allocation bitmap, int32_t ix, int32_t iy, int offset_x, int offset_y, uchar4 def) {
    uchar4 out;
	if( ix+offset_x >= 0 && iy+offset_y >= 0 && ix+offset_x < rsAllocationGetDimX(bitmap) && iy+offset_y < rsAllocationGetDimY(bitmap) ) {
    	out = rsGetElementAt_uchar4(bitmap, ix+offset_x, iy+offset_y);
	}
	else {
    	out = def;
	}
	return out;
}

uchar4 __attribute__((kernel)) avg_multi(uchar4 in, uint32_t x, uint32_t y) {
    int32_t ix = x;
    int32_t iy = y;
    uchar4 pixel0 = in;
    uchar4 pixel1 = read_aligned_pixel(bitmap1, ix, iy, offset_x1, offset_y1, in);
    uchar4 pixel2 = read_aligned_pixel(bitmap2, ix, iy, offset_x2, offset_y2, in);
    uchar4 pixel3 = read_aligned_pixel(bitmap3, ix, iy, offset_x3, offset_y3, in);
    uchar4 pixel4 = read_aligned_pixel(bitmap4, ix, iy, offset_x4, offset_y4, in);
    uchar4 pixel5 = read_aligned_pixel(bitmap5, ix, iy, offset_x5, offset_y5, in);
    uchar4 pixel6 = read_aligned_pixel(bitmap6, ix, iy, offset_x6, offset_y6, in);
    uchar4 pixel7 = read_aligned_pixel(bitmap7, ix, iy, offset_x7, offset_y7, in);

	float3 result = convert_float3(pixel0.rgb);
	result += convert_float3(pixel1.rgb);
	result += convert_float3(pixel2.rgb);
	result += convert_float3(pixel3.rgb);
	result += convert_float3(pixel4.rgb);
	result += convert_float3(pixel5.rgb);
	result += convert_float3(pixel6.rgb);
	result += convert_float3(pixel7.rgb);

	result /= 8.0f;

	uchar4 out;
    out.r = (uchar)clamp(result.r+0.5f, 0.0f, 255.0f);
    out.g = (uchar)clamp(result.g+0.5f, 0.0f, 255.0f);
    out.b = (uchar)clamp(result.b+0.5f, 0.0f, 255.0f);
    out.a = 255;

	return out;
}
