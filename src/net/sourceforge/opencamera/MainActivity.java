package net.sourceforge.opencamera;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ZoomControls;

class MyDebug {
	static final boolean LOG = false;
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Sensor mSensorMagnetic = null;
	private LocationManager mLocationManager = null;
	private LocationListener locationListener = null;
	private Preview preview = null;
	private int current_orientation = 0;
	private OrientationEventListener orientationEventListener = null;
	private boolean supports_auto_stabilise = false;
	private boolean supports_force_video_4k = false;

	// for testing:
	public boolean is_test = false;
	public Bitmap gallery_bitmap = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onCreate");
		}
    	long time_s = System.currentTimeMillis();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		if( getIntent() != null && getIntent().getExtras() != null ) {
			is_test = getIntent().getExtras().getBoolean("test_project");
			if( MyDebug.LOG )
				Log.d(TAG, "is_test: " + is_test);
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		if( MyDebug.LOG ) {
			Log.d(TAG, "standard max memory = " + activityManager.getMemoryClass() + "MB");
			Log.d(TAG, "large max memory = " + activityManager.getLargeMemoryClass() + "MB");
		}
		//if( activityManager.getMemoryClass() >= 128 ) { // test
		if( activityManager.getLargeMemoryClass() >= 128 ) {
			supports_auto_stabilise = true;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "supports_auto_stabilise? " + supports_auto_stabilise);

		// hack to rule out phones unlikely to have 4K video, so no point even offering the option!
		// both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL)
		if( activityManager.getLargeMemoryClass() >= 512 ) {
			supports_force_video_4k = true;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "supports_force_video_4k? " + supports_force_video_4k);

		// keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "found accelerometer");
			mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "no support for accelerometer");
		}
		if( mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "found magnetic sensor");
			mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "no support for magnetic sensor");
		}

		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

		updateGalleryIcon();
		clearSeekBar();

		preview = new Preview(this, savedInstanceState);
		((ViewGroup) findViewById(R.id.preview)).addView(preview);
		
        orientationEventListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				MainActivity.this.onOrientationChanged(orientation);
			}
        };

        final String done_first_time_key = "done_first_time";
		boolean has_done_first_time = sharedPreferences.contains(done_first_time_key);
        if( !has_done_first_time && !is_test ) {
	        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Open Camera");
            alertDialog.setMessage(R.string.intro_text);
            alertDialog.setPositiveButton(R.string.intro_ok, null);
            alertDialog.show();

			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putBoolean(done_first_time_key, true);
			editor.apply();
        }
        
		if( MyDebug.LOG )
			Log.d(TAG, "time for Activity startup: " + (System.currentTimeMillis() - time_s));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) { 
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyDown: " + keyCode);
        if( keyCode == KeyEvent.KEYCODE_MENU ) {
        	// needed to support hardware menu button
        	// tested successfully on Samsung S3 (via RTL)
        	// see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
			View view = findViewById(R.id.settings);
        	clickedSettings(view);
            return true;
        }
        return super.onKeyDown(keyCode, event); 
    }

	private SensorEventListener accelerometerListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			preview.onAccelerometerSensorChanged(event);
		}
	};
	
	private SensorEventListener magneticListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			preview.onMagneticSensorChanged(event);
		}
	};
	
    @Override
    protected void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
        super.onResume();

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
		// done here rather than onCreate, so that changing it in preferences takes effect without restarting app
		{
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
			if( sharedPreferences.getBoolean("preference_max_brightness", true) ) {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
	        }
			else {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			}
	        getWindow().setAttributes(layout); 
		}

        mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(magneticListener, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();

		// Define a listener that responds to location updates
		boolean store_location = sharedPreferences.getBoolean("preference_location", false);
		if( store_location ) {
			locationListener = new LocationListener() {
			    public void onLocationChanged(Location location) {
					if( MyDebug.LOG )
						Log.d(TAG, "onLocationChanged");
			    	preview.locationChanged(location);
			    }

			    public void onStatusChanged(String provider, int status, Bundle extras) {
			    }

			    public void onProviderEnabled(String provider) {
			    }

			    public void onProviderDisabled(String provider) {
			    }
			};
			
			// see https://sourceforge.net/p/opencamera/tickets/1/
			if( mLocationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) ) {
				mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
			}
			if( mLocationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) ) {
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
			}
		}

		layoutUI();

		updateGalleryIcon(); // update in case images deleted whilst idle

		preview.onResume();

    }

    @Override
    protected void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
        super.onPause();
        mSensorManager.unregisterListener(accelerometerListener);
        mSensorManager.unregisterListener(magneticListener);
        orientationEventListener.disable();
        if( this.locationListener != null ) {
            mLocationManager.removeUpdates(locationListener);
        }
		preview.onPause();
    }

    public void layoutUI() {
		if( MyDebug.LOG )
			Log.d(TAG, "layoutUI");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String ui_placement = sharedPreferences.getString("preference_ui_placement", "ui_right");
		boolean ui_placement_right = ui_placement.equals("ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		// new code for orientation fixed to landscape	
		// the display orientation should be locked to landscape, but how many degrees is that?
	    int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
	    }
	    // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
	    // relative_orientation is clockwise from landscape-left
    	//int relative_orientation = (current_orientation + 360 - degrees) % 360;
    	int relative_orientation = (current_orientation + degrees) % 360;
		if( MyDebug.LOG ) {
			Log.d(TAG, "    current_orientation = " + current_orientation);
			Log.d(TAG, "    degrees = " + degrees);
			Log.d(TAG, "    relative_orientation = " + relative_orientation);
		}
		int ui_rotation = (360 - relative_orientation) % 360;
		preview.setUIRotation(ui_rotation);
		int align_left = RelativeLayout.ALIGN_LEFT;
		int align_right = RelativeLayout.ALIGN_RIGHT;
		//int align_top = RelativeLayout.ALIGN_TOP;
		//int align_bottom = RelativeLayout.ALIGN_BOTTOM;
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int above = RelativeLayout.ABOVE;
		int below = RelativeLayout.BELOW;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( ( relative_orientation == 0 && ui_placement_right ) || ( relative_orientation == 180 && ui_placement_right ) || relative_orientation == 90 || relative_orientation == 270) {
			if( !ui_placement_right && ( relative_orientation == 90 || relative_orientation == 270 ) ) {
				//align_top = RelativeLayout.ALIGN_BOTTOM;
				//align_bottom = RelativeLayout.ALIGN_TOP;
				above = RelativeLayout.BELOW;
				below = RelativeLayout.ABOVE;
				align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
				align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
			}
			View view = findViewById(R.id.settings);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.settings);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.exposure);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gallery);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.focus_mode);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.exposure);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.flash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.focus_mode);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.flash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_camera);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_video);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_camera);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.trash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			view.setRotation(180.0f); // should always match the zoom_seekbar, so that zoom in and out are in the same directions

			view = findViewById(R.id.zoom_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, R.id.zoom);
			layoutParams.addRule(above, R.id.zoom);
			layoutParams.addRule(below, 0);
			view.setLayoutParams(layoutParams);
		}
		else {
			View view = findViewById(R.id.switch_camera);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.switch_camera);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.flash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.switch_video);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.focus_mode);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.flash);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.exposure);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.focus_mode);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.exposure);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.settings);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.gallery);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.settings);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.share);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_right, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			view.setRotation(180.0f); // should always match the zoom_seekbar, so that zoom in and out are in the same directions

			view = findViewById(R.id.zoom_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.zoom);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(above, R.id.zoom);
			layoutParams.addRule(below, 0);
			view.setLayoutParams(layoutParams);
		}
		
		{
			// set seekbar info
			View view = findViewById(R.id.seekbar);
			view.setRotation(ui_rotation);

			int width_dp = 0;
			if( ui_rotation == 0 || ui_rotation == 180 ) {
				width_dp = 300;
			}
			else {
				width_dp = 200;
			}
			int height_dp = 50;
			final float scale = getResources().getDisplayMetrics().density;
			int width_pixels = (int) (width_dp * scale + 0.5f); // convert dps to pixels
			int height_pixels = (int) (height_dp * scale + 0.5f); // convert dps to pixels
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = findViewById(R.id.seekbar_zoom);
			view.setRotation(ui_rotation);
			view.setAlpha(0.5f);
			// n.b., using left_of etc doesn't work properly when using rotation (as the amount of space reserved is based on the UI elements before being rotated)
			if( ui_rotation == 0 ) {
				view.setTranslationX(0);
				view.setTranslationY(height_pixels);
			}
			else if( ui_rotation == 90 ) {
				view.setTranslationX(-height_pixels);
				view.setTranslationY(0);
			}
			else if( ui_rotation == 180 ) {
				view.setTranslationX(0);
				view.setTranslationY(-height_pixels);
			}
			else if( ui_rotation == 270 ) {
				view.setTranslationX(height_pixels);
				view.setTranslationY(0);
			}
		}
		
		{
			// set icon for taking photos vs videos
			ImageButton view = (ImageButton)findViewById(R.id.take_photo);
			if( preview != null ) {
				view.setImageResource(preview.isVideo() ? R.drawable.take_video : R.drawable.take_photo);
			}
		}
    }

    private void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
		    orientation = (orientation + 45) / 90 * 90;
		    orientation = orientation % 360;
		    if( orientation != current_orientation ) {
			    this.current_orientation = orientation;
				if( MyDebug.LOG ) {
					Log.d(TAG, "current_orientation is now: " + current_orientation);
				}
				layoutUI();
		    }
		}
	}

    public void clickedTakePhoto(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTakePhoto");
    	this.takePicture();
    }

    public void clickedSwitchCamera(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSwitchCamera");
		this.preview.switchCamera();
    }

    public void clickedSwitchVideo(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSwitchVideo");
		this.preview.switchVideo(true, true);
    }

    public void clickedFlash(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedFlash");
    	this.preview.cycleFlash();
    }
    
    public void clickedFocusMode(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedFocusMode");
    	this.preview.cycleFocusMode();
    }

    void clearSeekBar() {
		View view = findViewById(R.id.seekbar);
		view.setVisibility(View.GONE);
		view = findViewById(R.id.seekbar_zoom);
		view.setVisibility(View.GONE);
    }
    
    void setSeekBarExposure() {
		SeekBar seek_bar = ((SeekBar)findViewById(R.id.seekbar));
		final int min_exposure = preview.getMinimumExposure();
		seek_bar.setMax( preview.getMaximumExposure() - min_exposure );
		seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );
    }
    
    public void clickedExposure(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedExposure");
		SeekBar seek_bar = ((SeekBar)findViewById(R.id.seekbar));
		int visibility = seek_bar.getVisibility();
		if( visibility == View.GONE && preview.getCamera() != null && preview.supportsExposures() ) {
			final int min_exposure = preview.getMinimumExposure();
			seek_bar.setVisibility(View.VISIBLE);
			setSeekBarExposure();
			seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if( MyDebug.LOG )
						Log.d(TAG, "exposure seekbar onProgressChanged");
					preview.setExposure(min_exposure + progress, false);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}
			});

			ZoomControls seek_bar_zoom = (ZoomControls)findViewById(R.id.seekbar_zoom);
			seek_bar_zoom.setVisibility(View.VISIBLE);
			seek_bar_zoom.setOnZoomInClickListener(new OnClickListener(){
	            public void onClick(View v){
	            	preview.changeExposure(1, true);
	            }
	        });
			seek_bar_zoom.setOnZoomOutClickListener(new OnClickListener(){
		    	public void onClick(View v){
	            	preview.changeExposure(-1, true);
		        }
		    });
		}
		else if( visibility == View.VISIBLE ) {
			clearSeekBar();
		}
    }
    
    public void clickedSettings(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSettings");

		Intent intent = new Intent(this, MyPreferenceActivity.class);

		intent.putExtra("cameraId", this.preview.getCameraId());
		intent.putExtra("supports_auto_stabilise", this.supports_auto_stabilise);
		intent.putExtra("supports_force_video_4k", this.supports_force_video_4k);
		intent.putExtra("supports_face_detection", this.preview.supportsFaceDetection());

		putIntentExtra(intent, "color_effects", this.preview.getSupportedColorEffects());
		putIntentExtra(intent, "scene_modes", this.preview.getSupportedSceneModes());
		putIntentExtra(intent, "white_balances", this.preview.getSupportedWhiteBalances());
		//putIntentExtra(intent, "exposures", this.preview.getSupportedExposures());

		List<Camera.Size> preview_sizes = this.preview.getSupportedPreviewSizes();
		if( preview_sizes != null ) {
			int [] widths = new int[preview_sizes.size()];
			int [] heights = new int[preview_sizes.size()];
			int i=0;
			for(Camera.Size size: preview_sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			intent.putExtra("preview_widths", widths);
			intent.putExtra("preview_heights", heights);
		}
		
		List<Camera.Size> sizes = this.preview.getSupportedPictureSizes();
		if( sizes != null ) {
			int [] widths = new int[sizes.size()];
			int [] heights = new int[sizes.size()];
			int i=0;
			for(Camera.Size size: sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			intent.putExtra("resolution_widths", widths);
			intent.putExtra("resolution_heights", heights);
		}
		
		List<String> video_quality = this.preview.getSupportedVideoQuality();
		if( video_quality != null ) {
			String [] video_quality_arr = new String[video_quality.size()];
			String [] video_quality_string_arr = new String[video_quality.size()];
			int i=0;
			for(String value: video_quality) {
				video_quality_arr[i] = value;
				video_quality_string_arr[i] = this.preview.getCamcorderProfileDescription(value);
				i++;
			}
			intent.putExtra("video_quality", video_quality_arr);
			intent.putExtra("video_quality_string", video_quality_string_arr);
		}

		List<Camera.Size> video_sizes = this.preview.getSupportedVideoSizes();
		if( video_sizes != null ) {
			int [] widths = new int[video_sizes.size()];
			int [] heights = new int[video_sizes.size()];
			int i=0;
			for(Camera.Size size: video_sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			intent.putExtra("video_widths", widths);
			intent.putExtra("video_heights", heights);
		}
		
		putIntentExtra(intent, "flash_values", this.preview.getSupportedFlashValues());
		putIntentExtra(intent, "focus_values", this.preview.getSupportedFocusValues());

		this.startActivity(intent);
    }
    
    class Media {
    	public long id;
    	public boolean video;
    	public Uri uri;
    	public long date;
    	public int orientation;

    	Media(long id, boolean video, Uri uri, long date, int orientation) {
    		this.id = id;
    		this.video = video;
    		this.uri = uri;
    		this.date = date;
    		this.orientation = orientation;
    	}
    }
    
    private Media getLatestMedia(boolean video) {
    	Media media = null;
		Uri baseUri = video ? Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
		String [] projection = video ? new String[] {VideoColumns._ID, VideoColumns.DATE_TAKEN} : new String[] {ImageColumns._ID, ImageColumns.DATE_TAKEN, ImageColumns.ORIENTATION};
		String selection = video ? "" : ImageColumns.MIME_TYPE + "='image/jpeg'";
		String order = video ? VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC" : ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";
		Cursor cursor = null;
		try {
			cursor = getContentResolver().query(query, projection, selection, null, order);
			if( cursor != null && cursor.moveToFirst() ) {
				long id = cursor.getLong(0);
				long date = cursor.getLong(1);
				int orientation = video ? 0 : cursor.getInt(2);
				Uri uri = ContentUris.withAppendedId(baseUri, id);
				if( MyDebug.LOG )
					Log.d(TAG, "found most recent uri for " + (video ? "video" : "images") + ": " + uri);
				media = new Media(id, video, uri, date, orientation);
			}
		}
		finally {
			if( cursor != null ) {
				cursor.close();
			}
		}
		return media;
    }
    
    private Media getLatestMedia() {
		Media image_media = getLatestMedia(false);
		Media video_media = getLatestMedia(true);
		Media media = null;
		if( image_media != null && video_media == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "only found images");
			media = image_media;
		}
		else if( image_media == null && video_media != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "only found videos");
			media = video_media;
		}
		else if( image_media != null && video_media != null ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "found images and videos");
				Log.d(TAG, "latest image date: " + image_media.date);
				Log.d(TAG, "latest video date: " + video_media.date);
			}
			if( image_media.date >= video_media.date ) {
				if( MyDebug.LOG )
					Log.d(TAG, "latest image is newer");
				media = image_media;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "latest video is newer");
				media = video_media;
			}
		}
		return media;
    }

    public void updateGalleryIconToBlank() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateGalleryIconToBlank");
    	ImageButton galleryButton = (ImageButton) this.findViewById(R.id.gallery);
	    int bottom = galleryButton.getPaddingBottom();
	    int top = galleryButton.getPaddingTop();
	    int right = galleryButton.getPaddingRight();
	    int left = galleryButton.getPaddingLeft();
	    /*if( MyDebug.LOG )
			Log.d(TAG, "padding: " + bottom);*/
	    galleryButton.setImageBitmap(null);
		galleryButton.setImageResource(R.drawable.gallery);
		// workaround for setImageResource also resetting padding, Android bug
		galleryButton.setPadding(left, top, right, bottom);
		gallery_bitmap = null;
    }
    
    public void updateGalleryIconToBitmap(Bitmap bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateGalleryIconToBitmap");
    	ImageButton galleryButton = (ImageButton) this.findViewById(R.id.gallery);
		galleryButton.setImageBitmap(bitmap);
		gallery_bitmap = bitmap;
    }
    
    public void updateGalleryIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateGalleryIcon");
    	long time_s = System.currentTimeMillis();
    	Media media = getLatestMedia();
		Bitmap thumbnail = null;
    	if( media != null ) {
    		if( media.video ) {
    			  thumbnail = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), media.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
    		}
    		else {
    			  thumbnail = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), media.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
    		}
    		if( thumbnail != null ) {
	    		if( media.orientation != 0 ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "thumbnail size is " + thumbnail.getWidth() + " x " + thumbnail.getHeight());
	    			Matrix matrix = new Matrix();
	    			matrix.setRotate(media.orientation, thumbnail.getWidth() * 0.5f, thumbnail.getHeight() * 0.5f);
	    			try {
	    				Bitmap rotated_thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
	        		    // careful, as rotated_thumbnail is sometimes not a copy!
	        		    if( rotated_thumbnail != thumbnail ) {
	        		    	thumbnail.recycle();
	        		    	thumbnail = rotated_thumbnail;
	        		    }
	    			}
	    			catch(Throwable t) {
		    			if( MyDebug.LOG )
		    				Log.d(TAG, "failed to rotate thumbnail");
	    			}
	    		}
    		}
    	}
    	if( thumbnail != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set gallery button to thumbnail");
			updateGalleryIconToBitmap(thumbnail);
    	}
    	else {
			if( MyDebug.LOG )
				Log.d(TAG, "set gallery button to blank");
			updateGalleryIconToBlank();
    	}
		if( MyDebug.LOG )
			Log.d(TAG, "time to update gallery icon: " + (System.currentTimeMillis() - time_s));
    }
    
    public void clickedGallery(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedGallery");
		//Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		Uri uri = null;
		Media media = getLatestMedia();
		if( media != null ) {
			uri = media.uri;
		}

		if( uri != null ) {
			// check uri exists
			if( MyDebug.LOG )
				Log.d(TAG, "found most recent uri: " + uri);
			try {
				ContentResolver cr = getContentResolver();
				ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
				if( pfd == null ) {
					if( MyDebug.LOG )
						Log.d(TAG, "uri no longer exists (1): " + uri);
					uri = null;
				}
				pfd.close();
			}
			catch(IOException e) {
				if( MyDebug.LOG )
					Log.d(TAG, "uri no longer exists (2): " + uri);
				uri = null;
			}
		}
		if( uri == null ) {
			uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		}
		if( !is_test ) {
			// don't do if testing, as unclear how to exit activity to finish test (for testGallery())
			if( MyDebug.LOG )
				Log.d(TAG, "launch uri:" + uri);
			final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
			try {
				// REVIEW_ACTION means we can view video files without autoplaying
				Intent intent = new Intent(REVIEW_ACTION, uri);
				this.startActivity(intent);
			}
			catch(ActivityNotFoundException e) {
				if( MyDebug.LOG )
					Log.d(TAG, "REVIEW_ACTION intent didn't work, try ACTION_VIEW");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				// from http://stackoverflow.com/questions/11073832/no-activity-found-to-handle-intent - needed to fix crash if no gallery app installed
				//Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("blah")); // test
				if( intent.resolveActivity(getPackageManager()) != null ) {
					this.startActivity(intent);
				}
				else{
					preview.showToast(null, "No Gallery app available");
				}
			}
		}
    }

    static private void putIntentExtra(Intent intent, String key, List<String> values) {
		if( values != null ) {
			String [] values_arr = new String[values.size()];
			int i=0;
			for(String value: values) {
				values_arr[i] = value;
				i++;
			}
			intent.putExtra(key, values_arr);
		}

    }

    public void clickedShare(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedShare");
    	this.preview.clickedShare();
    }

    public void clickedTrash(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTrash");
    	this.preview.clickedTrash();
    	// Calling updateGalleryIcon() immediately has problem that it still returns the latest image that we've just deleted!
    	// But works okay if we call after a delay. 100ms works fine on Nexus 7 and Galaxy Nexus, but set to 500 just to be safe.
    	final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
		    	updateGalleryIcon();
			}
		}, 500);
    }

    private void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
    	this.preview.takePicturePressed();
    }

	@Override
	protected void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
	    super.onSaveInstanceState(state);
	    if( this.preview != null ) {
	    	preview.onSaveInstanceState(state);
	    }
	}

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if( event.getAction() == KeyEvent.ACTION_DOWN ) {
            int keyCode = event.getKeyCode();
            switch( keyCode ) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
        		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        		String volume_keys = sharedPreferences.getString("preference_volume_keys", "volume_take_photo");
        		if( volume_keys.equals("volume_take_photo") ) {
                	takePicture();
                    return true;
        		}
        		else if( volume_keys.equals("volume_zoom") ) {
        			if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
        				this.preview.zoomIn();
        			else
        				this.preview.zoomOut();
                    return true;
        		}
        		else if( volume_keys.equals("volume_exposure") ) {
        			if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
        				this.preview.changeExposure(1, true);
        			else
        				this.preview.changeExposure(-1, true);
                    return true;
        		}
        		// else do nothing
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void broadcastFile(File file) {
    	// note that the new method means that the new folder shows up as a file when connected to a PC via MTP (at least tested on Windows 8)
    	if( file.isDirectory() ) {
    		//this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
        	// ACTION_MEDIA_MOUNTED no longer allowed on Android 4.4! Gives: SecurityException: Permission Denial: not allowed to send broadcast android.intent.action.MEDIA_MOUNTED
    		// note that we don't actually need to broadcast anything, the folder and contents appear straight away (both in Gallery on device, and on a PC when connecting via MTP)
    		// also note that we definitely don't want to broadcast ACTION_MEDIA_SCANNER_SCAN_FILE or use scanFile() for folders, as this means the folder shows up as a file on a PC via MTP (and isn't fixed by rebooting!)
    	}
    	else {
        	// both of these work fine, but using MediaScannerConnection.scanFile() seems to be preferred over sending an intent
    		//this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        	MediaScannerConnection.scanFile(this, new String[] { file.getAbsolutePath() }, null,
        			new MediaScannerConnection.OnScanCompletedListener() {
    		 		public void onScanCompleted(String path, Uri uri) {
    		 			if( MyDebug.LOG ) {
    		 				Log.d("ExternalStorage", "Scanned " + path + ":");
    		 				Log.d("ExternalStorage", "-> uri=" + uri);
    		 			}
    		 		}
    			}
    		);
    	}
	}
    
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public File getImageFolder() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String folder_name = sharedPreferences.getString("preference_save_location", "OpenCamera");
		File file = null;
		if( folder_name.length() > 0 && folder_name.lastIndexOf('/') == folder_name.length()-1 ) {
			// ignore final '/' character
			folder_name = folder_name.substring(0, folder_name.length()-1);
		}
		//if( folder_name.contains("/") ) {
		if( folder_name.startsWith("/") ) {
			file = new File(folder_name);
		}
		else {
	        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), folder_name);
		}
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "folder_name: " + folder_name);
			Log.d(TAG, "full path: " + file);
		}*/
        return file;
    }

    /** Create a File for saving an image or video */
    @SuppressLint("SimpleDateFormat")
	public File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

    	File mediaStorageDir = getImageFolder();
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if( !mediaStorageDir.exists() ) {
            if( !mediaStorageDir.mkdirs() ) {
        		if( MyDebug.LOG )
        			Log.e(TAG, "failed to create directory");
                return null;
            }
            broadcastFile(mediaStorageDir);
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String index = "";
        File mediaFile = null;
        for(int count=1;count<=100;count++) {
            if( type == MEDIA_TYPE_IMAGE ) {
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + index + ".jpg");
            }
            else if( type == MEDIA_TYPE_VIDEO ) {
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "VID_"+ timeStamp + index + ".mp4");
            }
            else {
                return null;
            }
            if( !mediaFile.exists() ) {
            	break;
            }
            index = "_" + count; // try to find a unique filename
        }
        

		if( MyDebug.LOG ) {
			Log.d(TAG, "getOutputMediaFile returns: " + mediaFile);
		}
        return mediaFile;
    }
    
    public boolean supportsAutoStabilise() {
    	return this.supports_auto_stabilise;
    }

    public boolean supportsForceVideo4K() {
    	return this.supports_force_video_4k;
    }

    @SuppressWarnings("deprecation")
	public long freeMemory() { // return free memory in MB
    	try {
    		File image_folder = this.getImageFolder();
	        StatFs statFs = new StatFs(image_folder.getAbsolutePath());
	        // cast to long to avoid overflow!
	        long blocks = statFs.getAvailableBlocks();
	        long size = statFs.getBlockSize();
	        long free  = (blocks*size) / 1048576;
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
			}*/
	        return free;
    	}
    	catch(IllegalArgumentException e) {
    		// can fail on emulator, at least!
    		return -1;
    	}
    }
    
    public static String getDonateLink() {
    	return "https://play.google.com/store/apps/details?id=harman.mark.donation";
    }

    /*public static String getDonateMarketLink() {
    	return "market://details?id=harman.mark.donation";
    }*/

    // for testing:
    public Preview getPreview() {
    	return this.preview;
    }
}
