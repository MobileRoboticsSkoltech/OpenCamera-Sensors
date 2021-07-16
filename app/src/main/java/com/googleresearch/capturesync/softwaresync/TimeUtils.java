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

package com.googleresearch.capturesync.softwaresync;

/** Helper conversions between time scales. */
public final class TimeUtils {

  public static double nanosToMillis(double nanos) {
    return nanos / 1_000_000L;
  }

  public static long nanosToSeconds(long nanos) {
    return nanos / 1_000_000_000L;
  }

  public static double nanosToSeconds(double nanos) {
    return nanos / 1_000_000_000L;
  }

  public static long millisToNanos(long millis) {
    return millis * 1_000_000L;
  }

  public static long secondsToNanos(int seconds) {
    return seconds * 1_000_000_000L;
  }

  private TimeUtils() {}
}
