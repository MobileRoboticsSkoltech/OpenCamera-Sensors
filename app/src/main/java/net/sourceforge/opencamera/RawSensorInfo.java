package net.sourceforge.opencamera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;


/**
 * Handles gyroscope and accelerometer raw info recording
 */
public class RawSensorInfo implements SensorEventListener {
    private static final String TAG = "RawSensorInfo";
    private static final String SENSOR_TYPE_ACCEL = "accel";
    private static final String SENSOR_TYPE_GYRO = "gyro";
    private static final String CSV_SEPARATOR = ",";

    final private SensorManager mSensorManager;
    final private Sensor mSensor;
    final private Sensor mSensorAccel;
    private PrintWriter mGyroBufferedWriter;
    private PrintWriter mAccelBufferedWriter;
    private boolean mIsRecording;

    public RawSensorInfo(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (MyDebug.LOG) {
            Log.d(TAG, "RawSensorInfo");
            if (mSensor == null) {
                Log.d(TAG, "gyroscope not available");
            }
            if (mSensorAccel == null) {
                Log.d(TAG, "accelerometer not available");
            }
        }
    }

    public int getSensorMinDelay(int sensorType) {
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            return mSensorAccel.getMinDelay();
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            return mSensor.getMinDelay();
        } else {
            // Unsupported sensorType
            if (MyDebug.LOG) {
                Log.d(TAG, "Unsupported sensor type was provided");
            }
            return 0;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mIsRecording) {
            StringBuffer sensorData = new StringBuffer();

            for (int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]);
                sensorData.append(CSV_SEPARATOR);
            }
            sensorData.append(event.timestamp);
            sensorData.append('\n');

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mAccelBufferedWriter.write(sensorData.toString());
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mGyroBufferedWriter.write(sensorData.toString());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO: Add logs for when sensor accuracy decreased
    }

    /**
     * Handles sensor info file creation, uses StorageUtils to work both with SAF and standard file
     * access.
     */
    private FileWriter getRawSensorInfoFileWriter(MainActivity mainActivity, String sensorType,
            Date lastVideoDate) throws IOException {
        StorageUtilsWrapper storageUtils = mainActivity.getStorageUtils();
        FileWriter fileWriter;
        try {
            if (storageUtils.isUsingSAF()) {
                Uri saveUri = storageUtils.createOutputCaptureInfoFileSAF(
                        StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, sensorType, "csv", lastVideoDate
                );
                ParcelFileDescriptor rawSensorInfoPfd = mainActivity
                        .getContentResolver()
                        .openFileDescriptor(saveUri, "w");
                fileWriter = new FileWriter(rawSensorInfoPfd.getFileDescriptor());
                File saveFile = storageUtils.getFileFromDocumentUriSAF(saveUri, false);
                storageUtils.broadcastFile(saveFile, true, false, true);

            } else {
                File saveFile = storageUtils.createOutputCaptureInfoFile(
                        StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, sensorType, "csv", lastVideoDate
                );
                fileWriter = new FileWriter(saveFile);
                if (MyDebug.LOG) {
                    Log.d(TAG, "save to: " + saveFile.getAbsolutePath());
                }
                storageUtils.broadcastFile(saveFile, false, false, false);
            }
            return fileWriter;
        } catch (IOException e) {
            e.printStackTrace();
            if (MyDebug.LOG) {
                Log.e(TAG, "failed to open raw sensor info files");
            }
            throw new IOException(e);
        }
    }

    private PrintWriter setupRawSensorInfoWriter(MainActivity mainActivity, String sensorType,
            Date currentVideoDate) throws IOException {
        FileWriter rawSensorInfoFileWriter = getRawSensorInfoFileWriter(
                mainActivity, sensorType, currentVideoDate
        );
        PrintWriter rawSensorInfoWriter = new PrintWriter(
                new BufferedWriter(rawSensorInfoFileWriter)
        );
        return rawSensorInfoWriter;
    }

    void startRecording(MainActivity mainActivity, Date currentVideoDate) {
        try {
            mGyroBufferedWriter = setupRawSensorInfoWriter(
                    mainActivity, SENSOR_TYPE_GYRO, currentVideoDate
            );
            mAccelBufferedWriter = setupRawSensorInfoWriter(
                    mainActivity, SENSOR_TYPE_ACCEL, currentVideoDate
            );
            mIsRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
            if (MyDebug.LOG) {
                Log.e(TAG, "Unable to setup sensor info writer");
            }
        }
    }

    void stopRecording() {
        if (MyDebug.LOG) {
            Log.d(TAG, "Close all files");
        }
        if (mGyroBufferedWriter != null) {
            mGyroBufferedWriter.flush();
            mGyroBufferedWriter.close();
        }
        if (mAccelBufferedWriter != null) {
            mAccelBufferedWriter.flush();
            mAccelBufferedWriter.close();
        }
        mIsRecording = false;
    }

    boolean isRecording() {
        return mIsRecording;
    }

    void enableSensors(int accelSampleRate, int gyroSampleRate) {
        if (MyDebug.LOG) {
            Log.d(TAG, "enableSensors");
        }

        if (mSensor != null) {
            mSensorManager.registerListener(this, mSensor, gyroSampleRate);
        }
        if (mSensorAccel != null) {
            mSensorManager.registerListener(this, mSensorAccel, accelSampleRate);
        }
    }

    void disableSensors() {
        if (MyDebug.LOG) {
            Log.d(TAG, "disableSensors");
        }
        mSensorManager.unregisterListener(this);
    }
}
