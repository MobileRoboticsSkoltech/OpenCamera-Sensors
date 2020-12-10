package net.sourceforge.opencamera.sensorremote;

import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;

import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestHandler {
    private final RawSensorInfo mRawSensorInfo;
    private final MainActivity mContext;

    RequestHandler(MainActivity context) {
        mContext = context;
        mRawSensorInfo = context.getRawSensorInfoManager();
    }

    File handleImuRequest(long durationMillis) {
        if (mRawSensorInfo != null && !mRawSensorInfo.isRecording()) {
            // TODO: custom rates?
            mContext.runOnUiThread(
                    () -> {
                        mRawSensorInfo.enableSensors(0, 0);
                        Date currentDate = new Date();
                        mRawSensorInfo.startRecording(mContext, currentDate);
                    }
            );

            // Keep recording for requested duration
            try {
                Log.d("RemoteRpcServer", "thread " + Thread.currentThread().getName());
                Thread.sleep(durationMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mContext.runOnUiThread(
                    () -> {
                        mRawSensorInfo.stopRecording();
                        mRawSensorInfo.disableSensors();
                    }
            );
            //TODO: sensor choice
            File imuFile = new File(mRawSensorInfo.getLastAccelPath());
            return imuFile;
        } else {
            throw new RuntimeException();
            // TODO: errors
        }
    }

    void handleVideoStartRequest() {
        mContext.runOnUiThread(
                () -> {
                    Preview preview = mContext.getPreview();
                    // Making sure video is switched on
                    if (!preview.isVideo()) {
                        preview.switchVideo(false, true);
                    }
                    // In video mode this means "start video"
                    mContext.takePicture(false);
                }
        );
    }

    void handleVideoStopRequest() {
        mContext.runOnUiThread(
                () -> {
                    Preview preview = mContext.getPreview();
                    if (preview.isVideo() && preview.isVideoRecording()) {
                        preview.stopVideo(false);
                    }
                }
        );
    }
}
