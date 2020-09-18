package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.RawSensorInfo;

public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";
    private final RawSensorInfo rawSensorInfo;
    private final MainActivity main_activity;
    private final SharedPreferences sharedPreferences;


    ExtendedAppInterface(MainActivity main_activity, Bundle savedInstanceState) {
        super(main_activity, savedInstanceState);
        this.rawSensorInfo = new RawSensorInfo(main_activity);
        this.main_activity = main_activity;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
    }

    public boolean getIMURecordingPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    @Override
    public void stoppedVideo(int video_method, Uri uri, String filename) {
        if (MyDebug.LOG)
            Log.d(TAG, "stopped video");
        if (getIMURecordingPref()) {
            rawSensorInfo.stopRecording();
            rawSensorInfo.disableSensors();

            // TODO: add message to strings.xml
            main_activity.getPreview().showToast(null, "Stopped recording sensor info");
        }
        super.stoppedVideo(video_method, uri, filename);
    }

    @Override
    public void stoppingVideo() {
        super.stoppingVideo();
    }

    @Override
    public void startedVideo() {
        if (MyDebug.LOG)
            Log.d(TAG, "started video");
        if (getIMURecordingPref()) {
            rawSensorInfo.enableSensors();
            rawSensorInfo.startRecording();

            // TODO: add message to strings.xml
            main_activity.getPreview().showToast(null, "Started recording sensor info");
        }
        super.startedVideo();
    }

    @Override
    public void startingVideo() {
        super.startingVideo();
    }
}
