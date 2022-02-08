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

package com.googleresearch.capturesync.softwaresync.phasealign;

import java.util.Locale;

/**
 * PhaseAligner response providing image stream phase alignment results.
 */
public final class PhaseResponse {
    private final long mPhaseNs;
    private final long mExposureTimeToShiftNs;
    private final long mFrameDurationToShiftNs;
    private final long mDiffFromGoalNs;
    private final boolean mIsAligned;

    private PhaseResponse(
            long phaseNs,
            long exposureTimeToShiftNs,
            long frameDurationToShiftNs,
            long diffFromGoalNs,
            boolean isAligned) {
        mPhaseNs = phaseNs;
        mExposureTimeToShiftNs = exposureTimeToShiftNs;
        mFrameDurationToShiftNs = frameDurationToShiftNs;
        mDiffFromGoalNs = diffFromGoalNs;
        mIsAligned = isAligned;
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for new PhaseResponses. All parameters are required.
     */
    static final class Builder {
        private Long mPhaseNs;
        private Long mExposureTimeToShiftNs;
        private Long mFrameDurationToShiftNs;
        private Long mDiffFromGoalNs;
        private Boolean mIsAligned;

        Builder() {
        }

        Builder setPhaseNs(long phaseNs) {
            mPhaseNs = phaseNs;
            return this;
        }

        Builder setExposureTimeToShiftNs(long exposureTimeToShiftNs) {
            mExposureTimeToShiftNs = exposureTimeToShiftNs;
            return this;
        }

        Builder setFrameDurationToShiftNs(long frameDurationToShiftNs) {
            mFrameDurationToShiftNs = frameDurationToShiftNs;
            return this;
        }

        Builder setDiffFromGoalNs(long diffFromGoalNs) {
            mDiffFromGoalNs = diffFromGoalNs;
            return this;
        }

        Builder setIsAligned(boolean isAligned) {
            mIsAligned = isAligned;
            return this;
        }

        PhaseResponse build() {
            String missing = "";
            if (mPhaseNs == null) {
                missing += " mPhaseNs";
            }
            if (mExposureTimeToShiftNs == null) {
                missing += " mExposureTimeToShiftNs";
            }
            if (mFrameDurationToShiftNs == null) {
                missing += " mFrameDurationToShiftNs";
            }
            if (mDiffFromGoalNs == null) {
                missing += " mDiffFromGoalNs";
            }
            if (mIsAligned == null) {
                missing += " mIsAligned";
            }

            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing required properties:" + missing);
            }

            return new PhaseResponse(
                    mPhaseNs,
                    mExposureTimeToShiftNs,
                    mFrameDurationToShiftNs,
                    mDiffFromGoalNs,
                    mIsAligned);
        }
    }

    /**
     * The measured phase in this response.
     */
    public long phaseNs() {
        return mPhaseNs;
    }

    /**
     * Estimated sensor exposure time needed in an inserted frame to align the phase to the goal
     * phase. This should be used to set CaptureRequest.SENSOR_EXPOSURE_TIME. This currently overrides
     * setting frame duration and is the only method for introducing phase shift.
     */
    public long exposureTimeToShiftNs() {
        return mExposureTimeToShiftNs;
    }

    /**
     * Difference between the goal phase and the phase in this response.
     */
    public long diffFromGoalNs() {
        return mDiffFromGoalNs;
    }

    /**
     * True if the current phase is within the threshold of the goal phase.
     */
    public boolean isAligned() {
        return mIsAligned;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,
                "PhaseResponse{phaseNs=%d, exposureTimeToShiftNs=%d, frameDurationToShiftNs=%d,"
                        + " diffFromGoalNs=%d, isAligned=%s}",
                mPhaseNs, mExposureTimeToShiftNs, mFrameDurationToShiftNs, mDiffFromGoalNs, mIsAligned);
    }
}
