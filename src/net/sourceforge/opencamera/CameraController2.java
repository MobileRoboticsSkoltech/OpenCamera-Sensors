package net.sourceforge.opencamera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
	private static final String TAG = "CameraController2";

	private Context context = null;
	private CameraDevice camera = null;
	private String cameraIdS = null;
	private CameraCaptureSession captureSession = null;
	private CaptureRequest.Builder previewBuilder = null;
	private ImageReader imageReader = null;
	//private ImageReader previewImageReader = null;
	private SurfaceHolder holder = null;
	private SurfaceTexture texture = null;
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

	private List<String> convertFocusModesToValues(int [] supported_focus_modes_arr) {
		if( MyDebug.LOG )
			Log.d(TAG, "convertFocusModesToValues()");
	    List<Integer> supported_focus_modes = new ArrayList<Integer>();
	    for(int i=0;i<supported_focus_modes_arr.length;i++)
	    	supported_focus_modes.add(supported_focus_modes_arr[i]);
	    List<String> output_modes = new Vector<String>();
		if( supported_focus_modes != null ) {
			// also resort as well as converting
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
				output_modes.add("focus_mode_auto");
				if( MyDebug.LOG ) {
					Log.d(TAG, " supports focus_mode_auto");
				}
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ) {
				output_modes.add("focus_mode_macro");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_macro");
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
				output_modes.add("focus_mode_manual");
				if( MyDebug.LOG ) {
					Log.d(TAG, " supports focus_mode_manual");
				}
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) ) {
				output_modes.add("focus_mode_edof");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_edof");
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ) {
				output_modes.add("focus_mode_continuous_video");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_continuous_video");
			}
		}
		return output_modes;
	}

	@Override
	CameraFeatures getCameraFeatures() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCameraFeatures()");
	    CameraFeatures camera_features = new CameraFeatures();
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
		    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);

			if( MyDebug.LOG ) {
				int hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
				if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
					Log.d(TAG, "Hardware Level: LEGACY");
				else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
					Log.d(TAG, "Hardware Level: LIMITED");
				else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
					Log.d(TAG, "Hardware Level: FULL");
				else
					Log.e(TAG, "Unknown Hardware Level!");
			}

		    // TODO: zoom
		    
			int [] face_modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
			camera_features.supports_face_detection = false;
			for(int i=0;i<face_modes.length && !camera_features.supports_face_detection;i++) {
				if( face_modes[i] == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE ) {
					camera_features.supports_face_detection = true;
				}
			}
			if( camera_features.supports_face_detection ) {
				int face_count = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
				if( face_count <= 0 ) {
					camera_features.supports_face_detection = false;
				}
			}

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

			//android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceHolder.class);
			android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
			camera_features.preview_sizes = new ArrayList<CameraController.Size>();
			for(android.util.Size camera_size : camera_preview_sizes) {
				camera_features.preview_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
			}
			
			// TODO: current_fps_range
			
			if( characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
				// TODO: flash
			}

			int [] supported_focus_modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); // Android format
			camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes); // convert to our format (also resorts)
			camera_features.max_num_focus_areas = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
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
		if( texture != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set size of preview texture");
			texture.setDefaultBufferSize(width, height);
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
		if( previewBuilder == null || captureSession == null )
			return;
		int focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_manual") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	}
    	else if( focus_value.equals("focus_mode_macro") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_MACRO;
    	}
    	else if( focus_value.equals("focus_mode_edof") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_EDOF;
    	}
    	else if( focus_value.equals("focus_mode_continuous_video") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    	}
    	else {
    		if( MyDebug.LOG )
    			Log.d(TAG, "setFocusValue() received unknown focus value " + focus_value);
    		return;
    	}
    	previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, focus_mode);
    	setRepeatingRequest();
	}

	private String convertFocusModeToValue(int focus_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "convertFocusModeToValue: " + focus_mode);
		String focus_value = "";
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO ) {
    		focus_value = "focus_mode_auto";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO ) {
    		focus_value = "focus_mode_macro";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_EDOF ) {
    		focus_value = "focus_mode_edof";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
    		focus_value = "focus_mode_continuous_video";
    	}
    	return focus_value;
	}
	
	@Override
	public String getFocusValue() {
		if( previewBuilder == null || captureSession == null )
			return "";
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		return convertFocusModeToValue(focus_mode);
	}

	@Override
	void setFlashValue(String flash_value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getFlashValue() {
		// TODO Auto-generated method stub
		// returns "" if flash isn't supported
		return "";
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
		if( previewBuilder == null || captureSession == null )
			return false;
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO || focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO )
			return true;
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
		this.texture = null;
	}

	@Override
	void setPreviewTexture(SurfaceTexture texture) throws IOException {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewTexture");
		this.texture = texture;
		this.holder = null;
	}
	
	private void setRepeatingRequest() {
		if( MyDebug.LOG )
			Log.d(TAG, "setRepeatingRequest");
		if( previewBuilder == null || captureSession == null )
			return;
		try {
			captureSession.setRepeatingRequest(previewBuilder.build(), mCaptureCallback, null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void capture() {
		if( MyDebug.LOG )
			Log.d(TAG, "capture");
		if( previewBuilder == null || captureSession == null )
			return;
		try {
			captureSession.capture(previewBuilder.build(), mCaptureCallback, null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	void startPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "startPreview");

		try {
			captureSession = null;
			previewBuilder = null;

			if( MyDebug.LOG )
				Log.d(TAG, "picture size: " + imageReader.getWidth() + " x " + imageReader.getHeight());
			/*if( MyDebug.LOG )
				Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/
        	Surface surface = null;
            if( holder != null ) {
            	surface = holder.getSurface();
            }
            else if( texture != null ) {
            	surface = new Surface(texture);
            }

			previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			if( MyDebug.LOG && holder != null ) {
				Log.d(TAG, "holder surface: " + holder.getSurface());
				if( holder.getSurface() == null )
					Log.d(TAG, "holder surface is null!");
				else if( !holder.getSurface().isValid() )
					Log.d(TAG, "holder surface is not valid!");
			}
			previewBuilder.addTarget(surface);

			class MyStateCallback extends CameraCaptureSession.StateCallback {
				@Override
				public void onConfigured(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigured");
					if( camera == null ) {
						return;
					}
					captureSession = session;
					previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
					//previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
					previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
					setRepeatingRequest();
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigureFailed");
				}
			}
			MyStateCallback myStateCallback = new MyStateCallback();

			camera.createCaptureSession(Arrays.asList(surface/*, previewImageReader.getSurface()*/, imageReader.getSurface()),
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
		if( MyDebug.LOG )
			Log.d(TAG, "autoFocus");
		if( previewBuilder == null || captureSession == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "capture session not available");
			return;
		}
    	//previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
    	//previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
    	/*previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
		if( MyDebug.LOG ) {
			Float focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
			Log.d(TAG, "focus_distance: " + focus_distance);
		}*/
    	//setRepeatingRequest();
    	capture();
	}

	@Override
	void cancelAutoFocus() {
		if( previewBuilder == null || captureSession == null )
			return;
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
    	//setRepeatingRequest();
    	capture();
	}

	@Override
	void takePicture(PictureCallback raw, PictureCallback jpeg) {
		// TODO Auto-generated method stub

	}

	@Override
	void setDisplayOrientation(int degrees) {
		// do nothing - for CameraController2, the preview display orientation is handled via the TextureView's transform
	}

	@Override
	int getDisplayOrientation() {
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

	private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() { 
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "onCaptureCompleted");*/
			int af_state = result.get(CaptureResult.CONTROL_AF_STATE);
			if( af_state != CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ) {
				if( MyDebug.LOG )
					Log.d(TAG, "CONTROL_AF_STATE = " + af_state);
			}
		}
	};
}
