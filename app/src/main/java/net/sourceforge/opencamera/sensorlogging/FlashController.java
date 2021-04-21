package net.sourceforge.opencamera.sensorlogging;

import android.os.SystemClock;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.StorageUtils;
import net.sourceforge.opencamera.StorageUtilsWrapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

public class FlashController {
    private final static String TAG = "FlashController";
    private final static String TIMESTAMP_FILE_SUFFIX = "_flash";
    private BufferedWriter mFlashBufferedWriter;
    private final MainActivity mContext;

    public boolean isRecording() {
        return mIsRecording;
    }

    private volatile boolean mIsRecording;


    public FlashController(MainActivity context) {
        mContext = context;
    }

    public void startRecording(Date currentVideoDate) throws IOException {
        StorageUtilsWrapper storageUtils = mContext.getStorageUtils();

        File frameTimestampFile = storageUtils.createOutputCaptureInfo(
                StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, "csv", TIMESTAMP_FILE_SUFFIX, currentVideoDate
        );
        mFlashBufferedWriter = new BufferedWriter(
                new PrintWriter(frameTimestampFile)
        );
        mIsRecording = true;
    }

    public void onFlashFired() {
        if (isRecording() && mFlashBufferedWriter != null) {
            long timestamp = SystemClock.elapsedRealtimeNanos();
            try {
                mFlashBufferedWriter
                        .append(Long.toString(timestamp))
                        .append("\n");
            } catch (IOException e) {
                Log.d(TAG, "Failed to write flash timestamp");
            }
        }
    }

    public void stopRecording() {
        mIsRecording = false;
        try {
            if (mFlashBufferedWriter != null) {
                Log.d(TAG, "Before writer close()");
                mFlashBufferedWriter.flush();
                mFlashBufferedWriter.close();
                mFlashBufferedWriter = null;
                Log.d(TAG, "After writer close()");
            }
        } catch (IOException e) {
            Log.d(TAG, "Exception occurred when attempting to close mFlashBufferedWriter");
            e.printStackTrace();
        }
    }
}
