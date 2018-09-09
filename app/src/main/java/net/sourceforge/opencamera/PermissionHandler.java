package net.sourceforge.opencamera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

/**	Android 6+ permission handling:
 */
public class PermissionHandler {
	private static final String TAG = "PermissionHandler";

	private final MainActivity main_activity;

	final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
	final private int MY_PERMISSIONS_REQUEST_STORAGE = 1;
	final private int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2;
	final private int MY_PERMISSIONS_REQUEST_LOCATION = 3;

	PermissionHandler(MainActivity main_activity) {
	    this.main_activity = main_activity;
    }

	/** Show a "rationale" to the user for needing a particular permission, then request that permission again
	 *  once they close the dialog.
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void showRequestPermissionRationale(final int permission_code) {
		if( MyDebug.LOG )
			Log.d(TAG, "showRequestPermissionRational: " + permission_code);
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		boolean ok = true;
		String [] permissions = null;
		int message_id = 0;
		switch (permission_code) {
			case MY_PERMISSIONS_REQUEST_CAMERA:
				if (MyDebug.LOG)
					Log.d(TAG, "display rationale for camera permission");
				permissions = new String[]{Manifest.permission.CAMERA};
				message_id = R.string.permission_rationale_camera;
				break;
			case MY_PERMISSIONS_REQUEST_STORAGE:
				if (MyDebug.LOG)
					Log.d(TAG, "display rationale for storage permission");
				permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
				message_id = R.string.permission_rationale_storage;
				break;
			case MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
				if (MyDebug.LOG)
					Log.d(TAG, "display rationale for record audio permission");
				permissions = new String[]{Manifest.permission.RECORD_AUDIO};
				message_id = R.string.permission_rationale_record_audio;
				break;
			case MY_PERMISSIONS_REQUEST_LOCATION:
				if (MyDebug.LOG)
					Log.d(TAG, "display rationale for location permission");
				permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
				message_id = R.string.permission_rationale_location;
				break;
			default:
				if (MyDebug.LOG)
					Log.e(TAG, "showRequestPermissionRational unknown permission_code: " + permission_code);
				ok = false;
				break;
		}

		if( ok ) {
			final String [] permissions_f = permissions;
			new AlertDialog.Builder(main_activity)
			.setTitle(R.string.permission_rationale_title)
			.setMessage(message_id)
			.setIcon(android.R.drawable.ic_dialog_alert)
        	.setPositiveButton(android.R.string.ok, null)
			.setOnDismissListener(new DialogInterface.OnDismissListener() {
				public void onDismiss(DialogInterface dialog) {
					if( MyDebug.LOG )
						Log.d(TAG, "requesting permission...");
					ActivityCompat.requestPermissions(main_activity, permissions_f, permission_code);
				}
			}).show();
		}
	}

	void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		if( ActivityCompat.shouldShowRequestPermissionRationale(main_activity, Manifest.permission.CAMERA) ) {
	        // Show an explanation to the user *asynchronously* -- don't block
	        // this thread waiting for the user's response! After the user
	        // sees the explanation, try again to request the permission.
	    	showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
	    }
	    else {
	    	// Can go ahead and request the permission
			if( MyDebug.LOG )
				Log.d(TAG, "requesting camera permission...");
	        ActivityCompat.requestPermissions(main_activity, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

	void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		if( ActivityCompat.shouldShowRequestPermissionRationale(main_activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ) {
	        // Show an explanation to the user *asynchronously* -- don't block
	        // this thread waiting for the user's response! After the user
	        // sees the explanation, try again to request the permission.
	    	showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_STORAGE);
	    }
	    else {
	    	// Can go ahead and request the permission
			if( MyDebug.LOG )
				Log.d(TAG, "requesting storage permission...");
	        ActivityCompat.requestPermissions(main_activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        }
    }

	void requestRecordAudioPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestRecordAudioPermission");
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		if( ActivityCompat.shouldShowRequestPermissionRationale(main_activity, Manifest.permission.RECORD_AUDIO) ) {
	        // Show an explanation to the user *asynchronously* -- don't block
	        // this thread waiting for the user's response! After the user
	        // sees the explanation, try again to request the permission.
	    	showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
	    }
	    else {
	    	// Can go ahead and request the permission
			if( MyDebug.LOG )
				Log.d(TAG, "requesting record audio permission...");
	        ActivityCompat.requestPermissions(main_activity, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

	void requestLocationPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestLocationPermission");
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		if( ActivityCompat.shouldShowRequestPermissionRationale(main_activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
				ActivityCompat.shouldShowRequestPermissionRationale(main_activity, Manifest.permission.ACCESS_COARSE_LOCATION) ) {
	        // Show an explanation to the user *asynchronously* -- don't block
	        // this thread waiting for the user's response! After the user
	        // sees the explanation, try again to request the permission.
	    	showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_LOCATION);
	    }
	    else {
	    	// Can go ahead and request the permission
			if( MyDebug.LOG )
				Log.d(TAG, "requesting loacation permissions...");
	        ActivityCompat.requestPermissions(main_activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

	public void onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
		if( MyDebug.LOG )
			Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
			return;
		}

		switch( requestCode ) {
	        case MY_PERMISSIONS_REQUEST_CAMERA:
	        {
	            // If request is cancelled, the result arrays are empty.
	            if( grantResults.length > 0
	                && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
	                // permission was granted, yay! Do the
	                // contacts-related task you need to do.
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "camera permission granted");
	            	main_activity.getPreview().retryOpenCamera();
	            }
	            else {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "camera permission denied");
	                // permission denied, boo! Disable the
	                // functionality that depends on this permission.
	            	// Open Camera doesn't need to do anything: the camera will remain closed
	            }
	            return;
	        }
	        case MY_PERMISSIONS_REQUEST_STORAGE:
	        {
	            // If request is cancelled, the result arrays are empty.
	            if( grantResults.length > 0
	                && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
	                // permission was granted, yay! Do the
	                // contacts-related task you need to do.
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "storage permission granted");
	            	main_activity.getPreview().retryOpenCamera();
	            }
	            else {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "storage permission denied");
	                // permission denied, boo! Disable the
	                // functionality that depends on this permission.
	            	// Open Camera doesn't need to do anything: the camera will remain closed
	            }
	            return;
	        }
	        case MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
	        {
	            // If request is cancelled, the result arrays are empty.
	            if( grantResults.length > 0
	                && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
	                // permission was granted, yay! Do the
	                // contacts-related task you need to do.
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "record audio permission granted");
	        		// no need to do anything
	            }
	            else {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "record audio permission denied");
	                // permission denied, boo! Disable the
	                // functionality that depends on this permission.
	        		// no need to do anything
	        		// note that we don't turn off record audio option, as user may then record video not realising audio won't be recorded - best to be explicit each time
	            }
	            return;
	        }
	        case MY_PERMISSIONS_REQUEST_LOCATION:
	        {
	            // If request is cancelled, the result arrays are empty.
	            if( grantResults.length > 0
	                && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
	                // permission was granted, yay! Do the
	                // contacts-related task you need to do.
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "location permission granted");
	                main_activity.initLocation();
	            }
	            else {
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "location permission denied");
	                // permission denied, boo! Disable the
	                // functionality that depends on this permission.
	        		// for location, seems best to turn the option back off
	        		if( MyDebug.LOG )
	        			Log.d(TAG, "location permission not available, so switch location off");
		    		main_activity.getPreview().showToast(null, R.string.permission_location_not_available);
					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main_activity);
					SharedPreferences.Editor editor = settings.edit();
					editor.putBoolean(PreferenceKeys.LocationPreferenceKey, false);
					editor.apply();
	            }
	            return;
	        }
	        default:
	        {
	    		if( MyDebug.LOG )
	    			Log.e(TAG, "unknown requestCode " + requestCode);
	        }
	    }
	}
}
