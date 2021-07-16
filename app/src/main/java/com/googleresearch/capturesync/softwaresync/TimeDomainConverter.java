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

/**
 * Interface used to manage time domain conversion, implemented by {@link SoftwareSyncBase}. This
 * allows {@link ResultProcessor} to convert sensor timestamps to the synchronized time domain
 * without needing full access to the softwaresync object.
 */
public interface TimeDomainConverter {

  /**
   * Calculates the leader time associated with the given local time in nanoseconds. The local time
   * must be in the SystemClock.elapsedRealTimeNanos() localClock domain, nanosecond units. This
   * includes timestamps such as the sensor timestamp from the camera. leader_time =
   * local_elapsed_time_ns + leader_from_local_ns.
   *
   * @param localTimeNs given local time (local clock SystemClock.elapsedRealtimeNanos() domain).
   * @return leader synchronized time in nanoseconds.
   */
  long leaderTimeForLocalTimeNs(long localTimeNs);

}
