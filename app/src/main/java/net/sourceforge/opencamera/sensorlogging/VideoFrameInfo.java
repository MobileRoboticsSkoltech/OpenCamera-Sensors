package net.sourceforge.opencamera.sensorlogging;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import com.googleresearch.capturesync.SoftwareSyncController;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase;

import net.sourceforge.opencamera.ExtendedAppInterface;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.StorageUtils;
import net.sourceforge.opencamera.StorageUtilsWrapper;
import net.sourceforge.opencamera.cameracontroller.YuvImageUtils;
import net.sourceforge.opencamera.preview.Preview;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles frame images and timestamps saving during video recording,
 * sequential Executor is used to queue saving tasks in the background thread.
 * Images get saved every EVERY_N_FRAME-th time if shouldSaveFrame is true
 */
public class VideoFrameInfo implements Closeable {
    private final static String TAG = "FrameInfo";
    private final static String UNSYNCED_TIMESTAMP_FILE_SUFFIX = "_imu_timestamps";
    private final static String SYNCED_TIMESTAMP_FILE_SUFFIX = "_recsync";
    /*
    Value used to save frames for debugging and matching frames with video
    TODO: in future versions make sure this value is big enough not to cause frame rate drop / buffer allocation problems on devices other than already tested
    */
    private final static int EVERY_N_FRAME = 60;
    private final static int PHASE_CALC_N_FRAMES = 60;

    //Sequential executor for frame and timestamps saving queue
    private final ExecutorService frameProcessor = Executors.newSingleThreadExecutor();
    private final Date mVideoDate;
    private final StorageUtilsWrapper mStorageUtils;
    private final ExtendedAppInterface mAppInterface;
    private final boolean mShouldSaveUnsyncedTimestamps;
    private final boolean mShouldSaveSyncedTimestamps;
    private final boolean mShouldSaveFrames;
    private final MainActivity mContext;
    private final YuvImageUtils mYuvUtils;
    private final BlockingQueue<VideoPhaseInfo> mPhaseInfoReporter;
    private final List<Long> durationsNs;
    private BufferedWriter mUnsyncedFrameBufferedWriter = null;
    private BufferedWriter mSyncedFrameBufferedWriter = null;
    private SoftwareSyncBase softwareSync = null;
    private long mLastTimestamp = 0;

    private int mFrameNumber = 0;

    public BlockingQueue<VideoPhaseInfo> getPhaseInfoReporter() {
        return mPhaseInfoReporter;
    }

    public VideoFrameInfo(
            Date videoDate,
            MainActivity context,
            boolean shouldSaveUnsyncedTimestamps,
            boolean shouldSaveSyncedTimestamps,
            boolean shouldSaveFrames,
            BlockingQueue<VideoPhaseInfo> videoPhaseInfoReporter
    ) {
        mVideoDate = videoDate;
        mStorageUtils = context.getStorageUtils();
        mAppInterface = context.getApplicationInterface();
        mShouldSaveUnsyncedTimestamps = shouldSaveUnsyncedTimestamps;
        mShouldSaveSyncedTimestamps = shouldSaveSyncedTimestamps;
        mShouldSaveFrames = shouldSaveFrames;
        mContext = context;
        mYuvUtils = mAppInterface.getYuvUtils();
        mPhaseInfoReporter = videoPhaseInfoReporter;
        mPhaseInfoReporter.clear();
        durationsNs = new ArrayList<>();
    }

    /**
     * Initializes writers. Frame submitting writes nothing until this method is called.
     *
     * @throws IOException if unable to create files for timestamps recording.
     */
    public void prepare() throws IOException {
        if (mShouldSaveUnsyncedTimestamps) {
            File unsyncedTimestampFile = mStorageUtils.createOutputCaptureInfo(
                    StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, "csv", UNSYNCED_TIMESTAMP_FILE_SUFFIX, mVideoDate
            );
            mUnsyncedFrameBufferedWriter = new BufferedWriter(new PrintWriter(unsyncedTimestampFile));
        }

        if (mShouldSaveSyncedTimestamps) {
            if (!mAppInterface.isSoftwareSyncRunning()) {
                throw new IllegalStateException("Cannot save synced timestamps without RecSync running");
            }
            final SoftwareSyncController softwareSyncController = mAppInterface.getSoftwareSyncController();
            softwareSync = softwareSyncController.getSoftwareSync();

            final String suffix = SYNCED_TIMESTAMP_FILE_SUFFIX +
                    (softwareSyncController.isLeader() ? "_leader_" : "_client_") +
                    softwareSync.getName();
            File syncedTimestampFile = mStorageUtils.createOutputCaptureInfo(
                    StorageUtils.MEDIA_TYPE_RAW_SENSOR_INFO, "csv", suffix, mVideoDate
            );
            mSyncedFrameBufferedWriter = new BufferedWriter(new PrintWriter(syncedTimestampFile));
        }
    }

    public void submitProcessFrame(long timestamp) {
        if (!frameProcessor.isShutdown()) {
            frameProcessor.execute(
                    () -> {
                        // TODO: here we assume that video has more frames than PHASE_CALC_N_FRAMES
                        if (mFrameNumber < PHASE_CALC_N_FRAMES) {
                            // Should calculate phase
                            if (mLastTimestamp != 0) {
                                long duration = timestamp - mLastTimestamp;
                                // add frame duration
                                if (MyDebug.LOG) {
                                    Log.d(TAG, "new frame duration, value: " + duration);
                                }
                                durationsNs.add(duration);
                            }
                            mLastTimestamp = timestamp;
                        } else if (mFrameNumber == PHASE_CALC_N_FRAMES) {
                            // Should report phase
                            Preview preview = mContext.getPreview();

                            long exposureTime = 0;
                            if(preview.getCameraController().captureResultHasExposureTime() ) {
                                exposureTime = preview.getCameraController().captureResultExposureTime();
                            }
                            mPhaseInfoReporter.add(
                                    new VideoPhaseInfo(timestamp, durationsNs, exposureTime)
                            );
                        }

                        writeTimestamp(timestamp);
                        mFrameNumber++;
                    }
            );
        } else {
            Log.e(TAG, "Received new frame after frameProcessor executor shutdown");
        }
    }

    public void submitProcessFrame(long timestamp, byte[] imageData, int width, int height, int rotation) {
        // Submit image data (only if needed)
        if (!frameProcessor.isShutdown()) {
            frameProcessor.execute(
                    () -> {
                        try {
                            if (mShouldSaveFrames && mFrameNumber % EVERY_N_FRAME == 0) {
                                Bitmap bitmap = mYuvUtils.yuv420ToBitmap(imageData, width, height, mContext);

                                if (MyDebug.LOG) {
                                    Log.d(TAG, "Should save frame, timestamp: " + timestamp);
                                }
                                File frameFile = mStorageUtils.createOutputCaptureInfo(
                                        StorageUtils.MEDIA_TYPE_VIDEO_FRAME, "jpg", String.valueOf(timestamp), mVideoDate
                                );
                                writeFrameJpeg(bitmap, frameFile, rotation);
                            }
                        } catch (IOException e) {
                            mAppInterface.onFrameInfoRecordingFailed();
                            Log.e(TAG, "Failed to write frame info, timestamp: " + timestamp);
                            e.printStackTrace();
                            this.close();
                        }
                    }
            );
        } else {
            Log.e(TAG, "Received new frame after frameProcessor executor shutdown");
        }

        // Submit timestamp info (should be used for every frame)
        submitProcessFrame(timestamp);
    }

    private void writeTimestamp(long timestamp) {
        if (mUnsyncedFrameBufferedWriter != null) writeTimestamp(mUnsyncedFrameBufferedWriter, timestamp);
        if (mSyncedFrameBufferedWriter != null) writeTimestamp(mSyncedFrameBufferedWriter,
                softwareSync.leaderTimeForLocalTimeNs(timestamp));
    }

    private void writeTimestamp(BufferedWriter writer, long timestamp) {
        try {
            writer.append(Long.toString(timestamp)).append("\n");
        } catch (IOException e) {
            mAppInterface.onFrameInfoRecordingFailed();
            Log.e(TAG, "Failed to write timestamp " + timestamp + " using " + writer.toString());
            e.printStackTrace();
            this.close();
        }
    }

    private void writeFrameJpeg(Bitmap bitmap, File frameFile, int rotation) throws IOException {
        FileOutputStream fos = new FileOutputStream(frameFile);
        // Apply rotation
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();
    }

    @Override
    public void close() {
        // Clear current phase info to avoid it being reported in the next recordings
        mPhaseInfoReporter.clear();

        if (frameProcessor != null) {
            if (MyDebug.LOG) {
                Log.d(TAG, "Attempting to shutdown frame processor");
            }
            // should let all assigned tasks finish execution
            frameProcessor.shutdown();
        }

        if (MyDebug.LOG) {
            Log.d(TAG, "Closing frame info, frame number: " + mFrameNumber);
        }

        if (mUnsyncedFrameBufferedWriter != null) closeWriter(mUnsyncedFrameBufferedWriter);
        if (mSyncedFrameBufferedWriter != null) closeWriter(mSyncedFrameBufferedWriter);
    }

    private void closeWriter(BufferedWriter writer) {
        final String writerName = writer.toString();
        try {
            Log.d(TAG, "Before " + writerName + " close()");
            writer.close();
            Log.d(TAG, "After " + writerName + " close()");
        } catch (IOException e) {
            Log.d(TAG, "Exception occurred when attempting to close " + writerName);
            e.printStackTrace();
        }
    }
}
