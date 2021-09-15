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

package com.googleresearch.capturesync.softwaresync;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simple Network Time Protocol (SNTP) for clock synchronization logic between leader and clients.
 * This implements the leader half of the protocol, with SntpListener implementing the client side.
 *
 * <p>Provides a doSNTP function allowing the leader to initiate synchronization with a client
 * address. The SntpListener class is used by the clients to handle responding to these messages.
 */
public class SimpleNetworkTimeProtocol implements AutoCloseable {
    private static final String TAG = "SNTP";

    private final DatagramSocket mNptpSocket;
    private final int mNptpPort;

    /**
     * Sequentially manages SNTP synchronization of clients.
     */
    private final ExecutorService mNptpExecutor = Executors.newSingleThreadExecutor();

    /**
     * Keeps track of SNTP client sync tasks already in the pipeline to avoid duplicate requests.
     */
    private final Set<InetAddress> mClientSyncTasks = new HashSet<>();

    private final Object mClientSyncTasksLock = new Object();
    private final SoftwareSyncLeader mLeader;
    private final Ticker mLocalClock;

    public SimpleNetworkTimeProtocol(
            Ticker localClock, DatagramSocket nptpSocket, int nptpPort, SoftwareSyncLeader leader) {
        mLocalClock = localClock;
        mNptpSocket = nptpSocket;
        mNptpPort = nptpPort;
        mLeader = leader;
    }

    /**
     * Check if requesting client is already in the queue. If not, then submit a new task to do n-PTP
     * synchronization with that client. Synchronization involves sending and receiving messages on
     * the nptp socket, calculating the clock offsetNs, and finally sending an rpc to update the
     * offsetNs on the client.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    void submitNewSyncRequest(final InetAddress clientAddress) {
        // Skip if we have already enqueued a sync task with this client.
        synchronized (mClientSyncTasksLock) {
            if (mClientSyncTasks.contains(clientAddress)) {
                Log.w(TAG, "Already queued sync with " + clientAddress + ", skipping.");
                return;
            } else {
                mClientSyncTasks.add(clientAddress);
            }
        }

        // Add SNTP request to executor queue.
        mNptpExecutor.submit(
                () -> {
                    // If the client no longer exists, no need to synchronize.
                    if (!mLeader.getClients().containsKey(clientAddress)) {
                        Log.w(TAG, "Client was removed, exiting SNTP routine.");
                        return true;
                    }

                    Log.d(TAG, "Starting sync with client" + clientAddress);
                    // Calculate clock offsetNs between client and leader using a naive
                    // version of the precision time protocol (SNTP).
                    SntpOffsetResponse response = doSNTP(clientAddress);

                    if (response.status()) {
                        // Apply local offsetNs to bestOffset so everyone has the same offsetNs.
                        final long alignedOffset = response.offsetNs() + mLeader.getLeaderFromLocalNs();

                        // Update client sync accuracy locally.
                        mLeader.updateClientWithOffsetResponse(clientAddress, response);

                        // Send an RPC to update the offsetNs on the client.
                        Log.d(TAG, "Sending offsetNs update to " + clientAddress + ": " + alignedOffset);
                        mLeader.sendRpc(
                                SyncConstants.METHOD_OFFSET_UPDATE, String.valueOf(alignedOffset), clientAddress);
                    }

                    // Pop client from the queue regardless of success state. Clients  will be added back in
                    // the queue as needed based on their state at the next heartbeat.
                    synchronized (mClientSyncTasksLock) {
                        mClientSyncTasks.remove(clientAddress);
                    }

                    if (response.status()) {
                        mLeader.onRpc(SyncConstants.METHOD_MSG_OFFSET_UPDATED, clientAddress.toString());
                    }

                    return response.status();
                });
    }

    /**
     * Performs Min filter SNTP synchronization with the client over the socket using UDP.
     *
     * <p>Naive PTP protocol is as follows:
     *
     * <p>[1]At time t0 in the leader clock domain, Leader sends the message (t0).
     *
     * <p>[2]At time t1 in the client clock domain, Client receives the message (t0).
     *
     * <p>[3]At time t2 in the client clock domain, Client sends the message (t0,t1,t2).
     *
     * <p>[4]At time t3 in the leader clock domain, Leader receives the message (t0,t1,t2).
     *
     * <p>Clock offsetNs = ((t1 - t0) + (t2 - t3)) / 2. [Client] current_time_in_leader_domain = now()
     * - offsetNs.
     *
     * <p>Round-trip latency = (t3 - t0) - (t2 - t1).
     *
     * <p>Final Clock offsetNs is calculated using the message with the smallest round-trip latency.
     *
     * @param clientAddress The client InetAddress to perform synchronization with.
     * @return SntpOffsetResponse containing the offsetNs and sync accuracy with the client.
     */
    private SntpOffsetResponse doSNTP(InetAddress clientAddress) throws IOException {
        final int longSize = Long.SIZE / Byte.SIZE;
        byte[] buf = new byte[longSize * 3];
        long bestLatency = Long.MAX_VALUE; // Start with initial high round trip
        long bestOffset = 0;
        // If there are several failed SNTP round trip sync messages, fail out.
        int missingMessageCountdown = 10;
        SntpOffsetResponse failureResponse =
                SntpOffsetResponse.create(/*offset=*/ 0, /*syncAccuracy=*/ 0, false);

        for (int i = 0; i < SyncConstants.NUM_SNTP_CYCLES; i++) {
            // 1 - Send UDP SNTP message to the client with t0 at time t0.
            long t0 = mLocalClock.read();
            ByteBuffer t0bytebuffer = ByteBuffer.allocate(longSize);
            t0bytebuffer.putLong(t0);
            mNptpSocket.send(new DatagramPacket(t0bytebuffer.array(), longSize, clientAddress, mNptpPort));

            // Steps 2 and 3 happen on client side B.
            // 4 - Recv UDP message with t0,t0',t1 at time t1'.
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                mNptpSocket.receive(packet);
            } catch (SocketTimeoutException e) {
                // If we didn't receive a message in time, then skip this PTP pair and continue.
                Log.w(TAG, "UDP PTP message missing, skipping");
                missingMessageCountdown--;
                if (missingMessageCountdown <= 0) {
                    Log.w(
                            TAG, String.format("Missed too many messages, leaving doSNTP for %s", clientAddress));
                    return failureResponse;
                }
                continue;
            }
            final long t3 = mLocalClock.read();

            if (packet.getLength() != 3 * longSize) {
                Log.w(TAG, "Corrupted UDP message, skipping");
                continue;
            }
            ByteBuffer t3buffer = ByteBuffer.allocate(longSize * 3);
            t3buffer.put(packet.getData(), 0, packet.getLength());
            t3buffer.flip();
            LongBuffer longbuffer = t3buffer.asLongBuffer();
            final long t0Msg = longbuffer.get();
            final long t1Msg = longbuffer.get();
            final long t2Msg = longbuffer.get();

            // Confirm that the received message contains the same t0  as the t0 from this cycle,
            // otherwise skip.
            if (t0Msg != t0) {
                Log.w(
                        TAG,
                        String.format(
                                "Out of order PTP message received, skipping: Expected %d vs %d", t0, t0Msg));

                // Note: Wait or catch and throw away the next message to get back in sync.
                try {
                    mNptpSocket.receive(packet);
                } catch (SocketTimeoutException e) {
                    // If still waiting, continue.
                }
                // Since this was an incorrect cycle, move on to a new cycle.
                continue;
            }

            final long timeOffset = ((t1Msg - t0) + (t2Msg - t3)) / 2;
            final long roundTripLatency = (t3 - t0) - (t2Msg - t1Msg);

            Log.v(
                    TAG,
                    String.format(
                            "% 3d | PTP: %d,%d,%d,%d | Latency: %,.3f ms",
                            i, t0, t1Msg, t2Msg, t3, TimeUtils.nanosToMillis((double) roundTripLatency)));

            if (roundTripLatency < bestLatency) {
                bestOffset = timeOffset;
                bestLatency = roundTripLatency;
                // If round trip latency is under minimum round trip latency desired, stop here.
                if (roundTripLatency < SyncConstants.MIN_ROUND_TRIP_LATENCY_NS) {
                    break;
                }
            }
        }

        Log.v(
                TAG,
                String.format(
                        "Client %s : SNTP best latency %,d ns, offsetNs %,d ns",
                        clientAddress, bestLatency, bestOffset));

        return SntpOffsetResponse.create(bestOffset, bestLatency, true);
    }

    @Override
    public void close() {
        mNptpExecutor.shutdown();
        // Wait up to 0.5 seconds for the executor service to finish.
        try {
            mNptpExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("SNTP Executor didn't close gracefully: " + e);
        }
    }
}
