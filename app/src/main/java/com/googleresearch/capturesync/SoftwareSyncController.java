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

package com.googleresearch.capturesync;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings.Secure;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.googleresearch.capturesync.softwaresync.ClientInfo;
import com.googleresearch.capturesync.softwaresync.NetworkHelpers;
import com.googleresearch.capturesync.softwaresync.RpcCallback;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncClient;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncLeader;
import com.googleresearch.capturesync.softwaresync.SyncConstants;
import com.googleresearch.capturesync.softwaresync.TimeUtils;
import com.googleresearch.capturesync.softwaresync.phasealign.PeriodCalculator;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.recsync.SyncSettingsContainer;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller managing setup and tear down the SoftwareSync object. Needs Network permissions.
 */
public class SoftwareSyncController implements Closeable {
    private static final String TAG = "SoftwareSyncController";

    private final MainActivity mMainActivity;
    private final PhaseAlignController mPhaseAlignController;
    private final PeriodCalculator mPeriodCalculator;
    private String mSyncStatus;
    private SoftwareSyncBase mSoftwareSync;
    private AlignPhasesTask mAlignPhasesTask;

    private boolean mIsLeader;
    private State mState = State.IDLE;

    /**
     * Possible states of RecSync on this device.
     */
    public enum State {
        IDLE, // When other states are not applicable.
        SETTINGS_APPLICATION, // Received setting are being applied.
        PERIOD_CALCULATION, // PeriodCalculator is calculating.
        PHASE_ALIGNMENT, // PhaseAlignController is aligning.
        RECORDING // A video is being recorded.
    }

    /**
     * Tell devices to save the frame at the requested trigger time.
     */
    public static final int METHOD_SET_TRIGGER_TIME = 200_000;
    /**
     * Tell devices to calculate frames period and phase align.
     */
    public static final int METHOD_DO_PHASE_ALIGN = 200_001;
    /**
     * Tell devices to set the chosen settings to the requested values.
     */
    public static final int METHOD_SET_SETTINGS = 200_002;
    /**
     * Tell devices to start or stop video recording.
     */
    public static final int METHOD_RECORD = 200_003;
    /**
     * Tell devices to upload files by tag.
     */
    public static final int METHOD_UPLOAD_BY_TAG = 200_004;

    private long mUpcomingTriggerTimeNs;

    /**
     * Constructor passed in with: - context - For setting UI elements and triggering captures. -
     * captureButton - The button used to send at trigger request by the leader. - statusView - The
     * TextView used to show currently connected clients on the leader device.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public SoftwareSyncController(
            MainActivity mainActivity, PhaseAlignController phaseAlignController, PeriodCalculator periodCalculator) {
        mMainActivity = mainActivity;
        mPhaseAlignController = phaseAlignController;
        mPeriodCalculator = periodCalculator;

        setupSoftwareSync();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setupSoftwareSync() {
        Log.w(TAG, "setup SoftwareSync");
        if (mSoftwareSync != null) {
            return;
        }

        // Get Wifi Manager and use NetworkHelpers to determine local and leader IP addresses.
        WifiManager wifiManager = (WifiManager) mMainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        InetAddress leaderAddress;
        InetAddress localAddress;

        // Use last 4 digits of the serial as the name of the client.
        String name = lastFourSerial();
        Log.w(TAG, "Name/Serial# (Last 4 digits): " + name);

        // Determine leadership.
        try {
            NetworkHelpers networkHelper = new NetworkHelpers(wifiManager);
            mIsLeader = networkHelper.isLeader();

            if (mIsLeader) {
                // IP determination is not yet implemented for a leader.
                localAddress = null;
                leaderAddress = null;
            } else {
                localAddress = networkHelper.getIPAddress();
                leaderAddress = networkHelper.getHotspotServerAddress();
            }

            Log.w(
                    TAG,
                    String.format(
                            "Current IP: %s , Leader IP: %s | Leader? %s",
                            localAddress, leaderAddress, mIsLeader ? "Y" : "N"));
        } catch (SocketException e) {
            Log.e(TAG, "Error: " + e);
            throw new IllegalStateException(
                    "Unable to determine leadership, check if WiFi or hotspot is enabled.", e);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Error: " + e);
            throw new IllegalStateException(
                    "Unable to get IP addresses, check Network permissions.", e);
        }

        // Set up shared rpcs.
        Map<Integer, RpcCallback> sharedRpcs = new HashMap<>();

        //sharedRpcs.put(
        //        METHOD_SET_TRIGGER_TIME,
        //        payload -> {
        //            Log.d(TAG, "Setting next trigger to" + payload);
        //            upcomingTriggerTimeNs = Long.valueOf(payload);
        //            // TODO: (MROB) change to video
        //            context.setUpcomingCaptureStill(upcomingTriggerTimeNs);
        //        });

        // Start frames period calculation and then the phase aligning algorithm.
        sharedRpcs.put(
                METHOD_DO_PHASE_ALIGN,
                payload -> {
                    Log.d(TAG, "Phase alignment request received.");

                    if (mState == State.PERIOD_CALCULATION || mState == State.PHASE_ALIGNMENT) {
                        Log.d(TAG, "The previous phase alignment request is still processing.");
                        return;
                    }

                    mAlignPhasesTask = new AlignPhasesTask(this, mPhaseAlignController, mPeriodCalculator);
                    mAlignPhasesTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                });

        // Apply the received settings.
        sharedRpcs.put(
                METHOD_SET_SETTINGS,
                payload -> {
                    Log.d(TAG, "Received payload with settings: " + payload);

                    if (mState != State.IDLE && mState != State.SETTINGS_APPLICATION) {
                        Log.d(TAG, "Settings cannot be applied at state " + mState);
                        return;
                    }

                    SyncSettingsContainer settings = null;
                    try {
                        settings = SyncSettingsContainer.deserializeFromString(payload);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to deserialize the settings string: " + payload + "\n" + e.getMessage());
                    }

                    if (settings != null) {
                        mState = State.SETTINGS_APPLICATION;
                        mMainActivity.getApplicationInterface().getSoftwareSyncUtils().applyAndLockSettings(settings, () -> mState = State.IDLE);
                    }
                });

        // Switch the recording status (start or stop video recording) to the opposite of the received one.
        sharedRpcs.put(
                METHOD_RECORD,
                payload -> {
                    Log.d(TAG, String.format(
                            "Received record request with payload: %s. Current recording status: %s.",
                            payload, mMainActivity.getPreview().isVideoRecording()));

                    if (mState != State.IDLE && mState != State.RECORDING) {
                        Log.d(TAG, "Recording status cannot be switched at state " + mState);
                        return;
                    }

                    if (!mMainActivity.getPreview().isVideo()) {
                        // This should not happen as capture mode is to be synced before recording.
                        throw new IllegalStateException("Received recording request in photo mode");
                    }
                    if (mMainActivity.getPreview().isVideoRecording() == Boolean.parseBoolean(payload)) {
                        mState = (mState == State.RECORDING) ? State.IDLE : State.RECORDING;
                        mMainActivity.runOnUiThread(() -> mMainActivity.takePicturePressed(false, false));
                    }
                });

        // Upload file by tag to the server.
        sharedRpcs.put(
                METHOD_UPLOAD_BY_TAG,
                payload -> {
                    Log.d(TAG, "Received payload with settings: " + payload);

                    String VIDEO_BY_TAG_REQUEST_REGEX = "(serverIp=)(\\S+)(\\^tag=)(\\S*)(&devices=)((\\S{4})*)";
                    Pattern VIDEO_BY_TAG_REQUEST_PATTERN = Pattern.compile(VIDEO_BY_TAG_REQUEST_REGEX);
                    Matcher videoByTagRequestMatcher = VIDEO_BY_TAG_REQUEST_PATTERN.matcher(payload);

                    if (!videoByTagRequestMatcher.find()) return;
                    String serverIp = videoByTagRequestMatcher.group(2);
                    String tag = videoByTagRequestMatcher.group(4);
                    String devicesStr = videoByTagRequestMatcher.group(6);
                    List<String> devices = Collections.emptyList();
                    for (int i = 0; i < devicesStr.length() / 4; i++) {
                        devices.add(devicesStr.substring(i * 4, i * 4 + 3));
                    }
                    if (devices.isEmpty() || devices.contains(lastFourSerial())) {
                        mMainActivity.getApplicationInterface().getFileUploadService().uploadByTag(tag, serverIp);
                    }
                });

        if (mIsLeader) {
            // Leader.
            long initTimeNs = TimeUtils.millisToNanos(System.currentTimeMillis());

            // Create rpc mapping specific to leader.
            Map<Integer, RpcCallback> leaderRpcs = new HashMap<>(sharedRpcs);

            // Update status text when the status changes.
            leaderRpcs.put(SyncConstants.METHOD_MSG_ADDED_CLIENT, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_REMOVED_CLIENT, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_SYNCING, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_OFFSET_UPDATED, payload -> updateClientsUI());

            mSoftwareSync = new SoftwareSyncLeader(name, initTimeNs, localAddress, leaderRpcs);
        } else {
            // Client.
            Map<Integer, RpcCallback> clientRpcs = new HashMap<>(sharedRpcs);

            // Update status text to "waiting for leader".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_WAITING_FOR_LEADER,
                    payload ->
                            mMainActivity.runOnUiThread(
                                    () -> mSyncStatus = mMainActivity.getString(R.string.rec_sync_waiting_for_leader, mSoftwareSync.getName())));

            // Update status text to "waiting for sync".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_SYNCING,
                    payload ->
                            mMainActivity.runOnUiThread(
                                    () -> mSyncStatus = mMainActivity.getString(R.string.rec_sync_waiting_for_sync, mSoftwareSync.getName())));

            // Update status text to "synced to leader".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_OFFSET_UPDATED,
                    payload ->
                            mMainActivity.runOnUiThread(() -> mSyncStatus =
                                    mMainActivity.getString(
                                            R.string.rec_sync_synced_to_leader,
                                            mSoftwareSync.getName(), mSoftwareSync.getLeaderAddress())));

            mSoftwareSync = new SoftwareSyncClient(name, localAddress, leaderAddress, clientRpcs);
        }

        if (mIsLeader) {
            mMainActivity.runOnUiThread(
                    () -> mSyncStatus = mMainActivity.getString(R.string.rec_sync_leader, mSoftwareSync.getName()));
        } else {
            mMainActivity.runOnUiThread(
                    () -> mSyncStatus = mMainActivity.getString(R.string.rec_sync_client, mSoftwareSync.getName()));
        }
    }

    private static class AlignPhasesTask extends AsyncTask<Void, Void, Void> {
        private static final String TAG = "AlignPhasesTask";

        private final SoftwareSyncController mSoftwareSyncController;
        private final PhaseAlignController mPhaseAlignController;
        private final PeriodCalculator mPeriodCalculator;

        AlignPhasesTask(SoftwareSyncController softwareSyncController,
                        PhaseAlignController phaseAlignController,
                        PeriodCalculator periodCalculator) {
            mSoftwareSyncController = softwareSyncController;
            mPhaseAlignController = phaseAlignController;
            mPeriodCalculator = periodCalculator;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        protected Void doInBackground(Void... voids) {
            if (mSoftwareSyncController.mState != State.IDLE) {
                Log.d(TAG, "Period calculation and phase alignment cannot be started at state " +
                        mSoftwareSyncController.mState);
                return null;
            }

            Log.v(TAG, "Calculating frames period.");
            mSoftwareSyncController.mState = State.PERIOD_CALCULATION;
            try {
                long periodNs = mPeriodCalculator.getPeriodNs();
                Log.i(TAG, "Calculated frames period: " + periodNs);
                mPhaseAlignController.setPeriodNs(periodNs);
            } catch (InterruptedException | NoSuchElementException e) {
                Log.e(TAG, "Failed calculating frames period: ", e);
            }

            // Note: One could pass the current phase of the leader and have all clients sync to
            // that, reducing potential error, though special attention should be placed to phases
            // close to the zero or period boundary.
            Log.v(TAG, "Starting phase alignment.");
            mSoftwareSyncController.mState = State.PHASE_ALIGNMENT;
            mPhaseAlignController.startAlign(() -> mSoftwareSyncController.mState = State.IDLE);

            return null;
        }
    }

    private String lastFourSerial() {
        String serial = Secure.getString(mMainActivity.getContentResolver(), Secure.ANDROID_ID);
        if (serial.length() <= 4) {
            return serial;
        } else {
            return serial.substring(serial.length() - 4);
        }
    }

    /**
     * Show the number of connected clients on the leader status UI.
     *
     * <p>If the number of clients doesn't equal TOTAL_NUM_CLIENTS, show as bright red.
     */
    private void updateClientsUI() {
        SoftwareSyncLeader leader = ((SoftwareSyncLeader) mSoftwareSync);
        final int clientCount = leader.getClients().size();
        mMainActivity.runOnUiThread(
                () -> {
                    StringBuilder msg = new StringBuilder();
                    msg.append(
                            mMainActivity.getString(
                                    R.string.rec_sync_leader_clients, mSoftwareSync.getName(),
                                    mMainActivity.getResources().getQuantityString(R.plurals.clients_num, clientCount, clientCount)));
                    for (Entry<InetAddress, ClientInfo> entry : leader.getClients().entrySet()) {
                        ClientInfo client = entry.getValue();
                        if (client.syncAccuracy() == 0) {
                            msg.append(mMainActivity.getString(R.string.rec_sync_client_syncing, client.name()));
                        } else {
                            msg.append(
                                    mMainActivity.getString(
                                            R.string.rec_sync_client_synced, client.name(), client.syncAccuracy() / 1e6));
                        }
                    }
                    mSyncStatus = msg.toString();
                });
    }

    @Override
    public void close() {
        Log.w(TAG, "close SoftwareSyncController");
        if (mSoftwareSync != null) {
            try {
                mSoftwareSync.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error closing SoftwareSync", e);
            }
            mSoftwareSync = null;
        }
    }

    /**
     * Change the lock status of the settings selected for synchronization. If the settings are
     * getting locked then the provided settings are saved for broadcasts.
     *
     * @param settings the locked settings to be saved.
     * @throws IllegalArgumentException if null is provided when settings are to be locked.
     */
    public void switchSettingsLock(SyncSettingsContainer settings) {
        if (isSettingsBroadcasting()) {
            ((SoftwareSyncLeader) mSoftwareSync).setSavedSettings(null);
        } else if (settings != null) {
            ((SoftwareSyncLeader) mSoftwareSync).setSavedSettings(settings);
        } else {
            throw new IllegalArgumentException("Settings to be locked cannot be null");
        }
    }

    /**
     * Indicates whether this device is a leader.
     *
     * @return true if this device is a leader, false if it is a client.
     */
    public boolean isLeader() {
        return mIsLeader;
    }

    /**
     * Indicates whether the last finished alignment attempt was successful.
     *
     * @return true if the last alignment attempt was successful, false if it wasn't or no attempts
     * were made.
     */
    public boolean isAligned() {
        return mPhaseAlignController.wasAligned();
    }

    /**
     * Current broadcasting status of the leader's settings. If this is true then each new client
     * will receive the previously saved settings of the leader.
     *
     * @return true if the settings are being broadcast, false if they are not.
     * @throws IllegalStateException if the device if not a leader.
     */
    public boolean isSettingsBroadcasting() {
        if (mIsLeader) {
            return ((SoftwareSyncLeader) mSoftwareSync).getSavedSettings() != null;
        }
        throw new IllegalStateException("Cannot check the settings lock status for a client");
    }

    /**
     * Provides the given timestamp to {@link PeriodCalculator} and a converted to leader time
     * domain version of the given timestamp to {@link PhaseAlignController}.
     *
     * @param timestamp a timestamp to be provided.
     */
    public void updateTimestamp(long timestamp) {
        if (mState != State.PHASE_ALIGNMENT) { // No need to, when phase alignment is running.
            mPeriodCalculator.onFrameTimestamp(timestamp);
        }
        mPhaseAlignController.updateCaptureTimestamp(mSoftwareSync.leaderTimeForLocalTimeNs(timestamp));
    }

    public SoftwareSyncBase getSoftwareSync() {
        return mSoftwareSync;
    }

    public String getSyncStatus() {
        return mSyncStatus;
    }
}
