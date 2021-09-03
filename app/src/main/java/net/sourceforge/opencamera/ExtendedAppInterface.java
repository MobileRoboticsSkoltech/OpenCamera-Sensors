package net.sourceforge.opencamera;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.RequiresApi;

import com.googleresearch.capturesync.PhaseAlignController;
import com.googleresearch.capturesync.SoftwareSyncController;
import com.googleresearch.capturesync.softwaresync.phasealign.PeriodCalculator;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseConfig;

import net.sourceforge.opencamera.cameracontroller.YuvImageUtils;
import net.sourceforge.opencamera.recsync.SoftwareSyncUtils;
import net.sourceforge.opencamera.sensorlogging.FlashController;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoFrameInfo;
import net.sourceforge.opencamera.sensorlogging.VideoPhaseInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Extended implementation of ApplicationInterface, adds raw sensor recording layer and RecSync
 * controls to the interface.
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";
    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final RawSensorInfo mRawSensorInfo;
    private final FlashController mFlashController;
    private final SharedPreferences mSharedPreferences;
    private final MainActivity mMainActivity;
    private final YuvImageUtils mYuvUtils;
    private final PhaseAlignController mPhaseAlignController;
    private final PeriodCalculator mPeriodCalculator;

    private SoftwareSyncController mSoftwareSyncController;
    private SoftwareSyncUtils mSoftwareSyncUtils;
    private BroadcastReceiver mConnectionStatusChecker = null;

    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        mRawSensorInfo = mainActivity.getRawSensorInfoManager();
        mMainActivity = mainActivity;
        mFlashController = new FlashController(mainActivity);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        // We create it only once here (not during the video) as it is a costly operation
        // (instantiates RenderScript object)
        mYuvUtils = new YuvImageUtils(mainActivity);
        mPhaseAlignController = new PhaseAlignController(getDefaultPhaseConfig(), mainActivity);
        mPeriodCalculator = new PeriodCalculator(mainActivity);
    }

    private PhaseConfig getDefaultPhaseConfig() {
        PhaseConfig phaseConfig;
        try {
            InputStream inputStream = mMainActivity.getResources().openRawResource(R.raw.default_phaseconfig);
            byte[] buffer = new byte[inputStream.available()];
            // noinspection ResultOfMethodCallIgnored
            inputStream.read(buffer);
            inputStream.close();
            JSONObject json = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            phaseConfig = PhaseConfig.parseFromJSON(json);
        } catch (JSONException | IOException e) {
            throw new IllegalArgumentException("Error parsing default phase config file: ", e);
        }
        return phaseConfig;
    }

    /**
     * Create {@link VideoFrameInfo} with the current preferences.
     *
     * @return the created {@link VideoFrameInfo}.
     * @throws IOException if unable to create files for timestamps recording.
     */
    public VideoFrameInfo setupFrameInfo() throws IOException {
        return new VideoFrameInfo(
                getLastVideoDate(),
                mMainActivity,
                getIMURecordingPref(),
                getEnableRecSyncPref(),
                getIMURecordingPref() && getSaveFramesPref(),
                getVideoPhaseInfoReporter()
        );
    }

    public FlashController getFlashController() {
        return mFlashController;
    }

    public SoftwareSyncController getSoftwareSyncController() {
        return mSoftwareSyncController;
    }

    public SoftwareSyncUtils getSoftwareSyncUtils() {
        return mSoftwareSyncUtils;
    }

    public YuvImageUtils getYuvUtils() {
        return mYuvUtils;
    }

    public BlockingQueue<VideoPhaseInfo> getVideoPhaseInfoReporter() {
        return mMainActivity.getPreview().getVideoPhaseInfoReporter();
    }

    /**
     * Provides the current leader-client status of RecSync.
     *
     * @return a string describing the current status.
     */
    public String getSyncStatusText() {
        return mSoftwareSyncController.getSyncStatus();
    }

    /**
     * Provides the current phase error from RecSync.
     *
     * @return a {@link Pair} of a string with its color describing the current phase error.
     */
    public Pair<String, Integer> getPhaseErrorText() {
        return mPhaseAlignController.getPhaseError();
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

    private boolean getAccelPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.AccelPreferenceKey, true);
    }

    private boolean getGyroPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GyroPreferenceKey, true);
    }

    private boolean getMagneticPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.MagnetometerPrefKey, true);
    }

    @Override
    void onDestroy() {
        mYuvUtils.close();
        stopSoftwareSync();
        super.onDestroy();
    }

    public void cameraOpened() {
        // Should be at the end of this method as it may close the camera
        if (isSoftwareSyncRunning()) {
            final Runnable applySettingsRunnable = mSoftwareSyncUtils.getApplySettingsRunnable();
            if (applySettingsRunnable != null) {
                applySettingsRunnable.run();
            }
        }
    }

    @Override
    public void cameraClosed() {
        mPhaseAlignController.stopAlign();
        super.cameraClosed();
    }

    @Override
    public void startingVideo() {
        if (MyDebug.LOG) {
            Log.d(TAG, "starting video");
        }
        if (getIMURecordingPref() && useCamera2() && (getGyroPref() || getAccelPref() || getMagneticPref())) {
            // Extracting sample rates from shared preferences
            try {
                mMainActivity.getPreview().showToast("Starting video with IMU recording...", true);
                startImu(getAccelPref(), getGyroPref(), getMagneticPref(), mLastVideoDate);
                // TODO: add message to strings.xml
            } catch (NumberFormatException e) {
                if (MyDebug.LOG) {
                    Log.e(TAG, "Failed to retrieve the sample rate preference value");
                    e.printStackTrace();
                }
            }
        } else if (getIMURecordingPref() && !useCamera2()) {
            mMainActivity.getPreview().showToast(null, "Not using Camera2API! Can't record in sync with IMU");
            mMainActivity.getPreview().stopVideo(false);
        } else if (getIMURecordingPref() && !(getGyroPref() || getMagneticPref() || getAccelPref())) {
            mMainActivity.getPreview().showToast(null, "Requested IMU recording but no sensors were enabled");
            mMainActivity.getPreview().stopVideo(false);
        }

        if (getVideoFlashPref()) {
            try {
                mFlashController.startRecording(mLastVideoDate);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start flash controller");
                e.printStackTrace();
            }
        }

        super.startingVideo();

        if (isSoftwareSyncRunning() && !mSoftwareSyncController.isLeader()) {
            ImageButton view = mMainActivity.findViewById(R.id.take_photo);
            view.setImageResource(R.drawable.ic_empty);
            view.setContentDescription(getContext().getResources().getString(R.string.do_nothing));
        }
    }

    @Override
    public void startedVideo() {
        super.startedVideo();
        if (isSoftwareSyncRunning()) {
            View pauseVideoButton = mMainActivity.findViewById(R.id.pause_video);
            pauseVideoButton.setVisibility(View.GONE);
            View takePhotoVideoButton = mMainActivity.findViewById(R.id.take_photo_when_video_recording);
            takePhotoVideoButton.setVisibility(View.GONE);
        }
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
            mMainActivity.getPreview().showToast("Stopping video with IMU recording...", true);
        }

        if (mFlashController.isRecording()) {
            mFlashController.stopRecording();
        }

        super.stoppingVideo();
    }

    public void onFrameInfoRecordingFailed() {
        mMainActivity.getPreview().showToast(null, "Couldn't write frame timestamps");
    }

    public void startImu(boolean wantAccel, boolean wantGyro, boolean wantMagnetic, Date currentDate) {
        if (wantAccel) {
            int accelSampleRate = getSensorSampleRatePref(PreferenceKeys.AccelSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_ACCELEROMETER, accelSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Accelerometer unavailable");
            }
        }
        if (wantGyro) {
            int gyroSampleRate = getSensorSampleRatePref(PreferenceKeys.GyroSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_GYROSCOPE, gyroSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Gyroscope unavailable");
            }
        }
        if (wantMagnetic) {
            int magneticSampleRate = getSensorSampleRatePref(PreferenceKeys.MagneticSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_MAGNETIC_FIELD, magneticSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Magnetometer unavailable");
            }
        }

        //mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate, get Pref(), getAccelPref())
        Map<Integer, Boolean> wantSensorRecordingMap = new HashMap<>();
        wantSensorRecordingMap.put(Sensor.TYPE_ACCELEROMETER, getAccelPref());
        wantSensorRecordingMap.put(Sensor.TYPE_GYROSCOPE, getGyroPref());
        wantSensorRecordingMap.put(Sensor.TYPE_MAGNETIC_FIELD, getMagneticPref());
        mRawSensorInfo.startRecording(mMainActivity, currentDate, wantSensorRecordingMap);
    }

    /**
     * Starts RecSync by instantiating a {@link SoftwareSyncController}. If one is already running
     * then it is closed and a new one is initialized.
     * <p>
     * Starts pick Wi-Fi activity if neither Wi-Fi nor hotspot is enabled.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void startSoftwareSync() {
        // Start softwaresync, close it first if it's already running.
        if (isSoftwareSyncRunning()) {
            stopSoftwareSync();
        }

        try {
            mSoftwareSyncController =
                    new SoftwareSyncController(mMainActivity, mPhaseAlignController, mPeriodCalculator);
        } catch (IllegalStateException e) {
            // Wi-Fi and hotspot are disabled.
            Log.e(TAG, "Couldn't start SoftwareSync: Wi-Fi and hotspot are disabled.");
            disableRecSyncSetting();
            showSimpleAlert("Cannot start RecSync", "Enable either Wi-Fi or hotspot for RecSync to be able to start.");
            return;
        }

        // Listen for wifi or hotpot status changes.
        IntentFilter intentFilter = new IntentFilter();
        if (mSoftwareSyncController.isLeader()) {
            // Need to get WIFI_AP_STATE_CHANGED_ACTION hidden in WiFiManager.
            String action;
            WifiManager wifiManager = (WifiManager) mMainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            try {
                Field field = wifiManager.getClass().getDeclaredField("WIFI_AP_STATE_CHANGED_ACTION");
                action = (String) field.get(wifiManager);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Cannot get WIFI_AP_STATE_CHANGED_ACTION value from WifiManager.", e);
            }
            intentFilter.addAction(action);
            mConnectionStatusChecker = new HotspotStatusChecker();
        } else {
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mConnectionStatusChecker = new WifiStatusChecker();
        }
        mMainActivity.registerReceiver(mConnectionStatusChecker, intentFilter);

        mSoftwareSyncUtils = new SoftwareSyncUtils(mMainActivity);
    }

    private class HotspotStatusChecker extends BroadcastReceiver {
        private static final String TAG = "HotspotStatusChecker";

        private final int WIFI_AP_STATE_ENABLED;

        HotspotStatusChecker() {
            // Need to get WIFI_AP_STATE_ENABLED hidden in WifiManager
            WifiManager wifiManager = (WifiManager) mMainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            try {
                Field field = wifiManager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
                WIFI_AP_STATE_ENABLED = (int) field.get(wifiManager);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Cannot get WIFI_AP_STATE_ENABLED value from WifiManager.", e);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            if (state != WIFI_AP_STATE_ENABLED) {
                Log.e(TAG, "Hotspot has been stopped, disabling RecSync.");
                disableRecSyncSetting();
                showSimpleAlert("Hotspot was stopped", "Stopping RecSync. Enable either Wi-Fi or hotspot and re-enable RecSync in the settings.");
            }
        }
    }

    private class WifiStatusChecker extends BroadcastReceiver {
        private static final String TAG = "WifiStatusChecker";

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo == null || networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                Log.e(TAG, "Wi-Fi connection has been closed, disabling RecSync.");
                disableRecSyncSetting();
                showSimpleAlert("Wi-Fi was stopped", "Stopping RecSync. Enable either Wi-Fi or hotspot and re-enable RecSync in the settings.");
            }
        }
    }

    private void disableRecSyncSetting() {
        if (!mMainActivity.isCameraInBackground()) {
            mMainActivity.openSettings();
        }
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, false);
        editor.apply();
        stopSoftwareSync(); // Preference wasn't clicked so this won't be triggered
        getDrawPreview().updateSettings(); // Because we cache the enable RecSync setting
    }

    private void showSimpleAlert(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);

        AlertDialog alert = builder.create();
        mMainActivity.showAlert(alert);
    }

    /**
     * Closes {@link SoftwareSyncController} if one is running.
     */
    public void stopSoftwareSync() {
        if (isSoftwareSyncRunning()) {
            mMainActivity.unregisterReceiver(mConnectionStatusChecker);
            mSoftwareSyncUtils = null;
            mConnectionStatusChecker = null;
            mSoftwareSyncController.close();
            mSoftwareSyncController = null;
        }
    }

    /**
     * Whether SoftwareSync is currently running (i.e. {@link SoftwareSyncController} is
     * initialized).
     *
     * @return true if SoftwareSync is currently running, false if it is not.
     */
    public boolean isSoftwareSyncRunning() {
        return mSoftwareSyncController != null;
    }
}
