package net.sourceforge.opencamera.sensorremote;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoPhaseInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class RequestHandler {
    public static final String TAG = "RequestHandler";
    public static final String SENSOR_DATA_END_MARKER = "sensor_end";
    public static final long MAX_IMU_DURATION_MS = 60_000;
    // Set to some adequate time which is likely more than phase report time
    // TODO: we could set it dynamically using current PHASE_CALC_N_FRAMES
    public static final long PHASE_POLL_TIMEOUT_MS = 10_000;
    private final RawSensorInfo mRawSensorInfo;
    private final MainActivity mContext;

    RequestHandler(MainActivity context) {
        mContext = context;
        mRawSensorInfo = context.getRawSensorInfoManager();
    }

    private String getSensorData(File imuFile) throws IOException {
        StringBuilder msg = new StringBuilder();
        msg.append(imuFile.getName())
                .append("\n");
        try (BufferedReader br = new BufferedReader(new FileReader(imuFile))) {
            for (String line; (line = br.readLine()) != null; ) {
                msg.append(line)
                        .append("\n");
            }
        }
        String res = msg.toString();
        if (MyDebug.LOG) {
            Log.d(TAG, "constructed sensor msg, length: " + res.length());
        }
        return res;
    }

    RemoteRpcResponse handleImuRequest(long durationMillis, boolean wantAccel, boolean wantGyro) {
        if (mRawSensorInfo != null && !mRawSensorInfo.isRecording() && durationMillis <= MAX_IMU_DURATION_MS) {
            // TODO: custom rates?
            Callable<Void> recStartCallable = () -> {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                SharedPreferences.Editor prefEditor = sharedPreferences.edit();
                prefEditor.putBoolean(PreferenceKeys.AccelPreferenceKey, wantAccel);
                prefEditor.putBoolean(PreferenceKeys.GyroPreferenceKey, wantGyro);
                prefEditor.apply();

                mRawSensorInfo.enableSensors(0, 0);
                Date currentDate = new Date();
                mRawSensorInfo.startRecording(mContext, currentDate);
                return null;
            };

            Callable<Void> recStopCallable = () -> {
                mRawSensorInfo.stopRecording();
                mRawSensorInfo.disableSensors();
                return null;
            };

            try {
                // Await recording start
                FutureTask<Void> recStartTask = new FutureTask<>(recStartCallable);
                mContext.runOnUiThread(recStartTask);
                recStartTask.get();
                // Record for requested duration
                Thread.sleep(durationMillis);
                // Await recording stop
                FutureTask<Void> recStopTask = new FutureTask<>(recStopCallable);
                mContext.runOnUiThread(recStopTask);
                recStopTask.get();
                StringBuilder msg = new StringBuilder();
                try {
                    if (wantAccel && mRawSensorInfo.getLastAccelPath() != null) {
                        File imuFile = new File(mRawSensorInfo.getLastAccelPath());
                        msg.append(getSensorData(imuFile));
                        msg.append(SENSOR_DATA_END_MARKER);
                        msg.append("\n");
                    }
                    if (wantGyro && mRawSensorInfo.getLastGyroPath() != null) {
                        File imuFile = new File(mRawSensorInfo.getLastGyroPath());
                        msg.append(getSensorData(imuFile));
                        msg.append(SENSOR_DATA_END_MARKER);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return RemoteRpcResponse.error("Failed to open IMU file");
                }

                return RemoteRpcResponse.success(msg.toString());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return RemoteRpcResponse.error("Error in IMU recording");
            }
        } else {
            return RemoteRpcResponse.error("Error in IMU recording");
        }
    }

    RemoteRpcResponse handleVideoStartRequest() {
        // Start video recording
        Preview preview = mContext.getPreview();

        mContext.runOnUiThread(
                () -> {
                    // Making sure video is switched on
                    if (!preview.isVideo()) {
                        preview.switchVideo(false, true);
                    }
                    // In video mode this means "start video"
                    mContext.takePicture(false);
                }
        );

        // Await video phase event
        BlockingQueue<VideoPhaseInfo> videoPhaseInfoReporter = preview.getVideoPhaseInfoReporter();
        if (videoPhaseInfoReporter != null) {
            VideoPhaseInfo phaseInfo;
            try {
                phaseInfo = videoPhaseInfoReporter
                        .poll(PHASE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (phaseInfo != null) {
                    return RemoteRpcResponse.success(phaseInfo.toString());
                } else {
                    return RemoteRpcResponse.error("Failed to retrieve phase info, reached poll limit");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return RemoteRpcResponse.error("Failed to retrieve phase info");
            }
        } else {
            if (MyDebug.LOG) {
                Log.d(TAG, "Video frame info wasn't initialized, failed to retrieve phase info");
            }
            return RemoteRpcResponse.error("Video frame info wasn't initialized, failed to retrieve phase info");
        }
    }

    RemoteRpcResponse handleVideoStopRequest() {
        mContext.runOnUiThread(
                () -> {
                    Preview preview = mContext.getPreview();
                    if (preview.isVideo() && preview.isVideoRecording()) {
                        preview.stopVideo(false);
                    }
                }
        );
        return RemoteRpcResponse.success("");
    }
}
