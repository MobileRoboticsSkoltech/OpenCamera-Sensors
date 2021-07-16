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
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Leader which listens for registrations from SoftwareSyncClients, allowing it to broadcast times
 * at which both itself and clients will simultaneously perform actions.
 *
 * <p>SoftwareSync assumes that clients are connected to a leader wifi hotspot created by the device
 * this instance is running on.
 *
 * <p>The leader listens for client registrations and keeps track of connected clients. It also
 * listens for SNTP synchronization requests and processes them in a queue. Once it has determined
 * the offsetNs it sends an rpc message to the client, thereby synchronizing the client's clock with
 * the leader's to the precision requested.
 */
public class SoftwareSyncLeader extends SoftwareSyncBase {
  /** List of connected clients. */
  private final Map<InetAddress, ClientInfo> clients = new HashMap<>();

  private final Object clientsLock = new Object();

  /** Keeps track of how long since each client heartbeat was received, removing when stale. */
  private final ScheduledExecutorService staleClientChecker = Executors.newScheduledThreadPool(1);

  /** Send RPC messages on a separate thread, avoiding Network on Main Thread exceptions. */
  private final ExecutorService rpcMessageExecutor = Executors.newSingleThreadExecutor();

  /** Manages SNTP synchronization of clients. */
  private final SimpleNetworkTimeProtocol sntp;

  public SoftwareSyncLeader(
      String name, long initialTime, InetAddress address, Map<Integer, RpcCallback> rpcCallbacks) {
    this(name, new SystemTicker(), initialTime, address, rpcCallbacks);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private SoftwareSyncLeader(
      String name,
      Ticker localClock,
      long initialTime,
      InetAddress address,
      Map<Integer, RpcCallback> rpcCallbacks) {
    // Note: Leader address is required to be the same as local address.
    super(name, localClock, address, address);

    // Set up the offsetNs so that the leader synchronized time (via getLeaderTimeNs()) on all
    // devices
    // runs starting from the initial time given. When initialTimeNs is zero the
    // leader synchronized time is the default of localClock, ie. the time since boot of the leader
    // device.
    // For convenience, all devices could instead be shifted to the leader device UTC time,
    // ex. initialTimeNs = TimeUtils.millisToNanos(System.currentTimeMillis())
    setLeaderFromLocalNs(localClock.read() - initialTime);

    // Add client-specific RPC callbacks.
    rpcMap.put(
        SyncConstants.METHOD_HEARTBEAT,
        payload -> {
          // Received heartbeat from client, send back an acknowledge and then
          // check the client state and add to sntp queue if needed.
          Log.v(TAG, "Heartbeat received from client: " + payload);
          try {
            processHeartbeatRpc(payload);
          } catch (UnknownHostException e) {
            Log.e(TAG, "Processed heartbeat with corrupt host address: " + payload);
          }
        });

    // Add callbacks passed by user.
    addPublicRpcCallbacks(rpcCallbacks);

    // Set up SNTP instance for synchronizing with clients.
    sntp = new SimpleNetworkTimeProtocol(localClock, sntpSocket, SyncConstants.SNTP_PORT, this);

    // Start periodically checking for stale clients and removing as needed.
    staleClientChecker.scheduleAtFixedRate(
        this::removeStaleClients, 0, SyncConstants.STALE_TIME_NS, TimeUnit.NANOSECONDS);
  }

  public Map<InetAddress, ClientInfo> getClients() {
    synchronized (clientsLock) {
      return Collections.unmodifiableMap(clients);
    }
  }

  /**
   * Checks if the address is already associated with one of the clients in the list of tracked
   * clients. If so, just update the last heartbeat, otherwise create a new client entry in the
   * list.
   */
  private void addOrUpdateClient(String name, InetAddress address) {
    // Check if it's a new client, so we don't add again.
    synchronized (clientsLock) {
      boolean clientExists = clients.containsKey(address);
      // Add or replace entry with an updated ClientInfo.
      long offsetNs = 0;
      long syncAccuracyNs = 0;
      if (clientExists) {
        offsetNs = clients.get(address).offset();
        syncAccuracyNs = clients.get(address).syncAccuracy();
      }
      ClientInfo updatedClient =
          ClientInfo.create(name, address, offsetNs, syncAccuracyNs, localClock.read());
      clients.put(address, updatedClient);

      if (!clientExists) {
        // Notify via message on interface if client is new.
        onRpc(SyncConstants.METHOD_MSG_ADDED_CLIENT, updatedClient.name());
      }
    }
  }

  /** Removes clients whose last heartbeat was longer than STALE_TIME_NS ago. */
  private void removeStaleClients() {
    long t = localClock.read();
    synchronized (clientsLock) {
      // Use iterator to avoid concurrent modification exception.
      Iterator<Entry<InetAddress, ClientInfo>> clientIterator = clients.entrySet().iterator();
      while (clientIterator.hasNext()) {
        ClientInfo client = clientIterator.next().getValue();
        long timeSince = t - client.lastHeartbeat();
        if (timeSince > SyncConstants.STALE_TIME_NS) {
          Log.w(
              TAG,
              String.format(
                  "Stale client %s : time since %,d seconds",
                  client.name(), TimeUtils.nanosToSeconds(timeSince)));

          // Remove entry from the client list first.
          clientIterator.remove();
          // Client hasn't responded in a while, remove from list.
          onRpc(SyncConstants.METHOD_MSG_REMOVED_CLIENT, client.name());
        }
      }
    }
  }

  /** Finds and updates client sync accuracy within list. */
  void updateClientWithOffsetResponse(InetAddress clientAddress, SntpOffsetResponse response) {
    // Update client sync accuracy locally.
    synchronized (clientsLock) {
      if (!clients.containsKey(clientAddress)) {
        Log.w(TAG, "Tried to update a client info that is no longer in the list, Skipping.");
        return;
      }
      final ClientInfo client = clients.get(clientAddress);
      ClientInfo updatedClient =
          ClientInfo.create(
              client.name(),
              client.address(),
              response.offsetNs(),
              response.syncAccuracyNs(),
              client.lastHeartbeat());
      clients.put(client.address(), updatedClient);
    }
  }

  /**
   * Sends an RPC to every client in the leader's clients list.
   *
   * @param method int type of RPC (in {@link SyncConstants}).
   * @param payload String payload.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  private void internalBroadcastRpc(int method, String payload) {
    // Send RPC message to all clients and call onRPC of self as well.
    synchronized (clientsLock) {
      for (InetAddress address : clients.keySet()) {
        rpcMessageExecutor.submit(() -> sendRpc(method, payload, address));
      }
    }

    // Also call onRpc for self (leader).
    onRpc(method, payload);
  }

  /**
   * Public-facing broadcast RPC to all current clients, for non-softwaresync RPC methods only.
   *
   * @param method int type of RPC, must be greater than {@link
   *     SyncConstants#START_NON_SOFTWARESYNC_METHOD_IDS}.
   * @param payload String payload.
   */
  public void broadcastRpc(int method, String payload) {
    if (method < SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS) {
      throw new IllegalArgumentException(
          String.format(
              "Given method id %s, User method ids must" + " be >= %s",
              method, SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS));
    }
    internalBroadcastRpc(method, payload);
  }

  @Override
  public void close() throws IOException {
    sntp.close();
    staleClientChecker.shutdown();
    try {
      // Wait up to 0.5 seconds for this to close.
      staleClientChecker.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status.
      // Should only happen on app shutdown, fall out and continue.
    }
    super.close();
  }

  /**
   * Process a heartbeat rpc call from a client by responding with a heartbeat acknowledge, adding
   * or updating the client in the tracked clients list, and submitting a new SNTP sync request if
   * the client state is not yet synchronized.
   *
   * @param payload format of "ClientName,ClientAddress,ClientState"
   */
  private void processHeartbeatRpc(String payload) throws UnknownHostException {
    List<String> parts = Arrays.asList(payload.split(","));
    if (parts.size() != 3) {
      Log.e(
          TAG,
          "Heartbeat message has the wrong format, expected 3 comma-delimitted parts: "
              + payload
              + ". Skipping.");
      return;
    }
    String clientName = parts.get(0);
    InetAddress clientAddress = InetAddress.getByName(parts.get(1));
    boolean clientSyncState = Boolean.parseBoolean(parts.get(2));

    // Send heartbeat acknowledge RPC back to client first, containing the same payload.
    sendRpc(SyncConstants.METHOD_HEARTBEAT_ACK, payload, clientAddress);

    // Add or update client in clients.
    addOrUpdateClient(clientName, clientAddress);

    // If the client state is not yet synchronized, add it to the SNTP queue.
    if (!clientSyncState) {
      sntp.submitNewSyncRequest(clientAddress);
    }
  }
}
