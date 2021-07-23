package net.sourceforge.opencamera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import com.googleresearch.capturesync.SoftwareSyncController;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncLeader;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.YuvImageUtils;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.sensorlogging.FlashController;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoFrameInfo;
import net.sourceforge.opencamera.sensorlogging.VideoPhaseInfo;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Extended implementation of ApplicationInterface, adds raw sensor recording layer to the
 * interface.
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";
    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final RawSensorInfo mRawSensorInfo;

    public FlashController getFlashController() {
        return mFlashController;
    }

    private final FlashController mFlashController;
    private final SharedPreferences mSharedPreferences;
    private final MainActivity mMainActivity;
    private final YuvImageUtils mYuvUtils;
    private final Handler sendSettingsHandler = new Handler();

    private SoftwareSyncController mSoftwareSyncController;

    public VideoFrameInfo setupFrameInfo() throws IOException {
        return new VideoFrameInfo(
                getLastVideoDate(), mMainActivity, getSaveFramesPref(), getVideoPhaseInfoReporter()
        );
    }

    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        mRawSensorInfo = mainActivity.getRawSensorInfoManager();
        mMainActivity = mainActivity;
        mFlashController = new FlashController(mainActivity);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        // We create it only once here (not during the video) as it is a costly operation
        // (instantiates RenderScript object)
        mYuvUtils = new YuvImageUtils(mainActivity);

    }

    @Override
    void onDestroy() {
        mYuvUtils.close();
        super.onDestroy();
    }

    public BlockingQueue<VideoPhaseInfo> getVideoPhaseInfoReporter() {
        return mMainActivity.getPreview().getVideoPhaseInfoReporter();
    }

    public boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    public boolean getRemoteRecControlPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.RemoteRecControlPreferenceKey, false);
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
            if (sensorSampleRateString != null)
                sensorSampleRate = Integer.parseInt(sensorSampleRateString);
        } catch (NumberFormatException exception) {
            if (MyDebug.LOG)
                Log.e(TAG, "Sample rate invalid format: " + sensorSampleRateString);
        }
        return sensorSampleRate;
    }

    public boolean getSaveFramesPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.saveFramesPreferenceKey, false);
    }

    public boolean getEnableRecSyncPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.EnableRecSyncPreferenceKey, false);
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

    public YuvImageUtils getYuvUtils() {
        return mYuvUtils;
    }

    /**
     * Starts RecSync by instantiating a {@link SoftwareSyncController}. If one is already running
     * then it is closed and a new one is initialized.
     * <p>
     * Starts pick WiFI activity if neither WiFi nor hotspot is enabled.
     */
    public void startSoftwareSync() {
        // Start softwaresync, close it first if it's already running.
        if (mSoftwareSyncController != null) {
            mSoftwareSyncController.close();
            mSoftwareSyncController = null;
        }

        try {
            mSoftwareSyncController =
                    new SoftwareSyncController(mMainActivity, null, new TextView(mMainActivity));
        } catch (IllegalStateException e) {
            // If wifi is disabled, start pick wifi activity.
            Log.e(
                    TAG,
                    "Couldn't start SoftwareSync due to " + e + ", requesting user pick a wifi network.");
            mMainActivity.finish(); // Close current app, expect user to restart.
            mMainActivity.startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
        }
    }

    /**
     * Closes {@link SoftwareSyncController} if one is running.
     */
    public void stopSoftwareSync() {
        if (mSoftwareSyncController != null) {
            mSoftwareSyncController.close();
        }
    }

    public SoftwareSyncController getSoftwareSyncController() {
        return mSoftwareSyncController;
    }

    /**
     * Provides the current leader-client status of RecSync.
     *
     * @return a {@link TextView} describing the current status.
     * @throws IllegalStateException if {@link SoftwareSyncController} is not initialized.
     */
    public TextView getSyncStatusText() {
        if (mSoftwareSyncController != null) {
            return mSoftwareSyncController.getStatusText();
        } else {
            throw new IllegalStateException("Cannot provide sync status when RecSync is not running");
        }
    }

    /**
     * Container for the values of the settings and their "to be synced" statuses.
     */
    public static class SettingsContainer {
        final public boolean syncIso;
        final public boolean syncWb;
        final public boolean syncFlash;
        final public boolean syncFormat;

        final public boolean isVideo;
        final public long exposure;
        final public int iso;
        final public int wbTemperature;
        final public String wbMode;
        final public String flash;
        final public String format;

        public SettingsContainer(boolean syncIso, boolean syncWb, boolean syncFlash, boolean syncFormat,
                                 boolean isVideo, long exposure, int iso, int wbTemperature, String wbMode, String flash, String format) {
            this.syncIso = syncIso;
            this.syncWb = syncWb;
            this.syncFlash = syncFlash;
            this.syncFormat = syncFormat;
            this.isVideo = isVideo;
            this.exposure = exposure;
            this.iso = iso;
            this.wbTemperature = wbTemperature;
            this.wbMode = wbMode;
            this.flash = flash;
            this.format = format;
        }
    }

    /**
     * Collects current syncing preferences and settings.
     *
     * @return a container with collected settings.
     */
    public SettingsContainer collectSettings() {
        Log.d(TAG, "Collecting current settings.");

        Preview preview = mMainActivity.getPreview();
        CameraController cameraController = preview.getCameraController();

        boolean isVideo = preview.isVideo();

        SettingsContainer settings = new SettingsContainer(
                mSharedPreferences.getBoolean(PreferenceKeys.SyncIsoPreferenceKey, false),
                mSharedPreferences.getBoolean(PreferenceKeys.SyncWbPreferenceKey, false),
                mSharedPreferences.getBoolean(PreferenceKeys.SyncFlashPreferenceKey, false),
                mSharedPreferences.getBoolean(PreferenceKeys.SyncFormatPreferenceKey, false),
                isVideo,
                cameraController.captureResultExposureTime(),
                cameraController.captureResultIso(),
                cameraController.captureResultWhiteBalanceTemperature(),
                cameraController.getWhiteBalance(),
                cameraController.getFlashValue(),
                isVideo ?
                        mSharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, "preference_video_output_format_default") :
                        mSharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg")
        );

        return settings;
    }

    /**
     * Schedules a broadcast of the current settings to clients.
     *
     * @param settings describes the settings to be broadcast.
     * @throws IllegalStateException if {@link SoftwareSyncController} is not initialized after the
     * delay.
     */
    public void scheduleBroadcastSettings(SettingsContainer settings) {
        sendSettingsHandler.removeCallbacks(null);
        sendSettingsHandler.postDelayed(
                () -> {
                    Log.d(TAG, "Broadcasting current settings.");

                    // Construct the payload
                    String[] payloadParts = {
                            String.valueOf(settings.syncIso),
                            String.valueOf(settings.syncWb),
                            String.valueOf(settings.syncFlash),
                            String.valueOf(settings.syncFormat),
                            String.valueOf(settings.isVideo),
                            String.valueOf(settings.exposure),
                            String.valueOf(settings.iso),
                            String.valueOf(settings.wbTemperature),
                            settings.wbMode,
                            settings.flash,
                            settings.format
                    };
                    String payload = TextUtils.join(",", payloadParts);

                    // Send settings to all devices
                    if (mSoftwareSyncController == null) {
                        throw new IllegalStateException("Cannot broadcast settings when RecSync is not running");
                    }
                    ((SoftwareSyncLeader) mSoftwareSyncController.getSoftwareSync())
                            .broadcastRpc(SoftwareSyncController.METHOD_SET_SETTINGS, payload);
                },
                500);
    }

    /**
     * Applies the values of the settings received from a leader and locks them.
     *
     * @param settings describes the settings to be changed and the values to be applied.
     */
    public void applyAndLockSettings(SettingsContainer settings) {
        Log.d(TAG, "Applying and locking settings.");

        Preview preview = mMainActivity.getPreview();
        CameraController cameraController = preview.getCameraController();

        if (preview.isVideo() != settings.isVideo) {
            mMainActivity.clickedSwitchVideo(null);
        }

        cameraController.setExposureTime(settings.exposure);
        if (!preview.isExposureLocked()) {
            mMainActivity.clickedExposureLock(null);
        }

        if (settings.syncIso) {
            cameraController.setManualISO(true, settings.iso);
        }

        if (settings.syncWb) {
            cameraController.setWhiteBalance(settings.wbMode);
            cameraController.setWhiteBalanceTemperature(settings.wbTemperature);
            if (!preview.isWhiteBalanceLocked()) {
                mMainActivity.clickedWhiteBalanceLock(null);
            }
        }

        if (settings.syncFlash) {
            cameraController.setFlashValue(settings.flash);
        }

        if (settings.syncFormat) {
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            if (settings.isVideo) {
                editor.putString(PreferenceKeys.VideoFormatPreferenceKey, settings.format);
            } else {
                editor.putString(PreferenceKeys.ImageFormatPreferenceKey, settings.format);
            }
            editor.apply();
        }

        getDrawPreview().updateSettings();
    }
}
