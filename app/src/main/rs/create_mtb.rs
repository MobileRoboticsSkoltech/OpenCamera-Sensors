#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

int median_value = 0;

uchar4 __attribute__((kernel)) create_mtb(uchar4 in, uint32_t x, uint32_t y) {
	uchar value = max(in.r, in.g);
	value = max(value, in.b);

    uchar4 out;
    if( value <= median_value )
        out.r = 0;
    else
        out.r = 255;
	out.g = 0;
	out.b = 0;
	out.a = 255;
	return out;
}
