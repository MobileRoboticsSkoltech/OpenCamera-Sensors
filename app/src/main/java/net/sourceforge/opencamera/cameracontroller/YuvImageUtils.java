package net.sourceforge.opencamera.cameracontroller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Log;

import androidx.annotation.RequiresApi;

import net.sourceforge.opencamera.MyDebug;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * Class for manipulations with YUV images.
 */
public class YuvImageUtils implements Closeable {
    private final static String TAG = "YuvImageUtils";
    private final RenderScript mRenderScript;
    private final ScriptIntrinsicYuvToRGB mYuvToRgb;

    public YuvImageUtils(Context context) {
        mRenderScript = RenderScript.create(context);
        mYuvToRgb = ScriptIntrinsicYuvToRGB.create(mRenderScript, Element.U8_4(mRenderScript));
    }

    /**
     * Converts byte array with NV21 data to Bitmap using yuvToRgb Renderscript intrinsic
     */
    public Bitmap yuv420ToBitmap(byte[] imageData, int width, int height, Context context) {
        Allocation aIn = Allocation.createSized(mRenderScript, Element.U8(mRenderScript), imageData.length, Allocation.USAGE_SCRIPT);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation aOut = Allocation.createFromBitmap(mRenderScript, bitmap);
        aIn.copyFrom(imageData);
        mYuvToRgb.setInput(aIn);
        mYuvToRgb.forEach(aOut);
        aOut.copyTo(bitmap);
        aOut.destroy();
        aIn.destroy();

        return bitmap;
    }

    // Method taken from this answer:
    // https://stackoverflow.com/questions/44022062/converting-yuv-420-888-to-jpeg-and-saving-file-results-distorted-image
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static byte[] Yuv420ImageToNv21(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

    @Override
    public void close() {
        if (MyDebug.LOG) {
            Log.d(TAG, "Closing YuvUtils");
        }
        if (mRenderScript != null) {
            mRenderScript.destroy();
        }
    }
}
