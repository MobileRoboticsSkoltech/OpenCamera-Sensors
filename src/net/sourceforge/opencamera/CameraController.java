package net.sourceforge.opencamera;

import java.io.IOException;
import java.util.List;

import android.graphics.Rect;
import android.location.Location;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;

public abstract class CameraController {
	// for testing:
	public int count_camera_parameters_exception = 0;

	static class CameraFeatures {
		boolean is_zoom_supported = false;
		int max_zoom = 0;
		List<Integer> zoom_ratios = null;
		boolean supports_face_detection = false;
		List<CameraController.Size> picture_sizes = null;
		List<CameraController.Size> video_sizes = null;
		List<CameraController.Size> preview_sizes = null;
		boolean has_current_fps_range = false;
		int [] current_fps_range = new int[2];
		List<String> supported_flash_values = null;
		List<String> supported_focus_values = null;
		int max_num_focus_areas = 0;
		boolean is_exposure_lock_supported = false;
		boolean is_video_stabilization_supported = false;
		int min_exposure = 0;
		int max_exposure = 0;
		boolean can_disable_shutter_sound = false;
	}
	
	public static class Size {
		public int width = 0;
		public int height = 0;
		
		public Size(int width, int height) {
			this.width = width;
			this.height = height;
		}
		
		public boolean equals(Size that) {
			return this.width == that.width && this.height == that.height;
		}
	}
	
	static class Area {
		public Rect rect = null;
		public int weight = 0;
		
		public Area(Rect rect, int weight) {
			this.rect = rect;
			this.weight = weight;
		}
	}
	
	static interface FaceDetectionListener {
		public abstract void onFaceDetection(Face[] faces);
	}
	
	static interface PictureCallback {
		public abstract void onPictureTaken(byte[] data);
	}
	
	static interface AutoFocusCallback {
		public abstract void onAutoFocus(boolean success);
	}
	
	static class Face {
		public int score = 0;
		public Rect rect = null;

		Face(int score, Rect rect) {
			this.score = score;
			this.rect = rect;
		}
	}
	
	class SupportedValues {
		List<String> values = null;
		String selected_value = null;
		SupportedValues(List<String> values, String selected_value) {
			this.values = values;
			this.selected_value = selected_value;
		}
	}

	abstract void release();

	abstract CameraFeatures getCameraFeatures();
	abstract SupportedValues setSceneMode(String value);
	public abstract String getSceneMode();
	abstract SupportedValues setColorEffect(String value);
	public abstract String getColorEffect();
	abstract SupportedValues setWhiteBalance(String value);
	public abstract String getWhiteBalance();
	abstract SupportedValues setISO(String value);
    abstract String getISOKey();
    public abstract CameraController.Size getPictureSize();
    abstract void setPictureSize(int width, int height);
    public abstract CameraController.Size getPreviewSize();
    abstract void setPreviewSize(int width, int height);
	abstract void setVideoStabilization(boolean enabled);
	public abstract boolean getVideoStabilization();
	abstract public int getJpegQuality();
	abstract void setJpegQuality(int quality);
	abstract public int getZoom();
	abstract void setZoom(int value);
	abstract int getExposureCompensation();
	abstract boolean setExposureCompensation(int new_exposure);
	abstract void setPreviewFpsRange(int min, int max);
	abstract void getPreviewFpsRange(int [] fps_range);
	abstract List<int []> getSupportedPreviewFpsRange();

	public abstract String getDefaultSceneMode();
	public abstract String getDefaultColorEffect();
	public abstract String getDefaultWhiteBalance();
	public abstract String getDefaultISO();

	abstract void setFocusValue(String focus_value);
	abstract public String getFocusValue();
	abstract void setFlashValue(String flash_value);
	abstract public String getFlashValue();
	abstract void setRecordingHint(boolean hint);
	abstract void setAutoExposureLock(boolean enabled);
	abstract public boolean getAutoExposureLock();
	abstract void setRotation(int rotation);
	abstract void setLocationInfo(Location location);
	abstract void removeLocationInfo();
	abstract void enableShutterSound(boolean enabled);
	abstract boolean setFocusAndMeteringArea(List<CameraController.Area> areas);
	abstract void clearFocusAndMetering();
	public abstract List<CameraController.Area> getFocusAreas();
	public abstract List<CameraController.Area> getMeteringAreas();
	abstract boolean supportsAutoFocus();
	abstract boolean focusIsVideo();
	abstract void reconnect() throws IOException;
	abstract void setPreviewDisplay(SurfaceHolder holder) throws IOException;
	abstract void startPreview();
	abstract void stopPreview();
	public abstract boolean startFaceDetection();
	abstract void setFaceDetectionListener(final CameraController.FaceDetectionListener listener);
	abstract void autoFocus(final CameraController.AutoFocusCallback cb);
	abstract void cancelAutoFocus();
	abstract void takePicture(final CameraController.PictureCallback raw, final CameraController.PictureCallback jpeg);
	abstract void setDisplayOrientation(int degrees);
	abstract int getDisplayOrientation();
	abstract int getCameraOrientation();
	abstract boolean isFrontFacing();
	abstract void unlock();
	abstract void initVideoRecorder(MediaRecorder video_recorder);
	abstract String getParametersString();
}
