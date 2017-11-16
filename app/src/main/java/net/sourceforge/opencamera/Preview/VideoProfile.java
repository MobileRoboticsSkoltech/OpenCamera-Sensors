package net.sourceforge.opencamera.Preview;

import android.media.CamcorderProfile;

/** This is essentially similar to CamcorderProfile in that it encapsulates a set of video settings
	 *  to be passed to MediaRecorder, but allows us to create values without having to create a
	 *  CamcorderProfile (needed for slow motion / high speed video recording).
	 */
public class VideoProfile {
    final private CamcorderProfile camcorderProfile;
    public final int audioCodec;
    public final int fileFormat;
    public final int videoBitRate;
    public final int videoCodec;
    public final int videoFrameHeight;
    public final int videoFrameRate;
    public final int videoFrameWidth;

    VideoProfile(CamcorderProfile camcorderProfile) {
        this.camcorderProfile = camcorderProfile;

        this.audioCodec = camcorderProfile.audioCodec;
        this.fileFormat = camcorderProfile.fileFormat;
        this.videoBitRate = camcorderProfile.videoBitRate;
        this.videoCodec = camcorderProfile.videoCodec;
        this.videoFrameHeight = camcorderProfile.videoFrameHeight;
        this.videoFrameRate = camcorderProfile.videoFrameRate;
        this.videoFrameWidth = camcorderProfile.videoFrameWidth;
    }
    VideoProfile(int audioCodec, int fileFormat, int videoBitRate, int videoCodec, int videoFrameHeight, int videoFrameRate, int videoFrameWidth) {
        this.camcorderProfile = null;

        this.audioCodec = audioCodec;
        this.fileFormat = fileFormat;
        this.videoBitRate = videoBitRate;
        this.videoCodec = videoCodec;
        this.videoFrameHeight = videoFrameHeight;
        this.videoFrameRate = videoFrameRate;
        this.videoFrameWidth = videoFrameWidth;
    }

    /** Returns the CamcorderProfile this VideoProfile was created with, or null if it was not
     *  created with a CamcorderProfile.
     */
    CamcorderProfile getCamcorderProfile() {
        return this.camcorderProfile;
    }
}

