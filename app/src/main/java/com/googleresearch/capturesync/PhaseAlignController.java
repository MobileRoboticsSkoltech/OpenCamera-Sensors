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

import java.util.Locale;

/**
 * Calculates and adjusts camera phase by inserting frames of varying exposure lengths.
 *
 * <p>Phase alignment is an iterative process. Running for more iterations results in higher
 * accuracy up to the stability of the camera and the accuracy of the phase alignment configuration
 * values.
 */
public class PhaseAlignController {
    public static final String INJECT_FRAME = "injection_frame";
    private static final String TAG = "PhaseAlignController";

    // Maximum number of phase alignment iteration steps in the alignment process.
    // TODO(samansari): Make this a parameter that you pass in to this class. Then make the class that
    // constructs this pass the constant in.
    private static final int MAX_ITERATIONS = 60;
    // Delay after an alignment step to wait for phase to settle before starting the next iteration.
    private static final int PHASE_SETTLE_DELAY_MS = 400;
    private final MainActivity context;

    private final TextView phaseError;

    private final Handler handler;
    private final Object lock = new Object();
    private boolean inAlignState = false;
    private boolean wasAligned = false;

    private PhaseAligner phaseAligner;
    private final PhaseConfig phaseConfig;
    private PhaseResponse latestResponse;

    public PhaseAlignController(PhaseConfig config, MainActivity context, TextView phaseError) {
        handler = new Handler();
        phaseConfig = config;
        phaseAligner = new PhaseAligner(config);
        Log.v(TAG, "Loaded phase align config.");
        this.context = context;
        this.phaseError = phaseError;
    }

    protected void setPeriodNs(long periodNs) {
        phaseConfig.setPeriodNs(periodNs);
        this.phaseAligner = new PhaseAligner(phaseConfig);
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
        latestResponse = phaseAligner.passTimestamp(timestampNs);
        updatePhaseError();
        return latestResponse.phaseNs();
    }

    private void updatePhaseError() {
        final String phaseErrorStr = String.format(Locale.ENGLISH,
                "Phase Error: %.2f ms", TimeUtils.nanosToMillis((double) latestResponse.diffFromGoalNs())
        );
        context.runOnUiThread(() -> {
            phaseError.setText(phaseErrorStr);
            phaseError.setTextColor(latestResponse.isAligned() ? Color.GREEN : Color.RED);
        });
    }

    /** Submit an frame with a specific exposure to offset future frames and align phase. */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void doPhaseAlignStep() {
        Log.i(
                TAG,
                String.format(
                        "Current Phase: %.3f ms, Diff: %.3f ms, inserting frame exposure %.6f ms, lower bound"
                                + " %.6f ms.",
                        latestResponse.phaseNs() * 1e-6f,
                        latestResponse.diffFromGoalNs() * 1e-6f,
                        latestResponse.exposureTimeToShiftNs() * 1e-6f,
                        phaseAligner.getConfig().minExposureNs() * 1e-6f));

        final CameraController cameraController = context.getPreview().getCameraController();
        if (cameraController == null) {
            throw new IllegalStateException("Frame injection failed: camera is not open.");
        }
        if (!(cameraController instanceof CameraController2)) {
            throw new IllegalStateException("Frame injection failed: not using Camera2 API.");
        }

        try {
            ((CameraController2) cameraController).injectFrameWithExposure(latestResponse.exposureTimeToShiftNs());
        } catch (CameraAccessException e) {
            Log.e(TAG, "Frame injection failed.", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startAlign() {
        context.getPreview().showToast(context.getRecSyncToastBoxer(), R.string.phase_alignment_started);
        synchronized (lock) {
            if (inAlignState) {
                Log.i(TAG, "startAlign() called while already aligning.");
                return;
            }
            inAlignState = true;
            // Start inserting frames every {@code PHASE_SETTLE_DELAY_MS} ms to try and push the phase to
            // the goal phase. Stop after aligned to threshold or after {@code MAX_ITERATIONS}.
            handler.post(() -> work(MAX_ITERATIONS));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void work(int iterationsLeft) {
        if (latestResponse == null) {
            inAlignState = false;
            wasAligned = false;
            Log.e(TAG, "Aligning failed: no timestamps available, latest response is null.");
            context.getPreview().showToast(context.getRecSyncToastBoxer(), R.string.phase_alignment_failed);
            return;
        }

        // Check if Aligned / Not Aligned but able to iterate / Ran out of iterations.
        if (latestResponse.isAligned()) { // Aligned.
            Log.i(
                    TAG,
                    String.format(
                            "Reached: Current Phase: %.3f ms, Diff: %.3f ms",
                            latestResponse.phaseNs() * 1e-6f, latestResponse.diffFromGoalNs() * 1e-6f));
            synchronized (lock) {
                inAlignState = false;
            }

            wasAligned = true;
            Log.d(TAG, "Aligned.");
            context.getPreview().showToast(context.getRecSyncToastBoxer(), R.string.phase_alignment_finished);
        } else if (!latestResponse.isAligned() && iterationsLeft > 0) {
            // Not aligned but able to run another alignment iteration.
            doPhaseAlignStep();
            Log.v(TAG, "Queued another phase align step.");
            // TODO (samansari) : Replace this brittle delay-based solution to a response-based one.
            handler.postDelayed(
                    () -> work(iterationsLeft - 1), PHASE_SETTLE_DELAY_MS); // Try again after it settles.
        } else { // Reached max iterations before aligned.
            Log.i(
                    TAG,
                    String.format(
                            "Failed to Align, Stopping at: Current Phase: %.3f ms, Diff: %.3f ms",
                            latestResponse.phaseNs() * 1e-6f, latestResponse.diffFromGoalNs() * 1e-6f));
            synchronized (lock) {
                inAlignState = false;
            }

            wasAligned = false;
            Log.d(TAG, "Finishing alignment, reached max iterations.");
            context.getPreview().showToast(context.getRecSyncToastBoxer(), R.string.phase_alignment_failed);
        }
    }

    /**
     * Indicates whether the last finished alignment attempt was successful.
     *
     * @return true if the last alignment attempt was successful, false if it wasn't or no attempts
     * were made.
     */
    public boolean wasAligned() {
        return wasAligned;
    }
}
