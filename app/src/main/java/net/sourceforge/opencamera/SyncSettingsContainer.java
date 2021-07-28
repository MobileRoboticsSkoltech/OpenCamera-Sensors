package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Container for the values of the settings and their "to be synced" statuses for RecSync.
 */
public class SyncSettingsContainer implements Serializable {
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

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
     * Constructs a string with this settings serialized using {@link ByteArrayOutputStream} and
     * {@link ObjectOutputStream}.
     * <p>
     * The string is only constructed once and cached, the cached value is returned from all
     * subsequent invocations.
     *
     * @return a constructed string.
     */
    public String asString() {
        if (asStringCached == null) {
            final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try {
                final ObjectOutputStream objectWriter = new ObjectOutputStream(byteArray);
                objectWriter.writeObject(this);
                objectWriter.close();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot serialize the settings object");
            }
            asStringCached = bytesToString(byteArray.toByteArray());
        }
        return asStringCached;
    }

    /**
     * The container is constructed from a string that was build using this class's
     * {@link #asString()}.
     *
     * @param serializedSettings string containing a serialized {@link SyncSettingsContainer}.
     * @return a {@link SyncSettingsContainer} deserialized from the given string.
     * @throws IOException if failed to deserialize the given string.
     */
    public static SyncSettingsContainer fromString(String serializedSettings) throws IOException {
        final ByteArrayInputStream byteArray = new ByteArrayInputStream(stringToBytes(serializedSettings));
        final ObjectInputStream objectReader = new ObjectInputStream(byteArray);

        try {
            return (SyncSettingsContainer) objectReader.readObject();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bytesToString(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    private static byte[] stringToBytes(String str) {
        final int l = str.length();
        byte[] bytes = new byte[l / 2];
        for (int i = 0; i < l; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + Character.digit(str.charAt(i + 1), 16));
        }
        return bytes;
    }
}
