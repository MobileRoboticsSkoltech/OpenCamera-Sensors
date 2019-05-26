package net.sourceforge.opencamera.preview.camerasurface;

import net.sourceforge.opencamera.cameracontroller.CameraController;

import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.view.View;

/** Provides support for the surface used for the preview - this can either be
 *  a SurfaceView or a TextureView.
 */
public interface CameraSurface {
    View getView();
    void setPreviewDisplay(CameraController camera_controller); // n.b., uses double-dispatch similar to Visitor pattern - behaviour depends on type of CameraSurface and CameraController
    void setVideoRecorder(MediaRecorder video_recorder);
    void setTransform(Matrix matrix);
    void onPause();
    void onResume();
}
