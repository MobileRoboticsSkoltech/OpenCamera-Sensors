package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceHandler {
    private static final String TAG = "PreferenceHandler";

    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final SharedPreferences mSharedPreferences;

    PreferenceHandler(MainActivity mainActivity) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    public boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    public boolean getRemoteRecControlPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.RemoteRecControlPreferenceKey, false);
    }

    public boolean getSaveFramesPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.saveFramesPreferenceKey, false);
    }

    public boolean getEnableRecSyncPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, false);
    }

    public boolean getSyncIsoPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncIsoPreferenceKey, false);
    }

    public boolean getSyncWbPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncWbPreferenceKey, false);
    }

    public boolean getSyncFlashPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncFlashPreferenceKey, false);
    }

    public boolean getSyncFormatPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncFormatPreferenceKey, false);
    }

    public String getVideoFormatPref() {
        return mSharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, PreferenceKeys.VideoOutputFormatDefaultPreferenceKey);
    }

    public String getImageFormatPref() {
        return mSharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, PreferenceKeys.ImageFormatJpegPreferenceKey);
    }

    /**
     * Retrieves gyroscope and accelerometer sample rate preference and converts it to number.
     */
    public int getSensorSampleRatePref(String prefKey) {
        String sensorSampleRateString = mSharedPreferences.getString(
                prefKey,
                String.valueOf(SENSOR_FREQ_DEFAULT_PREF)
        );
        int sensorSampleRate = SENSOR_FREQ_DEFAULT_PREF;
        try {
            if (sensorSampleRateString != null)
                sensorSampleRate = Integer.parseInt(sensorSampleRateString);
        } catch (NumberFormatException exception) {
            if (MyDebug.LOG)
                Log.e(TAG, "Sample rate invalid format: " + sensorSampleRateString);
        }
        return sensorSampleRate;
    }

    boolean getAccelPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.AccelPreferenceKey, true);
    }

    boolean getGyroPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GyroPreferenceKey, true);
    }

    boolean getMagneticPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.MagnetometerPrefKey, true);
    }

    void setEnableRecSyncPref(boolean value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, value);
        editor.apply();
    }
}
