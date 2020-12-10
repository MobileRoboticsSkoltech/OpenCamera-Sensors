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


    public boolean isSensorAvailable(int sensorType) {
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            return mSensorAccel != null;
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            return mSensorGyro != null;
        } else {
            if (MyDebug.LOG) {
                Log.e(TAG, "Requested unsupported sensor");
            }
            throw new IllegalArgumentException();
        }
    }

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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mIsRecording) {
            StringBuilder sensorData = new StringBuilder();
            for (int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]).append(CSV_SEPARATOR);
            }
            sensorData.append(event.timestamp).append("\n");

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && mAccelBufferedWriter != null) {
                mAccelBufferedWriter.write(sensorData.toString());
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && mGyroBufferedWriter != null) {
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

    public void startRecording(MainActivity mainActivity, Date currentVideoDate) {
        startRecording(mainActivity, currentVideoDate, true, true);
    }

    public void startRecording(MainActivity mainActivity, Date currentVideoDate, boolean wantGyroRecording, boolean wantAccelRecording) {
        try {
            if (wantGyroRecording && mSensorGyro != null) {
                mGyroBufferedWriter = setupRawSensorInfoWriter(
                        mainActivity, SENSOR_TYPE_GYRO, currentVideoDate
                );
            }
            if (wantAccelRecording && mSensorAccel != null) {
                mAccelBufferedWriter = setupRawSensorInfoWriter(
                        mainActivity, SENSOR_TYPE_ACCEL, currentVideoDate
                );
            }
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
        enableSensor(Sensor.TYPE_GYROSCOPE, gyroSampleRate);
        enableSensor(Sensor.TYPE_ACCELEROMETER, accelSampleRate);
    }


    /**
     * Enables sensor with specified frequency
     * @return Returns false if sensor isn't available
     */
    public boolean enableSensor(int sensorType, int sampleRate) {
        if (MyDebug.LOG) {
            Log.d(TAG, "enableSensor");
        }

        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            if (mSensorAccel == null) return false;
            mSensorManager.registerListener(this, mSensorAccel, sampleRate);
            return true;
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            if (mSensorGyro == null) return false;
            mSensorManager.registerListener(this, mSensorGyro, sampleRate);
            return true;
        } else {
            return false;
        }
    }

    public void disableSensors() {
        if (MyDebug.LOG) {
            Log.d(TAG, "disableSensors");
        }
        mSensorManager.unregisterListener(this);
    }
}
