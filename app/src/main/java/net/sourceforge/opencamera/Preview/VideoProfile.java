package net.sourceforge.opencamera.Preview;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Log;

import net.sourceforge.opencamera.MyDebug;

/** This is essentially similar to CamcorderProfile in that it encapsulates a set of video settings
	 *  to be passed to MediaRecorder, but allows us to store additional fields.
	 */
public class VideoProfile {
	private static final String TAG = "VideoProfile";

    public boolean record_audio;
    public boolean no_audio_permission; // set to true if record_audio==false, but where the user had requested audio and we don't have microphone permission
    public int audioSource;
    public int audioCodec;
    public int audioChannels;
    public int audioBitRate;
    public int audioSampleRate;
    public int fileFormat;
    public int videoSource;
    public int videoCodec;
    public int videoFrameRate;
    public double videoCaptureRate;
    public int videoBitRate;
    public int videoFrameHeight;
    public int videoFrameWidth;

    /** Returns a dummy video profile, used if video isn't supported.
     */
    VideoProfile() {
    }

    VideoProfile(CamcorderProfile camcorderProfile) {
        this.record_audio = true;
        this.no_audio_permission = false;
        this.audioSource = MediaRecorder.AudioSource.CAMCORDER;
        this.audioCodec = camcorderProfile.audioCodec;
        this.audioChannels = camcorderProfile.audioChannels;
        this.audioBitRate = camcorderProfile.audioBitRate;
        this.audioSampleRate = camcorderProfile.audioSampleRate;
        this.fileFormat = camcorderProfile.fileFormat;
        this.videoSource = MediaRecorder.VideoSource.CAMERA;
        this.videoCodec = camcorderProfile.videoCodec;
        this.videoFrameRate = camcorderProfile.videoFrameRate;
        this.videoCaptureRate = camcorderProfile.videoFrameRate;
        this.videoBitRate = camcorderProfile.videoBitRate;
        this.videoFrameHeight = camcorderProfile.videoFrameHeight;
        this.videoFrameWidth = camcorderProfile.videoFrameWidth;
    }

    public String toString() {
        return ("\nAudioSource:        " + this.audioSource +
                "\nVideoSource:        " + this.videoSource +
                "\nFileFormat:         " + this.fileFormat +
                "\nAudioCodec:         " + this.audioCodec +
                "\nAudioChannels:      " + this.audioChannels +
                "\nAudioBitrate:       " + this.audioBitRate +
                "\nAudioSampleRate:    " + this.audioSampleRate +
                "\nVideoCodec:         " + this.videoCodec +
                "\nVideoFrameRate:     " + this.videoFrameRate +
                "\nVideoCaptureRate:   " + this.videoCaptureRate +
                "\nVideoBitRate:       " + this.videoBitRate +
                "\nVideoWidth:         " + this.videoFrameWidth +
                "\nVideoHeight:        " + this.videoFrameHeight
        );
    }

    /**
     * Copies the fields of this profile to a MediaRecorder instance.
     */
    public void copyToMediaRecorder(MediaRecorder media_recorder) {
        if( MyDebug.LOG )
            Log.d(TAG, "copyToMediaRecorder: " + media_recorder);
        if( record_audio ) {
            if( MyDebug.LOG )
                Log.d(TAG, "record audio");
            media_recorder.setAudioSource(this.audioSource);
        }
        media_recorder.setVideoSource(this.videoSource);
        // n.b., order may be important - output format should be first, at least
        // also match order of MediaRecorder.setProfile() just to be safe, see https://stackoverflow.com/questions/5524672/is-it-possible-to-use-camcorderprofile-without-audio-source
        media_recorder.setOutputFormat(this.fileFormat);
        media_recorder.setVideoFrameRate(this.videoFrameRate);
        // it's probably safe to always call setCaptureRate, but to be safe (and keep compatibility with old Open Camera versions), we only do so when needed
        if( this.videoCaptureRate != (double)this.videoFrameRate ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set capture rate");
            media_recorder.setCaptureRate(this.videoCaptureRate);
        }
        media_recorder.setVideoSize(this.videoFrameWidth, this.videoFrameHeight);
        media_recorder.setVideoEncodingBitRate(this.videoBitRate);
        media_recorder.setVideoEncoder(this.videoCodec);
        if( record_audio ) {
            media_recorder.setAudioEncodingBitRate(this.audioBitRate);
            media_recorder.setAudioChannels(this.audioChannels);
            media_recorder.setAudioSamplingRate(this.audioSampleRate);
            media_recorder.setAudioEncoder(this.audioCodec);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "done: " + media_recorder);
    }
}
