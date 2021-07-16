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

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Client which registers and synchronizes clocks with SoftwareSyncLeader. This allows it to receive
 * messages and timestamps at which to simultaneously process commands such as capturing a photo at
 * the same time as the leader device.
 *
 * <p>The client can be in one of internal three states: Waiting for leader, registered but not
 * synced, and synced.
 *
 * <p>On instantiation, the client attempts to register with the leader, and is waiting for the
 * leader to respond.
 *
 * <p>Then, once it is registered but not yet synced, it requests SNTP synchronization with the
 * leader. Here it listens for offsetNs update rpc messages and sets it's offsetNs to that, thereby
 * synchronizing it's clock with leader to the precision requested.
 *
 * <p>Finally, once the leader responds with an time correction offsetNs, it enters the synced
 * state.
 *
 * <p>>User should handle thrown IOExceptions for networking. The most common cause for a thrown
 * exception is when the user closes the client down, but there is still a socket receive or a
 * periodic heartbeat send still in progress.
 */
public class SoftwareSyncClient extends SoftwareSyncBase {

  /** Tracks the state of client synchronization with the leader. */
  private boolean synced;

  private final Object syncLock = new Object();
  private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

  /**
   * Time of last leader response received in the clock domain of the leader's
   * SystemClock.elapsedRealTimeNanos().
   */
  private long lastLeaderResponseTimeNs;

  private long lastLeaderOffsetResponseTimeNs;

  private SntpListener sntpThread;

  public SoftwareSyncClient(
      String name,
      InetAddress address,
      InetAddress leaderAddress,
      Map<Integer, RpcCallback> rpcCallbacks) {
    this(name, new SystemTicker(), address, leaderAddress, rpcCallbacks);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private SoftwareSyncClient(
      String name,
      Ticker localClock,
      InetAddress address,
      InetAddress leaderAddress,
      Map<Integer, RpcCallback> rpcCallbacks) {
    super(name, localClock, address, leaderAddress);

    // Add client-specific RPC callbacks.
    rpcMap.put(
        SyncConstants.METHOD_HEARTBEAT_ACK,
        payload -> {
          // Leader responded to heartbeat. update last response and change sync status as needed.
          lastLeaderResponseTimeNs = localClock.read();
          Log.v(TAG, "Heartbeat acknowledge received from leader.");
          updateState();
        });
    rpcMap.put(
        SyncConstants.METHOD_OFFSET_UPDATE,
        payload -> {
          lastLeaderOffsetResponseTimeNs = localClock.read();

          Log.d(TAG, "Received offsetNs update: (" + payload + "), stopping sntp sync request.");
          // Set the time offsetNs to the offsetNs passed in by the leader and update state.
          setLeaderFromLocalNs(Long.parseLong(payload));
          updateState();
          onRpc(SyncConstants.METHOD_MSG_OFFSET_UPDATED, Long.toString(getLeaderFromLocalNs()));
        });

    // Add callbacks passed by user.
    addPublicRpcCallbacks(rpcCallbacks);

    // Initial state is waiting to register with leader.
    reset();

    // Start periodically sending out a heartbeat to the leader.
    heartbeatScheduler.scheduleAtFixedRate(
        this::sendHeartbeat, 0, SyncConstants.HEARTBEAT_PERIOD_NS, TimeUnit.NANOSECONDS);
  }

  /* Resets the client synchronization state. */
  private void reset() {
    lastLeaderResponseTimeNs = 0;
    lastLeaderOffsetResponseTimeNs = 0;
    maybeStartSntpThread();
    updateState();
  }

  /**
   * Sends a heartbeat to the leader and waits for an acknowledge response. If there is a response,
   * it updates the last known leader response time. It then calls updateState() to see if there is
   * a state transition needed. It also posts a delayed request to heartbeatHandler to run this
   * again in HEARTBEAT_TIME_MS.
   */
  private void sendHeartbeat() {
    // First update current client state based on time since last response.
    updateState();

    // Generate heartbeat message containing the client address and the
    // string value of the synchronization state.
    final String heartbeatMsg;
    synchronized (syncLock) {
      // Note: Send messages using strings for simplicity.
      heartbeatMsg =
          String.format(
              "%s,%s,%s",
              getLocalClientInfo().name(),
              getLocalClientInfo().address().getHostAddress(),
              Boolean.toString(synced));
    }

    // Send heartbeat RPC to leader, expecting a METHOD_HEARTBEAT_ACK rpc back from leader.
    sendRpc(SyncConstants.METHOD_HEARTBEAT, heartbeatMsg, getLeaderAddress());
  }

  /**
   * Propagate state machine depending on lastLeaderResponseTimeNs and currentState. This should be
   * called periodically, such as every time a heartbeat is sent, and after it receives an offsetNs
   * update RPC.
   */
  private void updateState() {
    final long timestamp = localClock.read();
    final long timeSinceLastLeaderResponseNs = timestamp - lastLeaderResponseTimeNs;
    final long timeSinceLastLeaderOffsetResponseNs = timestamp - lastLeaderOffsetResponseTimeNs;
    updateState(timeSinceLastLeaderResponseNs, timeSinceLastLeaderOffsetResponseNs);
  }

  /**
   * SoftwareSyncClient only has two states: WAITING_FOR_LEADER (false) and SYNCED (true).
   *
   * <p>The state is determined based on the time since the last leader heartbeat acknowledge and
   * the time since the last offsetNs was given by the leader.
   *
   * <p>If the time since the leader has responded is longer than STALE_TIME_NS or the last offsetNs
   * received happened longer than STALE_OFFSET_TIME_NS ago, then transition to the not synced
   * WAITING_FOR_LEADER state. Otherwise, transition to the SYNCED state.
   */
  private void updateState(
      final long timeSinceLastLeaderResponseNs, final long timeSinceLastLeaderOffsetResponseNs) {
    final boolean newSyncState =
        (lastLeaderResponseTimeNs != 0
            && lastLeaderOffsetResponseTimeNs != 0
            && timeSinceLastLeaderResponseNs < SyncConstants.STALE_TIME_NS
            && timeSinceLastLeaderOffsetResponseNs < SyncConstants.STALE_OFFSET_TIME_NS);
    synchronized (syncLock) {
      if (newSyncState == synced) {
        return; // No state change, do nothing.
      }

      // Update synchronization state.
      synced = newSyncState;

      if (synced) { // WAITING_FOR_LEADER -> SYNCED.
        onRpc(SyncConstants.METHOD_MSG_SYNCING, null);
      } else { // SYNCED -> WAITING_FOR_LEADER.
        onRpc(SyncConstants.METHOD_MSG_WAITING_FOR_LEADER, null);
      }
    }
  }

  /** Start SNTP thread if it's not already running. */
  private void maybeStartSntpThread() {
    if (sntpThread == null || !sntpThread.isAlive()) {
      // Set up SNTP thread.
      sntpThread = new SntpListener(localClock, sntpSocket, sntpPort);
      sntpThread.start();
    }
  }

  /** Blocking stop of SNTP Thread if it's not already stopped. */
  private void maybeStopSntpThread() {
    if (sntpThread != null && sntpThread.isAlive()) {
      sntpThread.stopRunning();
      // Wait for thread to finish.
      try {
        sntpThread.join();
      } catch (InterruptedException e) {
        throw new IllegalStateException("SNTP Thread didn't close gracefully: " + e);
      }
    }
  }

  @Override
  public void close() throws IOException {
    maybeStopSntpThread();
    // Stop the heartbeat scheduler.
    heartbeatScheduler.shutdown();
    try {
      heartbeatScheduler.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status.
      // Should only happen on app shutdown, fall out and continue.
    }

    super.close();
  }
}
