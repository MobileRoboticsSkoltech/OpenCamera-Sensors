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
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech
 */

package com.googleresearch.capturesync;

import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.googleresearch.capturesync.softwaresync.TimeUtils;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseAligner;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseConfig;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseResponse;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraController2;

/**
 * Calculates and adjusts camera phase by inserting frames of varying exposure lengths.
 *
 * <p>Phase alignment is an iterative process. Running for more iterations results in higher
 * accuracy up to the stability of the camera and the accuracy of the phase alignment configuration
 * values.
 */
public class PhaseAlignController {
    private static final String TAG = "PhaseAlignController";

    // Maximum number of phase alignment iteration steps in the alignment process.
    // TODO(samansari): Make this a parameter that you pass in to this class. Then make the class that
    // constructs this pass the constant in.
    private static final int MAX_ITERATIONS = 60;
    // Delay after an alignment step to wait for phase to settle before starting the next iteration.
    private static final int PHASE_SETTLE_DELAY_MS = 400;

    private final MainActivity mMainActivity;
    private final TextView mPhaseError;
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

    public PhaseAlignController(PhaseConfig config, MainActivity context, TextView phaseError) {
        mHandler = new Handler();
        mPhaseConfig = config;
        mPhaseAligner = new PhaseAligner(config);
        Log.v(TAG, "Loaded phase align config.");
        mMainActivity = context;
        mPhaseError = phaseError;
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
        updatePhaseError();
        return mLatestResponse.phaseNs();
    }

    private void updatePhaseError() {
        final String phaseErrorStr = mMainActivity.getString(R.string.phase_error,
                TimeUtils.nanosToMillis((double) mLatestResponse.diffFromGoalNs()));
        mMainActivity.runOnUiThread(() -> {
            mPhaseError.setText(phaseErrorStr);
            mPhaseError.setTextColor(mLatestResponse.isAligned() ? Color.GREEN : Color.RED);
        });
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
        mMainActivity.getPreview().showToast(mMainActivity.getRecSyncToastBoxer(), R.string.phase_alignment_started);

        final CameraController currentCameraController = mMainActivity.getPreview().getCameraController();
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
        mMainActivity.getPreview().showToast(mMainActivity.getRecSyncToastBoxer(),
                wasAligned ? R.string.phase_alignment_succeeded : R.string.phase_alignment_failed);
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
}
