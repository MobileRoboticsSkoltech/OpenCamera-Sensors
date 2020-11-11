package net.sourceforge.opencamera;

import android.content.SharedPreferences;
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
        this.mRawSensorInfo = new RawSensorInfo(mainActivity);
        this.mMainActivity = mainActivity;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    private boolean getIMURecordingPref() {
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

    @Override
    public void startedVideo() {

        if (MyDebug.LOG) {
            Log.d(TAG, "started video");
        }
        if (getIMURecordingPref()) {
            // Extracting sample rates from shared preferences
            try {
                int accelSampleRate = getSensorSampleRatePref(PreferenceKeys.AccelSampleRatePreferenceKey);
                int gyroSampleRate = getSensorSampleRatePref(PreferenceKeys.GyroSampleRatePreferenceKey);
                mRawSensorInfo.enableSensors(accelSampleRate, gyroSampleRate);
                mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate);
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
