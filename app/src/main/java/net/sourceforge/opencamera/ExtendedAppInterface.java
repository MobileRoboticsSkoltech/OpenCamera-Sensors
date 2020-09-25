package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/** Extended implementation of ApplicationInterface,
 *  adds raw sensor recording layer to the interface
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";

    private final RawSensorInfo mRawSensorInfo;
    private final SharedPreferences mSharedPreferences;
    private final MainActivity mMainActivity;
    private Date lastVideoDate; // need to save the last video date to create raw sensor files with matching names


    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        this.mRawSensorInfo = new RawSensorInfo(mainActivity);
        this.mMainActivity = mainActivity;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    private boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    @Override
    public File createOutputVideoFile(String extension) throws IOException {
        lastVideoDate = new Date();
        File lastVideoFile = mMainActivity.getStorageUtils()
                .createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "", extension, lastVideoDate);
        return lastVideoFile;
    }

    @Override
    public Uri createOutputVideoSAF(String extension) throws IOException {
        lastVideoDate = new Date();
        Uri lastVideoFileSAF = mMainActivity.getStorageUtils()
                .createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_VIDEO, "", extension,
                        lastVideoDate);
        return lastVideoFileSAF;
    }

    @Override
    public void startedVideo() {

        if (MyDebug.LOG)
            Log.d(TAG, "started video");
        if (getIMURecordingPref()) {
            mRawSensorInfo.enableSensors();
            mRawSensorInfo.startRecording(mMainActivity, lastVideoDate);
            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast(null, "Recording sensor info");
        }

        super.startedVideo();
    }

    @Override
    public void stoppedVideo(int video_method, Uri uri, String filename) {
        if (MyDebug.LOG)
            Log.d(TAG, "stopped video");
        if (mRawSensorInfo.isRecording()) {
            mRawSensorInfo.stopRecording();
            mRawSensorInfo.disableSensors();

            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast(null, "Finished recording sensor info");
        }

        super.stoppedVideo(video_method, uri, filename);
    }
}
