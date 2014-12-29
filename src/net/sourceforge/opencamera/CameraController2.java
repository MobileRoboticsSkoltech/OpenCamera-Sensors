package net.sourceforge.opencamera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
	private static final String TAG = "CameraController2";

	private Context context = null;
	private CameraDevice camera = null;
	private String cameraIdS = null;
	private CameraCaptureSession captureSession = null;
	private CaptureRequest previewRequest = null;
	private ImageReader imageReader = null;
	//private ImageReader previewImageReader = null;
	private SurfaceHolder holder = null;
	private HandlerThread thread = null; 
	Handler handler = null;
	
	public CameraController2(Context context, int cameraId) {
		if( MyDebug.LOG )
			Log.d(TAG, "create new CameraController2: " + cameraId);

		this.context = context;

		thread = new HandlerThread("CameraBackground"); 
		thread.start(); 
		handler = new Handler(thread.getLooper());

		class MyStateCallback extends CameraDevice.StateCallback {
			boolean callback_done = false;
			@Override
			public void onOpened(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera opened");
				CameraController2.this.camera = camera;
				callback_done = true;
			}

			@Override
			public void onClosed(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera closed");
			}

			@Override
			public void onDisconnected(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera disconnected");
				camera.close();
				CameraController2.this.camera = null;
				callback_done = true;
			}

			@Override
			public void onError(CameraDevice camera, int error) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera error: " + error);
				callback_done = true;
			}
		};
		MyStateCallback myStateCallback = new MyStateCallback();

		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			this.cameraIdS = manager.getCameraIdList()[cameraId];
			manager.openCamera(cameraIdS, myStateCallback, handler);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			// throw as a RuntimeException instead, as this is what callers will catch
			throw new RuntimeException();
		}

		if( MyDebug.LOG )
			Log.d(TAG, "wait until camera opened...");
		// need to wait until camera is opened
		while( !myStateCallback.callback_done ) {
		}
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera failed to open");
			throw new RuntimeException();
		}
		if( MyDebug.LOG )
			Log.d(TAG, "camera now opened");

		/*CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
	    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		imageReader = ImageReader.newInstance(camera_picture_sizes[0].getWidth(), , ImageFormat.JPEG, 2);*/
	}

	@Override
	void release() {
		if( MyDebug.LOG )
			Log.d(TAG, "release");
		if( thread != null ) {
			thread.quitSafely();
			try {
				thread.join();
				thread = null;
				handler = null;
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		if( captureSession != null ) {
			captureSession.close();
			captureSession = null;
		}
		if( camera != null ) {
			camera.close();
			camera = null;
		}
		if( imageReader != null ) {
			imageReader.close();
			imageReader = null;
		}
		/*if( previewImageReader != null ) {
			previewImageReader.close();
			previewImageReader = null;
		}*/
	}

	@Override
	CameraFeatures getCameraFeatures() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCameraFeatures()");
	    CameraFeatures camera_features = new CameraFeatures();
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
		    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
		    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

		    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
			camera_features.picture_sizes = new ArrayList<CameraController.Size>();
			for(android.util.Size camera_size : camera_picture_sizes) {
				camera_features.picture_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
			}

		    android.util.Size [] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
			camera_features.video_sizes = new ArrayList<CameraController.Size>();
			for(android.util.Size camera_size : camera_video_sizes) {
				camera_features.video_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
			}

			android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceHolder.class);
			camera_features.preview_sizes = new ArrayList<CameraController.Size>();
			for(android.util.Size camera_size : camera_preview_sizes) {
				camera_features.preview_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
			}

		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	    return camera_features;
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
		Size size = new Size(imageReader.getWidth(), imageReader.getHeight());
		return size;
	}

	@Override
	void setPictureSize(int width, int height) {
		if( imageReader != null ) {
			imageReader.close();
		}
		imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2); 
	}

	@Override
	public Size getPreviewSize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setPreviewSize(int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewSize: " + width + " , " + height);
		if( holder != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set size of surface holder");
			holder.setFixedSize(width, height);
		}
		/*if( previewImageReader != null ) {
			previewImageReader.close();
		}
		previewImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2); 
		*/
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
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewDisplay");
		this.holder = holder;
	}

	@Override
	void startPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "startPreview");

		try {
			captureSession = null;
			previewRequest = null;

			if( MyDebug.LOG )
				Log.d(TAG, "picture size: " + imageReader.getWidth() + " x " + imageReader.getHeight());
			/*if( MyDebug.LOG )
				Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/

			class MyStateCallback extends CameraCaptureSession.StateCallback {
				@Override
				public void onConfigured(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigured");
					if( camera == null ) {
						return;
					}
					captureSession = session;
					try {
						CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
						if( MyDebug.LOG ) {
							Log.d(TAG, "holder surface: " + holder.getSurface());
							if( holder.getSurface() == null )
								Log.d(TAG, "holder surface is null!");
							else if( !holder.getSurface().isValid() )
								Log.d(TAG, "holder surface is not valid!");
						}
						builder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        builder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
						builder.addTarget(holder.getSurface());
						previewRequest = builder.build();
						captureSession.setRepeatingRequest(previewRequest, null, null);
					}
					catch(CameraAccessException e) {
						e.printStackTrace();
						captureSession = null;
						previewRequest = null;
					}
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigureFailed");
				}
			}
			MyStateCallback myStateCallback = new MyStateCallback();

			camera.createCaptureSession(Arrays.asList(holder.getSurface()/*, previewImageReader.getSurface()*/, imageReader.getSurface()),
				myStateCallback,
		 		null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			//throw new IOException();
		} 
	}

	@Override
	void stopPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "stopPreview");
		if( captureSession == null )
			return;
		try {
			captureSession.stopRepeating();
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
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
