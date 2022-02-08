/**
 * Copyright 2019 The Google Research Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech.
 */

package com.googleresearch.capturesync;

import static com.googleresearch.capturesync.softwaresync.SyncConstants.MAX_ITERATIONS;
import static com.googleresearch.capturesync.softwaresync.SyncConstants.PHASE_SETTLE_DELAY_MS;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.googleresearch.capturesync.softwaresync.TimeUtils;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseAligner;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseConfig;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseResponse;

import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.ToastBoxer;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraController2;
import net.sourceforge.opencamera.preview.Preview;

/**
 * Calculates and adjusts camera phase by inserting frames of varying exposure lengths.
 *
 * <p>Phase alignment is an iterative process. Running for more iterations results in higher
 * accuracy up to the stability of the camera and the accuracy of the phase alignment configuration
 * values.
 */
public class PhaseAlignController {
    private static final String TAG = "PhaseAlignController";

    private final Context mContext;
    private final Preview mPreview;
    private final ToastBoxer mToastBoxer;

    private final Handler mHandler;
    private final Object mLock = new Object();
    private Runnable mOnFinished;

    private boolean mInAlignState = false;
    private boolean mStopAlign = false;
    private boolean mWasAligned = false;

    private CameraController2 mCameraController;

    private PhaseAligner mPhaseAligner;
    private final PhaseConfig mPhaseConfig;
    private PhaseResponse mLatestResponse;

    public PhaseAlignController(PhaseConfig config, Context context, Preview preview, ToastBoxer toastBoxer) {
        mHandler = new Handler();
        mPhaseConfig = config;
        mPhaseAligner = new PhaseAligner(config);
        Log.v(TAG, "Loaded phase align config.");
        mContext = context;
        mPreview = preview;
        mToastBoxer = toastBoxer;
    }

    protected void setPeriodNs(long periodNs) {
        mPhaseConfig.setPeriodNs(periodNs);
        mPhaseAligner = new PhaseAligner(mPhaseConfig);
    }

    /**
     * Update the latest phase response from the latest frame timestamp to keep track of phase.
     *
     * <p>The timestamp is nanoseconds in the synchronized leader clock domain.
     *
     * @return phase of timestamp in nanoseconds in the same domain as given.
     */
    public long updateCaptureTimestamp(long timestampNs) {
        // TODO(samansaari) : Rename passTimestamp -> updateCaptureTimestamp or similar in softwaresync.
        mLatestResponse = mPhaseAligner.passTimestamp(timestampNs);
        return mLatestResponse.phaseNs();
    }

    /**
     * Starts phase alignment if it is not running.
     * <p>
     * Needs to be stopped with {@link #mStopAlign} if {@link CameraController} changes during the
     * alignment.
     *
     * @param onFinished a {@link Runnable} to be called when the alignment is finished (regardless
     *                   of was if successful or not). If phase alignment is already running, this
     *                   parameter is ignored.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startAlign(Runnable onFinished) {
        mPreview.showToast(mToastBoxer, R.string.phase_alignment_started);

        final CameraController currentCameraController = mPreview.getCameraController();
        if (currentCameraController == null) {
            throw new IllegalStateException("Alignment start failed: camera is not open.");
        }
        if (!(currentCameraController instanceof CameraController2)) {
            throw new IllegalStateException("Alignment start failed: not using Camera2 API.");
        }
        mCameraController = (CameraController2) currentCameraController;

        synchronized (mLock) {
            if (mInAlignState) {
                Log.i(TAG, "startAlign() called while already aligning.");
                return;
            }
            mInAlignState = true;
            mStopAlign = false;
            mOnFinished = onFinished;
            // Start inserting frames every {@code PHASE_SETTLE_DELAY_MS} ms to try and push the phase to
            // the goal phase. Stop after aligned to threshold or after {@code MAX_ITERATIONS}.
            mHandler.post(() -> work(MAX_ITERATIONS));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void work(int iterationsLeft) {
        if (mLatestResponse == null) {
            onAlignmentFinished(false);
            Log.e(TAG, "Aligning failed: no timestamps available, latest response is null.");
            return;
        }

        // Check if Aligned / Not Aligned but able to iterate / Ran out of iterations.
        if (mLatestResponse.isAligned()) { // Aligned.
            Log.i(
                    TAG,
                    String.format(
                            "Reached: Current Phase: %.3f ms, Diff: %.3f ms",
                            mLatestResponse.phaseNs() * 1e-6f, mLatestResponse.diffFromGoalNs() * 1e-6f));

            onAlignmentFinished(true);
            Log.d(TAG, "Aligned.");
        } else if (!mLatestResponse.isAligned() && iterationsLeft > 0) { // Not aligned but able to run another alignment iteration.
            if (mStopAlign) {
                onAlignmentFinished(false);
                Log.d(TAG, "Stopping alignment as received a command to.");
                return;
            }

            doPhaseAlignStep();
            Log.v(TAG, "Queued another phase align step.");
            // TODO (samansari) : Replace this brittle delay-based solution to a response-based one.
            mHandler.postDelayed(
                    () -> work(iterationsLeft - 1), PHASE_SETTLE_DELAY_MS); // Try again after it settles.
        } else { // Reached max iterations before aligned.
            Log.i(
                    TAG,
                    String.format(
                            "Failed to Align, Stopping at: Current Phase: %.3f ms, Diff: %.3f ms",
                            mLatestResponse.phaseNs() * 1e-6f, mLatestResponse.diffFromGoalNs() * 1e-6f));

            onAlignmentFinished(false);
            Log.d(TAG, "Finishing alignment, reached max iterations.");
        }
    }

    /**
     * Submit a frame with a specific exposure to offset future frames and align phase.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void doPhaseAlignStep() {
        Log.i(
                TAG,
                String.format(
                        "Current Phase: %.3f ms, Diff: %.3f ms, inserting frame exposure %.6f ms, lower bound"
                                + " %.6f ms.",
                        mLatestResponse.phaseNs() * 1e-6f,
                        mLatestResponse.diffFromGoalNs() * 1e-6f,
                        mLatestResponse.exposureTimeToShiftNs() * 1e-6f,
                        mPhaseAligner.getConfig().minExposureNs() * 1e-6f));

        try {
            mCameraController.injectFrameWithExposure(mLatestResponse.exposureTimeToShiftNs());
        } catch (CameraAccessException e) {
            Log.e(TAG, "Frame injection failed.", e);
        }
    }

    private void onAlignmentFinished(boolean wasAligned) {
        synchronized (mLock) {
            mInAlignState = false;
        }
        mWasAligned = wasAligned;
        if (mOnFinished != null) mOnFinished.run();
        mPreview.showToast(mToastBoxer, wasAligned ? R.string.phase_alignment_succeeded : R.string.phase_alignment_failed);
    }

    /**
     * Stop phase alignment if it is running.
     */
    public void stopAlign() {
        if (mInAlignState) mStopAlign = true;
    }

    /**
     * Indicates whether the last finished alignment attempt was successful.
     *
     * @return true if the last alignment attempt was successful, false if it wasn't or no attempts
     * were made.
     */
    public boolean wasAligned() {
        return mWasAligned;
    }

    /**
     * The current phase error description, if it is available.
     *
     * @return a {@link Pair} of a string containing the current phase error and the current
     * alignment status if phase error is determined, or null otherwise.
     */
    public Pair<String, Boolean> getPhaseError() {
        if (mLatestResponse != null) {
            final String phaseError = mContext.getString(R.string.phase_error,
                    TimeUtils.nanosToMillis((double) mLatestResponse.diffFromGoalNs()));
            return new Pair<>(phaseError, mLatestResponse.isAligned());
        } else {
            return null;
        }
    }
}
