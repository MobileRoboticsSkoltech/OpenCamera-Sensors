package net.sourceforge.opencamera.sensorremote;

import android.hardware.Sensor;
import android.util.Log;

import net.sourceforge.opencamera.ExtendedAppInterface;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoPhaseInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class RemoteRpcRequestHandler {
    public static final String TAG = "RequestHandler";
    public static final String SENSOR_DATA_END_MARKER = "sensor_end";
    private static final int BUFFER_SIZE = 1024;

    // Set to some adequate time which is likely more than phase report time
    // TODO: we could set it dynamically using current PHASE_CALC_N_FRAMES
    public static final long PHASE_POLL_TIMEOUT_MS = 10_000;
    private final RawSensorInfo mRawSensorInfo;
    private final MainActivity mContext;
    private final RemoteRpcResponse.Builder mResponseBuilder;

    RemoteRpcRequestHandler(MainActivity context) {
        mContext = context;
        mRawSensorInfo = context.getRawSensorInfoManager();
        mResponseBuilder = new RemoteRpcResponse.Builder(context);
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

    RemoteRpcResponse handleInvalidRequest() {
        return mResponseBuilder.error("Invalid request", mContext);
    }

    RemoteRpcResponse handleImuRequest(long durationMillis, boolean wantAccel,
                                       boolean wantGyro, boolean wantMagnetic,
                                       boolean wantGravity, boolean wantRotation) {
        if (mRawSensorInfo != null && !mRawSensorInfo.isRecording()) {
            // TODO: custom rates?
            Callable<Void> recStartCallable = () -> {
//                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
//                SharedPreferences.Editor prefEditor = sharedPreferences.edit();
//                prefEditor.putBoolean(PreferenceKeys.AccelPreferenceKey, wantAccel);
//                prefEditor.putBoolean(PreferenceKeys.GyroPreferenceKey, wantGyro);
//                prefEditor.putBoolean(PreferenceKeys.MagnetometerPrefKey, wantMagnetic);
//                prefEditor.apply();

                Date currentDate = new Date();
                mContext.getApplicationInterface().startImu(wantAccel, wantGyro, wantMagnetic, wantGravity, wantRotation, currentDate);
                return null;
            };

            Callable<Void> recStopCallable = () -> {
                mRawSensorInfo.stopRecording();
                mRawSensorInfo.disableSensors();
                return null;
            };

            if (wantAccel && !mRawSensorInfo.isSensorAvailable(Sensor.TYPE_ACCELEROMETER) ||
                    wantGyro && !mRawSensorInfo.isSensorAvailable(Sensor.TYPE_GYROSCOPE)
            ) {
                return mResponseBuilder.error("Requested sensor wasn't supported", mContext);
            }

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
                    Map<Integer, File> lastSensorFiles = mRawSensorInfo.getLastSensorFilesMap();
                    if (wantAccel && lastSensorFiles.get(Sensor.TYPE_ACCELEROMETER) != null) {
                        File imuFile = lastSensorFiles.get(Sensor.TYPE_ACCELEROMETER);
                        msg.append(getSensorData(imuFile));
                        msg.append(SENSOR_DATA_END_MARKER);
                        msg.append("\n");
                    }
                    if (wantGyro && lastSensorFiles.get(Sensor.TYPE_GYROSCOPE) != null) {
                        File imuFile = lastSensorFiles.get(Sensor.TYPE_GYROSCOPE);
                        msg.append(getSensorData(imuFile));
                        msg.append(SENSOR_DATA_END_MARKER);
                        msg.append("\n");
                    }
                    if (wantMagnetic && lastSensorFiles.get(Sensor.TYPE_MAGNETIC_FIELD) != null) {
                        File imuFile = lastSensorFiles.get(Sensor.TYPE_MAGNETIC_FIELD);
                        msg.append(getSensorData(imuFile));
                        msg.append(SENSOR_DATA_END_MARKER);
                        msg.append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return mResponseBuilder.error("Failed to open IMU file", mContext);
                }

                return mResponseBuilder.success(msg.toString(), mContext);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return mResponseBuilder.error("Error in IMU recording", mContext);
            }
        } else {
            return mResponseBuilder.error("Error in IMU recording", mContext);
        }
    }

    RemoteRpcResponse handleVideoStartRequest() {
        // Start video recording
        Preview preview = mContext.getPreview();

        Callable<Void> recStartCallable = () -> {
            // Making sure video is switched on
            if (!preview.isVideo()) {
                preview.switchVideo(false, true);
            }
            // In video mode this means "start video"
            mContext.takePicture(false);
            return null;
        };

        // Await recording start
        FutureTask<Void> recStartTask = new FutureTask<>(recStartCallable);
        mContext.runOnUiThread(recStartTask);

        // Await video phase event
        BlockingQueue<VideoPhaseInfo> videoPhaseInfoReporter = preview.getVideoPhaseInfoReporter();
        if (videoPhaseInfoReporter != null) {
            VideoPhaseInfo phaseInfo;
            try {
                phaseInfo = videoPhaseInfoReporter
                        .poll(PHASE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (phaseInfo != null) {
                    return mResponseBuilder.success(phaseInfo.toString(), mContext);
                } else {
                    return mResponseBuilder.error("Failed to retrieve phase info, reached poll limit", mContext);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return mResponseBuilder.error("Failed to retrieve phase info", mContext);
            }
        } else {
            if (MyDebug.LOG) {
                Log.d(TAG, "Video frame info wasn't initialized, failed to retrieve phase info");
            }
            return mResponseBuilder.error("Video frame info wasn't initialized, failed to retrieve phase info", mContext);
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
        return mResponseBuilder.success("", mContext);
    }

    void handleVideoGetRequest(PrintStream outputStream) {
        Preview preview = mContext.getPreview();
        BlockingQueue<String> videoReporter;
        if (preview != null &&
            (videoReporter = preview.getVideoAvailableReporter()) != null) {
            try {
                // await available video file
                if (preview.isVideoRecording()) {
                    videoReporter.take();
                }
                // get file
                ExtendedAppInterface appInterface = mContext.getApplicationInterface();
                File videoFile = appInterface.getLastVideoFile();
                boolean canRead = videoFile.canRead();
                Log.d(TAG, "Can read video file: " + canRead);
                if (videoFile.canRead()) {
                    // Transfer file size in bytes and filename
                    outputStream.println(mResponseBuilder.success(
                            videoFile.length() + "\n" + videoFile.getName() + "\n",
                            mContext
                    ));
                    outputStream.flush();
                    // Transfer file bytes
                    FileInputStream inputStream = new FileInputStream(videoFile);

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();
                    inputStream.close();
                } else {
                    outputStream.println(mResponseBuilder.error("Couldn't get last video file data", mContext));
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                outputStream.println(mResponseBuilder.error("Error getting video file", mContext));
            }
        } else {
            outputStream.println(
                    mResponseBuilder.error("Null reference", mContext)
            );
        }
    }
}
