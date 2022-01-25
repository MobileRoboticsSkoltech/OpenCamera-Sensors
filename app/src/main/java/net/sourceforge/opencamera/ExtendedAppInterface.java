package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.cameracontroller.YuvImageUtils;
import net.sourceforge.opencamera.sensorlogging.FlashController;
import net.sourceforge.opencamera.sensorlogging.RawSensorInfo;
import net.sourceforge.opencamera.sensorlogging.VideoFrameInfo;
import net.sourceforge.opencamera.sensorlogging.VideoPhaseInfo;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Extended implementation of ApplicationInterface, adds raw sensor recording layer to the
 * interface.
 */
public class ExtendedAppInterface extends MyApplicationInterface {
    private static final String TAG = "ExtendedAppInterface";
    private static final int SENSOR_FREQ_DEFAULT_PREF = 0;

    private final RawSensorInfo mRawSensorInfo;

    public FlashController getFlashController() {
        return mFlashController;
    }

    private final FlashController mFlashController;
    private final SharedPreferences mSharedPreferences;
    private final MainActivity mMainActivity;
    private final YuvImageUtils mYuvUtils;

    public VideoFrameInfo setupFrameInfo() throws IOException {
        return new VideoFrameInfo(
                getLastVideoDate(), mMainActivity, getSaveFramesPref(), getVideoPhaseInfoReporter()
        );
    }

    ExtendedAppInterface(MainActivity mainActivity, Bundle savedInstanceState) {
        super(mainActivity, savedInstanceState);
        mRawSensorInfo = mainActivity.getRawSensorInfoManager();
        mMainActivity = mainActivity;
        mFlashController = new FlashController(mainActivity);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        // We create it only once here (not during the video) as it is a costly operation
        // (instantiates RenderScript object)
        mYuvUtils = new YuvImageUtils(mainActivity);

    }

    @Override
    void onDestroy() {
        mYuvUtils.close();
        super.onDestroy();
    }

    public BlockingQueue<VideoPhaseInfo> getVideoPhaseInfoReporter() {
        return mMainActivity.getPreview().getVideoPhaseInfoReporter();
    }

    public boolean getIMURecordingPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.IMURecordingPreferenceKey, false);
    }

    public boolean getRemoteRecControlPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.RemoteRecControlPreferenceKey, false);
    }

    private boolean getAccelPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.AccelPreferenceKey, true);
    }

    private boolean getGyroPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GyroPreferenceKey, true);
    }

    private boolean getMagneticPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.MagnetometerPrefKey, true);
    }

    private boolean getRotationPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.RotationPreferenceKey, true);
    }

    private boolean getGravityPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.GravityPreferenceKey, true);
    }

    /**
     * Retrieves gyroscope and accelerometer sample rate preference and converts it to number
     */
    public int getSensorSampleRatePref(String prefKey) {
        String sensorSampleRateString = mSharedPreferences.getString(
                prefKey,
                String.valueOf(SENSOR_FREQ_DEFAULT_PREF)
        );
        int sensorSampleRate = SENSOR_FREQ_DEFAULT_PREF;
        try {
            if (sensorSampleRateString != null) sensorSampleRate = Integer.parseInt(sensorSampleRateString);
        }
        catch(NumberFormatException exception) {
            if( MyDebug.LOG )
                Log.e(TAG, "Sample rate invalid format: " + sensorSampleRateString);
        }
        return sensorSampleRate;
    }

    public boolean getSaveFramesPref() {
        return mSharedPreferences.getBoolean(PreferenceKeys.saveFramesPreferenceKey, false);
    }

    public void startImu(
            boolean wantAccel, boolean wantGyro,
            boolean wantMagnetic, boolean wantGravity,
            boolean wantRotation, Date currentDate
    ) {
        if (wantAccel) {
            int accelSampleRate = getSensorSampleRatePref(PreferenceKeys.AccelSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_ACCELEROMETER, accelSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Accelerometer unavailable");
            }
        }
        if (wantGyro) {
            int gyroSampleRate = getSensorSampleRatePref(PreferenceKeys.GyroSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_GYROSCOPE, gyroSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Gyroscope unavailable");
            }
        }
        if (wantMagnetic) {
            int magneticSampleRate = getSensorSampleRatePref(PreferenceKeys.MagneticSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_MAGNETIC_FIELD, magneticSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Magnetometer unavailable");
            }
        }

        if (!mRawSensorInfo.enableSensor(RawSensorInfo.TYPE_GPS, 0)) {
            mMainActivity.getPreview().showToast(null, "GPS unavailable");
        }


            //mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate, get Pref(), getAccelPref())
        if (wantRotation) {
            int rotationSampleRate = getSensorSampleRatePref(PreferenceKeys.RotationSampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_ROTATION_VECTOR, rotationSampleRate)) {
                mMainActivity.getPreview().showToast(null, "Rotation vector unavailable");
            }
        }

        if (wantGravity) {
            int gravitySampleRate = getSensorSampleRatePref(PreferenceKeys.GravitySampleRatePreferenceKey);
            if (!mRawSensorInfo.enableSensor(Sensor.TYPE_GRAVITY, gravitySampleRate)) {
                mMainActivity.getPreview().showToast(null, "Gravity unavailable");
            }
        }

        //mRawSensorInfo.startRecording(mMainActivity, mLastVideoDate, get Pref(), getAccelPref())
        Map<Integer, Boolean> wantSensorRecordingMap = new HashMap<>();
        wantSensorRecordingMap.put(Sensor.TYPE_ACCELEROMETER, getAccelPref());
        wantSensorRecordingMap.put(Sensor.TYPE_GYROSCOPE, getGyroPref());
        wantSensorRecordingMap.put(Sensor.TYPE_MAGNETIC_FIELD, getMagneticPref());
        wantSensorRecordingMap.put(Sensor.TYPE_GRAVITY, getGravityPref());
        wantSensorRecordingMap.put(Sensor.TYPE_ROTATION_VECTOR, getRotationPref());
        wantSensorRecordingMap.put(RawSensorInfo.TYPE_GPS, true);
        mRawSensorInfo.startRecording(mMainActivity, currentDate, wantSensorRecordingMap);
    }

    @Override
    public void startingVideo() {
        if (MyDebug.LOG) {
            Log.d(TAG, "starting video");
        }
        if (getIMURecordingPref() && useCamera2() && (getGyroPref() || getAccelPref() || getMagneticPref())) {
            // Extracting sample rates from shared preferences
            try {
                mMainActivity.getPreview().showToast("Starting video with IMU recording...", true);
                startImu(getAccelPref(), getGyroPref(), getMagneticPref(), getGravityPref(), getRotationPref(), mLastVideoDate);
                // TODO: add message to strings.xml
            } catch (NumberFormatException e) {
                if (MyDebug.LOG) {
                    Log.e(TAG, "Failed to retrieve the sample rate preference value");
                    e.printStackTrace();
                }
            }
        } else if (getIMURecordingPref() && !useCamera2()) {
            mMainActivity.getPreview().showToast(null, "Not using Camera2API! Can't record in sync with IMU");
            mMainActivity.getPreview().stopVideo(false);
        } else if (getIMURecordingPref() && !(getGyroPref() ||  getMagneticPref() || getAccelPref())) {
            mMainActivity.getPreview().showToast(null, "Requested IMU recording but no sensors were enabled");
            mMainActivity.getPreview().stopVideo(false);
        }

        if (getVideoFlashPref()) {
            try {
                mFlashController.startRecording(mLastVideoDate);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start flash controller");
                e.printStackTrace();
            }
        }

        super.startingVideo();
    }

    @Override
    public void stoppingVideo() {
        if (MyDebug.LOG) {
            Log.d(TAG, "stopping video");
        }
        if (mRawSensorInfo.isRecording()) {
            mRawSensorInfo.stopRecording();
            mRawSensorInfo.disableSensors();

            // TODO: add message to strings.xml
            mMainActivity.getPreview().showToast("Stopping video with IMU recording...", true);
        }

        if (mFlashController.isRecording()) {
            mFlashController.stopRecording();
        }

        super.stoppingVideo();
    }

    public void onFrameInfoRecordingFailed() {
        mMainActivity.getPreview().showToast(null, "Couldn't write frame timestamps");
    }

    public YuvImageUtils getYuvUtils() {
        return mYuvUtils;
    }
}
