package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.preview.VideoProfile;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoFrameInfo;

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
        this.mRawSensorInfo = new RawSensorInfo(mainActivity);
        this.mMainActivity = mainActivity;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    public boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    /**
     * Retrieves gyroscope and accelerometer sample rate preference and converts it to number
     */
    public int getSensorSampleRatePref(String prefKey) {
        String sensorSampleRateString = mSharedPreferences.getString(
                prefKey,
                String.valueOf(SENSOR_FREQ_DEFAULT_PREF)
        );
        int sensorSampleRate;
        try {
            sensorSampleRate = Integer.parseInt(sensorSampleRateString);

        }
        catch(NumberFormatException exception) {
            if( MyDebug.LOG )
                Log.e(TAG, "Sample rate invalid format: " + sensorSampleRateString);
            sensorSampleRate = SENSOR_FREQ_DEFAULT_PREF;
        }
        return sensorSampleRate;
    }

    public boolean getSaveFramesPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.saveFramesPreferenceKey, false);
    }

    @Override
    public void startingVideo() {
        if (MyDebug.LOG) {
            Log.d(TAG, "starting video");
        }
        if (getIMURecordingPref() && useCamera2()) {
            // Extracting sample rates from shared preferences
            try {
                int accelSampleRate = getSensorSampleRatePref(PreferenceKeys.AccelSampleRatePreferenceKey);
                int gyroSampleRate = getSensorSampleRatePref(PreferenceKeys.GyroSampleRatePreferenceKey);
                mRawSensorInfo.enableSensors(accelSampleRate, gyroSampleRate);
                mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate);
                // TODO: add message to strings.xml
                mMainActivity.getPreview().showToast(null, "Starting video with IMU recording");
            } catch (NumberFormatException e) {
                if (MyDebug.LOG) {
                    Log.e(TAG, "Failed to retrieve the sample rate preference value");
                    e.printStackTrace();
                }
            }
        } else if (getIMURecordingPref()) {
            mMainActivity.getPreview().showToast(null, "Not using Camera2API! Can't record in sync with IMU");
        }

        super.startingVideo();
    }

    @Override
    public void stoppingVideo() {
        if (MyDebug.LOG) {
            Log.d(TAG, "stopping video");
        }
        if (mRawSensorInfo.isRecording()) {
            mRawSensorInfo.stopRecording();
            mRawSensorInfo.disableSensors();

            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast(null, "Finished video with IMU recording");
        }

        super.stoppingVideo();
    }

    public void onFrameInfoRecordingFailed() {
        mMainActivity.getPreview().showToast(null, "Couldn't write frame timestamps");
    }
}
