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
 */

package com.googleresearch.capturesync;

import android.os.Handler;
import android.util.Log;

import com.googleresearch.capturesync.softwaresync.phasealign.PhaseAligner;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseConfig;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseResponse;

import net.sourceforge.opencamera.MainActivity;

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
    private static final int MAX_ITERATIONS = 50;
    // Delay after an alignment step to wait for phase to settle before starting the next iteration.
    private static final int PHASE_SETTLE_DELAY_MS = 400;
    private final MainActivity context;

    private final Handler handler;
    private final Object lock = new Object();
    private boolean inAlignState = false;

    private PhaseAligner phaseAligner;
    private final PhaseConfig phaseConfig;
    private PhaseResponse latestResponse;

    public PhaseAlignController(PhaseConfig config, MainActivity context) {
        handler = new Handler();
        phaseConfig = config;
        phaseAligner = new PhaseAligner(config);
        Log.v(TAG, "Loaded phase align config.");
        this.context = context;
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
        // TODO (samansari) : Pull this into an interface/callback.
        // context.runOnUiThread(() -> context.updatePhaseTextView(latestResponse.diffFromGoalNs()));
        return latestResponse.phaseNs();
    }

    /** Submit an frame with a specific exposure to offset future frames and align phase. */
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

        // TODO(samansari): Make this an interface.
        // context.injectFrame(latestResponse.exposureTimeToShiftNs());
    }

    public void startAlign() {
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

    private void work(int iterationsLeft) {
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

            Log.d(TAG, "Aligned.");
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
            Log.d(TAG, "Finishing alignment, reached max iterations.");
        }
    }
}
