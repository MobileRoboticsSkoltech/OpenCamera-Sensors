package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;

/**
 * Container for the values of the settings and their "to be synced" statuses for RecSync.
 */
public class SyncSettingsContainer {
    final public boolean syncISO;
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

    private String asStringCached = null;

    /**
     * Settings are collected from the current device.
     *
     * @param mainActivity - MainActivity of this device.
     */
    public SyncSettingsContainer(MainActivity mainActivity) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        Preview preview = mainActivity.getPreview();
        CameraController cameraController = preview.getCameraController();

        syncISO = sharedPreferences.getBoolean(PreferenceKeys.SyncIsoPreferenceKey, false);
        syncWb = sharedPreferences.getBoolean(PreferenceKeys.SyncWbPreferenceKey, false);
        syncFlash = sharedPreferences.getBoolean(PreferenceKeys.SyncFlashPreferenceKey, false);
        syncFormat = sharedPreferences.getBoolean(PreferenceKeys.SyncFormatPreferenceKey, false);

        isVideo = preview.isVideo();
        exposure = cameraController.captureResultExposureTime();
        iso = cameraController.captureResultIso();
        wbTemperature = cameraController.captureResultWhiteBalanceTemperature();
        wbMode = cameraController.getWhiteBalance();
        flash = cameraController.getFlashValue();
        format = isVideo ?
                sharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, "preference_video_output_format_default") :
                sharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg");
    }

    /**
     * The container saves the provided values.
     *
     * @param syncISO whether to sync ISO or not.
     * @param syncWb whether to sync white balance or not.
     * @param syncFlash whether to sync flash mode or not.
     * @param syncFormat whether to sync video/image format or not.
     * @param isVideo capture mode (true if video, false if photo).
     * @param exposure exposure time.
     * @param iso ISO value.
     * @param wbTemperature white balance temperature.
     * @param wbMode white balance mode.
     * @param flash flash mode.
     * @param format video/image format (must match the chosen capture mode).
     */
    public SyncSettingsContainer(boolean syncISO, boolean syncWb, boolean syncFlash, boolean syncFormat,
                                 boolean isVideo, long exposure, int iso, int wbTemperature, String wbMode, String flash, String format) {
        this.syncISO = syncISO;
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

    /**
     * Constructs a string with this settings in a format of a payload for RecSync RPCs.
     * <p>
     * The string is only constructed once and cached, the cached value is returned from all
     * subsequent invocations.
     *
     * @return a constructed string.
     */
    public String asString() {
        if (asStringCached == null) {
            String[] parts = {
                    String.valueOf(syncISO),
                    String.valueOf(syncWb),
                    String.valueOf(syncFlash),
                    String.valueOf(syncFormat),
                    String.valueOf(isVideo),
                    String.valueOf(exposure),
                    String.valueOf(iso),
                    String.valueOf(wbTemperature),
                    wbMode,
                    flash,
                    format
            };
            asStringCached = TextUtils.join(",", parts);
        }
        return asStringCached;
    }
}
