#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

rs_allocation c_histogram;

uchar4 __attribute__((kernel)) histogram_adjust(uchar4 in, uint32_t x, uint32_t y) {
	float in_r = in.r;
	float in_g = in.g;
	float in_b = in.b;
	//float value = (in_r + in_g + in_b)/3.0f;
	float value = fmax(in_r, in_g);
	value = fmax(value, in_b);
	int cdf_v = rsGetElementAt_int(c_histogram, value);
	int cdf_0 = rsGetElementAt_int(c_histogram, 0);
	int n_pixels = rsGetElementAt_int(c_histogram, 255);
	float num = (float)(cdf_v - cdf_0);
	float den = (float)(n_pixels - cdf_0);
	int equal_value = (int)( 255.0f * (num/den) ); // value that we should choose to fully equalise the histogram
	
	const float alpha = 0.5f; // 0.0 means no change, 1.0 means fully equalise
	int new_value = (int)( (1.0f-alpha) * value + alpha * equal_value );
	
	float scale = ((float)new_value) / (float)value;

	uchar4 out;
	out.r = min(255, (int)(in.r * scale));
	out.g = min(255, (int)(in.g * scale));
	out.b = min(255, (int)(in.b * scale));
	
	/*out.r = new_value;
	out.g = new_value;
	out.b = new_value;*/
	
	return out;
}
