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

/**
 * AutoValue class for SNTP offsetNs, synchronization accuracy and status.
 */
public final class SntpOffsetResponse {
    private final long mOffsetNs;
    private final long mSyncAccuracyNs;
    private final boolean mStatus;

    static SntpOffsetResponse create(long offset, long syncAccuracy, boolean status) {
        return new SntpOffsetResponse(offset, syncAccuracy, status);
    }

    private SntpOffsetResponse(long offsetNs, long syncAccuracyNs, boolean status) {
        mOffsetNs = offsetNs;
        mSyncAccuracyNs = syncAccuracyNs;
        mStatus = status;
    }

    /**
     * The time delta (leader - client) in nanoseconds of the AP SystemClock domain.
     *
     * <p>The client can take their local_time to get leader_time via: local_time (leader - client) =
     * leader_time.
     */
    public long offsetNs() {
        return mOffsetNs;
    }

    /**
     * The worst case error in the clock domains between leader and client for this response, in
     * nanoseconds of the AP SystemClock domain.
     */
    public long syncAccuracyNs() {
        return mSyncAccuracyNs;
    }

    /**
     * The success status of this response.
     */
    public boolean status() {
        return mStatus;
    }
}
