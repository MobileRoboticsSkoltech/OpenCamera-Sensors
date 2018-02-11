package net.sourceforge.opencamera.CameraController;

import android.annotation.TargetApi;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.os.Build;
import android.util.Log;

import net.sourceforge.opencamera.MyDebug;

import java.io.IOException;
import java.io.OutputStream;

/** Wrapper class to store DngCreator and Image.
 */
public class RawImage {
	private static final String TAG = "RawImage";

    private final DngCreator dngCreator;
    private final Image image;

    public RawImage(DngCreator dngCreator, Image image) {
        this.dngCreator = dngCreator;
        this.image = image;
    }

    /** Writes the dng file to the supplied output.
     */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void writeImage(OutputStream dngOutput) throws IOException {
		if( MyDebug.LOG )
			Log.d(TAG, "writeImage");
        dngCreator.writeImage(dngOutput, image);
    }

    /** Closes the image. Must be called to free up resources when no longer needed. After calling
     *  this method, this object should not be used.
     */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void close() {
		if( MyDebug.LOG )
			Log.d(TAG, "close");
        image.close();
        dngCreator.close();
    }
}
