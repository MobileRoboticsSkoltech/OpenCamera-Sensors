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

import android.os.HandlerThread;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SoftwareSyncBase is the abstract base class to SoftwareSyncLeader and SoftwareSyncClient, holding
 * shared objects such as UDP ports and sockets, local client information and methods for starting
 * and stopping shared threads such as the rpc socket thread.
 *
 * <p>When the user is finished they should call the idempotent method close().
 */
public abstract class SoftwareSyncBase implements Closeable, TimeDomainConverter {
    static final String TAG = "SoftwareSyncBase";

    private final ClientInfo mLocalClientInfo; // Client info for this device.
    private final InetAddress mLeaderAddress;
    final Ticker mLocalClock;

    /**
     * Offset to convert local time to leader time. leader_time = local_elapsed_time -
     * leader_from_local.
     */
    private long mLeaderFromLocalNs = 0;

    /* SNTP Setup */
    final int mSntpPort;
    final DatagramSocket mSntpSocket;

    /* RPC Setup. */
    private final int mRpcPort;
    private final DatagramSocket mRpcSocket;
    private final RpcThread mRpcListenerThread;
    final Map<Integer, RpcCallback> mRpcMap = new HashMap<>();
    /**
     * Handle onRPC events on a separate thread.
     */
    private final ExecutorService mRpcExecutor = Executors.newSingleThreadExecutor();

    SoftwareSyncBase(String name, Ticker localClock, InetAddress address, InetAddress leaderAddress) {
        mRpcPort = SyncConstants.RPC_PORT;
        mSntpPort = SyncConstants.SNTP_PORT;
        mLocalClock = localClock;

        // Set up local ClientInfo from the provided address.
        mLocalClientInfo = ClientInfo.create(name, address);

        // Leader device ip address is provided by the user.
        mLeaderAddress = leaderAddress;

        // Open sockets and start communication threads between leader and client devices.
        try {
            mRpcSocket = new DatagramSocket(null);
            mRpcSocket.setReuseAddress(true);
            mRpcSocket.setSoTimeout(SyncConstants.SOCKET_WAIT_TIME_MS);
            mRpcSocket.bind(new InetSocketAddress(SyncConstants.RPC_PORT));

            mSntpSocket = new DatagramSocket(null);
            mSntpSocket.setReuseAddress(true);
            mSntpSocket.setSoTimeout(SyncConstants.SOCKET_WAIT_TIME_MS);
            mSntpSocket.bind(new InetSocketAddress(SyncConstants.SNTP_PORT));

        } catch (BindException e) {
            throw new IllegalArgumentException("Socket already in use, close app and restart: " + e);
        } catch (SocketException e) {
            throw new IllegalArgumentException("Unable to open Sockets: " + e);
        }

        // Start an RPC thread loop that listens for packets on the rpc socket, processes and calls
        // onRpc with the processed method and payload.
        mRpcListenerThread = new RpcThread();
        mRpcListenerThread.start();
    }

    /**
     * Returns leader synchronized time in nanoseconds. This is in the clock domain of the leader's
     * localClock (SystemClock.elapsedRealtimeNanos())
     */
    public long getLeaderTimeNs() {
        return leaderTimeForLocalTimeNs(mLocalClock.read());
    }

    /**
     * Calculates the leader time associated with the given local time in nanoseconds. The local time
     * must be in the SystemClock.elapsedRealTimeNanos() localClock domain, nanosecond units. This
     * includes timestamps such as the sensor timestamp from the camera. leader_time =
     * local_elapsed_time_ns + leader_from_local_ns.
     *
     * @param localTimeNs given local time (local clock SystemClock.elapsedRealtimeNanos() domain).
     * @return leader synchronized time in nanoseconds.
     */
    @Override
    public long leaderTimeForLocalTimeNs(long localTimeNs) {
        return localTimeNs - mLeaderFromLocalNs;
    }

    public String getName() {
        return mLocalClientInfo.name();
    }

    ClientInfo getLocalClientInfo() {
        return mLocalClientInfo;
    }

    public InetAddress getLeaderAddress() {
        return mLeaderAddress;
    }

    /**
     * Returns get the localClock offsetNs between this devices local elapsed time and the leader in
     * nanoseconds.
     */
    public long getLeaderFromLocalNs() {
        return mLeaderFromLocalNs;
    }

    /**
     * Set the offsetNs between this device's local elapsed time and the leader synchronized time.
     */
    void setLeaderFromLocalNs(long value) {
        mLeaderFromLocalNs = value;
    }

    void addPublicRpcCallbacks(Map<Integer, RpcCallback> callbacks) {
        for (Integer key : callbacks.keySet()) {
            if (key < SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS) {
                throw new IllegalArgumentException(
                        String.format(
                                "Given method id %s, User method ids must" + " be >= %s",
                                key, SyncConstants.START_NON_SOFTWARESYNC_METHOD_IDS));
            }
        }
        mRpcMap.putAll(callbacks);
    }

    /**
     * Sends a message with arguments to the specified address over the rpc socket.
     */
    void sendRpc(int method, String arguments, InetAddress address) {
        byte[] messagePayload = arguments.getBytes();
        if (messagePayload.length + 4 > SyncConstants.RPC_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                    String.format(
                            "RPC arguments too big %d v %d",
                            messagePayload.length + 4, SyncConstants.RPC_BUFFER_SIZE));
        }

        byte[] fullPayload =
                ByteBuffer.allocate(messagePayload.length + 4).putInt(method).put(messagePayload).array();

        DatagramPacket packet = new DatagramPacket(fullPayload, fullPayload.length, address, mRpcPort);
        try {
            mRpcSocket.send(packet);
        } catch (IOException e) {
            throw new IllegalStateException("Error sending RPC packet.");
        }
    }

    /**
     * RPC thread loop that listens for packets on the rpc socket, processes and calls onRpc with the
     * processed method and payload.
     */
    private class RpcThread extends HandlerThread {
        private boolean mRunning;

        RpcThread() {
            super("RpcListenerThread");
        }

        void stopRunning() {
            mRunning = false;
        }

        @Override
        @SuppressWarnings("FutureReturnValueIgnored")
        public void run() {
            mRunning = true;

            byte[] buf = new byte[SyncConstants.RPC_BUFFER_SIZE];
            while (mRunning && !mRpcSocket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                try {
                    // Wait for a client message.
                    mRpcSocket.receive(packet);

                    // Separate packet string into int method and string payload
                    // First 4 bytes is the integer method.
                    ByteBuffer packetByteBuffer = ByteBuffer.wrap(packet.getData());
                    int method = packetByteBuffer.getInt(); // From first 4 bytes.
                    // Rest of the bytes are the payload.
                    String payload = new String(packet.getData(), 4, packet.getLength() - 4);

                    // Call onRpc with the method and payload in a separate thread.
                    mRpcExecutor.submit(() -> onRpc(method, payload));

                } catch (SocketTimeoutException e) {
                    // Do nothing since this is a normal timeout of the receive.
                } catch (IOException e) {
                    if (mRunning || mRpcSocket.isClosed()) {
                        Log.w(TAG, "Shutdown arrived in middle of a socket receive, ignoring error.");
                    } else {
                        throw new IllegalStateException("Socket Receive/Send error: " + e);
                    }
                }
            }
        }
    }

    /**
     * Handle RPCs using the existing RPC map.
     */
    public void onRpc(int method, String payload) {
        RpcCallback callback = mRpcMap.get(method);
        if (callback != null) {
            callback.call(payload);
        }
    }

    /**
     * Idempotent close that handles closing sockets, threads if they are open or running, etc. If a
     * user overrides this method it is expected make sure to call super as well.
     */
    @Override
    public void close() throws IOException {
        mRpcListenerThread.stopRunning();
        mRpcSocket.close();
        mSntpSocket.close();
    }
}
