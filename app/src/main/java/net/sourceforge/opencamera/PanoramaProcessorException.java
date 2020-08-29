package net.sourceforge.opencamera;

/** Exception for PanoramaProcessor class.
 */
@SuppressWarnings("WeakerAccess")
public class PanoramaProcessorException extends Exception {
    final static public int INVALID_N_IMAGES = 0; // the supplied number of images is not supported
    final static public int UNEQUAL_SIZES = 1; // images not of the same resolution
    final static public int FAILED_TO_CROP = 1; // failed to crop

    final private int code;

    PanoramaProcessorException(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
