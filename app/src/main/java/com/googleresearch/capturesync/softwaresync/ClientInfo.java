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

import java.net.InetAddress;
import java.util.Locale;

/**
 * Utility immutable class for providing accessors for client name, address, ip local address
 * ending, current best accuracy, and last known heartbeat.
 */
public final class ClientInfo {
    private final String mName;
    private final InetAddress mAddress;
    private final long mOffsetNs;
    private final long mSyncAccuracyNs;
    private final long mLastHeartbeatNs;

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
        mName = name;
        mAddress = address;
        mOffsetNs = offsetNs;
        mSyncAccuracyNs = syncAccuracyNs;
        mLastHeartbeatNs = lastHeartbeatNs;
    }

    public String name() {
        return mName;
    }

    public InetAddress address() {
        return mAddress;
    }

    /**
     * The time delta (leader - client) in nanoseconds of the AP SystemClock domain. The client can
     * take their local_time to get leader_time via: local_time (leader - client) = leader_time.
     */
    public long offset() {
        return mOffsetNs;
    }

    /**
     * The worst case error in the clock domains between leader and client for this response, in
     * nanoseconds of the AP SystemClock domain.
     */
    public long syncAccuracy() {
        return mSyncAccuracyNs;
    }

    /* The last time a client heartbeat was detected. */
    public long lastHeartbeat() {
        return mLastHeartbeatNs;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,
                "%s[%.2f ms]", name(), TimeUtils.nanosToMillis((double) syncAccuracy()));
    }
}
