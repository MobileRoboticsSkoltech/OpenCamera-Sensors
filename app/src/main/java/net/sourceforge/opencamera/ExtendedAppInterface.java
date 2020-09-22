package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

/** Extended implementation of ApplicationInterface,
 *  adds raw sensor recording layer to the interface
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";

    private final RawSensorInfo mRawSensorInfo;
    private final MainActivity mMainActivity;
    private final SharedPreferences mSharedPreferences;


    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        this.mRawSensorInfo = new RawSensorInfo(mainActivity);
        this.mMainActivity = mainActivity;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    public boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    @Override
    public void startedVideo() {
        if (MyDebug.LOG)
            Log.d(TAG, "started video");
        if (getIMURecordingPref()) {
            mRawSensorInfo.enableSensors();
            mRawSensorInfo.startRecording();

            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast(null, "Started recording sensor info");
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
            mMainActivity.getPreview().showToast(null, "Stopped recording sensor info");
        }
        super.stoppedVideo(video_method, uri, filename);
    }
}
