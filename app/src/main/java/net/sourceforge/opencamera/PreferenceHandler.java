package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceHandler {
    private static final String TAG = "PreferenceHandler";

    private final MainActivity mMainActivity;
    private final SharedPreferences mSharedPreferences;

    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    PreferenceHandler(MainActivity mainActivity) {
        mMainActivity = mainActivity;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
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

    public String getVideoFormatPref(String defValue) {
        return mSharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, defValue);
    }

    public String getImageFormatPref(String defValue) {
        return mSharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, defValue);
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

    void editEnableRecSync() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, false);
        editor.apply();
    }
}
