package net.sourceforge.opencamera.recsync;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.PreferenceHandler;
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

    private String mAsStringCached = null;

    /**
     * The container saves the provided values.
     *
     * @param syncISO       whether to sync ISO or not.
     * @param syncWb        whether to sync white balance or not.
     * @param syncFlash     whether to sync flash mode or not.
     * @param syncFormat    whether to sync video/image format or not.
     * @param isVideo       capture mode (true if video, false if photo).
     * @param exposure      exposure time.
     * @param iso           ISO value.
     * @param wbTemperature white balance temperature.
     * @param wbMode        white balance mode.
     * @param flash         flash mode.
     * @param format        video/image format (must match the chosen capture mode).
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
     * Builds {@link SyncSettingsContainer} using settings collected from the current device.
     *
     * @param mainActivity - MainActivity of this device.
     */
    public static SyncSettingsContainer buildFrom(MainActivity mainActivity) {
        PreferenceHandler prefs = mainActivity.getApplicationInterface().getPrefs();
        Preview preview = mainActivity.getPreview();
        CameraController cameraController = preview.getCameraController();

        boolean syncISO = prefs.isSyncIsoEnabled();
        boolean syncWb = prefs.isSyncWbEnabled();
        boolean syncFlash = prefs.isSyncFlashEnabled();
        boolean syncFormat = prefs.isSyncFormatEnabled();

        boolean isVideo = preview.isVideo();
        long exposure = cameraController.captureResultExposureTime();
        int iso = cameraController.captureResultIso();
        int wbTemperature = cameraController.captureResultWhiteBalanceTemperature();
        String wbMode = cameraController.getWhiteBalance();
        String flash = cameraController.getFlashValue();
        String format = isVideo ?
                prefs.getVideoFormat() :
                prefs.getImageFormat();

        return new SyncSettingsContainer(syncISO, syncWb, syncFlash, syncFormat,
                isVideo, exposure, iso, wbTemperature, wbMode, flash, format);
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
    public String serializeToString() {
        if (mAsStringCached == null) {
            final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try {
                final ObjectOutputStream objectWriter = new ObjectOutputStream(byteArray);
                objectWriter.writeObject(this);
                objectWriter.close();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot serialize the settings object");
            }
            // Byte-wise ISO_8859_1 is required for the encoding and decoding to work correctly
            mAsStringCached = new String(byteArray.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        return mAsStringCached;
    }

    /**
     * The container is constructed from a string that was build using this class's
     * {@link #serializeToString()}.
     *
     * @param serializedSettings string containing a serialized {@link SyncSettingsContainer}.
     * @return a {@link SyncSettingsContainer} deserialized from the given string.
     * @throws IOException if failed to deserialize the given string.
     */
    public static SyncSettingsContainer deserializeFromString(String serializedSettings) throws IOException {
        final ByteArrayInputStream byteArray = new ByteArrayInputStream(serializedSettings.getBytes(StandardCharsets.ISO_8859_1));
        final ObjectInputStream objectReader = new ObjectInputStream(byteArray);

        try {
            return (SyncSettingsContainer) objectReader.readObject();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
