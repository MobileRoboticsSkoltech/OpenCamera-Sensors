package net.sourceforge.opencamera;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

/** Handles magnetic sensor.
 */
class MagneticSensor {
    private static final String TAG = "MagneticSensor";

    private final MainActivity main_activity;

    private Sensor mSensorMagnetic;

    private int magnetic_accuracy = -1;
    private AlertDialog magnetic_accuracy_dialog;

    private boolean magneticListenerIsRegistered;

    MagneticSensor(final MainActivity main_activity) {
        this.main_activity = main_activity;
    }

    void initSensor(final SensorManager mSensorManager) {
        if( MyDebug.LOG )
            Log.d(TAG, "initSensor");
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "found magnetic sensor");
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no support for magnetic sensor");
        }
    }


    /** Registers the magnetic sensor, only if it's required (by user preferences), and hasn't already
     *  been registered.
     *  If the magnetic sensor was previously registered, but is no longer required by user preferences,
     *  then it is unregistered.
     */
    void registerMagneticListener(final SensorManager mSensorManager) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        if( !magneticListenerIsRegistered ) {
            if( needsMagneticSensor(sharedPreferences) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "register magneticListener");
                mSensorManager.registerListener(magneticListener, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
                magneticListenerIsRegistered = true;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "don't register magneticListener as not needed");
            }
        }
        else {
            if( needsMagneticSensor(sharedPreferences) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "magneticListener already registered");
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "magneticListener already registered but no longer needed");
                mSensorManager.unregisterListener(magneticListener);
                magneticListenerIsRegistered = false;
            }
        }
    }

    /** Unregisters the magnetic sensor, if it was registered.
     */
    void unregisterMagneticListener(final SensorManager mSensorManager) {
        if( magneticListenerIsRegistered ) {
            if( MyDebug.LOG )
                Log.d(TAG, "unregister magneticListener");
            mSensorManager.unregisterListener(magneticListener);
            magneticListenerIsRegistered = false;
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "magneticListener wasn't registered");
        }
    }

    private final SensorEventListener magneticListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if( MyDebug.LOG )
                Log.d(TAG, "magneticListener.onAccuracyChanged: " + accuracy);
            //accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW; // test
            MagneticSensor.this.magnetic_accuracy = accuracy;
            setMagneticAccuracyDialogText(); // update if a dialog is already open for this
            checkMagneticAccuracy();

            // test accuracy changing after dialog opened:
			/*Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				public void run() {
					MainActivity.this.magnetic_accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
					setMagneticAccuracyDialogText();
					checkMagneticAccuracy();
				}
			}, 5000);*/
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            main_activity.getPreview().onMagneticSensorChanged(event);
        }
    };

    private void setMagneticAccuracyDialogText() {
        if( MyDebug.LOG )
            Log.d(TAG, "setMagneticAccuracyDialogText()");
        if( magnetic_accuracy_dialog != null ) {
            String message = main_activity.getResources().getString(R.string.magnetic_accuracy_info) + " ";
            switch( magnetic_accuracy ) {
                case SensorManager.SENSOR_STATUS_UNRELIABLE:
                    message += main_activity.getResources().getString(R.string.accuracy_unreliable);
                    break;
                case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                    message += main_activity.getResources().getString(R.string.accuracy_low);
                    break;
                case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                    message += main_activity.getResources().getString(R.string.accuracy_medium);
                    break;
                case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                    message += main_activity.getResources().getString(R.string.accuracy_high);
                    break;
                default:
                    message += main_activity.getResources().getString(R.string.accuracy_unknown);
                    break;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "message: " + message);
            magnetic_accuracy_dialog.setMessage(message);
        }
    }

    private boolean shown_magnetic_accuracy_dialog = false; // whether the dialog for poor magnetic accuracy has been shown since application start

    /** Checks whether the user should be informed about poor magnetic sensor accuracy, and shows
     *  the dialog if so.
     */
    void checkMagneticAccuracy() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkMagneticAccuracy(): " + magnetic_accuracy);
        if( magnetic_accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE && magnetic_accuracy != SensorManager.SENSOR_STATUS_ACCURACY_LOW ) {
            if( MyDebug.LOG )
                Log.d(TAG, "accuracy is good enough (or accuracy not yet known)");
        }
        else if( shown_magnetic_accuracy_dialog ) {
            // if we've shown the dialog since application start, then don't show again even if the user didn't click to not show again
            if( MyDebug.LOG )
                Log.d(TAG, "already shown_magnetic_accuracy_dialog");
        }
        else if( main_activity.getPreview().isTakingPhotoOrOnTimer() || main_activity.getPreview().isVideoRecording() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "don't disturb whilst taking photo, on timer, or recording video");
        }
        else if( main_activity.isCameraInBackground() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "don't show magnetic accuracy dialog due to camera in background");
            // don't want to show dialog if another is open, or in settings, etc
        }
        else {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            if( !needsMagneticSensor(sharedPreferences) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "don't need magnetic sensor");
                // note, we shouldn't set shown_magnetic_accuracy_dialog to true here, otherwise we won't pick up if the user enables one of these options
            }
            else if( sharedPreferences.contains(PreferenceKeys.MagneticAccuracyPreferenceKey) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "user selected to no longer show the dialog");
                shown_magnetic_accuracy_dialog = true; // also set this flag, so future calls to checkMagneticAccuracy() will exit without needing to get/read the SharedPreferences
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "show dialog for magnetic accuracy");
                shown_magnetic_accuracy_dialog = true;
                magnetic_accuracy_dialog = main_activity.getMainUI().showInfoDialog(R.string.magnetic_accuracy_title, 0, PreferenceKeys.MagneticAccuracyPreferenceKey);
                setMagneticAccuracyDialogText();
            }
        }
    }

    /* Whether the user preferences indicate that we need the magnetic sensor to be enabled.
     */
    private boolean needsMagneticSensor(SharedPreferences sharedPreferences) {
        if( main_activity.getApplicationInterface().getGeodirectionPref() ||
                sharedPreferences.getBoolean(PreferenceKeys.AddYPRToComments, false) ||
                sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionLinesPreferenceKey, false) ||
                sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionPreferenceKey, false) ) {
            return true;
        }
        return false;
    }

    int getMagneticAccuracy() {
        return this.magnetic_accuracy;
    }

    void clearDialog() {
        this.magnetic_accuracy_dialog = null;
    }
}
