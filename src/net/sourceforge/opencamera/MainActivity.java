package net.sourceforge.opencamera;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

class MyDebug {
	static final boolean LOG = true;
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Preview preview = null;
	private int current_orientation = 0;
	private OrientationEventListener orientationEventListener = null;
	private boolean supports_auto_stabilise = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onCreate");
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		boolean is_test = false;
		if( getIntent() != null && getIntent().getExtras() != null ) {
			is_test = getIntent().getExtras().getBoolean("test_project");
			if( MyDebug.LOG )
				Log.d(TAG, "is_test: " + is_test);
		}

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

		// keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
        {
	        WindowManager.LayoutParams layout = getWindow().getAttributes();
	        layout.screenBrightness = 1.0f;
	        getWindow().setAttributes(layout); 
        }

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

		preview = new Preview(this, savedInstanceState);
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		
        orientationEventListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				MainActivity.this.onOrientationChanged(orientation);
			}
        };

        final String done_first_time_key = "done_first_time";
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean has_done_first_time = sharedPreferences.contains(done_first_time_key);
        if( !has_done_first_time && !is_test ) {
	        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Open Camera");
            alertDialog.setMessage("Open Camera is completely free. If you like this app, please consider making a donation by buying my donate app :) (see link in the Settings, or go to the Google Play page for this app).\n\n(This message won't show in future.)");
            alertDialog.setPositiveButton("OK", null);
            alertDialog.show();

			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putBoolean(done_first_time_key, true);
			editor.apply();
        }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    @Override
    protected void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
        super.onResume();
        mSensorManager.registerListener(preview, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();

        layoutUI();

        preview.onResume();
    }

    @Override
    protected void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
        super.onPause();
        mSensorManager.unregisterListener(preview);
        orientationEventListener.disable();
		preview.onPause();
    }

    private void layoutUI() {
		if( MyDebug.LOG )
			Log.d(TAG, "layoutUI");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String ui_placement = sharedPreferences.getString("preference_ui_placement", "ui_right");
		boolean ui_placement_right = ui_placement.equals("ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		/*
		// old code for changing orientation (not yet updated for switch_video or gallery)
		if( getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ) {
			if( MyDebug.LOG )
				Log.d(TAG, "display is in portrait orientation");

			View top_view = findViewById(R.id.switch_camera);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)top_view.getLayoutParams();
			if( ui_placement_right ) {
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			}
			else {
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
				layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
			}
			top_view.setLayoutParams(layoutParams);

			top_view = findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)top_view.getLayoutParams();
			if( ui_placement_right ) {
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.take_photo);
			}
			else {
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.take_photo);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
			}
			top_view.setLayoutParams(layoutParams);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "display is in landscape orientation");

			if( ui_placement_right ) {
				View view = findViewById(R.id.settings);
				RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, R.id.preview);
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.focus_mode);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.settings);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.flash);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.focus_mode);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.switch_camera);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.flash);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.trash);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.switch_camera);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.share);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.trash);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.take_photo);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, R.id.preview);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.zoom);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, R.id.preview);
				view.setLayoutParams(layoutParams);
			}
			else {
				View view = findViewById(R.id.switch_camera);
				RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, R.id.preview);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, 0);
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.flash);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.switch_camera);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.focus_mode);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.flash);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.settings);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.focus_mode);
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, 0);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.share);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.settings);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.trash);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
				layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.share);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.take_photo);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, R.id.preview);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, 0);
				view.setLayoutParams(layoutParams);

				view = findViewById(R.id.zoom);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(RelativeLayout.ALIGN_LEFT, R.id.preview);
				layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, 0);
				view.setLayoutParams(layoutParams);
			}
		}*/
		// new code for orientation fixed to landscape	
		if( getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ) {
			// despite being fixed to orientation, the app is switched to portrait when the screen is blanked
			if( MyDebug.LOG ) {
				Log.d(TAG, "unexpected portrait mode");
			}
			return;
		}
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
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int align_top = RelativeLayout.ALIGN_TOP;
		int align_bottom = RelativeLayout.ALIGN_BOTTOM;
		if( ( relative_orientation == 0 && ui_placement_right ) || ( relative_orientation == 180 && ui_placement_right ) || relative_orientation == 90 || relative_orientation == 270) {
			if( !ui_placement_right && ( relative_orientation == 90 || relative_orientation == 270 ) ) {
				align_top = RelativeLayout.ALIGN_BOTTOM;
				align_bottom = RelativeLayout.ALIGN_TOP;
			}
			View view = findViewById(R.id.settings);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, R.id.preview);
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.settings);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.focus_mode);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.gallery);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.flash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.focus_mode);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.flash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_camera);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_video);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.switch_camera);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, R.id.trash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, R.id.preview);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, R.id.preview);
			layoutParams.addRule(align_top, 0);
			layoutParams.addRule(align_bottom, R.id.preview);
			view.setLayoutParams(layoutParams);
			if( relative_orientation != 0 ) {
				view.setRotation(180.0f);
			}
			else {
				view.setRotation(0.0f);
			}
		}
		else {
			View view = findViewById(R.id.switch_camera);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.switch_camera);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.flash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.switch_video);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.focus_mode);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.flash);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.focus_mode);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.settings);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(align_left, 0);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.gallery);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.settings);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_top, R.id.preview);
			layoutParams.addRule(align_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, R.id.share);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			view.setLayoutParams(layoutParams);
			view.setRotation(ui_rotation);

			view = findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(align_top, 0);
			layoutParams.addRule(align_bottom, R.id.preview);
			view.setLayoutParams(layoutParams);
			if( relative_orientation == 180 ) {
				view.setRotation(180.0f);
			}
			else {
				view.setRotation(0.0f);
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
		this.preview.switchVideo(true);
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
    
    public void clickedSettings(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSettings");

		Intent intent = new Intent(this, MyPreferenceActivity.class);

		intent.putExtra("cameraId", this.preview.getCameraId());
		intent.putExtra("supports_auto_stabilise", this.supports_auto_stabilise);

		putIntentExtra(intent, "color_effects", this.preview.getSupportedColorEffects());
		putIntentExtra(intent, "scene_modes", this.preview.getSupportedSceneModes());
		putIntentExtra(intent, "white_balances", this.preview.getSupportedWhiteBalances());
		putIntentExtra(intent, "exposures", this.preview.getSupportedExposures());

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
			int current_size_index = this.preview.getCurrentPictureSizeIndex();
			intent.putExtra("current_resolution_index", current_size_index);
		}

		this.startActivity(intent);
    }
    
    public void clickedGallery(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedGallery");
		Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		this.startActivity(intent);
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
		    int cameraId = preview.getCameraId();
			if( MyDebug.LOG )
				Log.d(TAG, "save cameraId: " + cameraId);
	    	state.putInt("cameraId", cameraId);
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
        		else if ( volume_keys.equals("volume_zoom") ) {
        			if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
        				this.preview.zoomIn();
        			else
        				this.preview.zoomOut();
                    return true;
        		}
        		// else do nothing
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private File getImageFolder() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String folder_name = sharedPreferences.getString("preference_save_location", "OpenCamera");
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), folder_name);
		if( MyDebug.LOG ) {
			Log.d(TAG, "folder_name: " + folder_name);
			Log.d(TAG, "full path: " + file);
		}
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
	        this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(mediaStorageDir)));
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

		if( MyDebug.LOG ) {
			Log.d(TAG, "getOutputMediaFile returns: " + mediaFile);
		}
        return mediaFile;
    }
    
    public boolean supportsAutoStabilise() {
    	return this.supports_auto_stabilise;
    }

    @SuppressWarnings("deprecation")
	public static long freeMemory() { // return free memory in MB
    	try {
	        StatFs statFs = new StatFs(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
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

    // for testing:
    public Preview getPreview() {
    	return this.preview;
    }
}
