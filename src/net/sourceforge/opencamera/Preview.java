package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ZoomControls;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class Preview extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {
	private static final String TAG = "Preview";

	private Paint p = new Paint();
	private DecimalFormat decimalFormat = new DecimalFormat("##.00");
    private Camera.CameraInfo camera_info = new Camera.CameraInfo();
    private Matrix camera_to_preview_matrix = new Matrix();
    private Matrix preview_to_camera_matrix = new Matrix();
	private RectF face_rect = new RectF();
	private Rect text_bounds = new Rect();
    private int display_orientation = 0;

	private boolean ui_placement_right = true;

	private boolean app_is_paused = true;
	private SurfaceHolder mHolder = null;
	private boolean has_surface = false;
	private Camera camera = null;
	private int cameraId = 0;
	private boolean is_video = false;
	private MediaRecorder video_recorder = null;
	private boolean video_start_time_set = false;
	private long video_start_time = 0;
	private String video_name = null;

	private final int PHASE_NORMAL = 0;
	private final int PHASE_TIMER = 1;
	private final int PHASE_TAKING_PHOTO = 2;
	private final int PHASE_PREVIEW_PAUSED = 3; // the paused state after taking a photo
	private int phase = PHASE_NORMAL;
	/*private boolean is_taking_photo = false;
	private boolean is_taking_photo_on_timer = false;*/
	private Timer takePictureTimer = new Timer();
	private TimerTask takePictureTimerTask = null;
	private Timer beepTimer = new Timer();
	private TimerTask beepTimerTask = null;
	private long take_photo_time = 0;
	private int remaining_burst_photos = 0;
	private int n_burst = 1;

	private boolean is_preview_started = false;
	//private boolean is_preview_paused = false; // whether we are in the paused state after taking a photo
	private String preview_image_name = null;
	private Bitmap thumbnail = null; // thumbnail of last picture taken
	private boolean thumbnail_anim = false; // whether we are displaying the thumbnail animation
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
	private RectF thumbnail_anim_src_rect = new RectF();
	private RectF thumbnail_anim_dst_rect = new RectF();
	private Matrix thumbnail_anim_matrix = new Matrix();

	private int current_orientation = 0; // orientation received by onOrientationChanged
	private int current_rotation = 0; // orientation relative to camera's orientation (used for parameters.setOrientation())
	private boolean has_level_angle = false;
	private double level_angle = 0.0f;
	
	private float free_memory_gb = -1.0f;
	private long last_free_memory_time = 0;

	private boolean has_zoom = false;
	private int zoom_factor = 0;
	private int max_zoom_factor = 0;
	private ScaleGestureDetector scaleGestureDetector;
	private List<Integer> zoom_ratios = null;
	private boolean touch_was_multitouch = false;

	private List<String> supported_flash_values = null; // our "values" format
	private int current_flash_index = -1; // this is an index into the supported_flash_values array, or -1 if no flash modes available

	private List<String> supported_focus_values = null; // our "values" format
	private int current_focus_index = -1; // this is an index into the supported_focus_values array, or -1 if no focus modes available

	private List<String> color_effects = null;
	private List<String> scene_modes = null;
	private List<String> white_balances = null;
	private List<String> exposures = null;

	private List<Camera.Size> sizes = null;
	private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set
	
	private List<Integer> video_quality = null;
	private int current_video_quality = -1; // this is an index into the video_quality array, or -1 if not found (though this shouldn't happen?)
	
	private Location location = null;
	public boolean has_set_location = false;
	private Bitmap location_bitmap = null;
	private Rect location_dest = new Rect();

	class ToastBoxer {
		public Toast toast = null;

		ToastBoxer() {
		}
	}
	private ToastBoxer switch_camera_toast = new ToastBoxer();
	private ToastBoxer switch_video_toast = new ToastBoxer();
	private ToastBoxer flash_toast = new ToastBoxer();
	private ToastBoxer focus_toast = new ToastBoxer();
	private ToastBoxer take_photo_toast = new ToastBoxer();
	private ToastBoxer stopstart_video_toast = new ToastBoxer();
	
	private int ui_rotation = 0;

	private boolean supports_face_detection = false;
	private boolean using_face_detection = false;
	private Face [] faces_detected = null;
	private boolean has_focus_area = false;
	private int focus_screen_x = 0;
	private int focus_screen_y = 0;
	private long focus_complete_time = -1;
	private int focus_success = FOCUS_DONE;
	private static final int FOCUS_WAITING = 0;
	private static final int FOCUS_SUCCESS = 1;
	private static final int FOCUS_FAILED = 2;
	private static final int FOCUS_DONE = 3;

	private IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac = false;
	private float battery_frac = 0.0f;
	private long last_battery_time = 0;
	
	// for testing:
	public int count_cameraStartPreview = 0;
	public int count_cameraAutoFocus = 0;
	public int count_cameraTakePicture = 0;
	public boolean has_received_location = false;
	public boolean test_low_memory = false;
	public boolean test_have_angle = false;
	public float test_angle = 0.0f;
	public String test_last_saved_image = null;

	Preview(Context context) {
		this(context, null);
	}

	@SuppressWarnings("deprecation")
	Preview(Context context, Bundle savedInstanceState) {
		super(context);
		if( MyDebug.LOG ) {
			Log.d(TAG, "new Preview");
		}

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

	    scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        if( savedInstanceState != null ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "have savedInstanceState");
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		if( cameraId < 0 || cameraId >= Camera.getNumberOfCameras() ) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "cameraID not valid for " + Camera.getNumberOfCameras() + " cameras!");
    			cameraId = 0;
    		}
        }

    	location_bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.earth);
	}
	
	/*private void previewToCamera(float [] coords) {
		float alpha = coords[0] / (float)this.getWidth();
		float beta = coords[1] / (float)this.getHeight();
		coords[0] = 2000.0f * alpha - 1000.0f;
		coords[1] = 2000.0f * beta - 1000.0f;
	}*/

	/*private void cameraToPreview(float [] coords) {
		float alpha = (coords[0] + 1000.0f) / 2000.0f;
		float beta = (coords[1] + 1000.0f) / 2000.0f;
		coords[0] = alpha * (float)this.getWidth();
		coords[1] = beta * (float)this.getHeight();
	}*/

	private void calculateCameraToPreviewMatrix() {
		camera_to_preview_matrix.reset();
		// from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
		Camera.getCameraInfo(cameraId, camera_info);
		// Need mirror for front camera.
		boolean mirror = (camera_info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
		camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
		// This is the value for android.hardware.Camera.setDisplayOrientation.
		camera_to_preview_matrix.postRotate(display_orientation);
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		camera_to_preview_matrix.postScale(this.getWidth() / 2000f, this.getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(this.getWidth() / 2f, this.getHeight() / 2f);
	}

	private void calculatePreviewToCameraMatrix() {
		calculateCameraToPreviewMatrix();
		if( !camera_to_preview_matrix.invert(preview_to_camera_matrix) ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
		}
	}

	private ArrayList<Camera.Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];
		
		int focus_size = 50;
		if( MyDebug.LOG ) {
			Log.d(TAG, "x, y: " + x + ", " + y);
			Log.d(TAG, "focus x, y: " + focus_x + ", " + focus_y);
		}
		Rect rect = new Rect();
		rect.left = (int)focus_x - focus_size;
		rect.right = (int)focus_x + focus_size;
		rect.top = (int)focus_y - focus_size;
		rect.bottom = (int)focus_y + focus_size;
		if( rect.left < -1000 ) {
			rect.left = -1000;
			rect.right = rect.left + 2*focus_size;
		}
		else if( rect.right > 1000 ) {
			rect.right = 1000;
			rect.left = rect.right - 2*focus_size;
		}
		if( rect.top < -1000 ) {
			rect.top = -1000;
			rect.bottom = rect.top + 2*focus_size;
		}
		else if( rect.bottom > 1000 ) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2*focus_size;
		}

	    ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
	    areas.add(new Camera.Area(rect, 1000));
	    return areas;
	}

	@Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        //invalidate();
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "touch event: " + event.getAction());
		}*/
		if( event.getPointerCount() != 1 ) {
			//multitouch_time = System.currentTimeMillis();
			touch_was_multitouch = true;
			return true;
		}
		if( event.getAction() != MotionEvent.ACTION_UP ) {
			if( event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1 ) {
				touch_was_multitouch = false;
			}
			return true;
		}
		if( touch_was_multitouch ) {
			return true;
		}
		if( this.isTakingPhotoOrOnTimer() ) {
			return true;
		}

		// note, we always try to force start the preview (in case is_preview_paused has become false)
        startCameraPreview();

        if( camera != null && !this.using_face_detection ) {
            Camera.Parameters parameters = camera.getParameters();
			String focus_mode = parameters.getFocusMode();
    		this.has_focus_area = false;
            if( parameters.getMaxNumFocusAreas() == 0 ) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "focus areas not supported");
            }
            else if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "set focus (and metering?) area");
				this.has_focus_area = true;
				this.focus_screen_x = (int)event.getX();
				this.focus_screen_y = (int)event.getY();

				ArrayList<Camera.Area> areas = getAreas(event.getX(), event.getY());
			    parameters.setFocusAreas(areas);

			    // also set metering areas
			    if( parameters.getMaxNumMeteringAreas() == 0 ) {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "metering areas not supported");
			    }
			    else {
			    	parameters.setMeteringAreas(areas);
			    }

			    try {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "set focus areas parameters");
			    	camera.setParameters(parameters);
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "done");
			    }
			    catch(RuntimeException e) {
			    	// just in case something has gone wrong
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "failed to set parameters for focus area");
	        		e.printStackTrace();
			    }
            }
            else {
        		if( MyDebug.LOG )
        			Log.d(TAG, "set metering area");
        		// don't set has_focus_area in this mode
				ArrayList<Camera.Area> areas = getAreas(event.getX(), event.getY());
		    	parameters.setMeteringAreas(areas);

			    try {
			    	camera.setParameters(parameters);
			    }
			    catch(RuntimeException e) {
			    	// just in case something has gone wrong
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "failed to set parameters for focus area");
	        		e.printStackTrace();
			    }
            }
        }
        
		tryAutoFocus();
		return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    	@Override
    	public boolean onScale(ScaleGestureDetector detector) {
    		if( Preview.this.camera != null && Preview.this.has_zoom ) {
    			float zoom_ratio = Preview.this.zoom_ratios.get(zoom_factor)/100.0f;
    			zoom_ratio *= detector.getScaleFactor();

    			if( zoom_ratio <= 1.0f ) {
    				zoom_factor = 0;
    			}
    			else if( zoom_ratio >= zoom_ratios.get(max_zoom_factor)/100.0f ) {
    				zoom_factor = max_zoom_factor;
    			}
    			else {
    				// find the closest zoom level
    				if( detector.getScaleFactor() > 1.0f ) {
    					// zooming in
        				for(int i=zoom_factor;i<zoom_ratios.size();i++) {
        					if( zoom_ratios.get(i)/100.0f >= zoom_ratio ) {
        						zoom_factor = i;
        						break;
        					}
        				}
    				}
    				else {
    					// zooming out
        				for(int i=zoom_factor;i>0;i--) {
        					if( zoom_ratios.get(i)/100.0f <= zoom_ratio ) {
        						zoom_factor = i;
        						break;
        					}
        				}
    				}
    			}
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "ScaleListener.onScale zoom_ratio is now " + zoom_ratio);
    				Log.d(TAG, "    chosen new zoom_factor " + zoom_factor + " ratio " + zoom_ratios.get(zoom_factor)/100.0f);
    			}
    			Camera.Parameters parameters = camera.getParameters();
    			if( MyDebug.LOG )
    				Log.d(TAG, "zoom was: " + parameters.getZoom());
    			parameters.setZoom((int)zoom_factor);
	    		camera.setParameters(parameters);
	    		clearFocusAreas();

        		//invalidate();
    		}
    		return true;
    	}
    }
    
    public void clearFocusAreas() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearFocusAreas()");
        Camera.Parameters parameters = camera.getParameters();
        boolean update_parameters = false;
        if( parameters.getMaxNumFocusAreas() > 0 ) {
        	parameters.setFocusAreas(null);
        	update_parameters = true;
        }
        if( parameters.getMaxNumMeteringAreas() > 0 ) {
        	parameters.setMeteringAreas(null);
        	update_parameters = true;
        }
        if( update_parameters ) {
        	camera.setParameters(parameters);
        }
		has_focus_area = false;
		focus_success = FOCUS_DONE;
        //Log.d(TAG, "camera parameters null? " + (camera.getParameters().getFocusAreas()==null));
    }

    /*private void setCameraParameters() {
	}*/
	
	public void surfaceCreated(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		this.has_surface = true;
		this.openCamera(false); // if setting up for the first time, we wait until the surfaceChanged call to start the preview
		this.setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		this.has_surface = false;
		this.closeCamera();
	}
	
	private void stopVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "stopVideo()");
		if( video_recorder != null ) { // check again, just to be safe
    		if( MyDebug.LOG )
    			Log.d(TAG, "stop video recording");
    		showToast(stopstart_video_toast, "Stopped recording video");
			/*is_taking_photo = false;
			is_taking_photo_on_timer = false;*/
    		this.phase = PHASE_NORMAL;
			try {
				video_recorder.stop();
			}
			catch(RuntimeException e) {
				// stop() can throw a RuntimeException if stop is called too soon after start - we have no way to detect this, so have to catch it
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "runtime exception when stopping video");
			}
    		video_recorder.reset();
    		video_recorder.release(); 
    		video_recorder = null;
    		reconnectCamera();
    		if( video_name != null ) {
    			File file = new File(video_name);
    			if( file != null ) {
    				// need to scan when finished, so we update for the completed file
    				Activity activity = (Activity)this.getContext();
    				activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
    			}
    			// create thumbnail
    			{
	            	long time_s = System.currentTimeMillis();
    	    		if( thumbnail != null ) {
    	    			thumbnail.recycle();
    	    		}
    	    	    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
					try {
						retriever.setDataSource(video_name);
						thumbnail = retriever.getFrameAtTime(-1);
					}
    	    	    catch(IllegalArgumentException ex) {
    	    	    	// corrupt video file?
    	    	    }
    	    	    catch(RuntimeException ex) {
    	    	    	// corrupt video file?
    	    	    }
    	    	    finally {
    	    	    	try {
    	    	    		retriever.release();
    	    	    	}
    	    	    	catch(RuntimeException ex) {
    	    	    		// ignore
    	    	    	}
    	    	    }
    	    	    if( thumbnail != null ) {
    	    	    	Activity activity = (Activity)this.getContext();
    	    	    	ImageButton galleryButton = (ImageButton) activity.findViewById(R.id.gallery);
    	    	    	int width = thumbnail.getWidth();
    	    	    	int height = thumbnail.getHeight();
    					if( MyDebug.LOG )
    						Log.d(TAG, "    video thumbnail size " + width + " x " + height);
    	    	    	if( width > galleryButton.getWidth() ) {
    	    	    		float scale = (float) galleryButton.getWidth() / width;
    	    	    		int new_width = Math.round(scale * width);
    	    	    		int new_height = Math.round(scale * height);
        					if( MyDebug.LOG )
        						Log.d(TAG, "    scale video thumbnail to " + new_width + " x " + new_height);
    	    	    		Bitmap scaled_thumbnail = Bitmap.createScaledBitmap(thumbnail, new_width, new_height, true);
    	        		    // careful, as scaled_thumbnail is sometimes not a copy!
    	        		    if( scaled_thumbnail != thumbnail ) {
    	        		    	thumbnail.recycle();
    	        		    	thumbnail = scaled_thumbnail;
    	        		    }
    	    	    	}
    	    	    	galleryButton.setImageResource(android.R.color.transparent);
    	    	    	galleryButton.setImageBitmap(thumbnail);
    	    	    }
					if( MyDebug.LOG )
						Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - time_s));
    			}
    			video_name = null;
    		}
		}
	}
	
	private void reconnectCamera() {
        if( camera != null ) { // just to be safe
    		try {
				camera.reconnect();
		        this.startCameraPreview();
	    		setPreviewSize();
			}
    		catch (IOException e) {
        		if( MyDebug.LOG )
        			Log.e(TAG, "failed to reconnect to camera");
				e.printStackTrace();
	    	    showToast(null, "Failed to reconnect to camera");
	    	    closeCamera();
			}
			tryAutoFocus();
		}
	}

	private void closeCamera() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "closeCamera()");
		}
		has_focus_area = false;
		focus_success = FOCUS_DONE;
        has_set_location = false;
		//if( is_taking_photo_on_timer ) {
		if( this.isOnTimer() ) {
			takePictureTimerTask.cancel();
			if( beepTimerTask != null ) {
				beepTimerTask.cancel();
			}
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
    		this.phase = PHASE_NORMAL;
			if( MyDebug.LOG )
				Log.d(TAG, "cancelled camera timer");
		}
		if( camera != null ) {
			if( video_recorder != null ) {
				stopVideo();
			}
			//camera.setPreviewCallback(null);
			this.setPreviewPaused(false);
			camera.stopPreview();
			/*this.is_taking_photo = false;
			this.is_taking_photo_on_timer = false;*/
    		this.phase = PHASE_NORMAL;
			this.is_preview_started = false;
			showGUI(true);
			camera.release();
			camera = null;
		}
	}
	
	private void openCamera(boolean start_preview) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera()");
			Log.d(TAG, "cameraId: " + cameraId);
			Log.d(TAG, "start_preview?: " + start_preview);
			debug_time = System.currentTimeMillis();
		}
        has_set_location = false;
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		showGUI(true);
		if( !this.has_surface ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "preview surface not yet available");
			}
			return;
		}
		if( this.app_is_paused ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "don't open camera as app is paused");
			}
			return;
		}
		try {
			camera = Camera.open(cameraId);
		}
		catch(RuntimeException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to open camera: " + e.getMessage());
			e.printStackTrace();
			camera = null;
		}
		if( MyDebug.LOG ) {
			//Log.d(TAG, "time after opening camera: " + (System.currentTimeMillis() - debug_time));
		}
		if( camera != null ) {
			Activity activity = (Activity)this.getContext();
	        this.setCameraDisplayOrientation(activity);
	        new OrientationEventListener(activity) {
				@Override
				public void onOrientationChanged(int orientation) {
					Preview.this.onOrientationChanged(orientation);
				}
	        }.enable();
			if( MyDebug.LOG ) {
				//Log.d(TAG, "time after setting orientation: " + (System.currentTimeMillis() - debug_time));
			}

			try {
				camera.setPreviewDisplay(mHolder);
			}
			catch(IOException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "Failed to set preview display: " + e.getMessage());
				e.printStackTrace();
			}
			if( MyDebug.LOG ) {
				//Log.d(TAG, "time after setting preview display: " + (System.currentTimeMillis() - debug_time));
			}
			if( start_preview ) {
				startCameraPreview();
			}
			if( MyDebug.LOG ) {
				//Log.d(TAG, "time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
			}
			
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());

			Camera.Parameters parameters = camera.getParameters();

			// get available scene modes
			// note, important to set scene mode first - apparently this can affect the other supported features
			scene_modes = parameters.getSupportedSceneModes();
			String scene_mode = setupValuesPref(scene_modes, "preference_scene_mode", Camera.Parameters.SCENE_MODE_AUTO);
			if( scene_mode != null ) {
	        	parameters.setSceneMode(scene_mode);
			}

			this.has_zoom = parameters.isZoomSupported();
			if( MyDebug.LOG )
				Log.d(TAG, "has_zoom? " + has_zoom);
		    ZoomControls zoomControls = (ZoomControls) activity.findViewById(R.id.zoom);
		    this.zoom_factor = 0;
			if( this.has_zoom ) {
				this.max_zoom_factor = parameters.getMaxZoom();
				this.zoom_ratios = parameters.getZoomRatios();

			    zoomControls.setIsZoomInEnabled(true);
		        zoomControls.setIsZoomOutEnabled(true);
		        zoomControls.setZoomSpeed(20);

		        zoomControls.setOnZoomInClickListener(new OnClickListener(){
		            public void onClick(View v){
		            	zoomIn();
		            }
		        });

			    zoomControls.setOnZoomOutClickListener(new OnClickListener(){
			    	public void onClick(View v){
			    		zoomOut();
			        }
			    });
				zoomControls.setVisibility(View.VISIBLE);
			}
			else {
				zoomControls.setVisibility(View.GONE);
			}
			
			// get face detection supported
			this.faces_detected = null;
			this.supports_face_detection = parameters.getMaxNumDetectedFaces() > 0;
			if( this.supports_face_detection ) {
				this.using_face_detection = sharedPreferences.getBoolean("preference_face_detection", false);
			}
			else {
				this.using_face_detection = false;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "supports_face_detection?: " + supports_face_detection);
				Log.d(TAG, "using_face_detection?: " + using_face_detection);
			}
			if( this.using_face_detection ) {
				class MyFaceDetectionListener implements Camera.FaceDetectionListener {
				    @Override
				    public void onFaceDetection(Face[] faces, Camera camera) {
				    	faces_detected = new Face[faces.length];
				    	System.arraycopy(faces, 0, faces_detected, 0, faces.length);				    	
				    }
				}
				camera.setFaceDetectionListener(new MyFaceDetectionListener());
			}

			// get available color effects
			color_effects = parameters.getSupportedColorEffects();
			String color_effect = setupValuesPref(color_effects, "preference_color_effect", Camera.Parameters.EFFECT_NONE);
			if( color_effect != null ) {
	        	parameters.setColorEffect(color_effect);
			}

			// get available white balances
			white_balances = parameters.getSupportedWhiteBalance();
			String white_balance = setupValuesPref(white_balances, "preference_white_balance", Camera.Parameters.WHITE_BALANCE_AUTO);
			if( white_balance != null ) {
	        	parameters.setWhiteBalance(white_balance);
			}

			// get min/max exposure
			exposures = null;
			int min_exposure = parameters.getMinExposureCompensation();
			int max_exposure = parameters.getMaxExposureCompensation();
			if( min_exposure != 0 && max_exposure != 0 ) {
				exposures = new Vector<String>();
				for(int i=min_exposure;i<=max_exposure;i++) {
					exposures.add("" + i);
				}
				String exposure_s = setupValuesPref(exposures, "preference_exposure", "0");
				if( exposure_s != null ) {
					try {
						int exposure = Integer.parseInt(exposure_s);
						if( MyDebug.LOG )
							Log.d(TAG, "exposure: " + exposure);
						parameters.setExposureCompensation(exposure);
					}
					catch(NumberFormatException exception) {
						if( MyDebug.LOG )
							Log.d(TAG, "exposure invalid format, can't parse to int");
					}
				}
			}

			// get available sizes
	        sizes = parameters.getSupportedPictureSizes();
			if( MyDebug.LOG ) {
				for(int i=0;i<sizes.size();i++) {
		        	Camera.Size size = sizes.get(i);
		        	Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
				}
			}
			/*if( MyDebug.LOG ) {
				List<Camera.Size> preview_sizes = parameters.getSupportedPreviewSizes();
				for(int i=0;i<preview_sizes.size();i++) {
		        	Camera.Size size = preview_sizes.get(i);
		        	Log.d(TAG, "supported preview size: " + size.width + " , " + size.height);
				}
			}*/
			current_size_index = -1;
			String resolution_value = sharedPreferences.getString(getResolutionPreferenceKey(cameraId), "");
			if( MyDebug.LOG )
				Log.d(TAG, "resolution_value: " + resolution_value);
			if( resolution_value.length() > 0 ) {
				// parse the saved size, and make sure it is still valid
				int index = resolution_value.indexOf(' ');
				if( index == -1 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't find space");
				}
				else {
					String resolution_w_s = resolution_value.substring(0, index);
					String resolution_h_s = resolution_value.substring(index+1);
					if( MyDebug.LOG ) {
						Log.d(TAG, "resolution_w_s: " + resolution_w_s);
						Log.d(TAG, "resolution_h_s: " + resolution_h_s);
					}
					try {
						int resolution_w = Integer.parseInt(resolution_w_s);
						if( MyDebug.LOG )
							Log.d(TAG, "resolution_w: " + resolution_w);
						int resolution_h = Integer.parseInt(resolution_h_s);
						if( MyDebug.LOG )
							Log.d(TAG, "resolution_h: " + resolution_h);
						// now find size in valid list
						for(int i=0;i<sizes.size() && current_size_index==-1;i++) {
				        	Camera.Size size = sizes.get(i);
				        	if( size.width == resolution_w && size.height == resolution_h ) {
				        		current_size_index = i;
								if( MyDebug.LOG )
									Log.d(TAG, "set current_size_index to: " + current_size_index);
				        	}
						}
						if( current_size_index == -1 ) {
							if( MyDebug.LOG )
								Log.e(TAG, "failed to find valid size");
						}
					}
					catch(NumberFormatException exception) {
						if( MyDebug.LOG )
							Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
					}
				}
			}

			if( current_size_index == -1 ) {
				// set to largest
				Camera.Size current_size = null;
				for(int i=0;i<sizes.size();i++) {
		        	Camera.Size size = sizes.get(i);
		        	if( current_size == null || size.width*size.height > current_size.width*current_size.height ) {
		        		current_size_index = i;
		        		current_size = size;
		        	}
		        }
			}
			if( current_size_index != -1 ) {
				Camera.Size current_size = sizes.get(current_size_index);
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "Current size index " + current_size_index + ": " + current_size.width + ", " + current_size.height);

	    		// now save, so it's available for PreferenceActivity
				resolution_value = current_size.width + " " + current_size.height;
				if( MyDebug.LOG ) {
					Log.d(TAG, "save new resolution_value: " + resolution_value);
				}
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(getResolutionPreferenceKey(cameraId), resolution_value);
				editor.apply();

				// now set the size
	        	parameters.setPictureSize(current_size.width, current_size.height);
			}
			
			
    		/*if( MyDebug.LOG )
    			Log.d(TAG, "Current image quality: " + parameters.getJpegQuality());*/
			int image_quality = getImageQuality();
			parameters.setJpegQuality(image_quality);
    		if( MyDebug.LOG )
    			Log.d(TAG, "image quality: " + image_quality);

    		if( MyDebug.LOG ) {
    			//Log.d(TAG, "time after reading camera parameters: " + (System.currentTimeMillis() - debug_time));
    		}

			// get available sizes
	        video_quality = new Vector<Integer>();
	        // if we add more, remember to update MyPreferenceActivity.onCreate() code
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P) )
	        	video_quality.add(CamcorderProfile.QUALITY_1080P);
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P) )
	        	video_quality.add(CamcorderProfile.QUALITY_720P);
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P) )
	        	video_quality.add(CamcorderProfile.QUALITY_480P);
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF) )
	        	video_quality.add(CamcorderProfile.QUALITY_CIF);
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA) )
	        	video_quality.add(CamcorderProfile.QUALITY_QVGA);
	        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF) )
	        	video_quality.add(CamcorderProfile.QUALITY_QCIF);
			if( MyDebug.LOG ) {
				for(int i=0;i<video_quality.size();i++) {
		        	Log.d(TAG, "supported video quality: " + video_quality.get(i).intValue());
				}
			}

			current_video_quality = -1;
			String video_quality_value_s = sharedPreferences.getString(getVideoQualityPreferenceKey(cameraId), "");
			if( MyDebug.LOG )
				Log.d(TAG, "video_quality_value: " + video_quality_value_s);
			if( video_quality_value_s.length() > 0 ) {
				// parse the saved video quality, and make sure it is still valid
				try {
					int video_quality_value = Integer.parseInt(video_quality_value_s);
					if( MyDebug.LOG )
						Log.d(TAG, "video_quality_value: " + video_quality_value);
					// now find value in valid list
					for(int i=0;i<video_quality.size() && current_video_quality==-1;i++) {
			        	Integer value = video_quality.get(i);
			        	if( value.intValue() == video_quality_value ) {
			        		current_video_quality = i;
							if( MyDebug.LOG )
								Log.d(TAG, "set current_video_quality to: " + current_video_quality);
			        	}
					}
					if( current_video_quality == -1 ) {
						if( MyDebug.LOG )
							Log.e(TAG, "failed to find valid video_quality");
					}
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "video_quality invalid format, can't parse to int");
				}
			}
			if( current_video_quality == -1 && video_quality.size() > 0 ) {
				// default to highest quality
				current_video_quality = 0;
				if( MyDebug.LOG )
					Log.d(TAG, "set video_quality value to " + video_quality.get(current_video_quality).intValue());
			}
			if( current_video_quality != -1 ) {
	    		// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(getVideoQualityPreferenceKey(cameraId), "" + video_quality.get(current_video_quality).intValue());
				editor.apply();
			}

    		// update parameters
    		camera.setParameters(parameters);

    		// we do flash and focus after setting parameters, as these are done by calling separate functions, that themselves set the parameters directly
			List<String> supported_flash_modes = parameters.getSupportedFlashModes(); // Android format
		    View flashButton = (View) activity.findViewById(R.id.flash);
			current_flash_index = -1;
			if( supported_flash_modes != null && supported_flash_modes.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "flash modes: " + supported_flash_modes);
				supported_flash_values = getSupportedFlashModes(supported_flash_modes); // convert to our format (also resorts)

				String flash_value = sharedPreferences.getString(getFlashPreferenceKey(cameraId), "");
				if( flash_value.length() > 0 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "found existing flash_value: " + flash_value);
					if( !updateFlash(flash_value) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "flash value no longer supported!");
						updateFlash(0);
					}
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "found no existing flash_value");
					updateFlash(0);
				}
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "flash not supported");
				supported_flash_values = null;
			}
			flashButton.setVisibility(supported_flash_values != null ? View.VISIBLE : View.GONE);

			List<String> supported_focus_modes = parameters.getSupportedFocusModes(); // Android format
		    View focusModeButton = (View) activity.findViewById(R.id.focus_mode);
			current_focus_index = -1;
			if( supported_focus_modes != null && supported_focus_modes.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "focus modes: " + supported_focus_modes);
				supported_focus_values = getSupportedFocusModes(supported_focus_modes); // convert to our format (also resorts)

				String focus_value = sharedPreferences.getString(getFocusPreferenceKey(cameraId), "");
				if( focus_value.length() > 0 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "found existing focus_value: " + focus_value);
					if( !updateFocus(focus_value, false, false) ) { // don't need to save, as this is the value that's already saved
						if( MyDebug.LOG )
							Log.d(TAG, "focus value no longer supported!");
						updateFocus(0, false, true);
					}
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "found no existing focus_value");
					updateFocus(0, false, true);
				}
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "focus not supported");
				supported_focus_values = null;
			}
			focusModeButton.setVisibility(supported_focus_values != null ? View.VISIBLE : View.GONE);
			
			// must be done after setting parameters, as this function may set parameters
			updateParametersFromLocation();

			// now switch to video if saved
			boolean saved_is_video = sharedPreferences.getBoolean(getIsVideoPreferenceKey(), false);
			if( MyDebug.LOG ) {
				Log.d(TAG, "saved_is_video: " + saved_is_video);
			}
			if( saved_is_video != this.is_video ) {
				this.switchVideo(false);
			}
		}

		
		if( MyDebug.LOG ) {
			Log.d(TAG, "total time: " + (System.currentTimeMillis() - debug_time));
		}
	}

	private String setupValuesPref(List<String> values, String key, String default_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setupValuesPref, key: " + key);
		if( values != null && values.size() > 0 ) {
			if( MyDebug.LOG ) {
				for(int i=0;i<values.size();i++) {
		        	Log.d(TAG, "supported value: " + values.get(i));
				}
			}
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			String value = sharedPreferences.getString(key, default_value);
			if( MyDebug.LOG )
				Log.d(TAG, "value: " + value);
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

    		// now save, so it's available for PreferenceActivity
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(key, value);
			editor.apply();

        	return value;
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "values not supported");
			return null;
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceChanged " + w + ", " + h);
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if( mHolder.getSurface() == null ) {
            // preview surface does not exist
            return;
        }
        if( camera == null ) {
            return;
        }

        // stop preview before making changes
        //if( !this.is_preview_paused ) {
        if( this.phase != PHASE_PREVIEW_PAUSED ) {
            camera.stopPreview();
			this.is_preview_started = false;
        }
        // set preview size and make any resize, rotate or
        // reformatting changes here

        /*Camera.Parameters parameters = camera.getParameters();
		if( MyDebug.LOG )
			Log.d(TAG, "current preview size: " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);
        parameters.setPreviewSize(w, h);
        camera.setParameters(parameters);
		if( MyDebug.LOG )
			Log.d(TAG, "new preview size: " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);*/

        this.setPreviewSize();
 
        // start preview with new settings
        try {
			//camera.setPreviewCallback(this);
            camera.setPreviewDisplay(mHolder);
        }
        catch(IOException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "Error setting preview display: " + e.getMessage());
        }
        //if( !this.is_preview_paused ) {
        if( this.phase != PHASE_PREVIEW_PAUSED ) {
			startCameraPreview();
			tryAutoFocus(); // so we get the autofocus when starting up
        }

		MainActivity main_activity = (MainActivity)Preview.this.getContext();
		main_activity.layoutUI(); // need to force a layoutUI update (e.g., so UI is oriented correctly when app goes idle, device is then rotated, and app is then resumed
	}
	
	private void setPreviewSize() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewSize()");
		if( camera == null ) {
			return;
		}
		// set optimal preview size
    	Camera.Parameters parameters = camera.getParameters();
		if( MyDebug.LOG )
			Log.d(TAG, "current preview size: " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);
    	Camera.Size current_size = parameters.getPictureSize();
		if( MyDebug.LOG )
			Log.d(TAG, "current size: " + current_size.width + ", " + current_size.height);
        List<Camera.Size> preview_sizes = parameters.getSupportedPreviewSizes();
        if( preview_sizes.size() > 0 ) {
	        Camera.Size best_size = preview_sizes.get(0);
	        for(Camera.Size size : preview_sizes) {
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
	        	if( size.width*size.height > best_size.width*best_size.height ) {
	        		best_size = size;
	        	}
	        }
            parameters.setPreviewSize(best_size.width, best_size.height);
    		if( MyDebug.LOG )
    			Log.d(TAG, "new preview size: " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);

    		/*List<int []> fps_ranges = parameters.getSupportedPreviewFpsRange();
    		if( MyDebug.LOG ) {
		        for(int [] fps_range : fps_ranges) {
	    			Log.d(TAG, "    supported fps range: " + fps_range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX] + " to " + fps_range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
		        }
    		}
    		int [] fps_range = fps_ranges.get(fps_ranges.size()-1);
	        parameters.setPreviewFpsRange(fps_range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], fps_range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);*/
            camera.setParameters(parameters);
        }
	}

    /*private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, double targetRatio) {
		if( MyDebug.LOG )
			Log.d(TAG, "getOptimalPreviewSize() targetRatio: " + targetRatio);
        final double ASPECT_TOLERANCE = 0.05;
        if (sizes == null)
        	return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size
		Activity activity = (Activity)this.getContext();
        Display display = activity.getWindowManager().getDefaultDisplay();
        int targetHeight = Math.min(display.getHeight(), display.getWidth());
        if (targetHeight <= 0) {
            // We don't know the size of SurefaceView, use screen height
            WindowManager windowManager = (WindowManager)activity.getSystemService(Context.WINDOW_SERVICE);
            targetHeight = windowManager.getDefaultDisplay().getHeight();
        }
		if( MyDebug.LOG )
			Log.d(TAG, "targetHeight: " + targetHeight);
        // Try to find an size match aspect ratio and size
        for(Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
            	continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if( optimalSize == null ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "can't find exact aspect ratio, so ignore requirement");
            minDiff = Double.MAX_VALUE;
            for(Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
		if( MyDebug.LOG )
			Log.d(TAG, "optimal preview size is: " + optimalSize.width + ", " + optimalSize.height);
        return optimalSize;
    }*/

    // for the Preview - from http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	// note, if orientation is locked to landscape this is only called when setting up the activity, and will always have the same orientation
	private void setCameraDisplayOrientation(Activity activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "setCameraDisplayOrientation()");
	    Camera.CameraInfo info = new Camera.CameraInfo();
	    Camera.getCameraInfo(cameraId, info);
	    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
	    }
		if( MyDebug.LOG )
			Log.d(TAG, "    degrees = " + degrees);

	    int result = 0;
	    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	        result = (info.orientation + degrees) % 360;
	        result = (360 - result) % 360;  // compensate the mirror
	    }
	    else {  // back-facing
	        result = (info.orientation - degrees + 360) % 360;
	    }
		if( MyDebug.LOG ) {
			Log.d(TAG, "    info orientation is " + info.orientation);
			Log.d(TAG, "    setDisplayOrientation to " + result);
		}
	    camera.setDisplayOrientation(result);
	    this.display_orientation = result;
	}
	
	// for taking photos - from http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
	private void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		if( camera == null )
			return;
	    Camera.getCameraInfo(cameraId, camera_info);
	    orientation = (orientation + 45) / 90 * 90;
	    this.current_orientation = orientation % 360;
	    int new_rotation = 0;
	    if (camera_info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	    	new_rotation = (camera_info.orientation - orientation + 360) % 360;
	    }
	    else {  // back-facing camera
	    	new_rotation = (camera_info.orientation + orientation) % 360;
	    }
	    if( new_rotation != current_rotation ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "    current_orientation is " + current_orientation);
				Log.d(TAG, "    info orientation is " + camera_info.orientation);
				Log.d(TAG, "    set Camera rotation from " + current_rotation + " to " + new_rotation);
			}*/
	    	this.current_rotation = new_rotation;
	    }
	 }

	@Override
	public void onDraw(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onDraw()");*/
		if( this.app_is_paused ) {
    		/*if( MyDebug.LOG )
    			Log.d(TAG, "onDraw(): app is paused");*/
			return;
		}
		/*if( true ) // test
			return;*/
		/*if( MyDebug.LOG )
			Log.d(TAG, "ui_rotation: " + ui_rotation);*/

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		if( camera != null && sharedPreferences.getBoolean("preference_grid_3x3", false) ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/3.0f, 0.0f, canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(2.0f*canvas.getWidth()/3.0f, 0.0f, 2.0f*canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, canvas.getHeight()/3.0f, p);
			canvas.drawLine(0.0f, 2.0f*canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, 2.0f*canvas.getHeight()/3.0f, p);
		}

		// note, no need to check preferences here, as we do that when setting thumbnail_anim
		if( camera != null && this.thumbnail_anim && this.thumbnail != null ) {
			long time = System.currentTimeMillis() - this.thumbnail_anim_start_ms;
			final long duration = 500;
			if( time > duration ) {
				this.thumbnail_anim = false;
			}
			else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = this.thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = this.thumbnail.getHeight();
				Activity activity = (Activity)this.getContext();
			    View galleryButton = (View) activity.findViewById(R.id.gallery);
				float alpha = ((float)time)/(float)duration;

				int st_x = canvas.getWidth()/2;
				int st_y = canvas.getHeight()/2;
				int nd_x = galleryButton.getLeft() + galleryButton.getWidth()/2;
				int nd_y = galleryButton.getTop() + galleryButton.getHeight()/2;
				int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
				int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );

				float st_w = canvas.getWidth();
				float st_h = canvas.getHeight();
				float nd_w = galleryButton.getWidth();
				float nd_h = galleryButton.getHeight();
				//int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				//int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				float correction_w = st_w/nd_w - 1.0f;
				float correction_h = st_h/nd_h - 1.0f;
				int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
				int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
				thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2;
				thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2;
				thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2;
				thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2;
				//canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				//thumbnail_anim_matrix.reset();
				if( ui_rotation == 90 || ui_rotation == 270 ) {
					float ratio = ((float)thumbnail.getWidth())/(float)thumbnail.getHeight();
					thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, thumbnail.getWidth()/2, thumbnail.getHeight()/2);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation, thumbnail.getWidth()/2, thumbnail.getHeight()/2);
				canvas.drawBitmap(this.thumbnail, thumbnail_anim_matrix, p);
			}
		}
		
		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2, canvas.getHeight()/2);

		final float scale = getResources().getDisplayMetrics().density;
		int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
		// fine tuning to adjust placement of text with respect to the GUI, depending on orientation
		int text_extra_offset_y = 0;
		if( ui_rotation == 0 ) {
			text_extra_offset_y = (int)(0.5*text_y);
		}
		else if( ui_rotation == 180 ) {
			text_extra_offset_y = (int)(2.5*text_y);
		}
		else if( ui_rotation == 90 || ui_rotation == 270 ) {
			text_extra_offset_y = -(int)(0.5*text_y);
		}

		if( camera != null && this.phase != PHASE_PREVIEW_PAUSED ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/
			if( this.has_level_angle && sharedPreferences.getBoolean("preference_show_angle", true) ) {
				int color = Color.WHITE;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				// Convert the dps to pixels, based on density scale
				int pixels_offset_x = (int) (50 * scale + 0.5f); // convert dps to pixels
				int pixels_offset_y = text_extra_offset_y;
				if( getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ) {
					pixels_offset_x = 0;
					p.setTextAlign(Paint.Align.CENTER);
				}
				else if( ui_placement_right ) {
					pixels_offset_x = - pixels_offset_x;
					p.setTextAlign(Paint.Align.RIGHT);
				}
				else {
					p.setTextAlign(Paint.Align.LEFT);
				}
				if( Math.abs(this.level_angle) <= 1.0 ) {
					color = Color.GREEN;
				}
				drawTextWithBackground(canvas, p, "Angle: " + decimalFormat.format(this.level_angle), color, Color.BLACK, canvas.getWidth() / 2 + pixels_offset_x, canvas.getHeight() - pixels_offset_y);
			}
			//if( this.is_taking_photo_on_timer ) {
			if( this.isOnTimer() ) {
				long remaining_time = (take_photo_time - System.currentTimeMillis() + 999)/1000;
				if( MyDebug.LOG )
					Log.d(TAG, "remaining_time: " + remaining_time);
				if( remaining_time >= 0 ) {
					p.setTextSize(42 * scale + 0.5f); // convert dps to pixels
					p.setTextAlign(Paint.Align.CENTER);
					drawTextWithBackground(canvas, p, "" + remaining_time, Color.RED, Color.rgb(75, 75, 75), canvas.getWidth() / 2, canvas.getHeight() / 2);
				}
			}
			else if( this.video_recorder != null && video_start_time_set ) {
            	long video_time = (System.currentTimeMillis() - video_start_time);
            	//int ms = (int)(video_time % 1000);
            	video_time /= 1000;
            	int secs = (int)(video_time % 60);
            	video_time /= 60;
            	int mins = (int)(video_time % 60);
            	video_time /= 60;
            	long hours = video_time;
            	//String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs) + ":" + String.format("%03d", ms);
            	String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs);
            	/*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
    			p.setTextSize(24 * scale + 0.5f); // convert dps to pixels
    			p.setTextAlign(Paint.Align.CENTER);
    			int pixels_offset_y = (int) (164 * scale + 0.5f); // convert dps to pixels
				drawTextWithBackground(canvas, p, "" + time_s, Color.RED, Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() - pixels_offset_y);
			}
		}
		else if( camera == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawText("FAILED TO OPEN CAMERA.", canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			canvas.drawText("CAMERA MAY BE IN USE", canvas.getWidth() / 2, canvas.getHeight() / 2 + pixels_offset, p);
			canvas.drawText("BY ANOTHER APPLICATION?", canvas.getWidth() / 2, canvas.getHeight() / 2 + 2*pixels_offset, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( this.has_zoom && camera != null && sharedPreferences.getBoolean("preference_show_zoom", true) ) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			// only show when actually zoomed in
			if( zoom_ratio > 1.0f + 1.0e-5f ) {
				// Convert the dps to pixels, based on density scale
				int pixels_offset_y = 2*text_y+text_extra_offset_y;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				p.setTextAlign(Paint.Align.CENTER);
				drawTextWithBackground(canvas, p, "Zoom: " + zoom_ratio +"x", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() - pixels_offset_y);
			}
		}
		if( camera != null && sharedPreferences.getBoolean("preference_free_memory", true) ) {
			int pixels_offset_y = 1*text_y+text_extra_offset_y;
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			long time_now = System.currentTimeMillis();
			if( free_memory_gb < 0.0f || time_now > last_free_memory_time + 1000 ) {
				long free_mb = MainActivity.freeMemory();
				if( free_mb >= 0 ) {
					free_memory_gb = free_mb/1024.0f;
					last_free_memory_time = time_now;
				}
			}
			if( free_memory_gb >= 0.0f ) {
				drawTextWithBackground(canvas, p, "Free memory: " + decimalFormat.format(free_memory_gb) + "GB", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() - pixels_offset_y);
			}
		}
		
		{
			if( !this.has_battery_frac || System.currentTimeMillis() > this.last_battery_time + 60000 ) {
				// only check periodically - unclear if checking is costly in any way
				Activity activity = (Activity)this.getContext();
				Intent batteryStatus = activity.registerReceiver(null, battery_ifilter);
				int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				has_battery_frac = true;
				battery_frac = battery_level/(float)battery_scale;
				last_battery_time = System.currentTimeMillis();
				if( MyDebug.LOG )
					Log.d(TAG, "Battery status is " + battery_level + " / " + battery_scale + " : " + battery_frac);
			}
			//battery_frac = 0.2999f; // test
			int battery_x = (int) (5 * scale + 0.5f); // convert dps to pixels
			int battery_y = battery_x;
			int battery_width = (int) (5 * scale + 0.5f); // convert dps to pixels
			int battery_height = 4*battery_width;
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				battery_x += diff/2;
				battery_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				battery_y = canvas.getHeight() - battery_y - battery_height;
			}
			if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
				battery_x = canvas.getWidth() - battery_x - battery_width;
			}
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			canvas.drawRect(battery_x, battery_y, battery_x+battery_width, battery_y+battery_height, p);
			p.setColor(battery_frac >= 0.3f ? Color.GREEN : Color.RED);
			p.setStyle(Paint.Style.FILL);
			canvas.drawRect(battery_x+1, battery_y+1+(1.0f-battery_frac)*(battery_height-2), battery_x+battery_width-1, battery_y+battery_height-1, p);
		}
		
		boolean store_location = sharedPreferences.getBoolean("preference_location", false);
		if( store_location && has_set_location ) {
			int location_x = (int) (20 * scale + 0.5f); // convert dps to pixels
			int location_y = (int) (5 * scale + 0.5f); // convert dps to pixels
			int location_size = (int) (20 * scale + 0.5f); // convert dps to pixels
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff/2;
				location_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				location_y = canvas.getHeight() - location_y - location_size;
			}
			if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
				location_x = canvas.getWidth() - location_x - location_size;
			}
			location_dest.set(location_x, location_y, location_x + location_size, location_y + location_size);
			canvas.drawBitmap(location_bitmap, null, location_dest, p);
		}
		
		{
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.LEFT);
			int location_x = (int) (50 * scale + 0.5f); // convert dps to pixels
			int location_y = (int) (15 * scale + 0.5f); // convert dps to pixels
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff/2;
				location_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				location_y = canvas.getHeight() - location_y;
			}
			if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
				location_x = canvas.getWidth() - location_x;
				p.setTextAlign(Paint.Align.RIGHT);
			}
	        Calendar c = Calendar.getInstance();
	        String current_time = DateFormat.getTimeInstance().format(c.getTime());
	        drawTextWithBackground(canvas, p, current_time, Color.WHITE, Color.BLACK, location_x, location_y);
	    }

		canvas.restore();
		
		if( this.focus_success != FOCUS_DONE ) {
			int size = (int) (50 * scale + 0.5f); // convert dps to pixels
			if( this.focus_success == FOCUS_SUCCESS )
				p.setColor(Color.GREEN);
			else if( this.focus_success == FOCUS_FAILED )
				p.setColor(Color.RED);
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x = 0;
			int pos_y = 0;
			if( has_focus_area ) {
				pos_x = focus_screen_x;
				pos_y = focus_screen_y;
			}
			else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			canvas.drawRect(pos_x - size, pos_y - size, pos_x + size, pos_y + size, p);
			if( focus_complete_time != -1 && System.currentTimeMillis() > focus_complete_time + 1000 ) {
				focus_success = FOCUS_DONE;
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
		if( this.using_face_detection && this.faces_detected != null ) {
			p.setColor(Color.YELLOW);
			p.setStyle(Paint.Style.STROKE);
			for(Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less than 50
				if( face.score >= 50 ) {
					calculateCameraToPreviewMatrix();
					face_rect.set(face.rect);
					this.camera_to_preview_matrix.mapRect(face_rect);
					/*int eye_radius = (int) (5 * scale + 0.5f); // convert dps to pixels
					int mouth_radius = (int) (10 * scale + 0.5f); // convert dps to pixels
					float [] top_left = {face.rect.left, face.rect.top};
					float [] bottom_right = {face.rect.right, face.rect.bottom};
					canvas.drawRect(top_left[0], top_left[1], bottom_right[0], bottom_right[1], p);*/
					canvas.drawRect(face_rect, p);
					/*if( face.leftEye != null ) {
						float [] left_point = {face.leftEye.x, face.leftEye.y};
						cameraToPreview(left_point);
						canvas.drawCircle(left_point[0], left_point[1], eye_radius, p);
					}
					if( face.rightEye != null ) {
						float [] right_point = {face.rightEye.x, face.rightEye.y};
						cameraToPreview(right_point);
						canvas.drawCircle(right_point[0], right_point[1], eye_radius, p);
					}
					if( face.mouth != null ) {
						float [] mouth_point = {face.mouth.x, face.mouth.y};
						cameraToPreview(mouth_point);
						canvas.drawCircle(mouth_point[0], mouth_point[1], mouth_radius, p);
					}*/
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
		final float scale = getResources().getDisplayMetrics().density;
		p.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(127);
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
			float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
			if( paint.getTextAlign() == Paint.Align.CENTER )
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
		text_bounds.left += location_x - padding;
		text_bounds.top += location_y - padding;
		text_bounds.right += location_x + padding;
		text_bounds.bottom += location_y + padding;
		canvas.drawRect(text_bounds, paint);
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}

	public void zoomIn() {
		if( MyDebug.LOG )
			Log.d(TAG, "zoomIn()");
    	if(zoom_factor < max_zoom_factor) {
    		zoom_factor++;
    		if( MyDebug.LOG )
    			Log.d(TAG, "zoom in to " + zoom_factor);
			Camera.Parameters parameters = camera.getParameters();
			if( parameters.isZoomSupported() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "zoom was: " + parameters.getZoom());
				parameters.setZoom((int)zoom_factor);
				try {
					camera.setParameters(parameters);
				}
	        	catch(RuntimeException e) {
	        		// crash reported in v1.3 on device "PANTONE 5 SoftBank 107SH (SBM107SH)"
		    		if( MyDebug.LOG )
		    			Log.e(TAG, "runtime exception in zoomIn()");
					e.printStackTrace();
	        	}
	    		clearFocusAreas();
			}
        }
	}
	
	public void zoomOut() {
		if( MyDebug.LOG )
			Log.d(TAG, "zoomOut()");
		if(zoom_factor > 0) {
			zoom_factor--;
			if( MyDebug.LOG )
				Log.d(TAG, "zoom out to " + zoom_factor);
			Camera.Parameters parameters = camera.getParameters();
			if( parameters.isZoomSupported() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "zoom was: " + parameters.getZoom());
				parameters.setZoom((int)zoom_factor);
				try {
					camera.setParameters(parameters);
				}
	        	catch(RuntimeException e) {
	        		// see note for zoomIn()
		    		if( MyDebug.LOG )
		    			Log.e(TAG, "runtime exception in zoomOut()");
					e.printStackTrace();
	        	}
	    		clearFocusAreas();
			}
        }
	}

	public void switchCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "switchCamera()");
		//if( is_taking_photo && !is_taking_photo_on_timer ) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		int n_cameras = Camera.getNumberOfCameras();
		if( MyDebug.LOG )
			Log.d(TAG, "found " + n_cameras + " cameras");
		if( n_cameras > 1 ) {
			closeCamera();
			cameraId = (cameraId+1) % n_cameras;
		    Camera.CameraInfo info = new Camera.CameraInfo();
		    Camera.getCameraInfo(cameraId, info);
		    if( info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ) {
				showToast(switch_camera_toast, "Front Camera");
		    }
		    else {
				showToast(switch_camera_toast, "Back Camera");
		    }
			this.openCamera(true);
			
			// we update the focus, in case we weren't able to do it when switching video with a camera that didn't support focus modes
			updateFocusForVideo();
		}
	}

	public void switchVideo(boolean save) {
		if( MyDebug.LOG )
			Log.d(TAG, "switchVideo()");
		boolean old_is_video = is_video;
		if( this.is_video ) {
			if( video_recorder != null ) {
				stopVideo();
			}
			this.is_video = false;
			showToast(switch_video_toast, "Photo");
		}
		else {
			//if( is_taking_photo_on_timer ) {
			if( this.isOnTimer() ) {
				takePictureTimerTask.cancel();
				if( beepTimerTask != null ) {
					beepTimerTask.cancel();
				}
				/*is_taking_photo_on_timer = false;
				is_taking_photo = false;*/
				this.phase = PHASE_NORMAL;
				if( MyDebug.LOG )
					Log.d(TAG, "cancelled camera timer");
				this.is_video = true;
			}
			//else if( this.is_taking_photo ) {
			else if( this.phase == PHASE_TAKING_PHOTO ) {
				// wait until photo taken
				if( MyDebug.LOG )
					Log.d(TAG, "wait until photo taken");
			}
			else {
				this.is_video = true;
			}
			
			if( this.is_video ) {
				showToast(switch_video_toast, "Video");
				//if( this.is_preview_paused ) {
				if( this.phase == PHASE_PREVIEW_PAUSED ) {
					startCameraPreview();
				}
			}
		}
		
		if( is_video != old_is_video ) {
			updateFocusForVideo();

			Activity activity = (Activity)this.getContext();
			ImageButton view = (ImageButton)activity.findViewById(R.id.take_photo);
			view.setImageResource(is_video ? R.drawable.take_video : R.drawable.take_photo);

			if( save ) {
				// now save
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putBoolean(getIsVideoPreferenceKey(), is_video);
				editor.apply();
	    	}
		}
	}
	
	private void updateFocusForVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocusForVideo()");
		if( this.supported_focus_values != null && camera != null ) {
			Camera.Parameters parameters = camera.getParameters();
			String current_focus_mode = parameters.getFocusMode();
			boolean focus_is_video = current_focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			if( MyDebug.LOG ) {
				Log.d(TAG, "current_focus_mode: " + current_focus_mode);
				Log.d(TAG, "focus_is_video: " + focus_is_video + " , is_video: " + is_video);
			}
			if( focus_is_video != is_video ) {
				if( MyDebug.LOG )
					Log.d(TAG, "need to change focus mode");
				updateFocus(is_video ? "focus_mode_continuous_video" : "focus_mode_auto", true, true);
				if( MyDebug.LOG ) {
					parameters = camera.getParameters();
					current_focus_mode = parameters.getFocusMode();
					Log.d(TAG, "new focus mode: " + current_focus_mode);
				}
			}
		}
	}

	public void cycleFlash() {
		if( MyDebug.LOG )
			Log.d(TAG, "cycleFlash()");
		//if( is_taking_photo && !is_taking_photo_on_timer ) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		if( this.supported_flash_values != null && this.supported_flash_values.size() > 1 ) {
			int new_flash_index = (current_flash_index+1) % this.supported_flash_values.size();
			updateFlash(new_flash_index);

			// now save
			String flash_value = supported_flash_values.get(current_flash_index);
			if( MyDebug.LOG ) {
				Log.d(TAG, "save new flash_value: " + flash_value);
			}
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(getFlashPreferenceKey(cameraId), flash_value);
			editor.apply();
		}
	}

	private boolean updateFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + flash_value);
		if( supported_flash_values != null ) {
	    	int new_flash_index = supported_flash_values.indexOf(flash_value);
			if( MyDebug.LOG )
				Log.d(TAG, "new_flash_index: " + new_flash_index);
	    	if( new_flash_index != -1 ) {
	    		updateFlash(new_flash_index);
	    		return true;
	    	}
		}
    	return false;
	}
	
	private void updateFlash(int new_flash_index) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + new_flash_index);
		// updates the Flash button, and Flash camera mode
		if( supported_flash_values != null && new_flash_index != current_flash_index ) {
			boolean initial = current_flash_index==-1;
			current_flash_index = new_flash_index;
			if( MyDebug.LOG )
				Log.d(TAG, "    current_flash_index is now " + current_flash_index + " (initial " + initial + ")");

			Activity activity = (Activity)this.getContext();
		    ImageButton flashButton = (ImageButton) activity.findViewById(R.id.flash);
	    	String [] flash_entries = getResources().getStringArray(R.array.flash_entries);
	    	String [] flash_icons = getResources().getStringArray(R.array.flash_icons);
			String flash_value = supported_flash_values.get(current_flash_index);
			if( MyDebug.LOG )
				Log.d(TAG, "    flash_value: " + flash_value);
	    	String [] flash_values = getResources().getStringArray(R.array.flash_values);
	    	for(int i=0;i<flash_values.length;i++) {
				/*if( MyDebug.LOG )
					Log.d(TAG, "    compare to: " + flash_values[i]);*/
	    		if( flash_value.equals(flash_values[i]) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "    found entry: " + i);
	    			//flashButton.setText(flash_entries[i]);
	    			int resource = getResources().getIdentifier(flash_icons[i], null, activity.getApplicationContext().getPackageName());
	    			flashButton.setImageResource(resource);
	    			if( !initial ) {
	    				showToast(flash_toast, flash_entries[i]);
	    			}
	    			break;
	    		}
	    	}
	    	this.setFlash(flash_value);
		}
	}

	private void setFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFlash() " + flash_value);
		Camera.Parameters parameters = camera.getParameters();
    	if( flash_value.equals("flash_off") ) {
    		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    	}
    	else if( flash_value.equals("flash_auto") ) {
    		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
    	}
    	else if( flash_value.equals("flash_on") ) {
    		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
    	}
    	else if( flash_value.equals("flash_torch") ) {
    		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
    	}
    	else if( flash_value.equals("flash_red_eye") ) {
    		parameters.setFlashMode(Camera.Parameters.FLASH_MODE_RED_EYE);
    	}
		camera.setParameters(parameters);
	}

	private List<String> getSupportedFlashModes(List<String> supported_flash_modes) {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedFlashModes()");
		List<String> output_modes = new Vector<String>();
		if( supported_flash_modes != null ) {
			/*for(String flash_mode : supported_flash_modes) {
				if( flash_mode.equals(Camera.Parameters.FLASH_MODE_OFF) ) {
					output_modes.add("flash_off");
				}
				else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_AUTO) ) {
					output_modes.add("flash_auto");
				}
				else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_ON) ) {
					output_modes.add("flash_on");
				}
				else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_TORCH) ) {
					output_modes.add("flash_torch");
				}
				else if( flash_mode.equals(Camera.Parameters.FLASH_MODE_RED_EYE) ) {
					output_modes.add("flash_red_eye");
				}
			}*/
			// also resort as well as converting
			// first one will be the default choice
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_AUTO) ) {
				output_modes.add("flash_auto");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_auto");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_OFF) ) {
				output_modes.add("flash_off");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_off");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_ON) ) {
				output_modes.add("flash_on");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_on");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_TORCH) ) {
				output_modes.add("flash_torch");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_torch");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_RED_EYE) ) {
				output_modes.add("flash_red_eye");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_red_eye");
			}
		}
		return output_modes;
	}

	public void cycleFocusMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "cycleFocusMode()");
		//if( is_taking_photo && !is_taking_photo_on_timer ) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe - otherwise problem that changing the focus mode will cancel the autofocus before taking a photo, so we never take a photo, but is_taking_photo remains true!
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		if( this.supported_focus_values != null && this.supported_focus_values.size() > 1 ) {
			int new_focus_index = (current_focus_index+1) % this.supported_focus_values.size();
			updateFocus(new_focus_index, false, true);
		}
	}
	
	private boolean updateFocus(String focus_value, boolean quiet, boolean save) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + focus_value);
		if( this.supported_focus_values != null ) {
	    	int new_focus_index = supported_focus_values.indexOf(focus_value);
			if( MyDebug.LOG )
				Log.d(TAG, "new_focus_index: " + new_focus_index);
	    	if( new_focus_index != -1 ) {
	    		updateFocus(new_focus_index, quiet, save);
	    		return true;
	    	}
		}
    	return false;
	}

	private void updateFocus(int new_focus_index, boolean quiet, boolean save) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + new_focus_index + " current_focus_index: " + current_focus_index);
		// updates the Focus button, and Focus camera mode
		if( this.supported_focus_values != null && new_focus_index != current_focus_index ) {
			boolean initial = current_focus_index==-1;
			current_focus_index = new_focus_index;
			if( MyDebug.LOG )
				Log.d(TAG, "    current_focus_index is now " + current_focus_index + " (initial " + initial + ")");

			Activity activity = (Activity)this.getContext();
		    ImageButton focusModeButton = (ImageButton) activity.findViewById(R.id.focus_mode);
	    	String [] focus_entries = getResources().getStringArray(R.array.focus_mode_entries);
	    	String [] focus_icons = getResources().getStringArray(R.array.focus_mode_icons);
			String focus_value = supported_focus_values.get(current_focus_index);
			if( MyDebug.LOG )
				Log.d(TAG, "    focus_value: " + focus_value);
	    	String [] focus_values = getResources().getStringArray(R.array.focus_mode_values);
	    	for(int i=0;i<focus_values.length;i++) {
				if( MyDebug.LOG )
					Log.d(TAG, "    compare to: " + focus_values[i]);
	    		if( focus_value.equals(focus_values[i]) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "    found entry: " + i);
	    			int resource = getResources().getIdentifier(focus_icons[i], null, activity.getApplicationContext().getPackageName());
	    			focusModeButton.setImageResource(resource);
	    			if( !initial && !quiet ) {
	    				showToast(focus_toast, focus_entries[i]);
	    			}
	    			break;
	    		}
	    	}
	    	this.setFocus(focus_value);

	    	if( save ) {
				// now save
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(getFocusPreferenceKey(cameraId), focus_value);
				editor.apply();
	    	}
		}
	}

	private void setFocus(String focus_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFocus() " + focus_value);
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "null camera");
			return;
		}
		Camera.Parameters parameters = camera.getParameters();
    	if( focus_value.equals("focus_mode_auto") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
    	}
    	else if( focus_value.equals("focus_mode_infinity") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
    	}
    	else if( focus_value.equals("focus_mode_macro") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
    	}
    	else if( focus_value.equals("focus_mode_fixed") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
    	}
    	else if( focus_value.equals("focus_mode_edof") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_EDOF);
    	}
    	else if( focus_value.equals("focus_mode_continuous_video") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    	}
    	else {
    		if( MyDebug.LOG )
    			Log.d(TAG, "setFocus() received unknown focus value " + focus_value);
    	}
		camera.setParameters(parameters);
		clearFocusAreas();
		tryAutoFocus();
	}

	private List<String> getSupportedFocusModes(List<String> supported_focus_modes) {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedFocusModes()");
		List<String> output_modes = new Vector<String>();
		if( supported_focus_modes != null ) {
			// also resort as well as converting
			// first one will be the default choice
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ) {
				output_modes.add("focus_mode_auto");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_auto");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ) {
				output_modes.add("focus_mode_infinity");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_infinity");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_MACRO) ) {
				output_modes.add("focus_mode_macro");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_macro");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ) {
				output_modes.add("focus_mode_fixed");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_fixed");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_EDOF) ) {
				output_modes.add("focus_mode_edof");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_edof");
			}
			if( supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) {
				output_modes.add("focus_mode_continuous_video");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_continuous_video");
			}
		}
		return output_modes;
	}
	
	public void takePicturePressed() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicturePressed");
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			return;
		}
		//if( is_taking_photo_on_timer ) {
		if( this.isOnTimer() ) {
			takePictureTimerTask.cancel();
			if( beepTimerTask != null ) {
				beepTimerTask.cancel();
			}
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			if( MyDebug.LOG )
				Log.d(TAG, "cancelled camera timer");
		    showToast(take_photo_toast, "Cancelled timer");
			return;
		}
    	//if( is_taking_photo ) {
		if( this.phase == PHASE_TAKING_PHOTO ) {
    		if( is_video ) {
    			if( !video_start_time_set || System.currentTimeMillis() - video_start_time < 500 ) {
    				// if user presses to stop too quickly, we ignore
    				// firstly to reduce risk of corrupt video files when stopping too quickly (see RuntimeException we have to catch in stopVideo),
    				// secondly, to reduce a backlog of events which slows things down, if user presses start/stop repeatedly too quickly
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "ignore pressing stop video too quickly after start");
    			}
    			else {
    				stopVideo();
    			}
    		}
    		else {
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "already taking a photo");
    		}
    		return;
    	}

    	// make sure that preview running (also needed to hide trash/share icons)
        this.startCameraPreview();

        //is_taking_photo = true;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String timer_value = sharedPreferences.getString("preference_timer", "0");
		long timer_delay = 0;
		try {
			timer_delay = Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse timer_value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }

		String burst_mode_value = sharedPreferences.getString("preference_burst_mode", "1");
		try {
			n_burst = Integer.parseInt(burst_mode_value);
    		if( MyDebug.LOG )
    			Log.d(TAG, "n_burst: " + n_burst);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse burst_mode_value: " + burst_mode_value);
    		e.printStackTrace();
    		n_burst = 1;
        }
		remaining_burst_photos = n_burst-1;
		
		if( timer_delay == 0 ) {
			takePicture();
		}
		else {
			takePictureOnTimer(timer_delay, false);
		}
	}
	
	private void takePictureOnTimer(long timer_delay, boolean repeated) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "takePictureOnTimer");
			Log.d(TAG, "timer_delay: " + timer_delay);
		}
        this.phase = PHASE_TIMER;
		class TakePictureTimerTask extends TimerTask {
			public void run() {
				if( beepTimerTask != null ) {
					beepTimerTask.cancel();
				}
				takePicture();
			}
		}
		take_photo_time = System.currentTimeMillis() + timer_delay;
		if( MyDebug.LOG )
			Log.d(TAG, "take photo at: " + take_photo_time);
		if( !repeated ) {
			showToast(take_photo_toast, "Started timer");
		}
    	takePictureTimer.schedule(takePictureTimerTask = new TakePictureTimerTask(), timer_delay);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		if( sharedPreferences.getBoolean("preference_timer_beep", true) ) {
    		class BeepTimerTask extends TimerTask {
    			public void run() {
    			    try {
    			        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    					Activity activity = (Activity)getContext();
    			        Ringtone r = RingtoneManager.getRingtone(activity.getApplicationContext(), notification);
    			        r.play();
    			    }
    			    catch(Exception e) {
    			    }		
    			}
    		}
        	beepTimer.schedule(beepTimerTask = new BeepTimerTask(), 0, 1000);
		}
	}

	private void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
		this.thumbnail_anim = false;
        this.phase = PHASE_TAKING_PHOTO;
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		focus_success = FOCUS_DONE; // clear focus rectangle

        if( is_video ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "start video recording");
			MainActivity main_activity = (MainActivity)Preview.this.getContext();
			File videoFile = main_activity.getOutputMediaFile(MainActivity.MEDIA_TYPE_VIDEO);
			if( videoFile == null ) {
	            Log.e(TAG, "Couldn't create media video file; check storage permissions?");
	    	    showToast(null, "Failed to save video file");
			}
			else {
				video_name = videoFile.getAbsolutePath();
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "save to: " + video_name);
	        	this.camera.unlock();
	        	video_recorder = new MediaRecorder();
	        	video_recorder.setCamera(camera);
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
				boolean record_audio = sharedPreferences.getBoolean("preference_record_audio", true);
				if( record_audio ) {
					video_recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
				}
				video_recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

				/*video_recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
				if( record_audio ) {
					video_recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
				}
	        	video_recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);*/
				CamcorderProfile profile = CamcorderProfile.get(this.cameraId, current_video_quality != -1 ? video_quality.get(current_video_quality).intValue() : CamcorderProfile.QUALITY_HIGH);
	    		if( MyDebug.LOG ) {
	    			Log.d(TAG, "current_video_quality: " + current_video_quality);
	    			if( current_video_quality != -1 )
	    				Log.d(TAG, "current_video_quality value: " + video_quality.get(current_video_quality).intValue());
	    			Log.d(TAG, "resolution " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
	    		}
				if( record_audio ) {
					video_recorder.setProfile(profile);
				}
				else {
					// from http://stackoverflow.com/questions/5524672/is-it-possible-to-use-camcorderprofile-without-audio-source
					video_recorder.setOutputFormat(profile.fileFormat);
					video_recorder.setVideoFrameRate(profile.videoFrameRate);
					video_recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
					video_recorder.setVideoEncodingBitRate(profile.videoBitRate);
					video_recorder.setVideoEncoder(profile.videoCodec);
				}

	        	video_recorder.setOrientationHint(this.current_rotation);
	        	video_recorder.setOutputFile(video_name);
	        	try {
	        		/*if( true ) // test
	        			throw new IOException();*/
		        	video_recorder.setPreviewDisplay(mHolder.getSurface());
					video_recorder.prepare();
	            	video_recorder.start();
	            	video_start_time = System.currentTimeMillis();
	            	video_start_time_set = true;
    				showToast(stopstart_video_toast, "Started recording video");
    				// don't send intent for ACTION_MEDIA_SCANNER_SCAN_FILE yet - wait until finished, so we get completed file
				}
	        	catch(IOException e) {
		    		if( MyDebug.LOG )
		    			Log.e(TAG, "failed to save video");
					e.printStackTrace();
		    	    showToast(null, "Failed to save video");
		    		video_recorder.reset();
		    		video_recorder.release(); 
		    		video_recorder = null;
					/*is_taking_photo = false;
					is_taking_photo_on_timer = false;*/
					this.phase = PHASE_NORMAL;
					showGUI(true);
					this.reconnectCamera();
				}
	        	catch(RuntimeException e) {
	        		// needed for emulator at least - although MediaRecorder not meant to work with emulator, it's good to fail gracefully
		    		if( MyDebug.LOG )
		    			Log.e(TAG, "runtime exception starting video recorder");
					e.printStackTrace();
		    	    showToast(null, "Failed to record video");
		    		video_recorder.reset();
		    		video_recorder.release(); 
		    		video_recorder = null;
					/*is_taking_photo = false;
					is_taking_photo_on_timer = false;*/
					this.phase = PHASE_NORMAL;
					showGUI(true);
					this.reconnectCamera();
				}
			}
        	return;
		}

		showGUI(false);
        Camera.Parameters parameters = camera.getParameters();
		String focus_mode = parameters.getFocusMode();
		if( MyDebug.LOG )
			Log.d(TAG, "focus_mode is " + focus_mode);

		if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) {
	        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success, Camera camera) {
					if( MyDebug.LOG )
						Log.d(TAG, "autofocus complete: " + success);
					takePictureWhenFocused();
				}
	        };
			if( MyDebug.LOG )
				Log.d(TAG, "start autofocus to take picture");
	    	camera.autoFocus(autoFocusCallback);
			count_cameraAutoFocus++;
		}
		else {
			takePictureWhenFocused();
		}
	}

	private void takePictureWhenFocused() {
		// should be called when auto-focused
		if( MyDebug.LOG )
			Log.d(TAG, "takePictureWhenFocused");
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "remaining_burst_photos: " + remaining_burst_photos);

    	Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
    		// don't do anything here, but we need to implement the callback to get the shutter sound (at least on Galaxy Nexus and Nexus 7)
            public void onShutter() {
    			if( MyDebug.LOG )
    				Log.d(TAG, "shutterCallback.onShutter()");
            }
        };

        Camera.PictureCallback jpegPictureCallback = new Camera.PictureCallback() {
    	    public void onPictureTaken(byte[] data, Camera cam) {
    	    	// n.b., this is automatically run in a different thread
	            System.gc();
    			if( MyDebug.LOG )
    				Log.d(TAG, "onPictureTaken");

        		MainActivity main_activity = (MainActivity)Preview.this.getContext();
    			boolean image_capture_intent = false;
       	        Uri image_capture_intent_uri = null;
    	        String action = main_activity.getIntent().getAction();
    	        if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "from image capture intent");
        			image_capture_intent = true;
        	        Bundle myExtras = main_activity.getIntent().getExtras();
        	        if (myExtras != null) {
        	        	image_capture_intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            			if( MyDebug.LOG )
            				Log.d(TAG, "save to: " + image_capture_intent_uri);
        	        }
    	        }

    	        boolean success = false;
    	        Bitmap bitmap = null;
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Preview.this.getContext());
				boolean auto_stabilise = sharedPreferences.getBoolean("preference_auto_stabilise", false);
    			if( auto_stabilise && has_level_angle && main_activity.supportsAutoStabilise() )
    			{
    				//level_angle = -129;
    				if( test_have_angle )
    					level_angle = test_angle;
    				while( level_angle < -90 )
    					level_angle += 180;
    				while( level_angle > 90 )
    					level_angle -= 180;
        			if( MyDebug.LOG )
        				Log.d(TAG, "auto stabilising... angle: " + level_angle);
    				BitmapFactory.Options options = new BitmapFactory.Options();
    				//options.inMutable = true;
    				options.inPurgeable = true;
        			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        			int width = bitmap.getWidth();
        			int height = bitmap.getHeight();
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "level_angle: " + level_angle);
        				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
        				Log.d(TAG, "bitmap size: " + width*height*4);
        			}
        			/*for(int y=0;y<height;y++) {
        				for(int x=0;x<width;x++) {
        					int col = bitmap.getPixel(x, y);
        					col = col & 0xffff0000; // mask out red component
        					bitmap.setPixel(x, y, col);
        				}
        			}*/
        			if( test_low_memory ) {
        		    	level_angle = 45.0;
        			}
        		    Matrix matrix = new Matrix();
        		    double level_angle_rad_abs = Math.abs( Math.toRadians(level_angle) );
        		    int w1 = width, h1 = height;
        		    double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
        		    double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
        		    // apply a scale so that the overall image size isn't increased
        		    float orig_size = w1*h1;
        		    float rotated_size = (float)(w0*h0);
        		    float scale = (float)Math.sqrt(orig_size/rotated_size);
        			if( test_low_memory ) {
            			if( MyDebug.LOG )
            				Log.d(TAG, "TESTING LOW MEMORY");
        		    	scale *= 2.0f; // test 20MP
        		    	//scale *= 1.613f; // test 13MP
        			}
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
        				Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
        				Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
        			}
        		    matrix.postScale(scale, scale);
        		    w0 *= scale;
        		    h0 *= scale;
        		    w1 *= scale;
        		    h1 *= scale;
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
        				Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
        			}
        		    Camera.CameraInfo info = new Camera.CameraInfo();
        		    Camera.getCameraInfo(cameraId, info);
        		    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            		    matrix.postRotate((float)-level_angle);
        		    }
        		    else {
            		    matrix.postRotate((float)level_angle);
        		    }
        		    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        		    // careful, as new_bitmap is sometimes not a copy!
        		    if( new_bitmap != bitmap ) {
        		    	bitmap.recycle();
        		    	bitmap = new_bitmap;
        		    }
    	            System.gc();
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "rotated and scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
        				Log.d(TAG, "rotated and scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
        			}
        			double tan_theta = Math.tan(level_angle_rad_abs);
        			double sin_theta = Math.sin(level_angle_rad_abs);
        			double denom = (double)( h0/w0 + tan_theta );
        			double alt_denom = (double)( w0/h0 + tan_theta );
        			if( denom == 0.0 || denom < 1.0e-14 ) {
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "zero denominator?!");
        			}
        			else if( alt_denom == 0.0 || alt_denom < 1.0e-14 ) {
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "zero alt denominator?!");
        			}
        			else {
            			int w2 = (int)(( h0 + 2.0*h1*sin_theta*tan_theta - w0*tan_theta ) / denom);
            			int h2 = (int)(w2*h0/(double)w0);
            			int alt_h2 = (int)(( w0 + 2.0*w1*sin_theta*tan_theta - h0*tan_theta ) / alt_denom);
            			int alt_w2 = (int)(alt_h2*w0/(double)h0);
            			if( MyDebug.LOG ) {
            				//Log.d(TAG, "h0 " + h0 + " 2.0*h1*sin_theta*tan_theta " + 2.0*h1*sin_theta*tan_theta + " w0*tan_theta " + w0*tan_theta + " / h0/w0 " + h0/w0 + " tan_theta " + tan_theta);
            				Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
            				Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
            			}
            			if( alt_w2 < w2 ) {
                			if( MyDebug.LOG ) {
                				Log.d(TAG, "chose alt!");
                			}
            				w2 = alt_w2;
            				h2 = alt_h2;
            			}
            			if( w2 <= 0 )
            				w2 = 1;
            			else if( w2 >= bitmap.getWidth() )
            				w2 = bitmap.getWidth()-1;
            			if( h2 <= 0 )
            				h2 = 1;
            			else if( h2 >= bitmap.getHeight() )
            				h2 = bitmap.getHeight()-1;
            			int x0 = (bitmap.getWidth()-w2)/2;
            			int y0 = (bitmap.getHeight()-h2)/2;
            			if( MyDebug.LOG ) {
            				Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
            			}
            			new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
            		    if( new_bitmap != bitmap ) {
            		    	bitmap.recycle();
            		    	bitmap = new_bitmap;
            		    }
        	            System.gc();
        			}
    			}

    			String exif_orientation_s = null;
    			String picFileName = null;
    			File picFile = null;
    	        try {
	    			OutputStream outputStream = null;
	    			if( image_capture_intent ) {
	        			if( image_capture_intent_uri != null )
	        			{
	        			    // Save the bitmap to the specified URI (use a try/catch block)
	        			    outputStream = main_activity.getContentResolver().openOutputStream(image_capture_intent_uri);
	        			}
	        			else
	        			{
	        			    // If the intent doesn't contain an URI, send the bitmap as a parcel
	        			    // (it is a good idea to reduce its size to ~50k pixels before)
		        			if( MyDebug.LOG )
		        				Log.d(TAG, "sent to intent via parcel");
	        				if( bitmap == null ) {
			        			if( MyDebug.LOG )
			        				Log.d(TAG, "create bitmap");
			    				BitmapFactory.Options options = new BitmapFactory.Options();
			    				//options.inMutable = true;
			    				options.inPurgeable = true;
			        			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
			        			int width = bitmap.getWidth();
			        			int height = bitmap.getHeight();
			        			if( MyDebug.LOG ) {
			        				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
			        				Log.d(TAG, "bitmap size: " + width*height*4);
			        			}
			        			final int small_size_c = 128;
			        			if( width > small_size_c ) {
			        				float scale = ((float)small_size_c)/(float)width;
				        			if( MyDebug.LOG )
				        				Log.d(TAG, "scale to " + scale);
				        		    Matrix matrix = new Matrix();
				        		    matrix.postScale(scale, scale);
				        		    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
				        		    // careful, as new_bitmap is sometimes not a copy!
				        		    if( new_bitmap != bitmap ) {
				        		    	bitmap.recycle();
				        		    	bitmap = new_bitmap;
				        		    }
				        			if( MyDebug.LOG ) {
				        				Log.d(TAG, "scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
				        				Log.d(TAG, "scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
				        			}
				        		}
	        				}
	        				main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
	        				main_activity.finish();
	        			}
	    			}
	    			else {
	        			picFile = main_activity.getOutputMediaFile(MainActivity.MEDIA_TYPE_IMAGE);
	        	        if( picFile == null ) {
	        	            Log.e(TAG, "Couldn't create media image file; check storage permissions?");
	        	    	    showToast(null, "Failed to save image file");
	        	        }
	        	        else {
		    	            picFileName = picFile.getAbsolutePath();
	        	    		if( MyDebug.LOG )
	        	    			Log.d(TAG, "save to: " + picFileName);
		    	            outputStream = new FileOutputStream(picFile);
	        	        }
	    			}
	    			
	    			if( outputStream != null ) {
        	            if( bitmap != null ) {
        	    			int image_quality = getImageQuality();
            	            bitmap.compress(Bitmap.CompressFormat.JPEG, image_quality, outputStream);
        	            }
        	            else {
        	            	outputStream.write(data);
        	            }
        	            outputStream.close();
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "onPictureTaken saved photo");

        				success = true;
        	            if( picFile != null ) {
        	            	if( bitmap != null ) {
        	            		// need to update EXIF data!
                	    		if( MyDebug.LOG )
                	    			Log.d(TAG, "write temp file to record EXIF data");
        	            		File tempFile = File.createTempFile("opencamera_exif", "");
    		    	            OutputStream tempOutputStream = new FileOutputStream(tempFile);
            	            	tempOutputStream.write(data);
            	            	tempOutputStream.close();
                	    		if( MyDebug.LOG )
                	    			Log.d(TAG, "read back EXIF data");
            	            	ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());
            	            	String exif_aperture = exif.getAttribute(ExifInterface.TAG_APERTURE);
            	            	String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            	            	String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
            	            	String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
            	            	String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
            	            	String exif_gps_altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
            	            	String exif_gps_altitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
            	            	String exif_gps_datestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
            	            	String exif_gps_latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            	            	String exif_gps_latitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            	            	String exif_gps_longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            	            	String exif_gps_longitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            	            	String exif_gps_processing_method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
            	            	String exif_gps_timestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
            	            	// leave width/height, as this will have changed!
            	            	String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO);
            	            	String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
            	            	String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
            	            	String exif_orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            	            	exif_orientation_s = exif_orientation; // store for later use (for the thumbnail, to save rereading it)
            	            	String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

            					if( !tempFile.delete() ) {
            						if( MyDebug.LOG )
            							Log.e(TAG, "failed to delete temp " + tempFile.getAbsolutePath());
            					}
            	            	if( MyDebug.LOG )
                	    			Log.d(TAG, "now write new EXIF data");
            	            	ExifInterface exif_new = new ExifInterface(picFile.getAbsolutePath());
            	            	if( exif_aperture != null )
            	            		exif_new.setAttribute(ExifInterface.TAG_APERTURE, exif_aperture);
            	            	if( exif_datetime != null )
            	            		exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
            	            	if( exif_exposure_time != null )
            	            		exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
            	            	if( exif_flash != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
	            	            if( exif_focal_length != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
	            	            if( exif_gps_altitude != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, exif_gps_altitude);
	            	            if( exif_gps_altitude_ref != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, exif_gps_altitude_ref);
	            	            if( exif_gps_datestamp != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, exif_gps_datestamp);
	            	            if( exif_gps_latitude != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exif_gps_latitude);
	            	            if( exif_gps_latitude_ref != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exif_gps_latitude_ref);
	            	            if( exif_gps_longitude != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exif_gps_longitude);
	            	            if( exif_gps_longitude_ref != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exif_gps_longitude_ref);
	            	            if( exif_gps_processing_method != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, exif_gps_processing_method);
	            	            if( exif_gps_timestamp != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, exif_gps_timestamp);
	            	            	// leave width/height, as this will have changed!
	            	            if( exif_iso != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_ISO, exif_iso);
	            	            if( exif_make != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
	            	            if( exif_model != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
	            	            if( exif_orientation != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_ORIENTATION, exif_orientation);
	            	            if( exif_white_balance != null )
	            	            	exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);
            	            	exif_new.saveAttributes();
                	    		if( MyDebug.LOG )
                	    			Log.d(TAG, "now saved EXIF data");
        	            	}

        	            	main_activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(picFile)));
        	            	test_last_saved_image = picFileName;
        	            }
        	            if( image_capture_intent ) {
        	            	main_activity.setResult(Activity.RESULT_OK);
        	            	main_activity.finish();
        	            }
        	        }
    			}
    	        catch(FileNotFoundException e) {
    	    		if( MyDebug.LOG )
    	    			Log.e(TAG, "File not found: " + e.getMessage());
    	            e.getStackTrace();
    	    	    showToast(null, "Failed to save photo");
    	        }
    	        catch(IOException e) {
    	    		if( MyDebug.LOG )
    	    			Log.e(TAG, "I/O error writing file: " + e.getMessage());
    	            e.getStackTrace();
    	    	    showToast(null, "Failed to save photo");
    	        }

    			is_preview_started = false; // preview automatically stopped due to taking photo
    	        phase = PHASE_NORMAL; // need to set this even if remaining burst photos, so we can restart the preview
	            if( remaining_burst_photos > 0 ) {
	    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
	    	    	// (otherwise this can fail, at least on Nexus 7)
		            startCameraPreview();
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "burst mode photos remaining: onPictureTaken started preview");
	            }
	            else {
	    	        phase = PHASE_NORMAL;
					boolean pause_preview = sharedPreferences.getBoolean("preference_pause_preview", false);
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "pause_preview? " + pause_preview);
					if( pause_preview && success ) {
		    			setPreviewPaused(true);
		    			preview_image_name = picFileName;
					}
					else {
		    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
		    	    	// (otherwise this can fail, at least on Nexus 7)
			            startCameraPreview();
						showGUI(true);
		        		if( MyDebug.LOG )
		        			Log.d(TAG, "onPictureTaken started preview");
					}
	            }

	            if( bitmap != null ) {
        		    bitmap.recycle();
        		    bitmap = null;
	            }

	            if( success ) {
	            	// update thumbnail - this should be done after restarting preview, so that the preview is started asap
	            	long time_s = System.currentTimeMillis();
	                Camera.Parameters parameters = cam.getParameters();
	        		Camera.Size size = parameters.getPictureSize();
	        		int ratio = (int) Math.ceil((double) size.width / Preview.this.getWidth());
    				BitmapFactory.Options options = new BitmapFactory.Options();
    				options.inMutable = false;
    				options.inPurgeable = true;
    				options.inSampleSize = Integer.highestOneBit(ratio) * 4; // * 4 to increase performance, without noticeable loss in visual quality 
        			if( !sharedPreferences.getBoolean("preference_thumbnail_animation", true) ) {
        				// can use lower resolution if we don't have the thumbnail animation
        				options.inSampleSize *= 4;
        			}
    	    		if( MyDebug.LOG ) {
    	    			Log.d(TAG, "    picture width   : " + size.width);
    	    			Log.d(TAG, "    preview width   : " + Preview.this.getWidth());
    	    			Log.d(TAG, "    ratio           : " + ratio);
    	    			Log.d(TAG, "    inSampleSize    : " + options.inSampleSize);
    	    			Log.d(TAG, "    current_rotation: " + current_rotation);
    	    		}
    	    		if( thumbnail != null ) {
    	    			thumbnail.recycle();
    	    		}
        			thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        			int thumbnail_rotation = 0;
    				// now get the rotation from the Exif data
					try {
						if( exif_orientation_s == null ) {
							// haven't already read the exif orientation
		    	    		if( MyDebug.LOG )
		    	    			Log.d(TAG, "    read exif orientation");
		                	ExifInterface exif = new ExifInterface(picFile.getAbsolutePath());
			            	exif_orientation_s = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
						}
	    	    		if( MyDebug.LOG )
	    	    			Log.d(TAG, "    exif orientation string: " + exif_orientation_s);
						int exif_orientation = 0;
						// from http://jpegclub.org/exif_orientation.html
						if( exif_orientation_s.equals("0") || exif_orientation_s.equals("1") ) {
							// leave at 0
						}
						else if( exif_orientation_s.equals("3") ) {
							exif_orientation = 180;
						}
						else if( exif_orientation_s.equals("6") ) {
							exif_orientation = 90;
						}
						else if( exif_orientation_s.equals("8") ) {
							exif_orientation = 270;
						}
						else {
							// just leave at 0
		    	    		if( MyDebug.LOG )
		    	    			Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
						}
	    	    		if( MyDebug.LOG )
	    	    			Log.d(TAG, "    exif orientation: " + exif_orientation);
						thumbnail_rotation = (thumbnail_rotation + exif_orientation) % 360;
					}
					catch(IOException exception) {
						if( MyDebug.LOG )
							Log.e(TAG, "exif orientation ioexception");
						exception.printStackTrace();
					}
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "    thumbnail orientation: " + thumbnail_rotation);

        			if( thumbnail_rotation != 0 ) {
        				Matrix m = new Matrix();
        				m.setRotate(thumbnail_rotation, thumbnail.getWidth() * 0.5f, thumbnail.getHeight() * 0.5f);
        				Bitmap rotated_thumbnail = Bitmap.createBitmap(thumbnail, 0, 0,thumbnail.getWidth(), thumbnail.getHeight(), m, true);
        				if( rotated_thumbnail != thumbnail ) {
        					thumbnail.recycle();
        					thumbnail = rotated_thumbnail;
        				}
        			}

        			if( sharedPreferences.getBoolean("preference_thumbnail_animation", true) ) {
            			thumbnail_anim = true;
            			thumbnail_anim_start_ms = System.currentTimeMillis();
        			}
        			ImageButton galleryButton = (ImageButton) main_activity.findViewById(R.id.gallery);
        	    	galleryButton.setImageResource(android.R.color.transparent);
    			    galleryButton.setImageBitmap(thumbnail);
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - time_s));
	            }

	            System.gc();

	            if( remaining_burst_photos > 0 ) {
	            	remaining_burst_photos--;

	        		String timer_value = sharedPreferences.getString("preference_burst_interval", "0");
	        		long timer_delay = 0;
	        		try {
	        			timer_delay = Integer.parseInt(timer_value) * 1000;
	        		}
	                catch(NumberFormatException e) {
	            		if( MyDebug.LOG )
	            			Log.e(TAG, "failed to parse timer_value: " + timer_value);
	            		e.printStackTrace();
	            		timer_delay = 0;
	                }

	        		if( timer_delay == 0 ) {
	        			// we go straight to taking a photo rather than refocusing, for speed
	        			// need to manually set the phase and rehide the GUI
	        	        phase = PHASE_TAKING_PHOTO;
						showGUI(false);
		            	takePictureWhenFocused();
	        		}
	        		else {
	        			takePictureOnTimer(timer_delay, true);
	        		}
	            }
    	    }
    	};
    	{
    		if( MyDebug.LOG )
    			Log.d(TAG, "current_rotation: " + current_rotation);
			Camera.Parameters parameters = camera.getParameters();
			parameters.setRotation(current_rotation);
			camera.setParameters(parameters);

			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			boolean enable_sound = sharedPreferences.getBoolean("preference_shutter_sound", true);
    		if( MyDebug.LOG )
    			Log.d(TAG, "enable_sound? " + enable_sound);
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
            	camera.enableShutterSound(enable_sound);
            }
    		if( MyDebug.LOG )
    			Log.d(TAG, "about to call takePicture");
    		String toast_text = "";
    		if( n_burst > 1 ) {
    			int photo = (n_burst-remaining_burst_photos);
    			toast_text = "Taking a photo... (" +  photo + " / " + n_burst + ")";
    		}
    		else {
    			toast_text = "Taking a photo...";
    		}
    		if( MyDebug.LOG )
    			Log.d(TAG, toast_text);
    		try {
    			camera.takePicture(shutterCallback, null, jpegPictureCallback);
        		count_cameraTakePicture++;
    			showToast(take_photo_toast, toast_text);
    		}
    		catch(RuntimeException e) {
    			// just in case? We got a RuntimeException report here from 1 user on Google Play; I also encountered it myself once of Galaxy Nexus when starting up
    			if( MyDebug.LOG )
					Log.e(TAG, "runtime exception from takePicture");
    			e.printStackTrace();
	    	    showToast(null, "Failed to take picture");
    		}
    	}
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture exit");
    }

	public void clickedShare() {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedShare");
		//if( is_preview_paused ) {
		if( this.phase == PHASE_PREVIEW_PAUSED ) {
			if( preview_image_name != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Share: " + preview_image_name);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + preview_image_name));
				Activity activity = (Activity)this.getContext();
				activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			startCameraPreview();
			tryAutoFocus();
		}
	}

	public void clickedTrash() {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTrash");
		//if( is_preview_paused ) {
		if( this.phase == PHASE_PREVIEW_PAUSED ) {
			if( preview_image_name != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Delete: " + preview_image_name);
				File file = new File(preview_image_name);
				if( !file.delete() ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to delete " + preview_image_name);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "successsfully deleted " + preview_image_name);
					Activity activity = (Activity)this.getContext();
    	    	    showToast(null, "Photo deleted");
    	            activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
				}
			}
			startCameraPreview();
			tryAutoFocus();
		}
    }

    private void tryAutoFocus() {
		if( MyDebug.LOG )
			Log.d(TAG, "tryAutoFocus");
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "no camera");
		}
		else if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
		}
		else if( !this.is_preview_started ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview not yet started");
		}
		//else if( is_taking_photo ) {
		else if( this.isTakingPhotoOrOnTimer() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
		}
		else {
			// it's only worth doing autofocus when autofocus has an effect (i.e., auto or macro mode)
            Camera.Parameters parameters = camera.getParameters();
			String focus_mode = parameters.getFocusMode();
	        if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "try to start autofocus");
		        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						if( MyDebug.LOG )
							Log.d(TAG, "autofocus complete: " + success);
						focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
						focus_complete_time = System.currentTimeMillis();
					}
		        };
	
				this.focus_success = FOCUS_WAITING;
	    		this.focus_complete_time = -1;
	    		try {
	    			camera.autoFocus(autoFocusCallback);
	    			count_cameraAutoFocus++;
	    		}
	    		catch(RuntimeException e) {
	    			// just in case? We got a RuntimeException report here from 1 user on Google Play
	    			focus_success = FOCUS_DONE;

	    			if( MyDebug.LOG )
						Log.e(TAG, "runtime exception from autoFocus");
	    			e.printStackTrace();
	    		}
	        }
	        else if( has_focus_area ) {
	        	// do this so we get the focus box, for focus modes that support focus area, but don't support autofocus
				focus_success = FOCUS_SUCCESS;
				focus_complete_time = System.currentTimeMillis();
	        }
		}
    }
    
    private void startCameraPreview() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "startCameraPreview");
			debug_time = System.currentTimeMillis();
		}
		//if( camera != null && !is_taking_photo && !is_preview_started ) {
		if( camera != null && !this.isTakingPhotoOrOnTimer() && !is_preview_started ) {
			if( MyDebug.LOG )
				Log.d(TAG, "starting the camera preview");
	    	count_cameraStartPreview++;
			camera.startPreview();
			this.is_preview_started = true;
			if( MyDebug.LOG ) {
				Log.d(TAG, "time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
			}
			if( this.using_face_detection ) {
				if( MyDebug.LOG )
					Log.d(TAG, "start face detection");
				camera.startFaceDetection();
				faces_detected = null;
			}
		}
		this.setPreviewPaused(false);
    }

    private void setPreviewPaused(boolean paused) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewPaused: " + paused);
		Activity activity = (Activity)this.getContext();
	    View shareButton = (View) activity.findViewById(R.id.share);
	    View trashButton = (View) activity.findViewById(R.id.trash);
		/*is_preview_paused = paused;
		if( is_preview_paused ) {*/
	    if( paused ) {
	    	this.phase = PHASE_PREVIEW_PAUSED;
		    shareButton.setVisibility(View.VISIBLE);
		    trashButton.setVisibility(View.VISIBLE);
		    // shouldn't call showGUI(false), as should already have been disabled when we started to take a photo
		}
		else {
	    	this.phase = PHASE_NORMAL;
			shareButton.setVisibility(View.GONE);
		    trashButton.setVisibility(View.GONE);
		    preview_image_name = null;
			showGUI(true);
		}
    }
    
    private void showGUI(final boolean show) {
		if( MyDebug.LOG )
			Log.d(TAG, "showGUI: " + show);
		final Activity activity = (Activity)this.getContext();
		activity.runOnUiThread(new Runnable() {
			public void run() {
		    	final int visibility = show ? View.VISIBLE : View.GONE;
			    View switchCameraButton = (View) activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = (View) activity.findViewById(R.id.switch_video);
			    View flashButton = (View) activity.findViewById(R.id.flash);
			    View focusButton = (View) activity.findViewById(R.id.focus_mode);
			    switchCameraButton.setVisibility(visibility);
			    switchVideoButton.setVisibility(visibility);
			    if( supported_flash_values != null )
			    	flashButton.setVisibility(visibility);
			    if( supported_focus_values != null )
			    	focusButton.setVisibility(visibility);
			}
		});
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
		/*if( MyDebug.LOG )
    	Log.d(TAG, "onSensorChanged: " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);*/

		double x = event.values[0];
		double y = event.values[1];
		this.has_level_angle = true;
		this.level_angle = Math.atan2(-x, y) * 180.0 / Math.PI;
		if( this.level_angle < -0.0 ) {
			this.level_angle += 360.0;
		}
		this.level_angle -= (float)this.current_orientation;
		if( this.level_angle < -180.0 ) {
			this.level_angle += 360.0;
		}
		else if( this.level_angle > 180.0 ) {
			this.level_angle -= 360.0;
		}

		/*double x = Math.abs(event.values[0]);
		double y = Math.abs(event.values[1]);
		this.has_level_angle = true;
		this.level_angle = Math.atan2(x, y) * 180.0 / Math.PI;
		if( this.level_angle > 45.0 ) {
			this.level_angle = 90.0 - this.level_angle;
		}
		if( event.values[1] < 0.0 ) {
			this.level_angle = - this.level_angle;
		}*/

		this.invalidate();
	}

    public boolean supportsFaceDetection() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsFaceDetection");
    	return supports_face_detection;
    }

    List<String> getSupportedColorEffects() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedColorEffects");
		return this.color_effects;
    }

    List<String> getSupportedSceneModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedSceneModes");
		return this.scene_modes;
    }

    List<String> getSupportedWhiteBalances() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedWhiteBalances");
		return this.white_balances;
    }
    
    List<String> getSupportedExposures() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedExposures");
    	return this.exposures;
    }

    /*List<Camera.Size> getSupportedPictureSizes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedPictureSizes");
    	if( this.camera == null )
    		return new Vector<Camera.Size>();
		Camera.Parameters parameters = camera.getParameters();
    	List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
    	for(Camera.Size size : sizes) {
			Log.d(TAG, "    size: " + size.width + " x " + size.height);
    	}
    	return sizes;
    }*/
    public List<Camera.Size> getSupportedPictureSizes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedPictureSizes");
		return this.sizes;
    }
    
    int getCurrentPictureSizeIndex() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCurrentPictureSizeIndex");
    	return this.current_size_index;
    }
    
    List<Integer> getSupportedVideoQuality() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedVideoQuality");
		return this.video_quality;
    }
    
    public int getCameraId() {
    	return this.cameraId;
    }
    
    private int getImageQuality(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQuality");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String image_quality_s = sharedPreferences.getString("preference_quality", "90");
		int image_quality = 0;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
    }
    
    public void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
		this.app_is_paused = false;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String ui_placement = sharedPreferences.getString("preference_ui_placement", "ui_right");
		this.ui_placement_right = ui_placement.equals("ui_right");
		this.openCamera(true);
    }

    public void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
		this.app_is_paused = true;
		this.closeCamera();
    }
    
    public void showToast(final ToastBoxer clear_toast, final String message) {
		class RotatedTextView extends View {
			private String text = "";
			private Paint paint = new Paint();
			private Rect bounds = new Rect();
			private Rect rect = new Rect();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.text = text;
			}

			@Override 
			protected void onDraw(Canvas canvas) { 
				final float scale = getResources().getDisplayMetrics().density;
				paint.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				paint.getTextBounds(text, 0, text.length(), bounds);
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "bounds: " + bounds);
				}*/
				final int padding = (int) (14 * scale + 0.5f); // convert dps to pixels
				final int offset_y = (int) (32 * scale + 0.5f); // convert dps to pixels
				canvas.save();
				canvas.rotate(ui_rotation, canvas.getWidth()/2, canvas.getHeight()/2);

				rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
				rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
				rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
				rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(Color.rgb(75, 75, 75));
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);

				paint.setStyle(Paint.Style.STROKE);
				paint.setColor(Color.rgb(150, 150, 150));
				canvas.drawLine(rect.left, rect.top, rect.right, rect.top, paint);
				canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, paint);

				paint.setStyle(Paint.Style.FILL); // needed for Android 4.4!
				paint.setColor(Color.WHITE);
				canvas.drawText(text, canvas.getWidth()/2 - bounds.width()/2, canvas.getHeight()/2 + offset_y, paint);
				canvas.restore();
			} 
		}

		if( MyDebug.LOG )
			Log.d(TAG, "showToast");
		final Activity activity = (Activity)this.getContext();
		// We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
		// Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if( clear_toast != null && clear_toast.toast != null )
					clear_toast.toast.cancel();
				/*clear_toast = Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_SHORT);
				clear_toast.show();*/

				Toast toast = new Toast(activity);
				if( clear_toast != null )
					clear_toast.toast = toast;
				View text = new RotatedTextView(message, activity);
				toast.setView(text);
				toast.setDuration(Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}
	
	public void setUIRotation(int ui_rotation) {
		if( MyDebug.LOG )
			Log.d(TAG, "setUIRotation");
		this.ui_rotation = ui_rotation;
	}

    void locationChanged(Location location) {
		if( MyDebug.LOG )
			Log.d(TAG, "locationChanged");
		this.has_received_location = true;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		boolean store_location = sharedPreferences.getBoolean("preference_location", false);
		if( store_location ) {
			this.location = location;
		}
		updateParametersFromLocation();
    }
    
    private void updateParametersFromLocation() {
    	if( camera != null ) {
    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
    		boolean store_location = sharedPreferences.getBoolean("preference_location", false);
    		// Android camera source claims we need to check lat/long != 0.0d
    		if( store_location && location != null && ( location.getLatitude() != 0.0d || location.getLongitude() != 0.0d ) ) {
	    		if( MyDebug.LOG ) {
	    			Log.d(TAG, "updating parameters from location...");
	    			Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude());
	    		}
	            Camera.Parameters parameters = camera.getParameters();
	            parameters.removeGpsData();
	            parameters.setGpsLatitude(location.getLatitude());
	            parameters.setGpsLongitude(location.getLongitude());
	            parameters.setGpsProcessingMethod(location.getProvider()); // from http://boundarydevices.com/how-to-write-an-android-camera-app/
	            if( location.hasAltitude() ) {
		            parameters.setGpsAltitude(location.getAltitude());
	            }
	            else {
	            	// Android camera source claims we need to fake one if not present
	            	// and indeed, this is needed to fix crash on Nexus 7
		            parameters.setGpsAltitude(0);
	            }
	            if( location.getTime() != 0 ) { // from Android camera source
	            	parameters.setGpsTimestamp(location.getTime() / 1000);
	            }
	            camera.setParameters(parameters);
	            this.has_set_location = true;
    		}
    		else {
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "removing location data from parameters...");
	            Camera.Parameters parameters = camera.getParameters();
	            parameters.removeGpsData();
	            camera.setParameters(parameters);
	            this.has_set_location = false;
    		}
    	}
    }
	
	public boolean isVideo() {
		return is_video;
	}
	
    // must be static, to safely call from other Activities
    public static String getFlashPreferenceKey(int cameraId) {
    	return "flash_value_" + cameraId;
    }

    // must be static, to safely call from other Activities
    public static String getFocusPreferenceKey(int cameraId) {
    	return "focus_value_" + cameraId;
    }

    // must be static, to safely call from other Activities
    public static String getResolutionPreferenceKey(int cameraId) {
    	return "camera_resolution_" + cameraId;
    }
    
    // must be static, to safely call from other Activities
    public static String getVideoQualityPreferenceKey(int cameraId) {
    	return "video_quality_" + cameraId;
    }
    
    // must be static, to safely call from other Activities
    public static String getIsVideoPreferenceKey() {
    	return "is_video";
    }
    
    // for testing:
    public Camera getCamera() {
		/*if( MyDebug.LOG )
			Log.d(TAG, "getCamera: " + camera);*/
    	return this.camera;
    }
    
    public boolean supportsFocus() {
    	return this.supported_focus_values != null;
    }

    public boolean supportsFlash() {
    	return this.supported_flash_values != null;
    }
    
    public String getCurrentFlashValue() {
    	if( this.current_flash_index == -1 )
    		return null;
    	return this.supported_flash_values.get(current_flash_index);
    }
    
    public boolean hasFocusArea() {
    	return this.has_focus_area;
    }
    
    public boolean isTakingPhotoOrOnTimer() {
    	//return this.is_taking_photo;
    	return this.phase == PHASE_TAKING_PHOTO || this.phase == PHASE_TIMER;
    }
    
    public boolean isTakingPhoto() {
    	return this.phase == PHASE_TAKING_PHOTO;
    }

    public boolean isOnTimer() {
    	//return this.is_taking_photo_on_timer;
    	return this.phase == PHASE_TIMER;
    }

    public boolean isPreviewStarted() {
    	return this.is_preview_started;
    }
}
