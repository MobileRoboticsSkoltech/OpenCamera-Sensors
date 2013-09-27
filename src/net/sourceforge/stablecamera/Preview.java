package net.sourceforge.stablecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Vector;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.Uri;
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
class Preview extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {
	private static final String TAG = "Preview";

	private Paint p = new Paint();
	private DecimalFormat decimalFormat = new DecimalFormat("##.00");
	private boolean ui_placement_right = true;

	private SurfaceHolder mHolder = null;
	private Camera camera = null;
	private int cameraId = 0;
	private boolean is_taking_photo = false;
	private boolean is_preview_paused = false;
	private String preview_image_name = null;

	private int current_orientation = 0;
	private boolean has_level_angle = false;
	private double level_angle = 0.0f;

	private boolean has_zoom = false;
	private int zoom_factor = 0;
	private int max_zoom_factor = 0;
	private ScaleGestureDetector scaleGestureDetector;
	private List<Integer> zoom_ratios = null;

	private List<String> supported_flash_values = null; // our "values" format
	private int current_flash_index = -1; // this is an index into the supported_flash_values array, or -1 if no flash modes available

	private List<String> supported_focus_values = null; // our "values" format
	private int current_focus_index = -1; // this is an index into the supported_focus_values array, or -1 if no focus modes available

	private List<String> color_effects = null;
	private List<String> scene_modes = null;
	private List<String> white_balances = null;

	private List<Camera.Size> sizes = null;
	private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set

	private Toast switch_camera_toast = null;
	private Toast flash_toast = null;
	private Toast focus_toast = null;

	Preview(Context context) {
		this(context, null);
	}

	@SuppressWarnings("deprecation")
	Preview(Context context, Bundle savedInstanceState) {
		super(context);

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
	}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        //invalidate();

		// note, we always try to force start the preview (in case is_preview_paused has become false); also this does an auto-focus
        this.startCameraPreview();
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

        		//invalidate();
    		}
    		return true;
    	}
    }

    /*private void setCameraParameters() {
	}*/
	
	public void surfaceCreated(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		this.openCamera();
		this.setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		if( camera != null ) {
			//camera.setPreviewCallback(null);
			if( !this.is_preview_paused ) {
				camera.stopPreview();
			}
			camera.release();
			camera = null;
		}
	}
	
	public void openCamera() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera()");
			Log.d(TAG, "cameraId: " + cameraId);
		}
		try {
			camera = Camera.open(cameraId);
		}
		catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "Failed to open camera: " + e.getMessage());
			e.printStackTrace();
			camera = null;
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

			try {
				camera.setPreviewDisplay(mHolder);
			}
			catch(Exception e) {
				if( MyDebug.LOG )
					Log.d(TAG, "Failed to set preview display: " + e.getMessage());
				e.printStackTrace();
			}
			startCameraPreview();

			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());

			Camera.Parameters parameters = camera.getParameters();
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
		            	if(zoom_factor < max_zoom_factor){
		            		zoom_factor++;
		            		if( MyDebug.LOG )
		            			Log.d(TAG, "zoom in to " + zoom_factor);
		        			Camera.Parameters parameters = camera.getParameters();
		        			if( MyDebug.LOG )
		        				Log.d(TAG, "zoom was: " + parameters.getZoom());
		        			parameters.setZoom((int)zoom_factor);
		    	    		camera.setParameters(parameters);
		                }
		            }
		        });

			    zoomControls.setOnZoomOutClickListener(new OnClickListener(){
			    	public void onClick(View v){
			    		if(zoom_factor > 0){
			    			zoom_factor--;
			    			if( MyDebug.LOG )
			    				Log.d(TAG, "zoom out to " + zoom_factor);
		        			Camera.Parameters parameters = camera.getParameters();
		        			if( MyDebug.LOG )
		        				Log.d(TAG, "zoom was: " + parameters.getZoom());
		        			parameters.setZoom((int)zoom_factor);
		    	    		camera.setParameters(parameters);
			            }
			        }
			    });
				zoomControls.setVisibility(View.VISIBLE);
			}
			else {
				zoomControls.setVisibility(View.GONE);
			}

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
					updateFlash(flash_value);
					if( current_flash_index == -1 ) {
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
				flashButton.setVisibility(View.VISIBLE);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "flash not supported");
				supported_flash_values = null;
				flashButton.setVisibility(View.GONE);
			}

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
					updateFocus(focus_value);
					if( current_focus_index == -1 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "focus value no longer supported!");
						updateFocus(0);
					}
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "found no existing focus_value");
					updateFocus(0);
				}
				focusModeButton.setVisibility(View.VISIBLE);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "focus not supported");
				supported_focus_values = null;
				focusModeButton.setVisibility(View.GONE);
			}

			// get available color effects
			color_effects = parameters.getSupportedColorEffects();
			String color_effect = setupValuesPref(color_effects, "preference_color_effect", Camera.Parameters.EFFECT_NONE);
			if( color_effect != null ) {
	        	parameters.setColorEffect(color_effect);
			}

			// get available scene modes
			scene_modes = parameters.getSupportedSceneModes();
			String scene_mode = setupValuesPref(scene_modes, "preference_scene_mode", Camera.Parameters.SCENE_MODE_AUTO);
			if( scene_mode != null ) {
	        	parameters.setSceneMode(scene_mode);
			}

			// get available white balances
			white_balances = parameters.getSupportedWhiteBalance();
			String white_balance = setupValuesPref(white_balances, "preference_white_balance", Camera.Parameters.WHITE_BALANCE_AUTO);
			if( white_balance != null ) {
	        	parameters.setWhiteBalance(white_balance);
			}

			// get available sizes
	        sizes = parameters.getSupportedPictureSizes();
			if( MyDebug.LOG ) {
				for(int i=0;i<sizes.size();i++) {
		        	Camera.Size size = sizes.get(i);
		        	Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
				}
			}
			if( MyDebug.LOG ) {
				List<Camera.Size> preview_sizes = parameters.getSupportedPreviewSizes();
				for(int i=0;i<preview_sizes.size();i++) {
		        	Camera.Size size = preview_sizes.get(i);
		        	Log.d(TAG, "supported preview size: " + size.width + " , " + size.height);
				}
			}
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
								Log.d(TAG, "failed to find valid size");
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

    		// update parameters
    		camera.setParameters(parameters);
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
        if( !this.is_preview_paused ) {
	        try {
	            camera.stopPreview();
	        }
	        catch(Exception e) {
	            // ignore: tried to stop a non-existent preview
	        }
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

        /*{
			// set optimal preview size
        	Camera.Parameters parameters = camera.getParameters();
        	Camera.Size current_size = parameters.getPictureSize();
    		if( MyDebug.LOG )
    			Log.d(TAG, "current size: " + current_size.width + ", " + current_size.height);
	        List<Camera.Size> preview_sizes = parameters.getSupportedPreviewSizes();
	        Camera.Size optimalSize = getOptimalPreviewSize(preview_sizes, (double) current_size.width / current_size.height);
	        if( optimalSize != null ) {
	            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
	        }
        }*/
 
        // start preview with new settings
        try {
			//camera.setPreviewCallback(this);
            camera.setPreviewDisplay(mHolder);
        }
        catch(Exception e) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "Error setting preview display: " + e.getMessage());
        }
        if( !this.is_preview_paused ) {
			startCameraPreview();
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
	    Camera.CameraInfo info = new Camera.CameraInfo();
	    Camera.getCameraInfo(cameraId, info);
	    orientation = (orientation + 45) / 90 * 90;
	    this.current_orientation = orientation % 360;
	    int rotation = 0;
	    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	        rotation = (info.orientation - orientation + 360) % 360;
	    }
	    else {  // back-facing camera
	        rotation = (info.orientation + orientation) % 360;
	    }
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "    current_orientation is " + current_orientation);
			Log.d(TAG, "    info orientation is " + info.orientation);
			Log.d(TAG, "    set Camera rotation to " + rotation);
		}*/
		Camera.Parameters parameters = camera.getParameters();
		parameters.setRotation(rotation);
		camera.setParameters(parameters);
	 }

	@Override
	public void onDraw(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onDraw()");*/
		final float scale = getResources().getDisplayMetrics().density;
		p.setColor(Color.WHITE);
		p.setTextSize(24.0f);
		if( camera != null && !this.is_preview_paused ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/
			if( this.has_level_angle ) {
				//canvas.drawText("Angle: " + this.level_angle, canvas.getWidth() / 2, canvas.getHeight() / 2, p);
				//canvas.drawText("Angle: " + this.level_angle + " (" + this.current_orientation + ")", canvas.getWidth() / 2, canvas.getHeight() / 2, p);
				// Convert the dps to pixels, based on density scale
				int pixels_offset_x = (int) (50 * scale + 0.5f); // convert dps to pixels
				int pixels_offset_y = (int) (20 * scale + 0.5f); // convert dps to pixels
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
					p.setColor(Color.GREEN);
				}
				canvas.drawText("Angle: " + decimalFormat.format(this.level_angle), canvas.getWidth() / 2 + pixels_offset_x, canvas.getHeight() - pixels_offset_y, p);
				p.setColor(Color.WHITE);
			}
		}
		else if( camera == null ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawText("FAILED TO OPEN CAMERA", canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			canvas.drawText("CAMERA MAY BE IN USE BY ANOTHER APPLICATION?", canvas.getWidth() / 2, canvas.getHeight() / 2 + pixels_offset, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( this.has_zoom ) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			// Convert the dps to pixels, based on density scale
			int pixels_offset = (int) (100 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("Zoom: " + zoom_ratio +"x", canvas.getWidth() / 2, canvas.getHeight() - pixels_offset, p);
		}
	}

	public void switchCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "switchCamera()");
		int n_cameras = Camera.getNumberOfCameras();
		if( MyDebug.LOG )
			Log.d(TAG, "found " + n_cameras + " cameras");
		if( n_cameras > 1 ) {
			this.setPreviewPaused(false);
			if( camera != null ) {
				//camera.setPreviewCallback(null);
				camera.stopPreview();
				camera.release();
				camera = null;
			}
			cameraId = (cameraId+1) % n_cameras;
		    Camera.CameraInfo info = new Camera.CameraInfo();
		    Camera.getCameraInfo(cameraId, info);
		    if( info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ) {
				switch_camera_toast = showToast(switch_camera_toast, "Front Camera");
		    }
		    else {
				switch_camera_toast = showToast(switch_camera_toast, "Back Camera");
		    }
			this.openCamera();
		}
	}

	public void cycleFlash() {
		if( MyDebug.LOG )
			Log.d(TAG, "cycleFlash()");
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

	private void updateFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + flash_value);
    	int new_flash_index = supported_flash_values.indexOf(flash_value);
		if( MyDebug.LOG )
			Log.d(TAG, "new_flash_index: " + new_flash_index);
    	if( new_flash_index != -1 ) {
    		updateFlash(new_flash_index);
    	}
    	else {
    		this.current_flash_index = -1;
    	}
	}
	
	private void updateFlash(int new_flash_index) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + new_flash_index);
		// updates the Flash button, and Flash camera mode
		if( new_flash_index != current_flash_index ) {
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
	    				flash_toast = showToast(flash_toast, flash_entries[i]);
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
		if( this.supported_focus_values != null && this.supported_focus_values.size() > 1 ) {
			int new_focus_index = (current_focus_index+1) % this.supported_focus_values.size();
			updateFocus(new_focus_index);

			// now save
			String focus_value = supported_focus_values.get(current_focus_index);
			if( MyDebug.LOG ) {
				Log.d(TAG, "save new focus_value: " + focus_value);
			}
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(getFocusPreferenceKey(cameraId), focus_value);
			editor.apply();
		}
	}

	private void updateFocus(String focus_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + focus_value);
    	int new_focus_index = supported_focus_values.indexOf(focus_value);
		if( MyDebug.LOG )
			Log.d(TAG, "new_focus_index: " + new_focus_index);
    	if( new_focus_index != -1 ) {
    		updateFocus(new_focus_index);
    	}
    	else {
    		this.current_focus_index = -1;
    	}
	}
	
	private void updateFocus(int new_focus_index) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + new_focus_index);
		// updates the Focus button, and Focus camera mode
		if( new_focus_index != current_focus_index ) {
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
	    			if( !initial ) {
	    				focus_toast = showToast(focus_toast, focus_entries[i]);
	    			}
	    			break;
	    		}
	    	}
	    	this.setFocus(focus_value);
		}
	}

	private void setFocus(String focus_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFocus() " + focus_value);
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
    	else if( focus_value.equals("drawable/focus_mode_continuous_video") ) {
    		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    	}
		camera.setParameters(parameters);
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

	public void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
    	if( is_taking_photo ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "already taking a photo");
    		return;
    	}

        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, Camera camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "autofocus complete: " + success);
				takePictureWhenFocused();
			}
        };
        // make sure that preview running
        this.startCameraPreview();
        // wrap in try block just to be safe
        try {
        	camera.autoFocus(autoFocusCallback);
        }
        catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "failed to autofocus");
        	e.printStackTrace();
        }
	}

	private void takePictureWhenFocused() {
		// should be called when auto-focused
		if( MyDebug.LOG )
			Log.d(TAG, "takePictureWhenFocused");
    	if( is_taking_photo ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "already taking a photo");
    		return;
    	}

    	final Activity activity = (Activity)getContext();

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

    			Activity activity = (Activity)Preview.this.getContext();
    			boolean image_capture_intent = false;
       	        Uri image_capture_intent_uri = null;
    	        String action = activity.getIntent().getAction();
    	        if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "from image capture intent");
        			image_capture_intent = true;
        	        Bundle myExtras = activity.getIntent().getExtras();
        	        if (myExtras != null) {
        	        	image_capture_intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            			if( MyDebug.LOG )
            				Log.d(TAG, "save to: " + image_capture_intent_uri);
        	        }
    	        }

    	        Bitmap bitmap = null;
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Preview.this.getContext());
				boolean auto_stabilise = sharedPreferences.getBoolean("preference_auto_stabilise", false);
    			if( auto_stabilise && has_level_angle )
    			{
        			if( MyDebug.LOG )
        				Log.d(TAG, "auto stabilising...");
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
        		    Matrix matrix = new Matrix();
        		    /*{
        		    	// test for low memory
        		    	level_angle = 45.0;
            		    matrix.postScale(2.0f, 2.0f); // test for larger sizes
        		    }*/
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
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "rotated bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
        				Log.d(TAG, "rotated bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
        			}
    			}

    			String picFileName = null;
    	        try {
	    			File picFile = null;
	    			OutputStream outputStream = null;
	    			if( image_capture_intent ) {
	        			if( image_capture_intent_uri != null )
	        			{
	        			    // Save the bitmap to the specified URI (use a try/catch block)
	        			    outputStream = activity.getContentResolver().openOutputStream(image_capture_intent_uri);
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
	        				activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
            			    activity.finish();
	        			}
	    			}
	    			else {
	    				MainActivity main_activity = (MainActivity)Preview.this.getContext();
	        			picFile = main_activity.getOutputMediaFile(1);
	        	        if( picFile == null ) {
	        	            Log.e(TAG, "Couldn't create media file; check storage permissions?");
	        	            return;
	        	        }
	    	            picFileName = picFile.getAbsolutePath();
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "save to: " + picFileName);
	    	            outputStream = new FileOutputStream(picFile);
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

        	            if( picFile != null ) {
            	            activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(picFile)));
        	            }
        	            if( image_capture_intent ) {
            			    activity.setResult(Activity.RESULT_OK);
            			    activity.finish();
        	            }
        	        }
    			}
    	        catch(FileNotFoundException e) {
    	    		if( MyDebug.LOG )
    	    			Log.e(TAG, "File not found: " + e.getMessage());
    	            e.getStackTrace();
    	    	    Toast.makeText(activity.getApplicationContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
    	        }
    	        catch(IOException e) {
    	    		if( MyDebug.LOG )
    	    			Log.e(TAG, "I/O error writing file: " + e.getMessage());
    	            e.getStackTrace();
    	    	    Toast.makeText(activity.getApplicationContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
    	        }

    	        is_taking_photo = false;
				boolean pause_preview = sharedPreferences.getBoolean("preference_pause_preview", true);
				if( pause_preview ) {
	    			setPreviewPaused(true);
	    			preview_image_name = picFileName;
				}
				else {
	    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
	    	    	// (otherwise this can fail, at least on Nexus 7)
		            startCameraPreview();
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "onPictureTaken started preview");
				}
	            
	            if( bitmap != null ) {
        		    bitmap.recycle();
        		    bitmap = null;
	            }
	            System.gc();
    	    }
    	};
    	try {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			boolean enable_sound = sharedPreferences.getBoolean("preference_shutter_sound", true);
    		if( MyDebug.LOG )
    			Log.d(TAG, "enable_sound? " + enable_sound);
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
            	camera.enableShutterSound(enable_sound);
            }
    		camera.takePicture(shutterCallback, null, jpegPictureCallback);
    	    Toast.makeText(activity.getApplicationContext(), "Taking a photo...", Toast.LENGTH_SHORT).show();
    		is_taking_photo = true;
    	}
        catch(Exception e) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "Camera takePicture failed: " + e.getMessage());
            e.printStackTrace();
    	    Toast.makeText(activity.getApplicationContext(), "Failed to take picture", Toast.LENGTH_SHORT).show();
        }
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture exit");
    }

	public void clickedShare() {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedShare");
		if( is_preview_paused ) {
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
		}
	}

	public void clickedTrash() {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTrash");
		if( is_preview_paused ) {
			if( preview_image_name != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Delete: " + preview_image_name);
				File file = new File(preview_image_name);
				if( !file.delete() ) {
					if( MyDebug.LOG )
						Log.d(TAG, "failed to delete " + preview_image_name);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "successsfully deleted " + preview_image_name);
					Activity activity = (Activity)this.getContext();
		    	    Toast.makeText(activity.getApplicationContext(), "Photo deleted", Toast.LENGTH_SHORT).show();
    	            activity.sendBroadcast(new Intent(Intent. ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
				}
			}
			startCameraPreview();
		}
    }

    private void tryAutoFocus() {
		if( MyDebug.LOG )
			Log.d(TAG, "tryAutoFocus");
		if( camera != null ) {
			Camera.Parameters parameters = camera.getParameters();
			String focus_mode = parameters.getFocusMode();
			if( MyDebug.LOG )
				Log.d(TAG, "focus_mode is " + focus_mode);
			if( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "try to start autofocus");
		        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						if( MyDebug.LOG )
							Log.d(TAG, "autofocus complete: " + success);
					}
		        };
				camera.autoFocus(autoFocusCallback);
			}
		}
    }
    
    private void startCameraPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "startCameraPreview");
		if( camera != null ) {
			try {
				camera.startPreview();
			}
			catch(Exception e) {
				if( MyDebug.LOG )
					Log.d(TAG, "Failed to start camera preview: " + e.getMessage());
				e.printStackTrace();
			}
			tryAutoFocus();
		}
		this.setPreviewPaused(false);
    }

    private void setPreviewPaused(boolean paused) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewPaused: " + paused);
		Activity activity = (Activity)this.getContext();
	    View shareButton = (View) activity.findViewById(R.id.share);
	    View trashButton = (View) activity.findViewById(R.id.trash);
		is_preview_paused = paused;
		if( is_preview_paused ) {
		    shareButton.setVisibility(View.VISIBLE);
		    trashButton.setVisibility(View.VISIBLE);
		}
		else {
			shareButton.setVisibility(View.GONE);
		    trashButton.setVisibility(View.GONE);
		    preview_image_name = null;
		}
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
    List<Camera.Size> getSupportedPictureSizes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedPictureSizes");
		return this.sizes;
    }
    
    int getCurrentPictureSizeIndex() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCurrentPictureSizeIndex");
    	return this.current_size_index;
    }
    
    int getCameraId() {
    	return this.cameraId;
    }
    
    private int getImageQuality(){
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String image_quality_s = sharedPreferences.getString("preference_quality", "90");
		int image_quality = 0;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
    }
    
    public void onResume() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String ui_placement = sharedPreferences.getString("preference_ui_placement", "ui_right");
		this.ui_placement_right = ui_placement.equals("ui_right");
    }

	private Toast showToast(Toast clear_toast, String message) {
		if( clear_toast != null )
			clear_toast.cancel();
		Activity activity = (Activity)this.getContext();
		clear_toast = Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_SHORT);
		clear_toast.show();
		return clear_toast;
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
}
