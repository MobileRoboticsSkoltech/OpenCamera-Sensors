package net.sourceforge.opencamera.CameraController;

import net.sourceforge.opencamera.MyDebug;

import java.util.List;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.Image;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;

/** CameraController is an abstract class that wraps up the access/control to
 *  the Android camera, so that the rest of the application doesn't have to
 *  deal directly with the Android camera API. It also allows us to support
 *  more than one camera API through the same API (this is used to support both
 *  the original camera API, and Android 5's Camera2 API).
 *  The class is fairly low level wrapper about the APIs - there is some
 *  additional logical/workarounds where such things are API-specific, but
 *  otherwise the calling application still controls the behaviour of the
 *  camera.
 */
public abstract class CameraController {
	private static final String TAG = "CameraController";
	int cameraId = 0;

	public static final long EXPOSURE_TIME_DEFAULT = 1000000000l/30;

	// for testing:
	public int count_camera_parameters_exception = 0;
	public int count_precapture_timeout = 0;
	public boolean test_wait_capture_result = false; // whether to test delayed capture result in Camera2 API

	public static class CameraFeatures {
		public boolean is_zoom_supported = false;
		public int max_zoom = 0;
		public List<Integer> zoom_ratios = null;
		public boolean supports_face_detection = false;
		public List<CameraController.Size> picture_sizes = null;
		public List<CameraController.Size> video_sizes = null;
		public List<CameraController.Size> preview_sizes = null;
		public List<String> supported_flash_values = null;
		public List<String> supported_focus_values = null;
		public int max_num_focus_areas = 0;
		public float minimum_focus_distance = 0.0f;
		public boolean is_exposure_lock_supported = false;
		public boolean is_video_stabilization_supported = false;
		public boolean supports_iso_range = false;
		public int min_iso = 0;
		public int max_iso = 0;
		public boolean supports_exposure_time = false;
		public long min_exposure_time = 0l;
		public long max_exposure_time = 0l;
		public int min_exposure = 0;
		public int max_exposure = 0;
		public float exposure_step = 0.0f;
		public boolean can_disable_shutter_sound = false;
		public boolean supports_expo_bracketing = false;
		public boolean supports_raw = false;
	}

	public static class Size {
		public int width = 0;
		public int height = 0;
		
		public Size(int width, int height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public boolean equals(Object o) {
			if( !(o instanceof Size) )
				return false;
			Size that = (Size)o;
			return this.width == that.width && this.height == that.height;
		}
		
		@Override
		public int hashCode() {
			// must override this, as we override equals()
			// can't use:
			//return Objects.hash(width, height);
			// as this requires API level 19
			// so use this from http://stackoverflow.com/questions/11742593/what-is-the-hashcode-for-a-custom-class-having-just-two-int-properties
			return width*31 + height;
		}
	}
	
	/** An area has values from [-1000,-1000] (for top-left) to [1000,1000] (for bottom-right) for whatever is
	 * the current field of view (i.e., taking zoom into account).
	 */
	public static class Area {
		public Rect rect = null;
		public int weight = 0;
		
		public Area(Rect rect, int weight) {
			this.rect = rect;
			this.weight = weight;
		}
	}
	
	public static interface FaceDetectionListener {
		public abstract void onFaceDetection(Face[] faces);
	}
	
	public static interface PictureCallback {
		public abstract void onCompleted(); // called after all relevant on*PictureTaken() callbacks have been called and returned
		public abstract void onPictureTaken(byte[] data);
		/** Only called if RAW is requested.
		 *  Caller should call image.close() and dngCreator.close() when done with the image.
		 */
		public abstract void onRawPictureTaken(DngCreator dngCreator, Image image);
		/** Only called if burst is requested.
		 */
		public abstract void onBurstPictureTaken(List<byte[]> images);
		/* This is called for flash_frontscreen_auto or flash_frontscreen_on mode to indicate the caller should light up the screen
		 * (for flash_frontscreen_auto it will only be called if the scene is considered dark enough to require the screen flash).
		 * The screen flash can be removed when or after onCompleted() is called.
		 */
		public abstract void onFrontScreenTurnOn();
	}
	
	public static interface AutoFocusCallback {
		public abstract void onAutoFocus(boolean success);
	}
	
	public static interface ContinuousFocusMoveCallback {
		public abstract void onContinuousFocusMove(boolean start);
	}
	
	public static interface ErrorCallback {
		public abstract void onError();
	}
	
	public static class Face {
		public int score = 0;
		/* The has values from [-1000,-1000] (for top-left) to [1000,1000] (for bottom-right) for whatever is
		 * the current field of view (i.e., taking zoom into account).
		 */
		public Rect rect = null;

		Face(int score, Rect rect) {
			this.score = score;
			this.rect = rect;
		}
	}
	
	public static class SupportedValues {
		public List<String> values = null;
		public String selected_value = null;
		SupportedValues(List<String> values, String selected_value) {
			this.values = values;
			this.selected_value = selected_value;
		}
	}

	public abstract void release();

	public CameraController(int cameraId) {
		this.cameraId = cameraId;
	}
	public abstract String getAPI();
	public abstract CameraFeatures getCameraFeatures();
	public int getCameraId() {
		return cameraId;
	}
	public abstract SupportedValues setSceneMode(String value);
	public abstract String getSceneMode();
	public abstract SupportedValues setColorEffect(String value);
	public abstract String getColorEffect();
	public abstract SupportedValues setWhiteBalance(String value);
	public abstract String getWhiteBalance();
	public abstract SupportedValues setISO(String value);
    public abstract String getISOKey();
	public abstract int getISO();
	public abstract boolean setISO(int iso);
	public abstract long getExposureTime();
	public abstract boolean setExposureTime(long exposure_time);
    public abstract CameraController.Size getPictureSize();
    public abstract void setPictureSize(int width, int height);
    public abstract CameraController.Size getPreviewSize();
    public abstract void setPreviewSize(int width, int height);
	public abstract void setExpoBracketing(boolean want_expo_bracketing);
	/** n_images must be an odd number greater than 1.
	 */
	public abstract void setExpoBracketingNImages(int n_images);
	public abstract void setExpoBracketingStops(double stops);
	public abstract void setRaw(boolean want_raw);
	/**
	 * setUseCamera2FakeFlash() should be called after creating the CameraController, and before calling getCameraFeatures() or
	 * starting the preview (as it changes the available flash modes).
	 * "Fake flash" is an alternative mode for handling flash, for devices that have poor Camera2 support - typical symptoms
	 * include precapture never starting, flash not firing, photos being over or under exposed.
	 * Instead, we fake the precapture and flash simply by turning on the torch. After turning on torch, we wait for ae to stop
	 * scanning (and af too, as it can start scanning in continuous mode) - this is effectively the equivalent of precapture -
	 * before taking the photo. 
	 * In auto-focus mode, we make the decision ourselves based on the current ISO.
	 * We also handle the flash firing for autofocus by turning the torch on and off too. Advantages are:
	 *   - The flash tends to be brighter, and the photo can end up overexposed as a result if capture follows the autofocus.
	 *   - Some devices also don't seem to fire flash for autofocus in Camera2 mode (e.g., Samsung S7)
	 *   - When capture follows autofocus, we need to make the same decision for firing flash for both the autofocus and the capture.
	 */
	public void setUseCamera2FakeFlash(boolean use_fake_precapture) {
	}
	public boolean getUseCamera2FakeFlash() {
		return false;
	}
	public abstract void setVideoStabilization(boolean enabled);
	public abstract boolean getVideoStabilization();
	public abstract int getJpegQuality();
	public abstract void setJpegQuality(int quality);
	public abstract int getZoom();
	public abstract void setZoom(int value);
	public abstract int getExposureCompensation();
	public abstract boolean setExposureCompensation(int new_exposure);
	public abstract void setPreviewFpsRange(int min, int max);
	public abstract List<int []> getSupportedPreviewFpsRange();

	public String getDefaultSceneMode() {
		return "auto"; // chosen to match Camera.Parameters.SCENE_MODE_AUTO, but we also use compatible values for Camera2 API
	}
	public String getDefaultColorEffect() {
		return "none"; // chosen to match Camera.Parameters.EFFECT_NONE, but we also use compatible values for Camera2 API
	}
	public String getDefaultWhiteBalance() {
		return "auto"; // chosen to match Camera.Parameters.WHITE_BALANCE_AUTO, but we also use compatible values for Camera2 API
	}
	public String getDefaultISO() {
		return "auto";
	}
	public abstract long getDefaultExposureTime();

	public abstract void setFocusValue(String focus_value);
	public abstract String getFocusValue();
	public abstract float getFocusDistance();
	public abstract boolean setFocusDistance(float focus_distance);
	public abstract void setFlashValue(String flash_value);
	public abstract String getFlashValue();
	public abstract void setRecordingHint(boolean hint);
	public abstract void setAutoExposureLock(boolean enabled);
	public abstract boolean getAutoExposureLock();
	public abstract void setRotation(int rotation);
	public abstract void setLocationInfo(Location location);
	public abstract void removeLocationInfo();
	public abstract void enableShutterSound(boolean enabled);
	public abstract boolean setFocusAndMeteringArea(List<CameraController.Area> areas);
	public abstract void clearFocusAndMetering();
	public abstract List<CameraController.Area> getFocusAreas();
	public abstract List<CameraController.Area> getMeteringAreas();
	public abstract boolean supportsAutoFocus();
	public abstract boolean focusIsContinuous();
	public abstract boolean focusIsVideo();
	public abstract void reconnect() throws CameraControllerException;
	public abstract void setPreviewDisplay(SurfaceHolder holder) throws CameraControllerException;
	public abstract void setPreviewTexture(SurfaceTexture texture) throws CameraControllerException;
	public abstract void startPreview() throws CameraControllerException;
	public abstract void stopPreview();
	public abstract boolean startFaceDetection();
	public abstract void setFaceDetectionListener(final CameraController.FaceDetectionListener listener);
	public abstract void autoFocus(final CameraController.AutoFocusCallback cb);
	public abstract void cancelAutoFocus();
	public abstract void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback cb);
	public abstract void takePicture(final CameraController.PictureCallback picture, final ErrorCallback error);
	public abstract void setDisplayOrientation(int degrees);
	public abstract int getDisplayOrientation();
	public abstract int getCameraOrientation();
	public abstract boolean isFrontFacing();
	public abstract void unlock();
	public abstract void initVideoRecorderPrePrepare(MediaRecorder video_recorder);
	public abstract void initVideoRecorderPostPrepare(MediaRecorder video_recorder) throws CameraControllerException;
	public abstract String getParametersString();
	public boolean captureResultIsAEScanning() {
		return false;
	}
	public boolean captureResultHasIso() {
		return false;
	}
	public int captureResultIso() {
		return 0;
	}
	public boolean captureResultHasExposureTime() {
		return false;
	}
	public long captureResultExposureTime() {
		return 0;
	}
	public boolean captureResultHasFrameDuration() {
		return false;
	}
	public long captureResultFrameDuration() {
		return 0;
	}
	public boolean captureResultHasFocusDistance() {
		return false;
	}
	public float captureResultFocusDistanceMin() {
		return 0.0f;
	}
	public float captureResultFocusDistanceMax() {
		return 0.0f;
	}

	// gets the available values of a generic mode, e.g., scene, color etc, and makes sure the requested mode is available
	protected SupportedValues checkModeIsSupported(List<String> values, String value, String default_value) {
		if( values != null && values.size() > 1 ) { // n.b., if there is only 1 supported value, we also return null, as no point offering the choice to the user (there are some devices, e.g., Samsung, that only have a scene mode of "auto")
			if( MyDebug.LOG ) {
				for(int i=0;i<values.size();i++) {
		        	Log.d(TAG, "supported value: " + values.get(i));
				}
			}
			// make sure result is valid
			if( !values.contains(value) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "value not valid!");
				if( values.contains(default_value) )
					value = default_value;
				else
					value = values.get(0);
				if( MyDebug.LOG )
					Log.d(TAG, "value is now: " + value);
			}
			return new SupportedValues(values, value);
		}
		return null;
	}
}
