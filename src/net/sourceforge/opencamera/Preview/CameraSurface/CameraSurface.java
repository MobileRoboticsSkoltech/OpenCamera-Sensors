package net.sourceforge.opencamera.Preview.CameraSurface;

import net.sourceforge.opencamera.CameraController.CameraController;

import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.view.View;

public interface CameraSurface {
	abstract View getView();
	abstract void setPreviewDisplay(CameraController camera_controller); // n.b., uses double-dispatch similar to Visitor pattern - behaviour depends on type of CameraSurface and CameraController
	abstract void setVideoRecorder(MediaRecorder video_recorder);
	abstract void setTransform(Matrix matrix);
}
