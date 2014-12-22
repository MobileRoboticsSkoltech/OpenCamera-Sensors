package net.sourceforge.opencamera;

import java.io.IOException;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
	private static final String TAG = "CameraController2";
	private CameraDevice camera = null;
	
	public CameraController2(Context context, int cameraId) {
		class MyStateCallback extends CameraDevice.StateCallback {

			@Override
			public void onOpened(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera opened");
				CameraController2.this.camera = camera;
			}

			@Override
			public void onDisconnected(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera disconnected");
				CameraController2.this.camera = null;
			}

			@Override
			public void onError(CameraDevice camera, int error) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera error: " + error);
				CameraController2.this.camera = null;
			}
		};
		MyStateCallback myStateCallback = new MyStateCallback();

		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			String cameraIdS = manager.getCameraIdList()[cameraId];
			manager.openCamera(cameraIdS, myStateCallback, null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			// throw as a RuntimeException instead, as this is what callers will catch
			throw new RuntimeException();
		}
	}

	@Override
	void release() {
		// TODO Auto-generated method stub

	}

	@Override
	CameraFeatures getCameraFeatures() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setSceneMode(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSceneMode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setColorEffect(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getColorEffect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setWhiteBalance(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getWhiteBalance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setISO(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	String getISOKey() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Size getPictureSize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setPictureSize(int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public Size getPreviewSize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setPreviewSize(int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	void setVideoStabilization(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean getVideoStabilization() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getJpegQuality() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	void setJpegQuality(int quality) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getZoom() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	void setZoom(int value) {
		// TODO Auto-generated method stub

	}

	@Override
	int getExposureCompensation() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	boolean setExposureCompensation(int new_exposure) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void setPreviewFpsRange(int min, int max) {
		// TODO Auto-generated method stub

	}

	@Override
	void getPreviewFpsRange(int[] fps_range) {
		// TODO Auto-generated method stub

	}

	@Override
	List<int[]> getSupportedPreviewFpsRange() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultSceneMode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultColorEffect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultWhiteBalance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultISO() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setFocusValue(String focus_value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getFocusValue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setFlashValue(String flash_value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getFlashValue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setRecordingHint(boolean hint) {
		// TODO Auto-generated method stub

	}

	@Override
	void setAutoExposureLock(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean getAutoExposureLock() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void setRotation(int rotation) {
		// TODO Auto-generated method stub

	}

	@Override
	void setLocationInfo(Location location) {
		// TODO Auto-generated method stub

	}

	@Override
	void removeLocationInfo() {
		// TODO Auto-generated method stub

	}

	@Override
	void enableShutterSound(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	boolean setFocusAndMeteringArea(List<Area> areas) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void clearFocusAndMetering() {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Area> getFocusAreas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Area> getMeteringAreas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	boolean supportsAutoFocus() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	boolean focusIsVideo() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void reconnect() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	void setPreviewDisplay(SurfaceHolder holder) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	void startPreview() {
		// TODO Auto-generated method stub

	}

	@Override
	void stopPreview() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean startFaceDetection() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void setFaceDetectionListener(FaceDetectionListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	void autoFocus(AutoFocusCallback cb) {
		// TODO Auto-generated method stub

	}

	@Override
	void cancelAutoFocus() {
		// TODO Auto-generated method stub

	}

	@Override
	void takePicture(PictureCallback raw, PictureCallback jpeg) {
		// TODO Auto-generated method stub

	}

	@Override
	void setDisplayOrientation(int degrees) {
		// TODO Auto-generated method stub

	}

	@Override
	int getDisplayOrientation() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	int getCameraOrientation() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	boolean isFrontFacing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void unlock() {
		// TODO Auto-generated method stub

	}

	@Override
	void initVideoRecorder(MediaRecorder video_recorder) {
		// TODO Auto-generated method stub

	}

	@Override
	String getParametersString() {
		// TODO Auto-generated method stub
		return null;
	}

}
