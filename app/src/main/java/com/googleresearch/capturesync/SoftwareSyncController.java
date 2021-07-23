/**
 * Copyright 2019 The Google Research Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech
 */

package com.googleresearch.capturesync;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.provider.Settings.Secure;
import android.util.Log;
import android.widget.TextView;

import com.googleresearch.capturesync.softwaresync.ClientInfo;
import com.googleresearch.capturesync.softwaresync.NetworkHelpers;
import com.googleresearch.capturesync.softwaresync.RpcCallback;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncClient;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncLeader;
import com.googleresearch.capturesync.softwaresync.SyncConstants;
import com.googleresearch.capturesync.softwaresync.TimeUtils;

import net.sourceforge.opencamera.ExtendedAppInterface;
import net.sourceforge.opencamera.MainActivity;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

// Note : Needs Network permissions.

/**
 * Controller managing setup and tear down the SoftwareSync object.
 */
public class SoftwareSyncController implements Closeable {

    private static final String TAG = "SoftwareSyncController";
    private final MainActivity context;
    private final TextView statusView;
    private final PhaseAlignController phaseAlignController;
    private boolean isLeader;
    private boolean isSettingsLocked;
    private SoftwareSyncBase softwareSync;

    /* Tell devices to save the frame at the requested trigger time. */
    public static final int METHOD_SET_TRIGGER_TIME = 200_000;

    /* Tell devices to phase align. */
    public static final int METHOD_DO_PHASE_ALIGN = 200_001;
    /* Tell devices to set chosen settings to the requested values. */
    public static final int METHOD_SET_SETTINGS = 200_002;
    public static final int METHOD_START_RECORDING = 200_003;
    public static final int METHOD_STOP_RECORDING = 200_004;

    private long upcomingTriggerTimeNs;

    /**
     * Constructor passed in with: - context - For setting UI elements and triggering captures. -
     * captureButton - The button used to send at trigger request by the leader. - statusView - The
     * TextView used to show currently connected clients on the leader device.
     */
    public SoftwareSyncController(
            MainActivity context, PhaseAlignController phaseAlignController, TextView statusView) {
        this.context = context;
        this.phaseAlignController = phaseAlignController;
        this.statusView = statusView;

        setupSoftwareSync();
    }

    @SuppressWarnings("StringSplitter")
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

        //sharedRpcs.put(
        //        METHOD_DO_PHASE_ALIGN,
        //        payload -> {
        //            // Note: One could pass the current phase of the leader and have all clients sync to
        //            // that, reducing potential error, though special attention should be placed to phases
        //            // close to the zero or period boundary.
        //            Log.v(TAG, "Starting phase alignment.");
        //            phaseAlignController.startAlign();
        //        });

        // Apply the received settings.
        sharedRpcs.put(
                METHOD_SET_SETTINGS,
                payload -> {
                    Log.v(TAG, "Received payload: " + payload);

                    String[] segments = payload.split(",");
                    if (segments.length != 11) {
                        throw new IllegalArgumentException("Wrong number of segments in payload: " + payload);
                    }

                    ExtendedAppInterface.SettingsContainer settings = new ExtendedAppInterface.SettingsContainer(
                            // preferences
                            Boolean.parseBoolean(segments[0]), // syncIso
                            Boolean.parseBoolean(segments[1]), // syncWb
                            Boolean.parseBoolean(segments[2]), // syncFlash
                            Boolean.parseBoolean(segments[3]), // syncFormat
                            // values
                            Boolean.parseBoolean(segments[4]), // isVideo
                            Long.parseLong(segments[5]), // exposure
                            Integer.parseInt(segments[6]), // iso
                            Integer.parseInt(segments[7]), // wbTemperature
                            segments[8], // wbMode
                            segments[9], // flash
                            segments[10] // format
                    );

                    context.getApplicationInterface().applyAndLockSettings(settings);
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
                                    () -> statusView.setText(softwareSync.getName() + ": Waiting for Leader")));

            // Update status text to "waiting for sync".
            clientRpcs.put(
                    SyncConstants.METHOD_MSG_SYNCING,
                    payload ->
                            context.runOnUiThread(
                                    () -> statusView.setText(softwareSync.getName() + ": Waiting for Sync")));

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
                                            statusView.setText(
                                                    String.format(
                                                            "Client %s\n-Synced to Leader %s",
                                                            softwareSync.getName(), softwareSync.getLeaderAddress()))));

            softwareSync = new SoftwareSyncClient(name, localAddress, leaderAddress, clientRpcs);
        }

        if (isLeader) {
            context.runOnUiThread(
                    () -> {
                        statusView.setText("Leader : " + softwareSync.getName());
                        statusView.setTextColor(Color.rgb(0, 139, 0)); // Dark green.
                    });
        } else {
            context.runOnUiThread(
                    () -> {
                        statusView.setText("Client : " + softwareSync.getName());
                        statusView.setTextColor(Color.rgb(0, 0, 139)); // Dark blue.
                    });
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
                            String.format("Leader %s: %d clients.\n", softwareSync.getName(), clientCount));
                    for (Entry<InetAddress, ClientInfo> entry : leader.getClients().entrySet()) {
                        ClientInfo client = entry.getValue();
                        if (client.syncAccuracy() == 0) {
                            msg.append(String.format("-Client %s: syncing...\n", client.name()));
                        } else {
                            msg.append(
                                    String.format(
                                            "-Client %s: %.2f ms sync\n", client.name(), client.syncAccuracy() / 1e6));
                        }
                    }
                    statusView.setText(msg.toString());
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
     * Change the lock status of the settings selected for synchronization.
     */
    public void switchSettingsLock() {
        isSettingsLocked = !isSettingsLocked;
    }

    private String lastFourSerial() {
        String serial = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        if (serial.length() <= 4) {
            return serial;
        } else {
            return serial.substring(serial.length() - 4);
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public boolean isSettingsLocked() {
        return isSettingsLocked;
    }

    public TextView getStatusText() {
        return statusView;
    }

    public SoftwareSyncBase getSoftwareSync() {
        return softwareSync;
    }
}
