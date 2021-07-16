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

/**
 * Calculates the current camera phase and returns the necessary exposure offset needed to align.
 * Runs in the clock domain of the timestamps passed in to `passTimestamp`.
 *
 * <p>Note: A single instance of PhaseAligner expects a constant period. Generally this will be for
 * an image stream with a constant frame duration and therefore a constant period and phase. This is
 * normally found for exposures under 33ms, where frame duration is clamped to 33.33ms. PhaseAligner
 * expects the user to have a fixed frame duration and associated period when running phase
 * alignment.
 *
 * <p>Users are expected to instantiate PhaseAligner instances from configuration files specific to
 * their device hardware using `newWithJSONConfig(...)`, or manually creating a config using
 * `loadFromConfig()`.
 *
 * <p>Users pass timestamps from the image sequence and are returned a PhaseResponse, which provides
 * both the current phase state and the estimated exposure and frame duration needed to align. By
 * inserting a new frame into the sequence with the estimated exposure and/or frame duration the
 * sequence should align closer to the desired phase. Given that the actual frame duration of the
 * inserted frame may be different from the estimated, several iterations may be required, and the
 * user can check to stope via when the phase response is aligned with `isAligned`.
 */
public final class PhaseAligner {
  private final PhaseConfig config;

  /** Instantiate phase aligner using configuration options from a PhaseConfig proto. */
  public PhaseAligner(PhaseConfig config) {
    this.config = config;
  }

  /**
   * Given the latest image sequence timestamp, Responds with phase alignment information.
   *
   * <p>The response contains an estimated sensor exposure time or frame duration needed to align
   * align future frames to the desired goal phase, as well as the current alignment state.
   *
   * @param timestampNs timestamp in the same clock domain as used in the phase configuration. This
   *     can be either the local clock domain or the software synchronized leader clock domain, as
   *     long as it stays consistent for the duration of the phase aligner instance.
   * @return PhaseResponse containing the current phase state as well as the estimated sensor
   *     exposure time and frame duration needed to align.
   */
  public final PhaseResponse passTimestamp(long timestampNs) {
    long phaseNs = timestampNs % config.periodNs();
    long diffFromGoalNs = config.goalPhaseNs() - phaseNs;
    boolean isAligned = Math.abs(diffFromGoalNs) < config.alignThresholdNs();

    /* Stop early if already aligned. */
    if (isAligned) {
      return PhaseResponse.builder()
          .setPhaseNs(phaseNs)
          .setExposureTimeToShiftNs(0)
          .setFrameDurationToShiftNs(0)
          .setDiffFromGoalNs(diffFromGoalNs)
          .setIsAligned(isAligned)
          .build();
    }

    /* Since we can only shift phase into the future, shift negative offsets over by one period. */
    long desiredPhaseOffsetNs = diffFromGoalNs;
    if (diffFromGoalNs < 0) {
      desiredPhaseOffsetNs += config.periodNs();
    }

    /*
     * Calculate the frame duration needed to align to the `goalPhaseNs`, using the linear
     * relationship between offset and phase shift. Since durations <= period have no effect, add
     * another period to the duration.
     */
    long frameDurationNsToShift = desiredPhaseOffsetNs / 2 + config.periodNs();

    /*
     * Convert to estimated shift exposure time by removing the average overheadNs. Note: The
     * majority of noise in phase alignment is due to this varying estimated overheadNs.
     *
     * <p>Due to the indirect control of frame duration, choosing offsets <= minExposure causes no
     * change in phase. To avoid this, a minimum offset is manually chosen for the specific device
     * architecture.
     */
    long exposureTimeNsToShift =
        Math.max(config.minExposureNs(), frameDurationNsToShift - config.overheadNs());

    return PhaseResponse.builder()
        .setPhaseNs(phaseNs)
        .setExposureTimeToShiftNs(exposureTimeNsToShift)
        .setFrameDurationToShiftNs(frameDurationNsToShift)
        .setDiffFromGoalNs(diffFromGoalNs)
        .setIsAligned(isAligned)
        .build();
  }

  /** Returns the configuration options used to set up the phase aligner. */
  public final PhaseConfig getConfig() {
    return config;
  }
}
