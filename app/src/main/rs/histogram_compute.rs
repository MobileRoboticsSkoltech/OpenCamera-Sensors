#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

int32_t *histogram;

void init_histogram() {
	for(int i=0;i<256;i++)
		histogram[i] = 0;
}

void __attribute__((kernel)) histogram_compute(uchar4 in, uint32_t x, uint32_t y) {
	// We compute a histogram based on the max RGB value, so this matches with the scaling we do in histogram_adjust.rs.
	// This improves the look of the grass in testHDR24, testHDR27.
	uchar value = max(in.r, in.g);
	value = max(value, in.b);

	rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_f(float3 in_f, uint32_t x, uint32_t y) {
	// We compute a histogram based on the max RGB value, so this matches with the scaling we do in histogram_adjust.rs.
	// This improves the look of the grass in testHDR24, testHDR27.
    uchar3 in;
    in.r = (uchar)clamp(in_f.r+0.5f, 0.0f, 255.0f);
    in.g = (uchar)clamp(in_f.g+0.5f, 0.0f, 255.0f);
    in.b = (uchar)clamp(in_f.b+0.5f, 0.0f, 255.0f);

	uchar value = max(in.r, in.g);
	value = max(value, in.b);

	rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_avg(uchar4 in, uint32_t x, uint32_t y) {
    float3 in_f = convert_float3(in.rgb);
    float avg = (in_f.r + in_f.g + in_f.b)/3.0;
    uchar value = (int)(avg+0.5); // round to nearest
	value = min(value, (uchar)255); // just in case

	rsAtomicInc(&histogram[value]);
}

void __attribute__((kernel)) histogram_compute_avg_f(float3 in_f, uint32_t x, uint32_t y) {
    float avg = (in_f.r + in_f.g + in_f.b)/3.0;
    uchar value = (int)(avg+0.5); // round to nearest
	value = min(value, (uchar)255); // just in case

	rsAtomicInc(&histogram[value]);
}
