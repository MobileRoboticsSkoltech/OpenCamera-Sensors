/**
 * Copyright 2019 The Google Research Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googleresearch.capturesync.softwaresync.phasealign;

/** PhaseAligner response providing image stream phase alignment results. */
public final class PhaseResponse {
  private final long phaseNs;
  private final long exposureTimeToShiftNs;
  private final long frameDurationToShiftNs;
  private final long diffFromGoalNs;
  private final boolean isAligned;

  private PhaseResponse(
      long phaseNs,
      long exposureTimeToShiftNs,
      long frameDurationToShiftNs,
      long diffFromGoalNs,
      boolean isAligned) {
    this.phaseNs = phaseNs;
    this.exposureTimeToShiftNs = exposureTimeToShiftNs;
    this.frameDurationToShiftNs = frameDurationToShiftNs;
    this.diffFromGoalNs = diffFromGoalNs;
    this.isAligned = isAligned;
  }

  static Builder builder() {
    return new Builder();
  }

  /** Builder for new PhaseResponses. All parameters are required. */
  static final class Builder {
    private Long phaseNs;
    private Long exposureTimeToShiftNs;
    private Long frameDurationToShiftNs;
    private Long diffFromGoalNs;
    private Boolean isAligned;

    Builder() {}

    Builder setPhaseNs(long phaseNs) {
      this.phaseNs = phaseNs;
      return this;
    }

    Builder setExposureTimeToShiftNs(long exposureTimeToShiftNs) {
      this.exposureTimeToShiftNs = exposureTimeToShiftNs;
      return this;
    }

    Builder setFrameDurationToShiftNs(long frameDurationToShiftNs) {
      this.frameDurationToShiftNs = frameDurationToShiftNs;
      return this;
    }

    Builder setDiffFromGoalNs(long diffFromGoalNs) {
      this.diffFromGoalNs = diffFromGoalNs;
      return this;
    }

    Builder setIsAligned(boolean isAligned) {
      this.isAligned = isAligned;
      return this;
    }

    PhaseResponse build() {
      String missing = "";
      if (this.phaseNs == null) {
        missing += " phaseNs";
      }
      if (this.exposureTimeToShiftNs == null) {
        missing += " exposureTimeToShiftNs";
      }
      if (this.frameDurationToShiftNs == null) {
        missing += " frameDurationToShiftNs";
      }
      if (this.diffFromGoalNs == null) {
        missing += " diffFromGoalNs";
      }
      if (this.isAligned == null) {
        missing += " isAligned";
      }

      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }

      return new PhaseResponse(
          this.phaseNs,
          this.exposureTimeToShiftNs,
          this.frameDurationToShiftNs,
          this.diffFromGoalNs,
          this.isAligned);
    }
  }

  /** The measured phase in this response. */
  public long phaseNs() {
    return phaseNs;
  }

  /**
   * Estimated sensor exposure time needed in an inserted frame to align the phase to the goal
   * phase. This should be used to set CaptureRequest.SENSOR_EXPOSURE_TIME. This currently overrides
   * setting frame duration and is the only method for introducing phase shift.
   */
  public long exposureTimeToShiftNs() {
    return exposureTimeToShiftNs;
  }

  /** Difference between the goal phase and the phase in this response. */
  public long diffFromGoalNs() {
    return diffFromGoalNs;
  }

  /** True if the current phase is within the threshold of the goal phase. */
  public boolean isAligned() {
    return isAligned;
  }

  @Override
  public String toString() {
    return String.format(
        "PhaseResponse{phaseNs=%d, exposureTimeToShiftNs=%d, frameDurationToShiftNs=%d,"
            + " diffFromGoalNs=%d, isAligned=%s}",
        phaseNs, exposureTimeToShiftNs, frameDurationToShiftNs, diffFromGoalNs, isAligned);
  }
}
