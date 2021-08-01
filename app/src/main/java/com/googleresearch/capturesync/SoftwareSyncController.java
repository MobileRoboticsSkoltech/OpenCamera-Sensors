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
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech
 */

package com.googleresearch.capturesync;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings.Secure;
import android.util.Log;
import android.widget.TextView;

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
import net.sourceforge.opencamera.SyncSettingsContainer;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/**
 * Controller managing setup and tear down the SoftwareSync object. Needs Network permissions.
 */
public class SoftwareSyncController implements Closeable {
    private static final String TAG = "SoftwareSyncController";
    private final MainActivity context;
    private final TextView syncStatus;
    private final PhaseAlignController phaseAlignController;
    private final PeriodCalculator periodCalculator;
    private boolean isLeader;
    private SoftwareSyncBase softwareSync;
    private AlignPhasesTask alignPhasesTask;

    /** Tell devices to save the frame at the requested trigger time. */
    public static final int METHOD_SET_TRIGGER_TIME = 200_000;
    /** Tell devices to calculate frames period and phase align. */
    public static final int METHOD_DO_PHASE_ALIGN = 200_001;
    /** Tell devices to set the chosen settings to the requested values. */
    public static final int METHOD_SET_SETTINGS = 200_002;
    public static final int METHOD_START_RECORDING = 200_003;
    public static final int METHOD_STOP_RECORDING = 200_004;

    private long upcomingTriggerTimeNs;

    /**
     * Constructor passed in with: - context - For setting UI elements and triggering captures. -
     * captureButton - The button used to send at trigger request by the leader. - statusView - The
     * TextView used to show currently connected clients on the leader device.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public SoftwareSyncController(
            MainActivity context, PhaseAlignController phaseAlignController, PeriodCalculator periodCalculator, TextView syncStatus) {
        this.context = context;
        this.phaseAlignController = phaseAlignController;
        this.periodCalculator = periodCalculator;
        this.syncStatus = syncStatus;

        setupSoftwareSync();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setupSoftwareSync() {
        Log.w(TAG, "setup SoftwareSync");
        if (softwareSync != null) {
            return;
        }

        // Get Wifi Manager and use NetworkHelpers to determine local and leader IP addresses.
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        InetAddress leaderAddress;
        InetAddress localAddress;

        // Use last 4 digits of the serial as the name of the client.
        String name = lastFourSerial();
        Log.w(TAG, "Name/Serial# (Last 4 digits): " + name);

        // Determine leadership.
        try {
            NetworkHelpers networkHelper = new NetworkHelpers(wifiManager);
            isLeader = networkHelper.isLeader();

            if (isLeader) {
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
                            localAddress, leaderAddress, isLeader ? "Y" : "N"));
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
        //            Log.v(TAG, "Setting next trigger to" + payload);
        //            upcomingTriggerTimeNs = Long.valueOf(payload);
        //            // TODO: (MROB) change to video
        //            context.setUpcomingCaptureStill(upcomingTriggerTimeNs);
        //        });

        // Start frames period calculation and then the phase aligning algorithm.
        sharedRpcs.put(
                METHOD_DO_PHASE_ALIGN,
                payload -> {
                    Log.v(TAG, "Phase alignment request received.");
                    phaseAlignController.setMinExposureNs(context.getPreview().getMinimumExposureTime());
                    if (alignPhasesTask == null || alignPhasesTask.getStatus() == AsyncTask.Status.FINISHED) {
                        alignPhasesTask = new AlignPhasesTask(phaseAlignController, periodCalculator);
                        alignPhasesTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } else {
                        Log.e(TAG, "Phase aligning is already running.");
                    }
                });

        // Apply the received settings.
        sharedRpcs.put(
                METHOD_SET_SETTINGS,
                payload -> {
                    Log.v(TAG, "Received payload: " + payload);

                    SyncSettingsContainer settings = null;
                    try {
                        settings = SyncSettingsContainer.fromString(payload);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to deserialize the settings string: " + payload + "\n" + e.getMessage());
                    }

                    if (settings != null) {
                        context.getApplicationInterface().applyAndLockSettings(settings);
                    }
                });

        if (isLeader) {
            // Leader.
            long initTimeNs = TimeUtils.millisToNanos(System.currentTimeMillis());

            // Create rpc mapping specific to leader.
            Map<Integer, RpcCallback> leaderRpcs = new HashMap<>(sharedRpcs);

            // Update status text when the status changes.
            leaderRpcs.put(SyncConstants.METHOD_MSG_ADDED_CLIENT, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_REMOVED_CLIENT, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_SYNCING, payload -> updateClientsUI());
            leaderRpcs.put(SyncConstants.METHOD_MSG_OFFSET_UPDATED, payload -> updateClientsUI());

            softwareSync = new SoftwareSyncLeader(name, initTimeNs, localAddress, leaderRpcs);
        } else {
            // Client.
            Map<Integer, RpcCallback> clientRpcs = new HashMap<>(sharedRpcs);

            // Update status text to "waiting for leader".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_WAITING_FOR_LEADER,
                    payload ->
                            context.runOnUiThread(
                                    () -> syncStatus.setText(String.format(Locale.ENGLISH, "%s: Waiting for Leader", softwareSync.getName()))));

            // Update status text to "waiting for sync".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_SYNCING,
                    payload ->
                            context.runOnUiThread(
                                    () -> syncStatus.setText(String.format(Locale.ENGLISH, "%s: Waiting for Sync", softwareSync.getName()))));

            //clientRpcs.put(
            //        METHOD_START_RECORDING,
            //        payload -> {
            //            Log.v(TAG, "Starting video");
            //            context.runOnUiThread(
            //                    () -> context.startVideo(false)
            //            );
            //        });

            //clientRpcs.put(
            //        METHOD_STOP_RECORDING,
            //        payload -> {
            //            Log.v(TAG, "Stopping video");
            //            context.runOnUiThread(
            //                    context::stopVideo
            //            );
            //        });

            // Update status text to "synced to leader".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_OFFSET_UPDATED,
                    payload ->
                            context.runOnUiThread(
                                    () ->
                                            syncStatus.setText(
                                                    String.format(
                                                            "Client %s\n-Synced to Leader %s",
                                                            softwareSync.getName(), softwareSync.getLeaderAddress()))));

            softwareSync = new SoftwareSyncClient(name, localAddress, leaderAddress, clientRpcs);
        }

        if (isLeader) {
            context.runOnUiThread(
                    () -> {
                        syncStatus.setText(String.format(Locale.ENGLISH, "Leader: %s", softwareSync.getName()));
                        syncStatus.setTextColor(Color.rgb(0, 139, 0)); // Dark green.
                    });
        } else {
            context.runOnUiThread(
                    () -> {
                        syncStatus.setText(String.format(Locale.ENGLISH, "Client: %s", softwareSync.getName()));
                        syncStatus.setTextColor(Color.rgb(0, 0, 139)); // Dark blue.
                    });
        }
    }

    private static class AlignPhasesTask extends AsyncTask<Void, Void, Void> {
        private static final String TAG = "AlignPhasesTask";

        private final PhaseAlignController phaseAlignController;
        private final PeriodCalculator periodCalculator;

        AlignPhasesTask(PhaseAlignController phaseAlignController, PeriodCalculator periodCalculator) {
            this.phaseAlignController = phaseAlignController;
            this.periodCalculator = periodCalculator;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        protected Void doInBackground(Void... voids) {
            Log.v(TAG, "Calculating frames period.");
            try {
                long periodNs = periodCalculator.getPeriodNs();
                Log.i(TAG, "Calculated frames period: " + periodNs);
                phaseAlignController.setPeriodNs(periodNs);
            } catch (InterruptedException | NoSuchElementException e) {
                Log.e(TAG, "Failed calculating frames period: ", e);
            }

            // Note: One could pass the current phase of the leader and have all clients sync to
            // that, reducing potential error, though special attention should be placed to phases
            // close to the zero or period boundary.
            Log.v(TAG, "Starting phase alignment.");
            phaseAlignController.startAlign();

            return null;
        }
    }

    private String lastFourSerial() {
        String serial = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
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
        SoftwareSyncLeader leader = ((SoftwareSyncLeader) softwareSync);
        final int clientCount = leader.getClients().size();
        context.runOnUiThread(
                () -> {
                    StringBuilder msg = new StringBuilder();
                    msg.append(
                            String.format(Locale.ENGLISH,
                                    "Leader %s: %d clients.\n", softwareSync.getName(), clientCount));
                    for (Entry<InetAddress, ClientInfo> entry : leader.getClients().entrySet()) {
                        ClientInfo client = entry.getValue();
                        if (client.syncAccuracy() == 0) {
                            msg.append(String.format("-Client %s: syncing...\n", client.name()));
                        } else {
                            msg.append(
                                    String.format(Locale.ENGLISH,
                                            "-Client %s: %.2f ms sync\n", client.name(), client.syncAccuracy() / 1e6));
                        }
                    }
                    syncStatus.setText(msg.toString());
                });
    }

    @Override
    public void close() {
        Log.w(TAG, "close SoftwareSyncController");
        if (softwareSync != null) {
            try {
                softwareSync.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error closing SoftwareSync", e);
            }
            softwareSync = null;
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
            ((SoftwareSyncLeader) softwareSync).setSavedSettings(null);
        } else if (settings != null) {
            ((SoftwareSyncLeader) softwareSync).setSavedSettings(settings);
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
        return isLeader;
    }

    /**
     * Indicates whether the last finished alignment attempt was successful.
     *
     * @return true if the last alignment attempt was successful, false if it wasn't or no attempts
     * were made.
     */
    public boolean isAligned() {
        return phaseAlignController.wasAligned();
    }

    /**
     * Current broadcasting status of the leader's settings. If this is true then each new client
     * will receive the previously saved settings of the leader.
     *
     * @return true if the settings are being broadcast, false if they are not.
     * @throws IllegalStateException if the device if not a leader.
     */
    public boolean isSettingsBroadcasting() {
        if (isLeader) {
            return ((SoftwareSyncLeader) softwareSync).getSavedSettings() != null;
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
        periodCalculator.onFrameTimestamp(timestamp);
        phaseAlignController.updateCaptureTimestamp(softwareSync.leaderTimeForLocalTimeNs(timestamp));
    }

    public SoftwareSyncBase getSoftwareSync() {
        return softwareSync;
    }
}
