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

import java.net.InetAddress;

/**
 * Utility immutable class for providing accessors for client name, address, ip local address
 * ending, current best accuracy, and last known heartbeat.
 */
public final class ClientInfo {
  private final String name;
  private final InetAddress address;
  private final long offsetNs;
  private final long syncAccuracyNs;
  private final long lastHeartbeatNs;

  static ClientInfo create(
      String name, InetAddress address, long offset, long syncAccuracy, long lastHeartbeat) {
    return new ClientInfo(name, address, offset, syncAccuracy, lastHeartbeat);
  }

  static ClientInfo create(String name, InetAddress address) {
    return new ClientInfo(
        name, address, /*offsetNs=*/ 0, /*syncAccuracyNs=*/ 0, /*lastHeartbeatNs=*/ 0);
  }

  private ClientInfo(
      String name, InetAddress address, long offsetNs, long syncAccuracyNs, long lastHeartbeatNs) {
    this.name = name;
    this.address = address;
    this.offsetNs = offsetNs;
    this.syncAccuracyNs = syncAccuracyNs;
    this.lastHeartbeatNs = lastHeartbeatNs;
  }

  public String name() {
    return name;
  }

  public InetAddress address() {
    return address;
  }

  /**
   * The time delta (leader - client) in nanoseconds of the AP SystemClock domain. The client can
   * take their local_time to get leader_time via: local_time (leader - client) = leader_time.
   */
  public long offset() {
    return offsetNs;
  }

  /**
   * The worst case error in the clock domains between leader and client for this response, in
   * nanoseconds of the AP SystemClock domain.
   */
  public long syncAccuracy() {
    return syncAccuracyNs;
  }

  /* The last time a client heartbeat was detected. */
  public long lastHeartbeat() {
    return lastHeartbeatNs;
  }

  @Override
  public String toString() {
    return String.format("%s[%.2f ms]", name(), TimeUtils.nanosToMillis((double) syncAccuracy()));
  }
}
