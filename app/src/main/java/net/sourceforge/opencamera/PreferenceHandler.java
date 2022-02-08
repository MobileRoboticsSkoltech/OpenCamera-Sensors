package net.sourceforge.opencamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceHandler {
    private static final String TAG = "PreferenceHandler";

    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final SharedPreferences mSharedPreferences;

    PreferenceHandler(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isIMURecordingEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    public boolean isRemoteRecControlEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.RemoteRecControlPreferenceKey, false);
    }

    public boolean isSaveFramesEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.saveFramesPreferenceKey, false);
    }

    public boolean isEnableRecSyncEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, false);
    }

    public boolean isEnablePhaseAlignmentEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.EnablePhaseAlignmentPreferenceKey, false);
    }

    public boolean isSyncIsoEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncIsoPreferenceKey, false);
    }

    public boolean isSyncWbEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncWbPreferenceKey, false);
    }

    public boolean isSyncFlashEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncFlashPreferenceKey, false);
    }

    public boolean isSyncFormatEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.SyncFormatPreferenceKey, false);
    }

    public boolean isAccelEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.AccelPreferenceKey, true);
    }

    public boolean isGyroEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GyroPreferenceKey, true);
    }

    public boolean isMagneticEnabled() {
        return mSharedPreferences.getBoolean(PreferenceKeys.MagnetometerPrefKey, true);
    }

    public String getVideoFormat() {
        return mSharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, PreferenceKeys.VideoOutputFormatDefaultPreferenceKey);
    }

    public String getImageFormat() {
        return mSharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, PreferenceKeys.ImageFormatJpegPreferenceKey);
    }

    /**
     * Retrieves gyroscope and accelerometer sample rate preference and converts it to number.
     */
    public int getSensorSampleRate(String prefKey) {
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

    void setEnableRecSync(boolean value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, value);
        editor.apply();
    }
}
