package net.sourceforge.stablecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ZoomControls;

class Preview extends SurfaceView implements SurfaceHolder.Callback, /*Camera.PreviewCallback,*/ SensorEventListener {
	private static final String TAG = "Preview";

	private SurfaceHolder mHolder;
	//private int [] pixels = null; 
	private Camera camera = null;
	private List<Camera.Size> sizes = null;
	private Camera.Size current_size = null;
	private Paint p = new Paint();
	private boolean is_taking_photo = false;

	private boolean has_level_angle = false;
	private double level_angle = 0.0f;

	private boolean has_zoom = false;
	private int zoom_factor = 0;
	private int max_zoom_factor = 0;
	private ScaleGestureDetector scaleGestureDetector;
	private List<Integer> zoom_ratios = null;

	private List<String> supported_flash_values = null; // our "values" format
	private int current_flash_index = -1;

	@SuppressWarnings("deprecation")
	Preview(Context context) {
		super(context);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

	    scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
	}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        //invalidate();
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

    private void setCameraParameters() {
		if( camera != null ) {
			Camera.Parameters parameters = camera.getParameters();
	        sizes = parameters.getSupportedPictureSizes();
	        for(int i=0;i<sizes.size();i++) {
	        	Camera.Size size = sizes.get(i);
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "Supported size: " + size.width + ", " + size.height);
	        	if( current_size == null || size.width > current_size.width || (size.width == current_size.width && size.height > current_size.height) ) {
	        		current_size = size;
	        	}
	        }
	        if( current_size != null ) {
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "Current size: " + current_size.width + ", " + current_size.height);
	        	parameters.setPictureSize(current_size.width, current_size.height);
	    		camera.setParameters(parameters);
	        }
		}
	}
	
	public void surfaceCreated(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "Preview.surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try {
			camera = Camera.open();
		}
		catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "Failed to open camera");
			camera = null;
		}
		if( camera != null ) {

			try {
				camera.setPreviewDisplay(holder);
				camera.startPreview();
			}
			catch(IOException e) {
				if( MyDebug.LOG )
					Log.d(TAG, "Failed to start camera preview: " + e.getMessage());
				e.printStackTrace();
			}

			this.setCameraParameters();

			Camera.Parameters parameters = camera.getParameters();
			this.has_zoom = parameters.isZoomSupported();
			if( MyDebug.LOG )
				Log.d(TAG, "has_zoom? " + has_zoom);
			Activity activity = (Activity)this.getContext();
		    ZoomControls zoomControls = (ZoomControls) activity.findViewById(R.id.zoom);
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
			}
			else {
				zoomControls.setVisibility(View.GONE);
			}

			List<String> supported_flash_modes = parameters.getSupportedFlashModes(); // Android format
		    Button flashButton = (Button) activity.findViewById(R.id.flash);
			if( supported_flash_modes != null && supported_flash_modes.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "flash modes: " + supported_flash_modes);
				supported_flash_values = getSupportedFlashModes(supported_flash_modes); // convert to our format (also resorts)
		    	updateFlash(0);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "flash not supported");
				supported_flash_values = null;
				current_flash_index = -1;
				flashButton.setVisibility(View.GONE);
			}
		}

		this.setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "Preview.surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		if( camera != null ) {
			//camera.setPreviewCallback(null);
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( MyDebug.LOG )
			Log.d(TAG, "Preview.surfaceChanged " + w + ", " + h);
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
        try {
            camera.stopPreview();
        }
        catch(Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        // set preview size and make any resize, rotate or
        // reformatting changes here
		this.setCameraParameters();

        // start preview with new settings
        try {
			//camera.setPreviewCallback(this);
            camera.setPreviewDisplay(mHolder);
            camera.startPreview();
        }
        catch(Exception e) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
	}

	@Override
	public void onDraw(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "Preview.onDraw()");*/
		final float scale = getResources().getDisplayMetrics().density;
		p.setColor(Color.WHITE);
		p.setTextAlign(Paint.Align.CENTER);
		p.setTextSize(24.0f);
		if( camera != null ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/
			if( this.has_level_angle ) {
				canvas.drawText("Angle: " + this.level_angle, canvas.getWidth() / 2,
						canvas.getHeight() / 2, p);
			}
		}
		else {
			if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}
			canvas.drawText("FAILED TO OPEN CAMERA", canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( this.has_zoom ) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			// Convert the dps to pixels, based on density scale
			int pixels_offset = (int) (100 * scale + 0.5f); // convert dps to pixels
			canvas.drawText("Zoom: " + zoom_ratio +"x", canvas.getWidth() / 2, canvas.getHeight() - pixels_offset, p);
		}
	}

	public void cycleFlash() {
		if( MyDebug.LOG )
			Log.d(TAG, "Preview.cycleFlash()");
		if( this.supported_flash_values != null && this.supported_flash_values.size() > 1 ) {
			int new_flash_index = (current_flash_index+1) % this.supported_flash_values.size();
			updateFlash(new_flash_index);
		}
	}

	private void updateFlash(int new_flash_index) {
		// updates the Flash button, and Flash camera mode
		if( new_flash_index != current_flash_index ) {
			boolean initial = current_flash_index==-1;
			current_flash_index = new_flash_index;
			if( MyDebug.LOG )
				Log.d(TAG, "    current_flash_index is now " + current_flash_index);

			Activity activity = (Activity)this.getContext();
		    Button flashButton = (Button) activity.findViewById(R.id.flash);
	    	String [] flash_entries = getResources().getStringArray(R.array.flash_entries);
			String flash_value = supported_flash_values.get(current_flash_index);
	    	String [] flash_values = getResources().getStringArray(R.array.flash_values);
	    	for(int i=0;i<flash_values.length;i++) {
	    		if( flash_value.equals(flash_values[i]) ) {
	    			flashButton.setText(flash_entries[i]);
	    			if( !initial ) {
	    				Toast.makeText(activity.getApplicationContext(), flash_entries[i], Toast.LENGTH_SHORT).show();
	    			}
	    			break;
	    		}
	    	}
	    	this.setFlash(flash_value);
		}
	}

	private void setFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "Preview.setFlash() " + flash_value);
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
			Log.d(TAG, "Preview.getSupportedFlashModes()");
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
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_OFF) ) {
				output_modes.add("flash_off");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_off");
			}
			if( supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_AUTO) ) {
				output_modes.add("flash_auto");
				if( MyDebug.LOG )
					Log.d(TAG, " supports flash_auto");
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

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a File for saving an image or video */
    @SuppressLint("SimpleDateFormat")
	private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

		Activity activity = (Activity)getContext();
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "StableCamera");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if( !mediaStorageDir.exists() ) {
            if( !mediaStorageDir.mkdirs() ) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "failed to create directory");
                return null;
            }
	        activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(mediaStorageDir)));
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if( type == MEDIA_TYPE_IMAGE ) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            "IMG_"+ timeStamp + ".jpg");
        }
        else if( type == MEDIA_TYPE_VIDEO ) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            "VID_"+ timeStamp + ".mp4");
        }
        else {
            return null;
        }

        return mediaFile;
    }

    public void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
    	if( is_taking_photo ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "already taking a photo");
    		return;
    	}
		final Activity activity = (Activity)getContext();
        PictureCallback jpegPictureCallback = new PictureCallback() {
    	    public void onPictureTaken(byte[] data, Camera cam) {
    	    	// n.b., this is automatically run in a different thread
    			if( MyDebug.LOG )
    				Log.d(TAG, "onPictureTaken");
    			Bitmap bitmap = null;
    			if( hasLevelAngle() )
    			{
    				BitmapFactory.Options options = new BitmapFactory.Options();
    				//options.inMutable = true;
        			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        			int width = bitmap.getWidth();
        			int height = bitmap.getHeight();
        			if( MyDebug.LOG )
        				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
        			/*for(int y=0;y<height;y++) {
        				for(int x=0;x<width;x++) {
        					int col = bitmap.getPixel(x, y);
        					col = col & 0xffff0000; // mask out red component
        					bitmap.setPixel(x, y, col);
        				}
        			}*/
        		    Matrix matrix = new Matrix();
        		    //matrix.postScale(scaleWidth, scaleHeight);
        		    matrix.postRotate((float)getLevelAngle());
        		    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        		    bitmap = new_bitmap;
    			}

    	    	File picFile = getOutputMediaFile(1);
    	        if( picFile == null ) {
    	            Log.e(TAG, "Couldn't create media file; check storage permissions?");
    	            return;
    	        }
    	        try {
    	            FileOutputStream fos = new FileOutputStream(picFile);
    	            if( bitmap != null ) {
        	            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
    	            }
    	            else {
        	            fos.write(data);
    	            }
    	            fos.close();

    	            activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(picFile)));
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "onPictureTaken saved photo");
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

    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
    	    	// (otherwise this can fail, at least on Nexus 7)
	            try {
	            	camera.startPreview();
	            	is_taking_photo = false;
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "onPictureTaken started preview");
	            }
	            catch(Exception e) {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "Error starting camera preview after taking photo: " + e.getMessage());
	            	e.printStackTrace();
	            }
    	    }
    	};
    	try {
    		camera.takePicture(null, null, jpegPictureCallback);
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

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
		double x = Math.abs(event.values[0]);
		double y = Math.abs(event.values[1]);
		/*double z = Math.abs(event.values[2]);
		if( MyDebug.LOG )
	    	Log.d(TAG, "onSensorChanged: " + x + ", " + y + ", " + z);
	    	*/
		this.has_level_angle = true;
		this.level_angle = Math.atan2(x, y) * 180.0 / Math.PI;
		if( this.level_angle > 45.0 ) {
			this.level_angle = 90.0 - this.level_angle;
		}
		if( event.values[1] < 0.0 ) {
			this.level_angle = - this.level_angle;
		}
		this.invalidate();
	}
    
    public boolean hasLevelAngle() {
    	return this.has_level_angle;
    }
    public double getLevelAngle() {
    	return this.level_angle;
    }
}
