#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

int median_value = 0;

uchar __attribute__((kernel)) create_mtb(uchar4 in, uint32_t x, uint32_t y) {
	uchar value = max(in.r, in.g);
	value = max(value, in.b);

    uchar out;
    if( value <= median_value )
        out = 0;
    else
        out = 255;
	return out;
}
