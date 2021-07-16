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

/** Ports and other constants used by SoftwareSync. */
public class SyncConstants {
  public static final int SOCKET_WAIT_TIME_MS = 500;

  /** Heartbeat period between clients and leader. */
  public static final long HEARTBEAT_PERIOD_NS = TimeUtils.secondsToNanos(1);

  /**
   * Time until a lack of a heartbeat acknowledge from leader means a lost connection. Similarly the
   * time until a lack of a heartbeat from a client means the client is stale.
   */
  public static final long STALE_TIME_NS = 2 * HEARTBEAT_PERIOD_NS;

  /** Time until a given offsetNs by the leader is considered stale. */
  public static final long STALE_OFFSET_TIME_NS = TimeUtils.secondsToNanos(60 * 60);

  /** RPC. */
  public static final int RPC_PORT = 8244;
  public static final int RPC_BUFFER_SIZE = 1024;

  /** RPC Method ids.
   * [0 - 9,999] Reserved for SoftwareSync.
   *  - [0   -  99] Synchronization-related.
   *  - [100 - 199] Messages.
   * [10,000+  ] Available to user applications.
   */
  public static final int METHOD_HEARTBEAT = 1;
  public static final int METHOD_HEARTBEAT_ACK = 2;
  public static final int METHOD_OFFSET_UPDATE = 3;

  /* Define user RPC method ids using values greater or equal to this. */
  public static final int START_NON_SOFTWARESYNC_METHOD_IDS = 1_000;

  public static final int METHOD_MSG_ADDED_CLIENT = 1_101;
  public static final int METHOD_MSG_REMOVED_CLIENT = 1_102;
  public static final int METHOD_MSG_WAITING_FOR_LEADER = 1_103;
  public static final int METHOD_MSG_SYNCING = 1_104;
  public static final int METHOD_MSG_OFFSET_UPDATED = 1_105;


  /** Clock Sync - Simple Network Time Protocol (SNTP). */
  public static final int SNTP_PORT = 9428;
  public static final int SNTP_BUFFER_SIZE = 512;
  public static final int NUM_SNTP_CYCLES = 300;
  public static final long MIN_ROUND_TRIP_LATENCY_NS = TimeUtils.millisToNanos(1);

  private SyncConstants() {}
}
