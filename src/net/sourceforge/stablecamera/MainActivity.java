package net.sourceforge.stablecamera;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

class MyDebug {
	static final boolean LOG = true;
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager;
	private Sensor mSensorAccelerometer;
	private Preview preview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

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

		preview = new Preview(this);
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		
		/*preview.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				takePicture();
			}
		});*/
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
    }

    public void clickedFlash(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedFlash");
    	this.preview.cycleFlash();
    }
    
    public void clickedSettings(View view) {
		if( MyDebug.LOG )
			Log.d(TAG, "clickedSettings");
    }

    private void takePicture() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
    	this.preview.takePicture();
    }
}
