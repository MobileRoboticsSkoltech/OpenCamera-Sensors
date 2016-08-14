#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation bitmap1;
rs_allocation bitmap2;

float parameter_A0 = 1.0f;
float parameter_B0 = 0.0f;
float parameter_A1 = 1.0f;
float parameter_B1 = 0.0f;
float parameter_A2 = 1.0f;
float parameter_B2 = 0.0f;

const float weight_scale_c = (float)((1.0-1.0/127.5)/127.5);
float tonemap_scale = 1.0f;

uchar4 __attribute__((kernel)) hdr(uchar4 in, uint32_t x, uint32_t y) {
	const int n_bitmaps = 3;
	uchar4 pixels[n_bitmaps];
	pixels[0] = in;
	pixels[1] = rsGetElementAt_uchar4(bitmap1, x, y);
	pixels[2] = rsGetElementAt_uchar4(bitmap2, x, y);
	
	float parameter_A[n_bitmaps];
	float parameter_B[n_bitmaps];
	parameter_A[0] = parameter_A0;
	parameter_B[0] = parameter_B0;
	parameter_A[1] = parameter_A1;
	parameter_B[1] = parameter_B1;
	parameter_A[2] = parameter_A2;
	parameter_B[2] = parameter_B2;
	
	float hdr_r = 0.0f;
	float hdr_g = 0.0f;
	float hdr_b = 0.0f;
	float sum_weight = 0.0f;

	// calculateHDR	
	for(int i=0;i<n_bitmaps;i++) {
		float r = (float)pixels[i].r;
		float g = (float)pixels[i].g;
		float b = (float)pixels[i].b;
		float avg = (r+g+b) / 3.0f;
		// weight_scale_c chosen so that 0 and 255 map to a non-zero weight of 1.0/127.5
		float weight = 1.0f - weight_scale_c * fabs( 127.5f - avg );

		// response function
		r = parameter_A[i] * r + parameter_B[i];
		g = parameter_A[i] * g + parameter_B[i];
		b = parameter_A[i] * b + parameter_B[i];

		hdr_r += weight * r;
		hdr_g += weight * g;
		hdr_b += weight * b;
		sum_weight += weight;
	}
	hdr_r /= sum_weight;
	hdr_g /= sum_weight;
	hdr_b /= sum_weight;

	// tonemap
	float max_hdr = hdr_r;
	if( hdr_g > max_hdr )
		max_hdr = hdr_g;
	if( hdr_b > max_hdr )
		max_hdr = hdr_b;
	float scale = 255.0f / ( tonemap_scale + max_hdr );
	uchar4 out;
	out.r = (uchar)(scale * hdr_r);
	out.g = (uchar)(scale * hdr_g);
	out.b = (uchar)(scale * hdr_b);
	out.a = 255;
	return out;
}
