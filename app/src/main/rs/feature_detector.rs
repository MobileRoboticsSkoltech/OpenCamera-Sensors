#pragma version(1)
#pragma rs java_package_name(net.sourceforge.opencamera)
#pragma rs_fp_relaxed

float corner_threshold;

rs_allocation bitmap; // input bitmap

rs_allocation bitmap_Ix; // bitmap for Ix derivatives
rs_allocation bitmap_Iy; // bitmap for Iy derivatives

uchar __attribute__((kernel)) create_greyscale(uchar4 in, uint32_t x, uint32_t y) {
    //uchar value = max(in.r, in.g);
    //value = max(value, in.b);
    uchar value = 0.3*in.r + 0.59*in.g + 0.11*in.b;
    return value;
}

void __attribute__((kernel)) compute_derivatives(uchar in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    uchar Ix = 0;
    uchar Iy = 0;
    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
        // use Sobel operator

        int pixel0 = rsGetElementAt_uchar(bitmap, x-1, y-1);
        int pixel1 = rsGetElementAt_uchar(bitmap, x, y-1);
        int pixel2 = rsGetElementAt_uchar(bitmap, x+1, y-1);
        int pixel3 = rsGetElementAt_uchar(bitmap, x-1, y);
        int pixel5 = rsGetElementAt_uchar(bitmap, x+1, y);
        int pixel6 = rsGetElementAt_uchar(bitmap, x-1, y+1);
        int pixel7 = rsGetElementAt_uchar(bitmap, x, y+1);
        int pixel8 = rsGetElementAt_uchar(bitmap, x+1, y+1);

        //int iIx = (pixel2 + 2*pixel5 + pixel8) - (pixel0 + 2*pixel3 + pixel6);
        //int iIy = (pixel6 + 2*pixel7 + pixel8) - (pixel0 + 2*pixel1 + pixel2);
        //iIx /= 8;
        //iIy /= 8;
        int iIx = (pixel5 - pixel3)/2;
        int iIy = (pixel7 - pixel1)/2;

        // convert so we can store as a uchar

        iIx = max(iIx, -127);
        iIx = min(iIx, 128);
        iIx += 127; // iIx now runs from 0 to 255

        iIy = max(iIy, -127);
        iIy = min(iIy, 128);
        iIy += 127; // iIy now runs from 0 to 255

        Ix = iIx;
        Iy = iIy;
    }
    rsSetElementAt_uchar(bitmap_Ix, Ix, x, y);
    rsSetElementAt_uchar(bitmap_Iy, Iy, x, y);
}

float __attribute__((kernel)) corner_detector(uchar in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    float out = 0;

    const int radius = 2; // radius for corner detector
    const float weights[2*radius+1] = {1, 4, 6, 4, 1};

    // extra +1 as we won't have derivative info for the outermost pixels (see compute_derivatives)
    if( x >= radius+1 && x < width-radius-1 && y >= radius+1 && y < height-radius-1 ) {
        float h00 = 0.0;
        float h01 = 0.0;
        float h11 = 0.0;
        for(int cy=y-radius;cy<=y+radius;cy++) {
            for(int cx=x-radius;cx<=x+radius;cx++) {
                //int pixel_l = rsGetElementAt_uchar(bitmap, cx-1, cy);
                //int pixel_r = rsGetElementAt_uchar(bitmap, cx+1, cy);
                //int pixel_u = rsGetElementAt_uchar(bitmap, cx, cy-1);
                //int pixel_d = rsGetElementAt_uchar(bitmap, cx, cy+1);
                //int Ix = (pixel_r - pixel_l)/2;
                //int Iy = (pixel_d - pixel_u)/2;
                int Ix = rsGetElementAt_uchar(bitmap_Ix, cx, cy);
                int Iy = rsGetElementAt_uchar(bitmap_Iy, cx, cy);
                // convert from 0-255 to -127 - +128:
                Ix -= 127;
                Iy -= 127;

                int ix = (int)x;
                int iy = (int)y;
                int dx = cx - ix;
                int dy = cy - iy;
                /*float dist2 = dx*dx + dy*dy;
                const float sigma2 = 0.25f;
                float weight = exp(-dist2/(2.0f*sigma2)) / (6.28318530718f*sigma2);
                //float weight = 1.0;
                weight /= 65025.0f; // scale from (0, 255) to (0, 1)
                */
                float weight = weights[2+dx] * weights[2+dy];
                //weight = 36;

                h00 += weight*Ix*Ix;
                h01 += weight*Ix*Iy;
                h11 += weight*Iy*Iy;
            }
        }

        float det_H = h00*h11 - h01*h01;
        float tr_H = h00 + h11;
        //out = det_H - 0.1*tr_H*tr_H;
        out = det_H - 0.06*tr_H*tr_H;
    }

    return out;
}

uchar __attribute__((kernel)) local_maximum(float in, uint32_t x, uint32_t y) {
    int width = rsAllocationGetDimX(bitmap);
    int height = rsAllocationGetDimY(bitmap);

    uchar out = 0;

    if( in >= corner_threshold ) {
        //out = 255;
        // best of 3x3:
        /*if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
            if( in > rsGetElementAt_float(bitmap, x-1, y-1) &&
                in > rsGetElementAt_float(bitmap, x, y-1) &&
                in > rsGetElementAt_float(bitmap, x+1, y-1) &&

                in > rsGetElementAt_float(bitmap, x-1, y) &&
                in > rsGetElementAt_float(bitmap, x+1, y) &&

                in > rsGetElementAt_float(bitmap, x-1, y+1) &&
                in > rsGetElementAt_float(bitmap, x, y+1) &&
                in > rsGetElementAt_float(bitmap, x+1, y+1)
                ) {
                out = 255;
            }
        }*/
        // best of 5x5:
        if( x >= 2 && x < width-2 && y >= 2 && y < height-2 ) {
            if( in > rsGetElementAt_float(bitmap, x-2, y-2) &&
                in > rsGetElementAt_float(bitmap, x-1, y-2) &&
                in > rsGetElementAt_float(bitmap, x, y-2) &&
                in > rsGetElementAt_float(bitmap, x+1, y-2) &&
                in > rsGetElementAt_float(bitmap, x+2, y-2) &&

                in > rsGetElementAt_float(bitmap, x-2, y-1) &&
                in > rsGetElementAt_float(bitmap, x-1, y-1) &&
                in > rsGetElementAt_float(bitmap, x, y-1) &&
                in > rsGetElementAt_float(bitmap, x+1, y-1) &&
                in > rsGetElementAt_float(bitmap, x+2, y-1) &&

                in > rsGetElementAt_float(bitmap, x-2, y) &&
                in > rsGetElementAt_float(bitmap, x-1, y) &&
                in > rsGetElementAt_float(bitmap, x+1, y) &&
                in > rsGetElementAt_float(bitmap, x+2, y) &&

                in > rsGetElementAt_float(bitmap, x-2, y+1) &&
                in > rsGetElementAt_float(bitmap, x-1, y+1) &&
                in > rsGetElementAt_float(bitmap, x, y+1) &&
                in > rsGetElementAt_float(bitmap, x+1, y+1) &&
                in > rsGetElementAt_float(bitmap, x+2, y+1) &&

                in > rsGetElementAt_float(bitmap, x-2, y+2) &&
                in > rsGetElementAt_float(bitmap, x-1, y+2) &&
                in > rsGetElementAt_float(bitmap, x, y+2) &&
                in > rsGetElementAt_float(bitmap, x+1, y+2) &&
                in > rsGetElementAt_float(bitmap, x+2, y+2)
                ) {
                out = 255;
            }
        }
    }

    return out;
}
