package net.sourceforge.opencamera.Preview;

import android.media.CamcorderProfile;

/** This is essentially similar to CamcorderProfile in that it encapsulates a set of video settings
	 *  to be passed to MediaRecorder, but allows us to store additional fields.
	 */
public class VideoProfile {
    public int audioCodec;
    public int audioChannels;
    public int audioBitRate;
    public int audioSampleRate;
    public int fileFormat;
    public int videoCodec;
    public int videoFrameRate;
    public int videoBitRate;
    public int videoFrameHeight;
    public int videoFrameWidth;

    VideoProfile(CamcorderProfile camcorderProfile) {
        this.audioCodec = camcorderProfile.audioCodec;
        this.audioChannels = camcorderProfile.audioChannels;
        this.audioBitRate = camcorderProfile.audioBitRate;
        this.audioSampleRate = camcorderProfile.audioSampleRate;
        this.fileFormat = camcorderProfile.fileFormat;
        this.videoCodec = camcorderProfile.videoCodec;
        this.videoFrameRate = camcorderProfile.videoFrameRate;
        this.videoBitRate = camcorderProfile.videoBitRate;
        this.videoFrameHeight = camcorderProfile.videoFrameHeight;
        this.videoFrameWidth = camcorderProfile.videoFrameWidth;
    }

    public String toString() {
        return ("\nFileFormat:         " + this.fileFormat +
                "\nAudioCodec:         " + this.audioCodec +
                "\nAudioChannels:      " + this.audioChannels +
                "\nAudioBitrate:       " + this.audioBitRate +
                "\nAudioSampleRate:    " + this.audioSampleRate +
                "\nVideoCodec:         " + this.videoCodec +
                "\nVideoFrameRate:     " + this.videoFrameRate +
                "\nVideoBitRate:       " + this.videoBitRate +
                "\nVideoWidth:         " + this.videoFrameWidth +
                "\nVideoHeight:        " + this.videoFrameHeight
        );
    }
}
