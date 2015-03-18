package net.sourceforge.opencamera;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ZoomControls;

class MyDebug {
	static final boolean LOG = true;
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Sensor mSensorMagnetic = null;
	private LocationManager mLocationManager = null;
	private MyLocationListener [] locationListeners = null;
	private Preview preview = null;
	private int current_orientation = 0;
	private OrientationEventListener orientationEventListener = null;
	private boolean supports_auto_stabilise = false;
	private boolean supports_force_video_4k = false;
	private boolean supports_camera2 = false;
	private ArrayList<String> save_location_history = new ArrayList<String>();
	private boolean camera_in_background = false; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private GestureDetector gestureDetector;
    private boolean screen_is_locked = false;
    private Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<Integer, Bitmap>();
    private PopupView popup_view = null;
    private Uri last_media_scanned = null;

    private ToastBoxer screen_locked_toast = new ToastBoxer();
    ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();
    
	// for testing:
	public boolean is_test = false;
	public Bitmap gallery_bitmap = null;
	public boolean failed_to_scan = false;
	
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
		if( getIntent() != null && getIntent().getExtras() != null ) {
			boolean take_photo = getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
			if( MyDebug.LOG )
				Log.d(TAG, "take_photo?: " + take_photo);
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
		// both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
		// also added the check for having 128MB standard heap, to support modded LG G2, which has 128MB standard, 256MB large - see https://sourceforge.net/p/opencamera/tickets/9/
		if( activityManager.getMemoryClass() >= 128 || activityManager.getLargeMemoryClass() >= 512 ) {
			supports_force_video_4k = true;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "supports_force_video_4k? " + supports_force_video_4k);

		initCamera2Support();

        setWindowFlagsForCamera();

        // read save locations
        save_location_history.clear();
        int save_location_history_size = sharedPreferences.getInt("save_location_history_size", 0);
		if( MyDebug.LOG )
			Log.d(TAG, "save_location_history_size: " + save_location_history_size);
        for(int i=0;i<save_location_history_size;i++) {
        	String string = sharedPreferences.getString("save_location_history_" + i, null);
        	if( string != null ) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "save_location_history " + i + ": " + string);
        		save_location_history.add(string);
        	}
        }
        // also update, just in case a new folder has been set; this is also necessary to update the gallery icon
		updateFolderHistory();
		//updateFolderHistory("/sdcard/Pictures/OpenCameraTest");

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

		clearSeekBar();

		preview = new Preview(this, savedInstanceState);
		
		orientationEventListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				MainActivity.this.onOrientationChanged(orientation);
			}
        };

        View galleryButton = (View)findViewById(R.id.gallery);
        galleryButton.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				//preview.showToast(null, "Long click");
				longClickedGallery();
				return true;
			}
        });
        
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                // Note that system bars will only be "visible" if none of the
                // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            	if( !usingKitKatImmersiveMode() )
            		return;
        		if( MyDebug.LOG )
        			Log.d(TAG, "onSystemUiVisibilityChange: " + visibility);
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            		if( MyDebug.LOG )
            			Log.d(TAG, "system bars now visible");
                    // The system bars are visible. Make any desired
                    // adjustments to your UI, such as showing the action bar or
                    // other navigational controls.
                	preview.setImmersiveMode(false);
                	setImmersiveTimer();
                }
                else {
            		if( MyDebug.LOG )
            			Log.d(TAG, "system bars now NOT visible");
                    // The system bars are NOT visible. Make any desired
                    // adjustments to your UI, such as hiding the action bar or
                    // other navigational controls.
                	preview.setImmersiveMode(true);
                }
            }
        });

		boolean has_done_first_time = sharedPreferences.contains(getFirstTimePreferenceKey());
        if( !has_done_first_time && !is_test ) {
	        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle(R.string.app_name);
            alertDialog.setMessage(R.string.intro_text);
            alertDialog.setPositiveButton(R.string.intro_ok, null);
            alertDialog.show();

            setFirstTimeFlag();
        }

        preloadIcons(R.array.flash_icons);
        preloadIcons(R.array.focus_mode_icons);

		if( MyDebug.LOG )
			Log.d(TAG, "time for Activity startup: " + (System.currentTimeMillis() - time_s));
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void initCamera2Support() {
		if( MyDebug.LOG )
			Log.d(TAG, "initCamera2Support");
    	supports_camera2 = false;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
        	// currently Camera2 only supported if all camera have FULL support
        	CameraControllerManager2 manager2 = new CameraControllerManager2(this);
        	supports_camera2 = true;
        	for(int i=0;i<manager2.getNumberOfCameras() && supports_camera2;i++) {
        		if( !manager2.allowCamera2Support(i) ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "camera " + i + " doesn't have limited or full support for Camera2 API");
                	supports_camera2 = false;
        		}
        	}
        }
		if( MyDebug.LOG )
			Log.d(TAG, "supports_camera2? " + supports_camera2);
	}
	
	private void preloadIcons(int icons_id) {
    	long time_s = System.currentTimeMillis();
    	String [] icons = getResources().getStringArray(icons_id);
    	for(int i=0;i<icons.length;i++) {
    		int resource = getResources().getIdentifier(icons[i], null, this.getApplicationContext().getPackageName());
    		if( MyDebug.LOG )
    			Log.d(TAG, "load resource: " + resource);
    		Bitmap bm = BitmapFactory.decodeResource(getResources(), resource);
    		this.preloaded_bitmap_resources.put(resource, bm);
    	}
		if( MyDebug.LOG ) {
			Log.d(TAG, "time for preloadIcons: " + (System.currentTimeMillis() - time_s));
			Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
		}
	}
	
	@Override
	protected void onDestroy() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onDestroy");
			Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
		}
		// Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
		for(Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
			if( MyDebug.LOG )
				Log.d(TAG, "recycle: " + entry.getKey());
			entry.getValue().recycle();
		}
		preloaded_bitmap_resources.clear();
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void setFirstTimeFlag() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(getFirstTimePreferenceKey(), true);
		editor.apply();
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) { 
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyDown: " + keyCode);
		switch( keyCode ) {
        case KeyEvent.KEYCODE_VOLUME_UP:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
	        {
	    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	    		String volume_keys = sharedPreferences.getString(getVolumeKeysPreferenceKey(), "volume_take_photo");
	    		if( volume_keys.equals("volume_take_photo") ) {
	            	takePicture();
	                return true;
	    		}
	    		else if( volume_keys.equals("volume_focus") ) {
	    			if( preview.getCurrentFocusValue().equals("focus_mode_manual2") ) {
		    			if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
		    				preview.changeFocusDistance(-1, true);
		    			else
		    				preview.changeFocusDistance(1, true);
	    			}
	    			else {
	    				preview.requestAutoFocus();
	    			}
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
	    			String value = sharedPreferences.getString(MainActivity.getISOPreferenceKey(), preview.getCameraController().getDefaultISO());
	    			boolean manual_iso = !value.equals(preview.getCameraController().getDefaultISO());
	    			if( keyCode == KeyEvent.KEYCODE_VOLUME_UP ) {
	    				if( manual_iso ) {
	    					if( preview.supportsISORange() )
		    					this.preview.changeISO(1, true);
	    				}
	    				else
	    					this.preview.changeExposure(1, true);
	    			}
	    			else {
	    				if( manual_iso ) {
	    					if( preview.supportsISORange() )
		    					this.preview.changeISO(-1, true);
	    				}
	    				else
	    					this.preview.changeExposure(-1, true);
	    			}
	                return true;
	    		}
	    		else if( volume_keys.equals("volume_auto_stabilise") ) {
	    			if( this.supports_auto_stabilise ) {
						boolean auto_stabilise = sharedPreferences.getBoolean(getAutoStabilisePreferenceKey(), false);
						auto_stabilise = !auto_stabilise;
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.putBoolean(getAutoStabilisePreferenceKey(), auto_stabilise);
						editor.apply();
						String message = getResources().getString(R.string.preference_auto_stabilise) + ": " + getResources().getString(auto_stabilise ? R.string.on : R.string.off);
						preview.showToast(changed_auto_stabilise_toast, message);
	    			}
	    			else {
	    				preview.showToast(changed_auto_stabilise_toast, R.string.auto_stabilise_not_supported);
	    			}
	    			return true;
	    		}
	    		else if( volume_keys.equals("volume_really_nothing") ) {
	    			// do nothing, but still return true so we don't change volume either
	    			return true;
	    		}
	    		// else do nothing here, but still allow changing of volume (i.e., the default behaviour)
	    		break;
	        }
		case KeyEvent.KEYCODE_MENU:
			{
	        	// needed to support hardware menu button
	        	// tested successfully on Samsung S3 (via RTL)
	        	// see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
				openSettings();
	            return true;
			}
		case KeyEvent.KEYCODE_CAMERA:
			{
				if( event.getRepeatCount() == 0 ) {
	            	takePicture();
		            return true;
				}
			}
		case KeyEvent.KEYCODE_FOCUS:
			{
				preview.requestAutoFocus();
	            return true;
			}
		case KeyEvent.KEYCODE_ZOOM_IN:
			{
				preview.zoomIn();
	            return true;
			}
		case KeyEvent.KEYCODE_ZOOM_OUT:
			{
				preview.zoomOut();
	            return true;
			}
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

	public Location getLocation() {
		// returns null if not available
		if( locationListeners == null )
			return null;
		// location listeners should be stored in order best to worst
		for(int i=0;i<locationListeners.length;i++) {
			Location location = locationListeners[i].getLocation();
			if( location != null )
				return location;
		}
		return null;
	}
	
	public boolean testHasReceivedLocation() {
		if( locationListeners == null )
			return false;
		for(int i=0;i<locationListeners.length;i++) {
			if( locationListeners[i].test_has_received_location )
				return true;
		}
		return false;
	}
	
	private class MyLocationListener implements LocationListener {
		private Location location = null;
		public boolean test_has_received_location = false;
		
		Location getLocation() {
			return location;
		}
		
	    public void onLocationChanged(Location location) {
			if( MyDebug.LOG )
				Log.d(TAG, "onLocationChanged");
			this.test_has_received_location = true;
    		// Android camera source claims we need to check lat/long != 0.0d
    		if( location.getLatitude() != 0.0d || location.getLongitude() != 0.0d ) {
	    		if( MyDebug.LOG ) {
	    			Log.d(TAG, "received location:");
	    			Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy());
	    		}
				this.location = location;
    		}
	    }

	    public void onStatusChanged(String provider, int status, Bundle extras) {
	         switch( status ) {
	         	case LocationProvider.OUT_OF_SERVICE:
	         	case LocationProvider.TEMPORARILY_UNAVAILABLE:
	         	{
					if( MyDebug.LOG ) {
						if( status == LocationProvider.OUT_OF_SERVICE )
							Log.d(TAG, "location provider out of service");
						else if( status == LocationProvider.TEMPORARILY_UNAVAILABLE )
							Log.d(TAG, "location provider temporarily unavailable");
					}
					this.location = null;
					this.test_has_received_location = false;
	         		break;
	         	}
	         }
	    }

	    public void onProviderEnabled(String provider) {
	    }

	    public void onProviderDisabled(String provider) {
			if( MyDebug.LOG )
				Log.d(TAG, "onProviderDisabled");
			this.location = null;
			this.test_has_received_location = false;
	    }
	}
	
	private void setupLocationListener() {
		if( MyDebug.LOG )
			Log.d(TAG, "setupLocationListener");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Define a listener that responds to location updates
		// we only set it up if store_location is true, to avoid unnecessarily wasting battery
		boolean store_location = sharedPreferences.getBoolean(getLocationPreferenceKey(), false);
		if( store_location && locationListeners == null ) {
			locationListeners = new MyLocationListener[2];
			locationListeners[0] = new MyLocationListener();
			locationListeners[1] = new MyLocationListener();
			
			// location listeners should be stored in order best to worst
			// also see https://sourceforge.net/p/opencamera/tickets/1/ - need to check provider is available
			if( mLocationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) ) {
				mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListeners[1]);
			}
			if( mLocationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) ) {
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListeners[0]);
			}
		}
		else if( !store_location ) {
			freeLocationListeners();
		}
	}
	
	private void freeLocationListeners() {
		if( MyDebug.LOG )
			Log.d(TAG, "freeLocationListeners");
		if( locationListeners != null ) {
			for(int i=0;i<locationListeners.length;i++) {
	            mLocationManager.removeUpdates(locationListeners[i]);
	            locationListeners[i] = null;
			}
            locationListeners = null;
		}
	}

	@Override
    protected void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
        super.onResume();

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
		getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(magneticListener, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();

        setupLocationListener();

		layoutUI();

		updateGalleryIcon(); // update in case images deleted whilst idle

		preview.onResume();

    }
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		if( MyDebug.LOG )
			Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
		super.onWindowFocusChanged(hasFocus);
        if( !this.camera_in_background && hasFocus ) {
			// low profile mode is cleared when app goes into background
        	// and for Kit Kat immersive mode, we want to set up the timer
        	// we do in onWindowFocusChanged rather than onResume(), to also catch when window lost focus due to notification bar being dragged down (which prevents resetting of immersive mode)
            initImmersiveMode();
        }
	}

    @Override
    protected void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
        super.onPause();
		closePopup();
        mSensorManager.unregisterListener(accelerometerListener);
        mSensorManager.unregisterListener(magneticListener);
        orientationEventListener.disable();
		freeLocationListeners();
		preview.onPause();
    }

    public void layoutUI() {
		if( MyDebug.LOG )
			Log.d(TAG, "layoutUI");
		this.preview.updateUIPlacement();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String ui_placement = sharedPreferences.getString(getUIPlacementPreferenceKey(), "ui_right");
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
		int align_top = RelativeLayout.ALIGN_TOP;
		int align_bottom = RelativeLayout.ALIGN_BOTTOM;
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int above = RelativeLayout.ABOVE;
		int below = RelativeLayout.BELOW;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( !ui_placement_right ) {
			align_top = RelativeLayout.ALIGN_BOTTOM;
			align_bottom = RelativeLayout.ALIGN_TOP;
			above = RelativeLayout.BELOW;
			below = RelativeLayout.ABOVE;
			align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
			align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
		}
		{
			// we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
			View view = findViewById(R.id.gui_anchor);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
	
			view = findViewById(R.id.settings);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gui_anchor);
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
	
			view = findViewById(R.id.popup);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gallery);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
	
			view = findViewById(R.id.exposure_lock);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
	
			view = findViewById(R.id.exposure);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.exposure_lock);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);
	
			view = findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.exposure);
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

			view = findViewById(R.id.focus_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(left_of, R.id.zoom_seekbar);
			layoutParams.addRule(right_of, 0);
			layoutParams.addRule(align_top, 0);
			layoutParams.addRule(align_bottom, R.id.zoom_seekbar);
			view.setLayoutParams(layoutParams);
		}

		{
			// set seekbar info
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

			View view = findViewById(R.id.exposure_seekbar);
			view.setRotation(ui_rotation);
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = findViewById(R.id.exposure_seekbar_zoom);
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

			view = findViewById(R.id.iso_seekbar);
			view.setRotation(ui_rotation);
			lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = findViewById(R.id.exposure_time_seekbar);
			view.setRotation(ui_rotation);
			lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);
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
			View view = findViewById(R.id.popup_container);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			//layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(align_right, R.id.popup);
			layoutParams.addRule(below, R.id.popup);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(align_parent_top, 0);
			view.setLayoutParams(layoutParams);

			view.setRotation(ui_rotation);
			// reset:
			view.setTranslationX(0.0f);
			view.setTranslationY(0.0f);
			if( MyDebug.LOG ) {
				Log.d(TAG, "popup view width: " + view.getWidth());
				Log.d(TAG, "popup view height: " + view.getHeight());
			}
			if( ui_rotation == 0 || ui_rotation == 180 ) {
				view.setPivotX(view.getWidth()/2.0f);
				view.setPivotY(view.getHeight()/2.0f);
			}
			else {
				view.setPivotX(view.getWidth());
				view.setPivotY(ui_placement_right ? 0.0f : view.getHeight());
				if( ui_placement_right ) {
					if( ui_rotation == 90 )
						view.setTranslationY( view.getWidth() );
					else if( ui_rotation == 270 )
						view.setTranslationX( - view.getHeight() );
				}
				else {
					if( ui_rotation == 90 )
						view.setTranslationX( - view.getHeight() );
					else if( ui_rotation == 270 )
						view.setTranslationY( - view.getWidth() );
				}
			}
		}

		{
			// set icon for taking photos vs videos
			ImageButton view = (ImageButton)findViewById(R.id.take_photo);
			if( preview != null ) {
				view.setImageResource(preview.isVideo() ? R.drawable.take_video_selector : R.drawable.take_photo_selector);
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
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
		if( MyDebug.LOG )
			Log.d(TAG, "onConfigurationChanged()");
		// configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
		// needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }

    public void clickedTakePhoto(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTakePhoto");
    	this.takePicture();
    }

    public void clickedSwitchCamera(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSwitchCamera");
		this.closePopup();
		this.preview.switchCamera();
    }

    public void clickedSwitchVideo(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSwitchVideo");
		this.closePopup();
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
		View view = findViewById(R.id.exposure_seekbar);
		view.setVisibility(View.GONE);
		view = findViewById(R.id.iso_seekbar);
		view.setVisibility(View.GONE);
		view = findViewById(R.id.exposure_time_seekbar);
		view.setVisibility(View.GONE);
		view = findViewById(R.id.exposure_seekbar_zoom);
		view.setVisibility(View.GONE);
    }
    
    void setSeekBarExposure() {
		SeekBar seek_bar = ((SeekBar)findViewById(R.id.exposure_seekbar));
		final int min_exposure = preview.getMinimumExposure();
		seek_bar.setMax( preview.getMaximumExposure() - min_exposure );
		seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void clickedExposure(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedExposure");
		this.closePopup();
		SeekBar seek_bar = ((SeekBar)findViewById(R.id.exposure_seekbar));
		int visibility = seek_bar.getVisibility();
		SeekBar iso_seek_bar = ((SeekBar)findViewById(R.id.iso_seekbar));
		int iso_visibility = iso_seek_bar.getVisibility();
		SeekBar exposure_time_seek_bar = ((SeekBar)findViewById(R.id.exposure_time_seekbar));
		int exposure_time_visibility = iso_seek_bar.getVisibility();
		boolean is_open = visibility == View.VISIBLE || iso_visibility == View.VISIBLE || exposure_time_visibility == View.VISIBLE;
		if( is_open ) {
			clearSeekBar();
		}
		else if( preview.getCameraController() != null ) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String value = sharedPreferences.getString(MainActivity.getISOPreferenceKey(), preview.getCameraController().getDefaultISO());
			if( !value.equals(preview.getCameraController().getDefaultISO()) ) {
				if( preview.supportsISORange()) {
					iso_seek_bar.setVisibility(View.VISIBLE);
					final int min_iso = preview.getMinimumISO();
					final int max_iso = preview.getMaximumISO();
					/*iso_seek_bar.setMax( max_iso - min_iso );
					iso_seek_bar.setProgress( preview.getCameraController().getISO() - min_iso );*/
					{
						iso_seek_bar.setMax(100);
						double scaling = (preview.getCameraController().getISO() - min_iso)/(double)(max_iso - min_iso);
						double frac = MainActivity.seekbarScalingInverse(scaling);
						int iso_percent = (int)(frac*100.0 + 0.5); // add 0.5 for rounding
						if( iso_percent < 0 )
							iso_percent = 0;
						else if( iso_percent > 100 )
							iso_percent = 100;
						iso_seek_bar.setProgress(iso_percent);
					}
					iso_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
						@Override
						public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
							if( MyDebug.LOG )
								Log.d(TAG, "iso seekbar onProgressChanged: " + progress);
							//preview.setISO(min_iso + progress, false);
							double frac = progress/(double)100.0;
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time frac: " + frac);
							double scaling = MainActivity.seekbarScaling(frac);
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time scaling: " + scaling);
							int iso = min_iso + (int)(scaling * (max_iso - min_iso));
							preview.setISO(iso, false);
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}
					});
					if( preview.supportsExposureTime() ) {
						exposure_time_seek_bar.setVisibility(View.VISIBLE);
						final long min_exposure_time = preview.getMinimumExposureTime();
						final long max_exposure_time = preview.getMaximumExposureTime();
						long exposure_time = preview.getCameraController().getExposureTime();
						exposure_time_seek_bar.setMax(100);
						//double frac = (exposure_time - min_exposure_time)/(double)(max_exposure_time - min_exposure_time);
						double scaling = (exposure_time - min_exposure_time)/(double)(max_exposure_time - min_exposure_time);
						double frac = MainActivity.seekbarScalingInverse(scaling);
						// see below for formula
						int exposure_time_percent = (int)(frac*100.0 + 0.5); // add 0.5 for rounding
						if( exposure_time_percent < 0 )
							exposure_time_percent = 0;
						else if( exposure_time_percent > 100 )
							exposure_time_percent = 100;
						if( MyDebug.LOG ) {
							Log.d(TAG, "exposure_time: " + exposure_time);
							Log.d(TAG, "exposure_time scaling: " + scaling);
							Log.d(TAG, "exposure_time frac: " + frac);
							Log.d(TAG, "exposure_time_percent: " + exposure_time_percent);
						}
						exposure_time_seek_bar.setProgress(exposure_time_percent);
						exposure_time_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
							@Override
							public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
								if( MyDebug.LOG )
									Log.d(TAG, "exposure_time seekbar onProgressChanged: " + progress);
								double frac = progress/(double)100.0;
								if( MyDebug.LOG )
									Log.d(TAG, "exposure_time frac: " + frac);
								//long exposure_time = min_exposure_time + (long)(frac * (max_exposure_time - min_exposure_time));
								//double exposure_time_r = min_exposure_time_r + (frac * (max_exposure_time_r - min_exposure_time_r));
								//long exposure_time = (long)(1.0 / exposure_time_r);
								// we use the formula: [100^(percent/100) - 1]/99.0 rather than a simple linear scaling
								double scaling = MainActivity.seekbarScaling(frac);
								if( MyDebug.LOG )
									Log.d(TAG, "exposure_time scaling: " + scaling);
								long exposure_time = min_exposure_time + (long)(scaling * (max_exposure_time - min_exposure_time));
								preview.setExposureTime(exposure_time);
							}

							@Override
							public void onStartTrackingTouch(SeekBar seekBar) {
							}

							@Override
							public void onStopTrackingTouch(SeekBar seekBar) {
							}
						});
					}
				}
			}
			else {
				if( preview.supportsExposures() ) {
					final int min_exposure = preview.getMinimumExposure();
					seek_bar.setVisibility(View.VISIBLE);
					setSeekBarExposure();
					seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

						@Override
						public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
							if( MyDebug.LOG )
								Log.d(TAG, "exposure seekbar onProgressChanged: " + progress);
							preview.setExposure(min_exposure + progress, false);
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}
					});

					ZoomControls seek_bar_zoom = (ZoomControls)findViewById(R.id.exposure_seekbar_zoom);
					seek_bar_zoom.setVisibility(View.VISIBLE);
					seek_bar_zoom.setOnZoomInClickListener(new View.OnClickListener(){
			            public void onClick(View v){
			            	preview.changeExposure(1, true);
			            }
			        });
					seek_bar_zoom.setOnZoomOutClickListener(new View.OnClickListener(){
				    	public void onClick(View v){
			            	preview.changeExposure(-1, true);
				        }
				    });
				}
			}
		}
    }
    
    static double seekbarScaling(double frac) {
    	// For various seekbars, we want to use a non-linear scaling, so user has more control over smaller values
    	double scaling = (Math.pow(100.0, frac) - 1.0) / 99.0;
    	return scaling;
    }

    static double seekbarScalingInverse(double scaling) {
    	double frac = Math.log(99.0*scaling + 1.0) / Math.log(100.0);
    	return frac;
    }
    
    public void clickedExposureLock(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedExposureLock");
    	this.preview.toggleExposureLock();
    }
    
    public void clickedSettings(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSettings");
		openSettings();
    }

    public boolean popupIsOpen() {
		if( popup_view != null ) {
			return true;
		}
		return false;
    }

    // for testing
    public View getPopupButton(String key) {
    	return popup_view.getPopupButton(key);
    }

    void closePopup() {
		if( MyDebug.LOG )
			Log.d(TAG, "close popup");
		if( popupIsOpen() ) {
			ViewGroup popup_container = (ViewGroup)findViewById(R.id.popup_container);
			popup_container.removeAllViews();
			popup_view.close();
			popup_view = null;
			initImmersiveMode(); // to reset the timer when closing the popup
		}
    }
    
    Bitmap getPreloadedBitmap(int resource) {
		Bitmap bm = this.preloaded_bitmap_resources.get(resource);
		return bm;
    }

    public void clickedPopupSettings(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedPopupSettings");
		final ViewGroup popup_container = (ViewGroup)findViewById(R.id.popup_container);
		if( popupIsOpen() ) {
			closePopup();
			return;
		}
		if( preview.getCameraController() == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "open popup");

		clearSeekBar();
		preview.cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings

    	final long time_s = System.currentTimeMillis();

    	{
			// prevent popup being transparent
			popup_container.setBackgroundColor(Color.BLACK);
			popup_container.setAlpha(0.95f);
		}

    	popup_view = new PopupView(this);
		popup_container.addView(popup_view);
		
        // need to call layoutUI to make sure the new popup is oriented correctly
		// but need to do after the layout has been done, so we have a valid width/height to use
		popup_container.getViewTreeObserver().addOnGlobalLayoutListener( 
			new OnGlobalLayoutListener() {
				@SuppressWarnings("deprecation")
			    @SuppressLint("NewApi")
				@Override
			    public void onGlobalLayout() {
					if( MyDebug.LOG )
						Log.d(TAG, "onGlobalLayout()");
					if( MyDebug.LOG )
						Log.d(TAG, "time after global layout: " + (System.currentTimeMillis() - time_s));
		    		layoutUI();
					if( MyDebug.LOG )
						Log.d(TAG, "time after layoutUI: " + (System.currentTimeMillis() - time_s));
		    		// stop listening - only want to call this once!
		            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
		            	popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		            } else {
		            	popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		            }

		    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		    		String ui_placement = sharedPreferences.getString(getUIPlacementPreferenceKey(), "ui_right");
		    		boolean ui_placement_right = ui_placement.equals("ui_right");
		            ScaleAnimation animation = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, ui_placement_right ? 0.0f : 1.0f);
		    		animation.setDuration(100);
		    		popup_container.setAnimation(animation);
		        }
			}
		);

		if( MyDebug.LOG )
			Log.d(TAG, "time to create popup: " + (System.currentTimeMillis() - time_s));
    }
    
    private void openSettings() {
		if( MyDebug.LOG )
			Log.d(TAG, "openSettings");
		closePopup();
		preview.cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
		preview.stopVideo(false); // important to stop video, as we'll be changing camera parameters when the settings window closes
		
		Bundle bundle = new Bundle();
		bundle.putInt("cameraId", this.preview.getCameraId());
		bundle.putString("camera_api", this.preview.getCameraAPI());
		bundle.putBoolean("using_android_l", this.preview.usingCamera2API());
		bundle.putBoolean("supports_auto_stabilise", this.supports_auto_stabilise);
		bundle.putBoolean("supports_force_video_4k", this.supports_force_video_4k);
		bundle.putBoolean("supports_camera2", this.supports_camera2);
		bundle.putBoolean("supports_face_detection", this.preview.supportsFaceDetection());
		bundle.putBoolean("supports_video_stabilization", this.preview.supportsVideoStabilization());
		bundle.putBoolean("can_disable_shutter_sound", this.preview.canDisableShutterSound());

		putBundleExtra(bundle, "color_effects", this.preview.getSupportedColorEffects());
		putBundleExtra(bundle, "scene_modes", this.preview.getSupportedSceneModes());
		putBundleExtra(bundle, "white_balances", this.preview.getSupportedWhiteBalances());
		putBundleExtra(bundle, "isos", this.preview.getSupportedISOs());
		bundle.putString("iso_key", this.preview.getISOKey());
		if( this.preview.getCameraController() != null ) {
			bundle.putString("parameters_string", preview.getCameraController().getParametersString());
		}

		List<CameraController.Size> preview_sizes = this.preview.getSupportedPreviewSizes();
		if( preview_sizes != null ) {
			int [] widths = new int[preview_sizes.size()];
			int [] heights = new int[preview_sizes.size()];
			int i=0;
			for(CameraController.Size size: preview_sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			bundle.putIntArray("preview_widths", widths);
			bundle.putIntArray("preview_heights", heights);
		}
		
		List<CameraController.Size> sizes = this.preview.getSupportedPictureSizes();
		if( sizes != null ) {
			int [] widths = new int[sizes.size()];
			int [] heights = new int[sizes.size()];
			int i=0;
			for(CameraController.Size size: sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			bundle.putIntArray("resolution_widths", widths);
			bundle.putIntArray("resolution_heights", heights);
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
			bundle.putStringArray("video_quality", video_quality_arr);
			bundle.putStringArray("video_quality_string", video_quality_string_arr);
		}

		List<CameraController.Size> video_sizes = this.preview.getSupportedVideoSizes();
		if( video_sizes != null ) {
			int [] widths = new int[video_sizes.size()];
			int [] heights = new int[video_sizes.size()];
			int i=0;
			for(CameraController.Size size: video_sizes) {
				widths[i] = size.width;
				heights[i] = size.height;
				i++;
			}
			bundle.putIntArray("video_widths", widths);
			bundle.putIntArray("video_heights", heights);
		}
		
		putBundleExtra(bundle, "flash_values", this.preview.getSupportedFlashValues());
		putBundleExtra(bundle, "focus_values", this.preview.getSupportedFocusValues());

		setWindowFlagsForSettings();
		MyPreferenceFragment fragment = new MyPreferenceFragment();
		fragment.setArguments(bundle);
        getFragmentManager().beginTransaction().add(R.id.prefs_container, fragment, "PREFERENCE_FRAGMENT").addToBackStack(null).commit();
    }

    public void updateForSettings() {
    	updateForSettings(null);
    }

    public void updateForSettings(String toast_message) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "updateForSettings()");
			if( toast_message != null ) {
				Log.d(TAG, "toast_message: " + toast_message);
			}
		}
    	String saved_focus_value = null;
    	if( preview.getCameraController() != null && preview.isVideo() && !preview.focusIsVideo() ) {
    		saved_focus_value = preview.getCurrentFocusValue(); // n.b., may still be null
			// make sure we're into continuous video mode
			// workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
			// so to be safe, we always reset to continuous video mode, and then reset it afterwards
			preview.updateFocusForVideo(false);
    	}
		if( MyDebug.LOG )
			Log.d(TAG, "saved_focus_value: " + saved_focus_value);
    	
		updateFolderHistory();

		// update camera for changes made in prefs - do this without closing and reopening the camera app if possible for speed!
		// but need workaround for Nexus 7 bug, where scene mode doesn't take effect unless the camera is restarted - I can reproduce this with other 3rd party camera apps, so may be a Nexus 7 issue...
		boolean need_reopen = false;
		if( preview.getCameraController() != null ) {
			String scene_mode = preview.getCameraController().getSceneMode();
			if( MyDebug.LOG )
				Log.d(TAG, "scene mode was: " + scene_mode);
			String key = getSceneModePreferenceKey();
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String value = sharedPreferences.getString(key, preview.getCameraController().getDefaultSceneMode());
			if( !value.equals(scene_mode) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "scene mode changed to: " + value);
				need_reopen = true;
			}
		}

		layoutUI(); // needed in case we've changed left/right handed UI
        setupLocationListener(); // in case we've enabled GPS
		if( need_reopen || preview.getCameraController() == null ) { // if camera couldn't be opened before, might as well try again
			preview.onPause();
			preview.onResume(toast_message);
		}
		else {
			preview.setCameraDisplayOrientation(); // need to call in case the preview rotation option was changed
			preview.pausePreview();
			preview.setupCamera(toast_message, false);
		}

    	if( saved_focus_value != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "switch focus back to: " + saved_focus_value);
    		preview.updateFocus(saved_focus_value, true, false);
    	}
    }
    
    boolean cameraInBackground() {
    	return this.camera_in_background;
    }
    
    MyPreferenceFragment getPreferenceFragment() {
        MyPreferenceFragment fragment = (MyPreferenceFragment)getFragmentManager().findFragmentByTag("PREFERENCE_FRAGMENT");
        return fragment;
    }
    
    @Override
    public void onBackPressed() {
        final MyPreferenceFragment fragment = getPreferenceFragment();
        if( screen_is_locked ) {
			preview.showToast(screen_locked_toast, R.string.screen_is_locked);
        	return;
        }
        if( fragment != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "close settings");
			setWindowFlagsForCamera();
			updateForSettings();
        }
        else {
			if( popupIsOpen() ) {
    			closePopup();
    			return;
    		}
        }
        super.onBackPressed();        
    }
    
    boolean usingKitKatImmersiveMode() {
    	// whether we are using a Kit Kat style immersive mode (either hiding GUI, or everything)
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    		String immersive_mode = sharedPreferences.getString(getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
    		if( immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything") )
    			return true;
		}
		return false;
    }
    
    private Handler immersive_timer_handler = null;
    private Runnable immersive_timer_runnable = null;
    
    private void setImmersiveTimer() {
    	if( immersive_timer_handler != null && immersive_timer_runnable != null ) {
    		immersive_timer_handler.removeCallbacks(immersive_timer_runnable);
    	}
    	immersive_timer_handler = new Handler();
    	immersive_timer_handler.postDelayed(immersive_timer_runnable = new Runnable(){
    		@Override
    	    public void run(){
    			if( MyDebug.LOG )
    				Log.d(TAG, "setImmersiveTimer: run");
    			if( !camera_in_background && !popupIsOpen() && usingKitKatImmersiveMode() )
    				setImmersiveMode(true);
    	   }
    	}, 5000);
    }

    void initImmersiveMode() {
        if( !usingKitKatImmersiveMode() ) {
			setImmersiveMode(true);
		}
        else {
        	// don't start in immersive mode, only after a timer
        	setImmersiveTimer();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
	void setImmersiveMode(boolean on) {
		if( MyDebug.LOG )
			Log.d(TAG, "setImmersiveMode: " + on);
    	// n.b., preview.setImmersiveMode() is called from onSystemUiVisibilityChange()
    	if( on ) {
    		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && usingKitKatImmersiveMode() ) {
        		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    		}
    		else {
        		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        		String immersive_mode = sharedPreferences.getString(getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
        		if( immersive_mode.equals("immersive_mode_low_profile") )
        			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        		else
            		getWindow().getDecorView().setSystemUiVisibility(0);
    		}
    	}
    	else
    		getWindow().getDecorView().setSystemUiVisibility(0);
    }
    
    private void setWindowFlagsForCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "setWindowFlagsForCamera");
    	/*{
    		Intent intent = new Intent(this, MyWidgetProvider.class);
    		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    		AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
    		ComponentName widgetComponent = new ComponentName(this, MyWidgetProvider.class);
    		int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
    		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
    		sendBroadcast(intent);    		
    	}*/
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		// force to landscape mode
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		// keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
		if( sharedPreferences.getBoolean(getKeepDisplayOnPreferenceKey(), true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "do keep screen on");
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "don't keep screen on");
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		if( sharedPreferences.getBoolean(getShowWhenLockedPreferenceKey(), true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "do show when locked");
	        // keep Open Camera on top of screen-lock (will still need to unlock when going to gallery or settings)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "don't show when locked");
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}

        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
		// done here rather than onCreate, so that changing it in preferences takes effect without restarting app
		{
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
			if( sharedPreferences.getBoolean(getMaxBrightnessPreferenceKey(), true) ) {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
	        }
			else {
		        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			}
	        getWindow().setAttributes(layout); 
		}
		
		initImmersiveMode();
		camera_in_background = false;
    }
    
    private void setWindowFlagsForSettings() {
		// allow screen rotation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		// revert to standard screen blank behaviour
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // settings should still be protected by screen lock
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		{
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
	        layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
	        getWindow().setAttributes(layout); 
		}

		setImmersiveMode(false);
		camera_in_background = true;
    }
    
    private void showPreview(boolean show) {
		if( MyDebug.LOG )
			Log.d(TAG, "showPreview: " + show);
		final ViewGroup container = (ViewGroup)findViewById(R.id.hide_container);
		container.setBackgroundColor(Color.BLACK);
		container.setAlpha(show ? 0.0f : 1.0f);
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
		if( MyDebug.LOG )
			Log.d(TAG, "getLatestMedia: " + (video ? "video" : "images"));
    	Media media = null;
		Uri baseUri = video ? Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		//Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
		Uri query = baseUri;
		final int column_id_c = 0;
		final int column_date_taken_c = 1;
		final int column_data_c = 2;
		final int column_orientation_c = 3;
		String [] projection = video ? new String[] {VideoColumns._ID, VideoColumns.DATE_TAKEN, VideoColumns.DATA} : new String[] {ImageColumns._ID, ImageColumns.DATE_TAKEN, ImageColumns.DATA, ImageColumns.ORIENTATION};
		String selection = video ? "" : ImageColumns.MIME_TYPE + "='image/jpeg'";
		String order = video ? VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC" : ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";
		Cursor cursor = null;
		try {
			cursor = getContentResolver().query(query, projection, selection, null, order);
			if( cursor != null && cursor.moveToFirst() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "found: " + cursor.getCount());
				// now sorted in order of date - scan to most recent one in the Open Camera save folder
				boolean found = false;
				File save_folder = getImageFolder();
				String save_folder_string = save_folder.getAbsolutePath() + File.separator;
				if( MyDebug.LOG )
					Log.d(TAG, "save_folder_string: " + save_folder_string);
				do {
					String path = cursor.getString(column_data_c);
					if( MyDebug.LOG )
						Log.d(TAG, "path: " + path);
					// path may be null on Android 4.4!: http://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
					if( path != null && path.contains(save_folder_string) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "found most recent in Open Camera folder");
						// we filter files with dates in future, in case there exists an image in the folder with incorrect datestamp set to the future
						// we allow up to 2 days in future, to avoid risk of issues to do with timezone etc
						long date = cursor.getLong(column_date_taken_c);
				    	long current_time = System.currentTimeMillis();
						if( date > current_time + 172800000 ) {
							if( MyDebug.LOG )
								Log.d(TAG, "skip date in the future!");
						}
						else {
							found = true;
							break;
						}
					}
				} while( cursor.moveToNext() );
				if( !found ) {
					if( MyDebug.LOG )
						Log.d(TAG, "can't find suitable in Open Camera folder, so just go with most recent");
					cursor.moveToFirst();
				}
				long id = cursor.getLong(column_id_c);
				long date = cursor.getLong(column_date_taken_c);
				int orientation = video ? 0 : cursor.getInt(column_orientation_c);
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
    	// since we're now setting the thumbnail to the latest media on disk, we need to make sure clicking the Gallery goes to this
    	last_media_scanned = null;
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
		Uri uri = this.last_media_scanned;
		if( uri == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "go to latest media");
			Media media = getLatestMedia();
			if( media != null ) {
				uri = media.uri;
			}
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
					preview.showToast(null, R.string.no_gallery_app);
				}
			}
		}
    }

    private void updateFolderHistory() {
		String folder_name = getSaveLocation();
		updateFolderHistory(folder_name);
		updateGalleryIcon(); // if the folder has changed, need to update the gallery icon
    }
    
    private void updateFolderHistory(String folder_name) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "updateFolderHistory: " + folder_name);
			Log.d(TAG, "save_location_history size: " + save_location_history.size());
			for(int i=0;i<save_location_history.size();i++) {
				Log.d(TAG, save_location_history.get(i));
			}
		}
		while( save_location_history.remove(folder_name) ) {
		}
		save_location_history.add(folder_name);
		while( save_location_history.size() > 6 ) {
			save_location_history.remove(0);
		}
		writeSaveLocations();
		if( MyDebug.LOG ) {
			Log.d(TAG, "updateFolderHistory exit:");
			Log.d(TAG, "save_location_history size: " + save_location_history.size());
			for(int i=0;i<save_location_history.size();i++) {
				Log.d(TAG, save_location_history.get(i));
			}
		}
    }
    
    public void clearFolderHistory() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearFolderHistory");
		save_location_history.clear();
		updateFolderHistory(); // to re-add the current choice, and save
    }
    
    private void writeSaveLocations() {
		if( MyDebug.LOG )
			Log.d(TAG, "writeSaveLocations");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt("save_location_history_size", save_location_history.size());
		if( MyDebug.LOG )
			Log.d(TAG, "save_location_history_size = " + save_location_history.size());
        for(int i=0;i<save_location_history.size();i++) {
        	String string = save_location_history.get(i);
    		editor.putString("save_location_history_" + i, string);
        }
		editor.apply();
    }
    
    private void openFolderChooserDialog() {
		if( MyDebug.LOG )
			Log.d(TAG, "openFolderChooserDialog");
		showPreview(false);
		setWindowFlagsForSettings();
		final String orig_save_location = getSaveLocation();
		FolderChooserDialog fragment = new FolderChooserDialog() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				if( MyDebug.LOG )
					Log.d(TAG, "FolderChooserDialog dismissed");
				setWindowFlagsForCamera();
				showPreview(true);
				final String new_save_location = getSaveLocation();
				if( !orig_save_location.equals(new_save_location) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "changed save_folder to: " + getSaveLocation());
					updateFolderHistory();
					preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + getSaveLocation());
				}
				super.onDismiss(dialog);
			}
		};
		fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
    }
    
    private void longClickedGallery() {
		if( MyDebug.LOG )
			Log.d(TAG, "longClickedGallery");
		if( save_location_history.size() <= 1 ) {
			// go straight to choose folder dialog
			openFolderChooserDialog();
			return;
		}

		showPreview(false);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.choose_save_location);
        CharSequence [] items = new CharSequence[save_location_history.size()+2];
        int index=0;
        // save_location_history is stored in order most-recent-last
        for(int i=0;i<save_location_history.size();i++) {
        	items[index++] = save_location_history.get(save_location_history.size() - 1 - i);
        }
        final int clear_index = index;
        items[index++] = getResources().getString(R.string.clear_folder_history);
        final int new_index = index;
        items[index++] = getResources().getString(R.string.choose_another_folder);
		alertDialog.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if( which == clear_index ) {
					if( MyDebug.LOG )
						Log.d(TAG, "selected clear save history");
				    new AlertDialog.Builder(MainActivity.this)
			        	.setIcon(android.R.drawable.ic_dialog_alert)
			        	.setTitle(R.string.clear_folder_history)
			        	.setMessage(R.string.clear_folder_history_question)
			        	.setPositiveButton(R.string.answer_yes, new DialogInterface.OnClickListener() {
			        		@Override
					        public void onClick(DialogInterface dialog, int which) {
								if( MyDebug.LOG )
									Log.d(TAG, "confirmed clear save history");
								clearFolderHistory();
								setWindowFlagsForCamera();
								showPreview(true);
					        }
			        	})
			        	.setNegativeButton(R.string.answer_no, new DialogInterface.OnClickListener() {
			        		@Override
					        public void onClick(DialogInterface dialog, int which) {
								if( MyDebug.LOG )
									Log.d(TAG, "don't clear save history");
								setWindowFlagsForCamera();
								showPreview(true);
					        }
			        	})
						.setOnCancelListener(new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface arg0) {
								if( MyDebug.LOG )
									Log.d(TAG, "cancelled clear save history");
								setWindowFlagsForCamera();
								showPreview(true);
							}
						})
			        	.show();
				}
				else if( which == new_index ) {
					if( MyDebug.LOG )
						Log.d(TAG, "selected choose new folder");
					openFolderChooserDialog();
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "selected: " + which);
					if( which >= 0 && which < save_location_history.size() ) {
						String save_folder = save_location_history.get(save_location_history.size() - 1 - which);
						if( MyDebug.LOG )
							Log.d(TAG, "changed save_folder from history to: " + save_folder);
						preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + save_folder);
						SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.putString(getSaveLocationPreferenceKey(), save_folder);
						editor.apply();
						updateFolderHistory(); // to move new selection to most recent
					}
					setWindowFlagsForCamera();
					showPreview(true);
				}
			}
        });
		alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
		        setWindowFlagsForCamera();
				showPreview(true);
			}
		});
        alertDialog.show();
		//getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		setWindowFlagsForSettings();
    }

    static private void putBundleExtra(Bundle bundle, String key, List<String> values) {
		if( values != null ) {
			String [] values_arr = new String[values.size()];
			int i=0;
			for(String value: values) {
				values_arr[i] = value;
				i++;
			}
			bundle.putStringArray(key, values_arr);
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
		closePopup();
    	this.preview.takePicturePressed();
    }
    
    void lockScreen() {
		((ViewGroup) findViewById(R.id.locker)).setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility") @Override
            public boolean onTouch(View arg0, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
                //return true;
            }
		});
		screen_is_locked = true;
    }
    
    void unlockScreen() {
		((ViewGroup) findViewById(R.id.locker)).setOnTouchListener(null);
		screen_is_locked = false;
    }
    
    boolean isScreenLocked() {
    	return screen_is_locked;
    }

    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
        		if( MyDebug.LOG )
        			Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
        		final ViewConfiguration vc = ViewConfiguration.get(MainActivity.this);
        		//final int swipeMinDistance = 4*vc.getScaledPagingTouchSlop();
    			final float scale = getResources().getDisplayMetrics().density;
    			final int swipeMinDistance = (int) (160 * scale + 0.5f); // convert dps to pixels
        		final int swipeThresholdVelocity = vc.getScaledMinimumFlingVelocity();
        		if( MyDebug.LOG ) {
        			Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
        			Log.d(TAG, "swipeMinDistance: " + swipeMinDistance);
        		}
                float xdist = e1.getX() - e2.getX();
                float ydist = e1.getY() - e2.getY();
                float dist2 = xdist*xdist + ydist*ydist;
                float vel2 = velocityX*velocityX + velocityY*velocityY;
                if( dist2 > swipeMinDistance*swipeMinDistance && vel2 > swipeThresholdVelocity*swipeThresholdVelocity ) {
                	preview.showToast(screen_locked_toast, R.string.unlocked);
                	unlockScreen();
                }
            }
            catch(Exception e) {
            }
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
			preview.showToast(screen_locked_toast, R.string.screen_is_locked);
			return true;
        }
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

    public void broadcastFile(final File file, final boolean is_new_picture, final boolean is_new_video) {
		if( MyDebug.LOG )
			Log.d(TAG, "broadcastFile");
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
 			failed_to_scan = true; // set to true until scanned okay
 			if( MyDebug.LOG )
 				Log.d(TAG, "failed_to_scan set to true");
        	MediaScannerConnection.scanFile(this, new String[] { file.getAbsolutePath() }, null,
        			new MediaScannerConnection.OnScanCompletedListener() {
					public void onScanCompleted(String path, Uri uri) {
    		 			if( MyDebug.LOG ) {
    		 				Log.d("ExternalStorage", "Scanned " + path + ":");
    		 				Log.d("ExternalStorage", "-> uri=" + uri);
    		 			}
    		 			last_media_scanned = uri;
    		        	if( is_new_picture ) {
    		        		// note, we reference the string directly rather than via Camera.ACTION_NEW_PICTURE, as the latter class is now deprecated - but we still need to broadcase the string for other apps
    		        		sendBroadcast(new Intent( "android.hardware.action.NEW_PICTURE" , uri));
    		        		// for compatibility with some apps - apparently this is what used to be broadcast on Android?
    		        		sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));

	    		 			if( MyDebug.LOG ) // this code only used for debugging/logging
	    		 			{
    		        	        String[] CONTENT_PROJECTION = { Images.Media.DATA, Images.Media.DISPLAY_NAME, Images.Media.MIME_TYPE, Images.Media.SIZE, Images.Media.DATE_TAKEN, Images.Media.DATE_ADDED }; 
    		        	        Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
    		        	        if( c == null ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
    		        	        }
    		        	        else if( !c.moveToFirst() ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
    		        	        }
    		        	        else {
    			        	        String file_path = c.getString(c.getColumnIndex(Images.Media.DATA)); 
    			        	        String file_name = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME)); 
    			        	        String mime_type = c.getString(c.getColumnIndex(Images.Media.MIME_TYPE)); 
    			        	        long date_taken = c.getLong(c.getColumnIndex(Images.Media.DATE_TAKEN)); 
    			        	        long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED)); 
		    		 				Log.d(TAG, "file_path: " + file_path); 
		    		 				Log.d(TAG, "file_name: " + file_name); 
		    		 				Log.d(TAG, "mime_type: " + mime_type); 
		    		 				Log.d(TAG, "date_taken: " + date_taken); 
		    		 				Log.d(TAG, "date_added: " + date_added); 
    			        	        c.close(); 
    		        	        }
    		        		}
	    		 			/*{
	    		 				// hack: problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP (GPSDateStamp) set (tends to be around 2038 - possibly a driver bug of casting long to int?)
	    		 				// whilst we don't yet correct for that bug, the more immediate problem is that it also messes up the DATE_TAKEN field in the media store, which messes up Gallery apps
	    		 				// so for now, we correct it based on the DATE_ADDED value.
    		        	        String[] CONTENT_PROJECTION = { Images.Media.DATE_ADDED }; 
    		        	        Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
    		        	        if( c == null ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
    		        	        }
    		        	        else if( !c.moveToFirst() ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
    		        	        }
    		        	        else {
    			        	        long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED)); 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "replace date_taken with date_added: " + date_added); 
									ContentValues values = new ContentValues(); 
									values.put(Images.Media.DATE_TAKEN, date_added*1000); 
									getContentResolver().update(uri, values, null, null);
    			        	        c.close(); 
    		        	        }
	    		 			}*/
    		        	}
    		        	else if( is_new_video ) {
    		        		sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));
    		        	}
    		 			failed_to_scan = false;
    		 		}
    			}
    		);
	        /*ContentValues values = new ContentValues(); 
	        values.put(ImageColumns.TITLE, file.getName().substring(0, file.getName().lastIndexOf(".")));
	        values.put(ImageColumns.DISPLAY_NAME, file.getName());
	        values.put(ImageColumns.DATE_TAKEN, System.currentTimeMillis()); 
	        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
	        // TODO: orientation
	        values.put(ImageColumns.DATA, file.getAbsolutePath());
	        Location location = preview.getLocation();
	        if( location != null ) {
    	        values.put(ImageColumns.LATITUDE, location.getLatitude()); 
    	        values.put(ImageColumns.LONGITUDE, location.getLongitude()); 
	        }
	        try {
	    		this.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values); 
	        }
	        catch (Throwable th) { 
    	        // This can happen when the external volume is already mounted, but 
    	        // MediaScanner has not notify MediaProvider to add that volume. 
    	        // The picture is still safe and MediaScanner will find it and 
    	        // insert it into MediaProvider. The only problem is that the user 
    	        // cannot click the thumbnail to review the picture. 
    	        Log.e(TAG, "Failed to write MediaStore" + th); 
    	    }*/
    	}
	}
    
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private String getSaveLocation() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String folder_name = sharedPreferences.getString(getSaveLocationPreferenceKey(), "OpenCamera");
		return folder_name;
    }
    
    static File getBaseFolder() {
    	return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    static File getImageFolder(String folder_name) {
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
	        file = new File(getBaseFolder(), folder_name);
		}
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "folder_name: " + folder_name);
			Log.d(TAG, "full path: " + file);
		}*/
        return file;
    }
    
    public File getImageFolder() {
		String folder_name = getSaveLocation();
		return getImageFolder(folder_name);
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
            broadcastFile(mediaStorageDir, false, false);
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String index = "";
        File mediaFile = null;
        for(int count=1;count<=100;count++) {
    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if( type == MEDIA_TYPE_IMAGE ) {
        		String prefix = sharedPreferences.getString(getSavePhotoPrefixPreferenceKey(), "IMG_");
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                	prefix + timeStamp + index + ".jpg");
            }
            else if( type == MEDIA_TYPE_VIDEO ) {
        		String prefix = sharedPreferences.getString(getSaveVideoPrefixPreferenceKey(), "VID_");
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                		prefix + timeStamp + index + ".mp4");
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

    public boolean supportsCamera2() {
    	return this.supports_camera2;
    }

    void disableForceVideo4K() {
    	this.supports_force_video_4k = false;
    }

    @SuppressWarnings("deprecation")
	public long freeMemory() { // return free memory in MB
    	try {
    		File folder = this.getImageFolder();
	        StatFs statFs = new StatFs(folder.getAbsolutePath());
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
    		// this can happen if folder doesn't exist, or don't have read access
    		// if the save folder is a subfolder of DCIM, we can just use that instead
        	try {
        		String folder_name = getSaveLocation();
        		if( !folder_name.startsWith("/") ) {
        			File folder = getBaseFolder();
        	        StatFs statFs = new StatFs(folder.getAbsolutePath());
        	        // cast to long to avoid overflow!
        	        long blocks = statFs.getAvailableBlocks();
        	        long size = statFs.getBlockSize();
        	        long free  = (blocks*size) / 1048576;
        			/*if( MyDebug.LOG ) {
        				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
        			}*/
        	        return free;
        		}
        	}
        	catch(IllegalArgumentException e2) {
        		// just in case
        	}
    	}
		return -1;
    }
    
    public static String getDonateLink() {
    	return "https://play.google.com/store/apps/details?id=harman.mark.donation";
    }

    /*public static String getDonateMarketLink() {
    	return "market://details?id=harman.mark.donation";
    }*/

    public Preview getPreview() {
    	return this.preview;
    }

    // must be static, to safely call from other Activities:

    public static String getFirstTimePreferenceKey() {
        return "done_first_time";
    }

    public static String getUseCamera2PreferenceKey() {
    	return "preference_use_camera2";
    }

    public static String getFlashPreferenceKey(int cameraId) {
    	return "flash_value_" + cameraId;
    }

    public static String getFocusPreferenceKey(int cameraId) {
    	return "focus_value_" + cameraId;
    }

    public static String getResolutionPreferenceKey(int cameraId) {
    	return "camera_resolution_" + cameraId;
    }
    
    public static String getVideoQualityPreferenceKey(int cameraId) {
    	return "video_quality_" + cameraId;
    }
    
    public static String getIsVideoPreferenceKey() {
    	return "is_video";
    }
    
    public static String getExposurePreferenceKey() {
    	return "preference_exposure";
    }

    public static String getColorEffectPreferenceKey() {
    	return "preference_color_effect";
    }

    public static String getSceneModePreferenceKey() {
    	return "preference_scene_mode";
    }

    public static String getWhiteBalancePreferenceKey() {
    	return "preference_white_balance";
    }

    public static String getISOPreferenceKey() {
    	return "preference_iso";
    }
    
    public static String getExposureTimePreferenceKey() {
    	return "preference_exposure_time";
    }
    
    public static String getVolumeKeysPreferenceKey() {
    	return "preference_volume_keys";
    }
    
    public static String getQualityPreferenceKey() {
    	return "preference_quality";
    }
    
    public static String getAutoStabilisePreferenceKey() {
    	return "preference_auto_stabilise";
    }
    
    public static String getLocationPreferenceKey() {
    	return "preference_location";
    }
    
    public static String getGPSDirectionPreferenceKey() {
    	return "preference_gps_direction";
    }
    
    public static String getRequireLocationPreferenceKey() {
    	return "preference_require_location";
    }
    
    public static String getStampPreferenceKey() {
    	return "preference_stamp";
    }

    public static String getTextStampPreferenceKey() {
    	return "preference_textstamp";
    }

    public static String getStampFontSizePreferenceKey() {
    	return "preference_stamp_fontsize";
    }

    public static String getUIPlacementPreferenceKey() {
    	return "preference_ui_placement";
    }
    
    public static String getPausePreviewPreferenceKey() {
    	return "preference_pause_preview";
    }
    
    public static String getThumbnailAnimationPreferenceKey() {
    	return "preference_thumbnail_animation";
    }

    public static String getShowWhenLockedPreferenceKey() {
    	return "preference_show_when_locked";
    }

    public static String getKeepDisplayOnPreferenceKey() {
    	return "preference_keep_display_on";
    }

    public static String getMaxBrightnessPreferenceKey() {
    	return "preference_max_brightness";
    }

    public static String getSaveLocationPreferenceKey() {
    	return "preference_save_location";
    }

    public static String getSavePhotoPrefixPreferenceKey() {
    	return "preference_save_photo_prefix";
    }

    public static String getSaveVideoPrefixPreferenceKey() {
    	return "preference_save_video_prefix";
    }

    public static String getShowZoomControlsPreferenceKey() {
    	return "preference_show_zoom_controls";
    }

    public static String getShowZoomSliderControlsPreferenceKey() {
    	return "preference_show_zoom_slider_controls";
    }
    
    public static String getShowZoomPreferenceKey() {
    	return "preference_show_zoom";
    }
    
    public static String getShowISOPreferenceKey() {
    	return "preference_show_iso";
    }

    public static String getShowAnglePreferenceKey() {
    	return "preference_show_angle";
    }
    
    public static String getShowAngleLinePreferenceKey() {
    	return "preference_show_angle_line";
    }

    public static String getShowGeoDirectionPreferenceKey() {
    	return "preference_show_geo_direction";
    }
    
    public static String getShowFreeMemoryPreferenceKey() {
    	return "preference_free_memory";
    }
    
    public static String getShowTimePreferenceKey() {
    	return "preference_show_time";
    }
    
    public static String getShowBatteryPreferenceKey() {
    	return "preference_show_battery";
    }
    
    public static String getShowGridPreferenceKey() {
    	return "preference_grid";
    }
    
    public static String getShowCropGuidePreferenceKey() {
    	return "preference_crop_guide";
    }
    
    public static String getFaceDetectionPreferenceKey() {
    	return "preference_face_detection";
    }

    public static String getVideoStabilizationPreferenceKey() {
    	return "preference_video_stabilization";
    }
    
    public static String getForceVideo4KPreferenceKey() {
    	return "preference_force_video_4k";
    }
    
    public static String getVideoBitratePreferenceKey() {
    	return "preference_video_bitrate";
    }

    public static String getVideoFPSPreferenceKey() {
    	return "preference_video_fps";
    }
    
    public static String getVideoMaxDurationPreferenceKey() {
    	return "preference_video_max_duration";
    }
    
    public static String getVideoRestartPreferenceKey() {
    	return "preference_video_restart";
    }
    
    public static String getVideoFlashPreferenceKey() {
    	return "preference_video_flash";
    }

    public static String getLockVideoPreferenceKey() {
    	return "preference_lock_video";
    }
    
    public static String getRecordAudioPreferenceKey() {
    	return "preference_record_audio";
    }

    public static String getRecordAudioSourcePreferenceKey() {
    	return "preference_record_audio_src";
    }

    public static String getPreviewSizePreferenceKey() {
    	return "preference_preview_size";
    }

    public static String getRotatePreviewPreferenceKey() {
    	return "preference_rotate_preview";
    }

    public static String getLockOrientationPreferenceKey() {
    	return "preference_lock_orientation";
    }

    public static String getTimerPreferenceKey() {
    	return "preference_timer";
    }
    
    public static String getTimerBeepPreferenceKey() {
    	return "preference_timer_beep";
    }
    
    public static String getBurstModePreferenceKey() {
    	return "preference_burst_mode";
    }
    
    public static String getBurstIntervalPreferenceKey() {
    	return "preference_burst_interval";
    }
    
    public static String getShutterSoundPreferenceKey() {
    	return "preference_shutter_sound";
    }
    
    public static String getImmersiveModePreferenceKey() {
    	return "preference_immersive_mode";
    }
    
    // for testing:
	public ArrayList<String> getSaveLocationHistory() {
		return this.save_location_history;
	}
	
    public void usedFolderPicker() {
    	updateFolderHistory();
    }

    public boolean hasLocationListeners() {
		if( this.locationListeners == null )
			return false;
		if( this.locationListeners.length != 2 )
			return false;
		for(int i=0;i<this.locationListeners.length;i++) {
			if( this.locationListeners[i] == null )
				return false;
		}
		return true;
	}
}
