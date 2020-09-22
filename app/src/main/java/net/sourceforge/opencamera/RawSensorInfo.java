package net.sourceforge.opencamera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class RawSensorInfo implements SensorEventListener {
    private static final String TAG = "RawSensorInfo";
    private static final String SENSOR_INFO_PATH = "/OpenCamera_sensor_info/";
    private static final String CSV_SEPARATOR = ",";

    final private SensorManager mSensorManager;
    final private Sensor mSensor;
    final private Sensor mSensorAccel;
    private PrintWriter mGyroBufferedWriter;
    private PrintWriter mAccelBufferedWriter;
    private boolean mIsRecording;

    public RawSensorInfo(Context context) {
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if( MyDebug.LOG ) {
            Log.d(TAG, "RawSensorInfo");
            if( mSensor == null )
                Log.d(TAG, "gyroscope not available");
            else if( mSensorAccel == null )
                Log.d(TAG, "accelerometer not available");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mIsRecording) {
            StringBuffer sensorData = new StringBuffer();

            for(int j = 0; j < 3; j++) {
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

    void startRecording() {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US).format(new Date()
        );

        String mCurrentDirPath = Environment.getExternalStorageDirectory().getPath()
                + SENSOR_INFO_PATH
                + timestamp + "/";

        boolean dirsOk = new File(mCurrentDirPath).mkdirs();
        if (!dirsOk) {
            if( MyDebug.LOG )
                Log.e(TAG, "Cannot create directory for sensors");
        }

        String gyroFile =  mCurrentDirPath + timestamp + "gyro" + ".csv";
        String accelFile = mCurrentDirPath + timestamp + "acc" + ".csv";

        try {
            FileWriter gyroWriter = new FileWriter(gyroFile);
            FileWriter accelWriter = new FileWriter(accelFile);

            mGyroBufferedWriter = new PrintWriter(
                    new BufferedWriter(gyroWriter)
            );
            mAccelBufferedWriter = new PrintWriter(
                    new BufferedWriter(accelWriter)
            );
            mIsRecording = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to open csv files");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void stopRecording() {
        if( MyDebug.LOG )
            Log.d(TAG, "Close all files");
        if (mGyroBufferedWriter != null)
            mGyroBufferedWriter.close();
        if (mAccelBufferedWriter != null)
            mAccelBufferedWriter.close();
        mIsRecording = false;
    }

    boolean isRecording() {
        return mIsRecording;
    }

    void enableSensors() {
        if( MyDebug.LOG )
            Log.d(TAG, "enableSensors");

        // TODO: add customizable frequency in registerListener()
        if( mSensor != null )
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
        if( mSensorAccel != null )
            mSensorManager.registerListener(this, mSensorAccel, SensorManager.SENSOR_DELAY_UI);
    }

    void disableSensors() {
        if( MyDebug.LOG )
            Log.d(TAG, "disableSensors");
        mSensorManager.unregisterListener(this);
    }
}
