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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * SNTP listener thread, which is expected to only run while the client is waiting for the leader to
 * synchronize with it (WAITING_FOR_LEADER state). The {@link SimpleNetworkTimeProtocol} class is
 * used by the leader to send the SNTP messages that this thread listens for.
 */
public class SntpListener extends Thread {
    private static final String TAG = "SntpListener";

    private boolean mRunning;
    private final DatagramSocket mNptpSocket;
    private final int mNptpPort;
    private final Ticker mLocalClock;

    public SntpListener(Ticker localClock, DatagramSocket nptpSocket, int nptpPort) {
        mLocalClock = localClock;
        mNptpSocket = nptpSocket;
        mNptpPort = nptpPort;
    }

    public void stopRunning() {
        mRunning = false;
    }

    @Override
    public void run() {
        mRunning = true;

        Log.w(TAG, "Starting SNTP Listener thread.");

        byte[] buf = new byte[SyncConstants.SNTP_BUFFER_SIZE];
        while (mRunning && !mNptpSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                // Listen for PTP messages.
                mNptpSocket.receive(packet);

                // 2 (B) - Recv UDP message with t0 at time t0'.
                long t0r = mLocalClock.read();

                final int longSize = Long.SIZE / Byte.SIZE;

                if (packet.getLength() != longSize) {
                    Log.e(
                            TAG,
                            "Received UDP message with incorrect packet length "
                                    + packet.getLength()
                                    + ", skipping.");
                    continue;
                }

                // 3 (B) - Send UDP message with t0,t0',t1 at time t1.
                long t1 = mLocalClock.read();
                ByteBuffer buffer = ByteBuffer.allocate(3 * longSize);
                buffer.put(packet.getData(), 0, longSize);
                buffer.putLong(longSize, t0r);
                buffer.putLong(2 * longSize, t1);
                byte[] bufferArray = buffer.array();

                // Send SNTP response back.
                DatagramPacket response =
                        new DatagramPacket(bufferArray, bufferArray.length, packet.getAddress(), mNptpPort);
                mNptpSocket.send(response);
            } catch (SocketTimeoutException e) {
                // It is normal to time out most of the time, continue.
            } catch (IOException e) {
                if (mNptpSocket.isClosed()) {
                    // Stop here if socket is closed.
                    return;
                }
                throw new IllegalStateException("SNTP Thread didn't close gracefully: " + e);
            }
        }
        Log.w(TAG, "SNTP Listener thread finished.");
    }
}
