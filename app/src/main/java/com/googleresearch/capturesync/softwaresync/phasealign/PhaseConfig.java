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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A unique PhaseConfig is associated with a type of mobile device such as a Pixel 3, and a specific
 * capture mode, such as for a repeating request with exposures less than 33 ms.
 */
public final class PhaseConfig {
  private long periodNs;
  private final long goalPhaseNs;
  private final long alignThresholdNs;
  private final long overheadNs;
  private final long minExposureNs;

  private PhaseConfig(
      long periodNs, long goalPhaseNs, long alignThresholdNs, long overheadNs, long minExposureNs) {
    this.periodNs = periodNs;
    this.goalPhaseNs = goalPhaseNs;
    this.alignThresholdNs = alignThresholdNs;
    this.overheadNs = overheadNs;
    this.minExposureNs = minExposureNs;
  }

  public void setPeriodNs(long periodNs) {
    this.periodNs = periodNs;
  }

  /** Parse from a given JSON. */
  public static PhaseConfig parseFromJSON(JSONObject json) throws JSONException {
    if (!json.has("periodNs")) {
      throw new IllegalArgumentException("Missing PeriodNs in JSON.");
    }
    if (!json.has("goalPhaseNs")) {
      throw new IllegalArgumentException("Missing GoalPhaseNs in JSON.");
    }
    if (!json.has("alignThresholdNs")) {
      throw new IllegalArgumentException("Missing AlignThresholdNs in JSON.");
    }
    if (!json.has("overheadNs")) {
      throw new IllegalArgumentException("Missing OverheadNs in JSON.");
    }
    if (!json.has("minExposureNs")) {
      throw new IllegalArgumentException("Missing MinExposureNs in JSON.");
    }
    return new PhaseConfig(
        json.getLong("periodNs"),
        json.getLong("goalPhaseNs"),
        json.getLong("alignThresholdNs"),
        json.getLong("overheadNs"),
        json.getLong("minExposureNs"));
  }

  /**
   * Nominal period between two frames in the image sequence. This is usually very close to the
   * `SENSOR_FRAME_DURATION`. The period is assumed to be constant for the duration of phase
   * alignment. ie. generally only in situations where exposure is < 33ms. If the exposure is fixed
   * at a longer exposure, it may also work with a tuned config.
   */
  public long periodNs() {
    return periodNs;
  }

  /* The target phase to align to, usually chosen as half the period. */
  public long goalPhaseNs() {
    return goalPhaseNs;
  }

  /* The threshold on the absolute difference between the goal and current
   * phases to be considered aligned. */
  public long alignThresholdNs() {
    return alignThresholdNs;
  }

  /**
   * Measured average difference between frame duration and sensor exposure time for exposures
   * greater than 33ms.
   */
  public long overheadNs() {
    return overheadNs;
  }

  /* Lower bound sensor exposure time that still causes a phase shift.
   * This is usually slightly less than the period by the overhead. In practice
   * this must be tuned. */
  public long minExposureNs() {
    return minExposureNs;
  }

  public String toString() {
    return "PhaseConfig{"
        + "periodNs="
        + periodNs
        + ", "
        + "goalPhaseNs="
        + goalPhaseNs
        + ", "
        + "alignThresholdNs="
        + alignThresholdNs
        + ", "
        + "overheadNs="
        + overheadNs
        + ", "
        + "minExposureNs="
        + minExposureNs
        + "}";
  }
}
