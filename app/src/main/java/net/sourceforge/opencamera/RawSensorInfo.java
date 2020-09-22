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
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class RawSensorInfo implements SensorEventListener {
    private static final String TAG = "RawSensorInfo";
    private static final String SENSOR_INFO_PATH = "/OpenCamera_sensor_info/";
    private static final String CSV_SEPARATOR = ",";
    private static final int BUFFER_SIZE = 262144;

    final private SensorManager mSensorManager;
    final private Sensor mSensor;
    final private Sensor mSensorAccel;
    private BufferedWriter mGyroBufferedWriter;
    private BufferedWriter mAccelBufferedWriter;
    private boolean mIsRecording;

    public RawSensorInfo(Context context) {
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);

        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        //mSensorAccel = null;

        if( MyDebug.LOG ) {
            Log.d(TAG, "RawSensorInfo");
            if( mSensor == null )
                Log.d(TAG, "gyroscope not available");
            else if( mSensorAccel == null )
                Log.d(TAG, "accelerometer not available");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
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

            try {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    mAccelBufferedWriter.write(sensorData.toString());
                } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    mGyroBufferedWriter.write(sensorData.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                Log.d(TAG, "Cannot create directory for sensors");
        }

        String gyroFile =  mCurrentDirPath + timestamp + "gyro" + ".csv";
        String accelFile = mCurrentDirPath + timestamp + "acc" + ".csv";

        try {
            PrintWriter gyroWriter = new PrintWriter(gyroFile);
            PrintWriter accelWriter = new PrintWriter(accelFile);

            mGyroBufferedWriter = new BufferedWriter(gyroWriter, BUFFER_SIZE);
            mAccelBufferedWriter = new BufferedWriter(accelWriter, BUFFER_SIZE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to open csv files");
        }

        mIsRecording = true;

    }

    void stopRecording() {
        if( MyDebug.LOG )
            Log.d(TAG, "Close all files");
        try {
            if (mGyroBufferedWriter != null)
                mGyroBufferedWriter.close();
            if (mAccelBufferedWriter != null)
                mAccelBufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to close csv files");
        }

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
