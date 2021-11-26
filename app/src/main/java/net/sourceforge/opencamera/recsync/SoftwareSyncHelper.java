package net.sourceforge.opencamera.recsync;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.googleresearch.capturesync.SoftwareSyncController;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncLeader;

import net.sourceforge.opencamera.ExtendedAppInterface;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.ui.MainUI;
import net.sourceforge.opencamera.ui.ManualSeekbars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SoftwareSyncHelper {
    private static final String TAG = "SoftwareSyncHelper";

    private final MainActivity mMainActivity;
    private final ExtendedAppInterface mApplicationInterface;
    private final Preview mPreview;
    private final SoftwareSyncController mSoftwareSyncController;

    private Runnable mApplySettingsRunnable = null;

    public SoftwareSyncHelper(MainActivity mainActivity, SoftwareSyncController softwareSyncController) {
        mMainActivity = mainActivity;
        mSoftwareSyncController = softwareSyncController;
        mApplicationInterface = mMainActivity.getApplicationInterface();
        mPreview = mMainActivity.getPreview();
    }

    public Runnable getApplySettingsRunnable() {
        return mApplySettingsRunnable;
    }

    /**
     * Broadcasts a video recording request to clients. The request will be received by the leader
     * too.
     * <p>
     * All receivers will switch their recording status to the opposite of the current status on
     * this device (i.e. if this device is recording a video, it will stop and so will all the
     * clients).
     * <p>
     * If a client already has the recording status equal to the opposite of the status of this
     * device, it will not be changed.
     *
     * @throws IllegalStateException if {@link SoftwareSyncController} is not initialized or this
     *                               device is not a leader.
     */
    public void broadcastRecordingRequest() {
        Log.d(TAG, "Broadcasting video recording request.");

        if (!mApplicationInterface.isSoftwareSyncRunning()) {
            throw new IllegalStateException("Cannot broadcast recording request when RecSync is not running");
        }
        if (!mSoftwareSyncController.isLeader()) {
            throw new IllegalStateException("Cannot broadcast recording request from a client");
        }
        ((SoftwareSyncLeader) mSoftwareSyncController.getSoftwareSync()).broadcastRpc(
                SoftwareSyncController.METHOD_RECORD,
                String.valueOf(mPreview.isVideoRecording())
        );
    }

    /**
     * Prepares the application for video recording to be started to the moment when
     * {@link android.hardware.camera2.CameraCaptureSession} gets reopened.
     * <p>
     * Removes the previous preparation if there was one.
     */
    public void prepareVideoRecording() {
        Log.d(TAG, "Preparing video recording.");

        removeVideoRecordingPreparation(); // In case we want to re-prepare for some reason

        Log.d(TAG, "About to call Preview.prepareVideoRecording().");
        mMainActivity.runOnUiThread(mPreview::prepareVideoRecording);
    }

    /**
     * Removes the preparation for video recording of the application if it is set.
     */
    public void removeVideoRecordingPreparation() {
        Log.d(TAG, "Removing video recording preparation.");

        if (mPreview.isVideoRecordingPrepared()) {
            Log.d(TAG, "About to call mPreview.removeVideoRecordingPreparation().");
            mMainActivity.runOnUiThread(mPreview::removeVideoRecordingPreparation);
        }
    }

    /**
     * Starts video recording if the application was previously prepared for it by calling
     * {@link #prepareVideoRecording}.
     *
     * @return true if the recording was started, false otherwise.
     */
    public boolean startPreparedVideoRecording() {
        if (!mPreview.isVideoRecordingPrepared()) return false;

        mMainActivity.runOnUiThread(() -> mMainActivity.takePicturePressed(false, false));
        return true;
    }

    /**
     * Broadcasts the current settings to clients. The settings are applied to the leader too.
     *
     * @param settings describes the settings to be broadcast.
     * @throws IllegalStateException if after the delay {@link SoftwareSyncController} is not
     *                               initialized or this device is not a leader.
     */
    public void broadcastSettings(SyncSettingsContainer settings) {
        Log.d(TAG, "Broadcasting current settings.");

        if (!mApplicationInterface.isSoftwareSyncRunning()) {
            throw new IllegalStateException("Cannot broadcast settings when RecSync is not running");
        }
        if (!mSoftwareSyncController.isLeader()) {
            throw new IllegalStateException("Cannot broadcast settings from a client");
        }

        // Send settings to all devices
        ((SoftwareSyncLeader) mSoftwareSyncController.getSoftwareSync()).broadcastRpc(
                SoftwareSyncController.METHOD_SET_SETTINGS,
                settings.serializeToString()
        );
    }

    /**
     * Broadcasts a request to remove the video recording preparation.
     */
    public void broadcastClearVideoPreparationRequest() {
        ((SoftwareSyncLeader) mSoftwareSyncController.getSoftwareSync()).broadcastRpc(
                SoftwareSyncController.METHOD_STOP_PREPARE, "");
    }

    /**
     * Applies the settings received from a leader and locks them. Closes the previous attempt if it
     * exists and is still running.
     *
     * @param settings   describes the settings to be changed and the values to be applied.
     * @param onFinished a {@link Runnable} to be called when the settings are applied.
     */
    public void applyAndLockSettings(SyncSettingsContainer settings, Runnable onFinished) {
        Log.d(TAG, "Applying and locking settings.");

        final Preview preview = mMainActivity.getPreview();
        final MainUI mainUI = mMainActivity.getMainUI();
        final SettingsApplicationUtils utils = new SettingsApplicationUtils(preview, mainUI);

        final boolean isModeSwitchRequired = utils.isModeSwitchRequired(settings);

        // Create a new runnable to wait until camera is opened
        mApplySettingsRunnable = () -> {
            Log.d(TAG, "Starting the first part of settings application.");

            mApplySettingsRunnable = null;

            // Remove the preparation, because the recording mode might get switched
            removeVideoRecordingPreparation();

            // Close some UI elements for the changes to be reflected in them
            mMainActivity.runOnUiThread(() -> {
                mainUI.closeExposureUI();
                mainUI.closePopup();
                mainUI.destroyPopup();
            });

            mApplySettingsRunnable = () -> {
                Log.d(TAG, "Starting the second part of settings application.");

                mApplySettingsRunnable = null;

                utils.setCameraController(preview.getCameraController());

                utils.syncExposure(settings);
                if (settings.syncISO) utils.syncISO(settings);
                if (settings.syncWb) utils.syncWb(settings);
                if (settings.syncFlash) utils.syncFlash(settings);
                if (settings.syncFormat) utils.syncFormat(settings);

                mApplicationInterface.getDrawPreview().updateSettings(); // Ensure that the changes get cached

                Log.d(TAG, "Running onFinished().");
                if (onFinished != null) onFinished.run();
            };

            // May reopen the camera
            utils.syncCaptureFormat(settings);

            // Run the second part if camera is not going to be reopened
            if (!isModeSwitchRequired) {
                Log.d(TAG, "Executing the second part of settings application immediately.");
                mApplySettingsRunnable.run();
            }
        };

        // Run if camera is already opened
        if (!preview.openCameraFailed()) mApplySettingsRunnable.run();
    }

    private class SettingsApplicationUtils {
        private final Preview mPreview;
        private final MainUI mMainUI;
        private final ManualSeekbars mManualSeekbars = mMainActivity.getManualSeekbars();

        private CameraController mCameraController;

        SettingsApplicationUtils(Preview preview, MainUI mainUI) {
            mPreview = preview;
            mMainUI = mainUI;
        }

        public void setCameraController(CameraController value) {
            mCameraController = value;
        }

        public boolean isModeSwitchRequired(SyncSettingsContainer settings) {
            return mPreview.isVideo() != settings.isVideo;
        }

        private void syncCaptureFormat(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: capture format");

            if (isModeSwitchRequired(settings)) {
                mMainActivity.runOnUiThread(() -> mMainActivity.switchVideo(null));
            }
        }

        private void syncExposure(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: exposure");

            if (!mPreview.isExposureLocked()) {
                mMainActivity.runOnUiThread(() -> mMainActivity.lockExposure(null));
            }
            mPreview.setExposureTime(settings.exposure);
            // Reflect the change in the UI
            mManualSeekbars.setProgressSeekbarShutterSpeed(
                    mMainActivity.findViewById(R.id.exposure_time_seekbar),
                    mPreview.getMinimumExposureTime(),
                    mPreview.getMaximumExposureTime(),
                    mCameraController.getExposureTime()
            ); // We get exposure from the controller in case the selected value is unsupported
        }

        private void syncISO(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: ISO");

            mCameraController.setManualISO(true, settings.iso); // Set ISO to manual (lock it)
            // Reflect the change in the UI
            mPreview.setISO(settings.iso);
            mApplicationInterface.setISOPref("" + mCameraController.getISO()); // So it is not set to auto
            mMainActivity.runOnUiThread(() -> {
                mMainUI.setupExposureUI();
                mMainUI.closeExposureUI();
            });
            mManualSeekbars.setProgressSeekbarISO(
                    mMainActivity.findViewById(R.id.iso_seekbar),
                    mPreview.getMinimumISO(),
                    mPreview.getMaximumISO(),
                    mCameraController.getISO()
            ); // We get ISO from the controller in case the selected value is unsupported
        }

        private void syncWb(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: white balance");

            // Lock wb only if the selected mode is not auto
            if (!settings.wbMode.equals(CameraController.WHITE_BALANCE_DEFAULT) && !mPreview.isWhiteBalanceLocked()) {
                mMainActivity.runOnUiThread(() -> mMainActivity.lockWhiteBalance(null));
            }
            // If the selected mode is supported set it, otherwise try to set manual mode
            List<String> supportedValues = mPreview.getSupportedWhiteBalances();
            if (supportedValues.contains(settings.wbMode)) {
                mCameraController.setWhiteBalance(settings.wbMode);
            } else if (supportedValues.contains("manual")) {
                mCameraController.setWhiteBalance("manual");
            }
            String resultingWbMode = mCameraController.getWhiteBalance();
            mApplicationInterface.setWhiteBalancePref(resultingWbMode); // Reflect the resulting mode in the UI
            // If the resulting mode is manual apply the selected temperature
            if (resultingWbMode.equals("manual")) {
                mPreview.setWhiteBalanceTemperature(settings.wbTemperature);
                // Reflect the change in the UI
                mManualSeekbars.setProgressSeekbarWhiteBalance(
                        mMainActivity.findViewById(R.id.white_balance_seekbar),
                        mPreview.getMinimumWhiteBalanceTemperature(),
                        mPreview.getMaximumWhiteBalanceTemperature(),
                        mCameraController.getWhiteBalanceTemperature()
                ); // We get the temperature from the controller in case the selected value is unsupported
            }
        }

        private void syncFlash(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: flash mode");

            mMainActivity.runOnUiThread(() -> {
                mPreview.updateFlash(settings.flash);
                mMainUI.setPopupIcon(); // Reflect the change in the UI
            });
        }

        private void syncFormat(SyncSettingsContainer settings) {
            Log.d(TAG, "Syncing settings: file format");

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mMainActivity);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (settings.isVideo) {
                // Check that the selected format is supported (HEVC and WebM may be not)
                List<String> supportedFormats = getSupportedVideoFormats();
                if (supportedFormats.contains(settings.format)) {
                    editor.putString(PreferenceKeys.VideoFormatPreferenceKey, settings.format);
                }
            } else {
                editor.putString(PreferenceKeys.ImageFormatPreferenceKey, settings.format);
            }
            editor.apply();
        }

        private List<String> getSupportedVideoFormats() {
            // Construct the list of the supported formats the same way MyPreferenceFragment does
            List<String> supportedFormats = new ArrayList<>(Arrays.asList(mMainActivity.getResources().getStringArray(R.array.preference_video_output_format_values)));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                supportedFormats.remove("preference_video_output_format_mpeg4_hevc");
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                supportedFormats.remove("preference_video_output_format_webm");
            }
            return supportedFormats;
        }
    }
}
