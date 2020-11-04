package net.sourceforge.opencamera;

import android.media.Image;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.StorageUtils;
import net.sourceforge.opencamera.StorageUtilsWrapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FrameInfo implements Closeable {
    private final static String TAG = "FrameInfo";
    private final static int EVERY_N_FRAME = 10;

    //Sequential executor for frame and timestamps IO
    private final Executor frameProcessor = Executors.newSingleThreadExecutor();
    private final Date mVideoDate;
    private final StorageUtilsWrapper mStorageUtils;
    private final BufferedWriter mFrameBufferedWriter;

    private int mFrameNumber = 0;

    public FrameInfo (Date videoDate, MainActivity context) throws IOException {
        mVideoDate = videoDate;
        mStorageUtils = context.getStorageUtils();
        File frameTimestampFile = mStorageUtils.createOutputCaptureInfo(
                StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, ".csv", "_frames", mVideoDate, context
        );
        mFrameBufferedWriter = new BufferedWriter(
                new PrintWriter(frameTimestampFile)
        );
    }

    public void submitProcessFrame(long timestamp) {
        frameProcessor.execute(
                () -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        try {
                            mFrameBufferedWriter
                                    .append(Long.toString(timestamp))
                                    .append("\n");
                            if (mFrameNumber % EVERY_N_FRAME == 0) {
                                if (MyDebug.LOG) {
                                    Log.d(TAG, "Should save frame, timestamp: " + timestamp);
                                }
                                // TODO: implement image saving
                            }
                            mFrameNumber++;
                        } catch(IOException e) {
                            // TODO: we don't want to skip that error (can result in an incomplete time series)
                            Log.e(TAG, "Failed to write frame timestamp: " + timestamp);
                            e.printStackTrace();
                        }
                    } else {
                        // TODO: maybe increase min SDK since this feature is crucial
                    }
                }
        );
    }

    @Override
    public void close() throws IOException {
        if (MyDebug.LOG) {
            Log.d(TAG, "Closing frame info, frame number: " + mFrameNumber);
        }
        if (mFrameBufferedWriter != null) {
            mFrameBufferedWriter.flush();
            mFrameBufferedWriter.close();
        }
    }
}
