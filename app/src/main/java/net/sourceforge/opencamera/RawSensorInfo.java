package net.sourceforge.opencamera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class RawSensorInfo extends GyroSensor {
    private static final String TAG = "RawSensorInfo";
    private static final String SENSOR_INFO_PATH = "/OpenCamera_sensor_info/";
    private static final String CSV_SEPARATOR = ",";

    private MyStringBuffer mGyroBuffer;
    private MyStringBuffer mAccelBuffer;
    private String mCurrentDirPath;
    boolean buffersInitialized = false;

    public RawSensorInfo(Context context) {
        super(context);
    }

    public void onSensorChanged(SensorEvent event) {
        if (buffersInitialized) {
            // StringBuilder is important here because it does not suffer when threading
            StringBuilder sensorData = new StringBuilder();

            for(int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]);
                sensorData.append(CSV_SEPARATOR);
            }
            sensorData.append(event.timestamp);
            sensorData.append('\n');

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mAccelBuffer.append(sensorData.toString());
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mGyroBuffer.append(sensorData.toString());
            }
        }
    }

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
            PrintStream gyroWriter = new PrintStream(gyroFile);
            PrintStream accelWriter = new PrintStream(accelFile);
            mGyroBuffer = new MyStringBuffer(gyroWriter);
            mAccelBuffer = new MyStringBuffer(accelWriter);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        buffersInitialized = true;

    }

    void stopRecording() {
        if( MyDebug.LOG )
            Log.d(TAG, "Close all files");
        mGyroBuffer.close();
        mAccelBuffer.close();
        buffersInitialized = false;
    }
}
