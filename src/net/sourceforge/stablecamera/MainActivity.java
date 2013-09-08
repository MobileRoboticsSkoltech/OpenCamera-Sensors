package net.sourceforge.stablecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	SensorManager mSensorManager;
	Sensor mSensorAccelerometer;
	Preview preview;
	boolean is_taking_photo = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        // keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
            Log.d(TAG, "found accelerometer");
			mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}
		else {
            Log.d(TAG, "no support for accelerometer");
		}

		preview = new Preview(this);
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		
		preview.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				takePicture();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mSensorManager.registerListener(preview, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mSensorManager.unregisterListener(preview);
    }

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "StableCamera");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if( !mediaStorageDir.exists() ) {
            if( !mediaStorageDir.mkdirs() ) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
	        sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(mediaStorageDir)));
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

    private void takePicture() {
    	Log.d(TAG, "takePicture");
    	if( is_taking_photo ) {
        	Log.d(TAG, "already taking a photo");
    		return;
    	}
        PictureCallback jpegPictureCallback = new PictureCallback() {
    	    public void onPictureTaken(byte[] data, Camera cam) {
    	    	// n.b., this is automatically run in a different thread
    	    	Log.d(TAG, "onPictureTaken");
    			Bitmap bitmap = null;
    			if( preview.hasLevelAngle() )
    			{
    				BitmapFactory.Options options = new BitmapFactory.Options();
    				//options.inMutable = true;
        			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        			int width = bitmap.getWidth();
        			int height = bitmap.getHeight();
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
        		    matrix.postRotate((float)preview.getLevelAngle());
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
    	        }
    	        catch(FileNotFoundException e) {
    	            Log.e(TAG, "File not found: " + e.getMessage());
    	            e.getStackTrace();
    	    	    Toast.makeText(getApplicationContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
    	        }
    	        catch(IOException e) {
    	            Log.e(TAG, "I/O error writing file: " + e.getMessage());
    	            e.getStackTrace();
    	    	    Toast.makeText(getApplicationContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
    	        }
	            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(picFile)));
    	    	Log.d(TAG, "onPictureTaken saved photo");

    	    	// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
    	    	// (otherwise this can fail, at least on Nexus 7)
	            try {
	            	preview.camera.startPreview();
	            	is_taking_photo = false;
	    	    	Log.d(TAG, "onPictureTaken started preview");
	            }
	            catch(Exception e) {
	            	Log.d(TAG, "Error starting camera preview after taking photo: " + e.getMessage());
	            	e.printStackTrace();
	            }
    	    }
    	};
    	try {
    		preview.camera.takePicture(null, null, jpegPictureCallback);
    	    Toast.makeText(getApplicationContext(), "Taking a photo...", Toast.LENGTH_SHORT).show();
    		is_taking_photo = true;
    	}
        catch(Exception e) {
            Log.d(TAG, "Camera takePicture failed: " + e.getMessage());
            e.printStackTrace();
    	    Toast.makeText(getApplicationContext(), "Failed to take picture", Toast.LENGTH_SHORT).show();
        }
    	Log.d(TAG, "takePicture exit");
    }
}
