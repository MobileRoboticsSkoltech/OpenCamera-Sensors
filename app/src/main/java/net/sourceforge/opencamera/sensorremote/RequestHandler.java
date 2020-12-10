package net.sourceforge.opencamera.sensorremote;

import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;

public class RequestHandler {
    private final RawSensorInfo mRawSensorInfo;
    private final MainActivity mContext;

    RequestHandler(MainActivity context) {
        mContext = context;
        mRawSensorInfo = context.getRawSensorInfoManager();
    }

    File handleImuRequest(long durationMillis) throws FileNotFoundException {
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

}
