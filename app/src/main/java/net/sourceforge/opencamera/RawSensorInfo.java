package net.sourceforge.opencamera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
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


public class RawSensorInfo extends GyroSensor {
    private static final String TAG = "RawSensorInfo";
    private static final String SENSOR_INFO_PATH = "/OpenCamera_sensor_info/";
    private static final String CSV_SEPARATOR = ",";
    private static final int BUFFER_SIZE = 262144;

    private BufferedWriter gyroBufferedWriter;
    private BufferedWriter accelBufferedWriter;
    private String mCurrentDirPath;
    boolean buffersInitialized = false;

    public RawSensorInfo(Context context) {
        super(context);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (buffersInitialized) {
            StringBuffer sensorData = new StringBuffer();

            for(int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]);
                sensorData.append(CSV_SEPARATOR);
            }
            sensorData.append(event.timestamp);
            sensorData.append('\n');

            try {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    accelBufferedWriter.write(sensorData.toString());
                } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    gyroBufferedWriter.write(sensorData.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    void startRecording() {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US).format(new Date()
        );
        mCurrentDirPath = Environment.getExternalStorageDirectory().getPath()
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

            gyroBufferedWriter = new BufferedWriter(gyroWriter, BUFFER_SIZE);
            accelBufferedWriter = new BufferedWriter(accelWriter, BUFFER_SIZE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to open csv files");
        }

        buffersInitialized = true;

    }

    @Override
    void stopRecording() {
        if( MyDebug.LOG )
            Log.d(TAG, "Close all files");
        try {
            if (gyroBufferedWriter != null)
                gyroBufferedWriter.close();
            if (accelBufferedWriter != null)
                accelBufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to close csv files");
        }
        buffersInitialized = false;
    }
}
