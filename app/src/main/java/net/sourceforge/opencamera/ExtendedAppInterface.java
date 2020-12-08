package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;

/**
 * Extended implementation of ApplicationInterface, adds raw sensor recording layer to the
 * interface.
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";
    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final RawSensorInfo mRawSensorInfo;
    private final SharedPreferences mSharedPreferences;
    private final MainActivity mMainActivity;


    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        this.mMainActivity = mainActivity;
        this.mRawSensorInfo = mainActivity.getRawSensorInfoManager();
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    private boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    private boolean getAccelPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.AccelPreferenceKey, true);
    }

    private boolean getGyroPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GyroPreferenceKey, true);
    }

    /**
     * Retrieves gyroscope and accelerometer sample rate preference and converts it to number
     */
    public int getSensorSampleRatePref(String prefKey) {
        String sensorSampleRateString = mSharedPreferences.getString(
                prefKey,
                String.valueOf(SENSOR_FREQ_DEFAULT_PREF)
        );
        int sensorSampleRate = SENSOR_FREQ_DEFAULT_PREF;
        try {
            if (sensorSampleRateString != null) sensorSampleRate = Integer.parseInt(sensorSampleRateString);
        }
        catch(NumberFormatException exception) {
            if( MyDebug.LOG )
                Log.e(TAG, "Sample rate invalid format: " + sensorSampleRateString);
        }
        return sensorSampleRate;
    }

    @Override
    public void startedVideo() {

        if (MyDebug.LOG) {
            Log.d(TAG, "started video");
        }
        if (getIMURecordingPref() && useCamera2() && (getGyroPref() || getAccelPref())) {
            // Extracting sample rates from shared preferences
            try {
                if (getAccelPref()) {
                    int accelSampleRate = getSensorSampleRatePref(PreferenceKeys.AccelSampleRatePreferenceKey);
                    if (!mRawSensorInfo.enableSensor(accelSampleRate, Sensor.TYPE_ACCELEROMETER)) {
                        mMainActivity.getPreview().showToast(null, "Accelerometer unavailable");
                    }
                }
                if (getGyroPref()) {
                    int gyroSampleRate = getSensorSampleRatePref(PreferenceKeys.GyroSampleRatePreferenceKey);
                    if (!mRawSensorInfo.enableSensor(gyroSampleRate, Sensor.TYPE_GYROSCOPE)) {
                        mMainActivity.getPreview().showToast(null, "Gyroscope unavailable");
                        // TODO: abort recording?
                    }
                }

                mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate, getGyroPref(), getAccelPref());
                // TODO: add message to strings.xml
                mMainActivity.getPreview().showToast(null, "Recording sensor info");
            } catch (NumberFormatException e) {
                if (MyDebug.LOG) {
                    Log.e(TAG, "Failed to retrieve the sample rate preference value");
                    e.printStackTrace();
                }
            }
        }

        super.startedVideo();
    }

    @Override
    public void stoppedVideo(int video_method, Uri uri, String filename) {
        if (MyDebug.LOG) {
            Log.d(TAG, "stopped video");
        }
        if (mRawSensorInfo.isRecording()) {
            mRawSensorInfo.stopRecording();
            mRawSensorInfo.disableSensors();

            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast(null, "Finished recording sensor info");
        }

        super.stoppedVideo(video_method, uri, filename);
    }
}
