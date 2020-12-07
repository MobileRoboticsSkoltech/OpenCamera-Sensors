package net.sourceforge.opencamera.sensorlogging;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.StorageUtils;
import net.sourceforge.opencamera.StorageUtilsWrapper;

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
    final private Sensor mSensorGyro;
    final private Sensor mSensorAccel;
    private PrintWriter mGyroBufferedWriter;
    private PrintWriter mAccelBufferedWriter;
    private boolean mIsRecording;

    public RawSensorInfo(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (MyDebug.LOG) {
            Log.d(TAG, "RawSensorInfo");
            if (mSensorGyro == null) {
                Log.d(TAG, "Gyroscope not available");
            }
            if (mSensorAccel == null) {
                Log.d(TAG, "Accelerometer not available");
            }
        }
    }

    public int getSensorMinDelay(int sensorType) {
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            return mSensorAccel.getMinDelay();
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            return mSensorGyro.getMinDelay();
        } else {
            // Unsupported sensorType
            if (MyDebug.LOG) {
                Log.d(TAG, "Unsupported sensor type was provided");
            }
            return 0;
        }
    }

    public void startRecording(MainActivity mainActivity, Date currentVideoDate) {
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

    public void stopRecording() {
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

    public boolean isRecording() {
        return mIsRecording;
    }

    public void enableSensors(int accelSampleRate, int gyroSampleRate) {
        if (MyDebug.LOG) {
            Log.d(TAG, "enableSensors");
        }

        if (mSensorGyro != null) {
            mSensorManager.registerListener(this, mSensorGyro, gyroSampleRate);
        }
        if (mSensorAccel != null) {
            mSensorManager.registerListener(this, mSensorAccel, accelSampleRate);
        }
    }

    public void disableSensors() {
        if (MyDebug.LOG) {
            Log.d(TAG, "disableSensors");
        }
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mIsRecording) {
            StringBuilder sensorData = new StringBuilder();
            for (int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]).append(CSV_SEPARATOR);
            }
            sensorData.append(event.timestamp).append("\n");

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mAccelBufferedWriter.write(sensorData.toString());
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mGyroBufferedWriter.write(sensorData.toString());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO: Add some action or notification for when sensor accuracy decreased
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
            fileWriter = new FileWriter(
                storageUtils.createOutputCaptureInfo(
                        StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO,
                        "csv",
                        "_" + sensorType,
                        lastVideoDate
                )
            );
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
}
