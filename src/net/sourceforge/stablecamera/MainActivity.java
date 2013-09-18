package net.sourceforge.stablecamera;

import java.util.List;

import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

class MyDebug {
	static final boolean LOG = true;
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Preview preview = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		if( MyDebug.LOG ) {
			ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
			Log.d(TAG, "standard max memory = " + activityManager.getMemoryClass() + "MB");
			Log.d(TAG, "large max memory = " + activityManager.getLargeMemoryClass() + "MB");
		}

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

		preview = new Preview(this, savedInstanceState);
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		
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

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String ui_placement = sharedPreferences.getString("preference_ui_placement", "ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		View top_view = findViewById(R.id.switch_camera);
		RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)top_view.getLayoutParams();
		if( ui_placement.equals("ui_left") ) {
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		}
		else {
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		}
		top_view.setLayoutParams(layoutParams);
    }

    @Override
    protected void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
        super.onPause();
        mSensorManager.unregisterListener(preview);
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

    public void clickedTrash(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedTrash");
    	this.preview.clickedTrash();
    }

    private void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
    	this.preview.takePicture();
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
}
