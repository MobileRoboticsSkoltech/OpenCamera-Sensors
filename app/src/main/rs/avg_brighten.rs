#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

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

uchar4 __attribute__((kernel)) avg_brighten(uchar4 in) {
    float3 rgb = convert_float3(in.rgb);
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
