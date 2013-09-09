package net.sourceforge.stablecamera;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ZoomControls;

class Preview extends SurfaceView implements SurfaceHolder.Callback, /*Camera.PreviewCallback,*/ SensorEventListener {
	private static final String TAG = "Preview";

	SurfaceHolder mHolder;
	//private int [] pixels = null; 
	public Camera camera = null;
	List<Camera.Size> sizes = null;
	Camera.Size current_size = null;
	Paint p = new Paint();
	boolean has_level_angle = false;
	double level_angle = 0.0f;
	boolean has_zoom = false;
	int zoom_factor = 0;
	int max_zoom_factor = 0;
	ScaleGestureDetector scaleGestureDetector;
	List<Integer> zoom_ratios = null;

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
        		Log.d(TAG, "ScaleListener.onScale zoom_ratio is now " + zoom_ratio);
        		Log.d(TAG, "    chosen new zoom_factor " + zoom_factor + " ratio " + zoom_ratios.get(zoom_factor)/100.0f);
    			Camera.Parameters parameters = camera.getParameters();
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
	        	Log.d(TAG, "Supported size: " + size.width + ", " + size.height);
	        	if( current_size == null || size.width > current_size.width || (size.width == current_size.width && size.height > current_size.height) ) {
	        		current_size = size;
	        	}
	        }
	        if( current_size != null ) {
	        	Log.d(TAG, "Current size: " + current_size.width + ", " + current_size.height);
	        	parameters.setPictureSize(current_size.width, current_size.height);
	    		camera.setParameters(parameters);
	        }
		}
	}
	
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG, "Preview.surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try {
			camera = Camera.open();
		}
		catch(Exception e) {
			Log.d(TAG, "Failed to open camera");
			camera = null;
		}
		if( camera != null ) {

			try {
				camera.setPreviewDisplay(holder);
				camera.startPreview();
			}
			catch(IOException e) {
				Log.d(TAG, "Failed to start camera preview: " + e.getMessage());
				e.printStackTrace();
			}

			this.setCameraParameters();

			Camera.Parameters parameters = camera.getParameters();
			this.has_zoom = parameters.isZoomSupported();
			Activity activity = (Activity)this.getContext();
		    ZoomControls zoomControls = (ZoomControls) activity.findViewById(R.id.zoom);
			Log.d(TAG, "have zoomControls? " + (zoomControls!=null));
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
		            		Log.d(TAG, "zoom in to " + zoom_factor);
		        			Camera.Parameters parameters = camera.getParameters();
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
		            		Log.d(TAG, "zoom out to " + zoom_factor);
		        			Camera.Parameters parameters = camera.getParameters();
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
		}

		this.setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
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
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
	}

	@Override
	public void onDraw(Canvas canvas) {
		//Log.d(TAG, "Preview.onDraw()");
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
			Log.d(TAG, "no camera!");
			Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			canvas.drawText("FAILED TO OPEN CAMERA", canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( this.has_zoom ) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			canvas.drawText("Zoom: " + zoom_ratio +"x", canvas.getWidth() / 2, canvas.getHeight() - 128, p);
		}
	}

	/*public void onPreviewFrame(byte[] data, Camera camera) {
		Log.d(TAG, "onPreviewFrame()");
		if( camera != null ) {
			Camera.Parameters parameters = camera.getParameters();
			int width = parameters.getPreviewSize().width;
			int height = parameters.getPreviewSize().height;
			Log.d(TAG, "preview size: " + width + ", " + height);
			int rgb_size = width * height;
			if( pixels == null || pixels.length != rgb_size ) {
				Log.d(TAG, "allocate pixel buffer size: " + rgb_size);
				pixels = new int[rgb_size];
			}
			convertYUV420_NV21toARGB8888(pixels, data, width, height);
		}
	}

	// from http://en.wikipedia.org/wiki/YUV#Y.27UV420p_.28and_Y.27V12_or_YV12.29_to_RGB888_conversion
	public void convertYUV420_NV21toARGB8888(int [] rgb, byte [] data, int width, int height) {
	    int size = width*height;
	    int offset = size;

	    for(int i=0, k=0; i < size; i+=2, k+=2) {
	        int y1 = data[i  ]&0xff;
	        int y2 = data[i+1]&0xff;
	        int y3 = data[width+i  ]&0xff;
	        int y4 = data[width+i+1]&0xff;

	        int u = data[offset+k  ]&0xff;
	        int v = data[offset+k+1]&0xff;
	        u = u-128;
	        v = v-128;

	        rgb[i  ] = convertYUVtoARGB(y1, u, v);
	        rgb[i+1] = convertYUVtoARGB(y2, u, v);
	        rgb[width+i  ] = convertYUVtoARGB(y3, u, v);
	        rgb[width+i+1] = convertYUVtoARGB(y4, u, v);

	        if (i!=0 && (i+2)%width==0)
	            i+=width;
	    }
	}

	private int convertYUVtoARGB(int y, int u, int v) {
	    int r = y + (int)1.402f*u;
	    int g = y - (int)(0.344f*v +0.714f*u);
	    int b = y + (int)1.772f*v;
	    r = r>255? 255 : r<0 ? 0 : r;
	    g = g>255? 255 : g<0 ? 0 : g;
	    b = b>255? 255 : b<0 ? 0 : b;
	    return 0xff000000 | (r<<16) | (g<<8) | b;
	}*/

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
		double x = Math.abs(event.values[0]);
		double y = Math.abs(event.values[1]);
		//double z = Math.abs(event.values[2]);
	    //Log.d(TAG, "onSensorChanged: " + x + ", " + y + ", " + z);
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
