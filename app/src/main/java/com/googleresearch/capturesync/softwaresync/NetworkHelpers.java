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

import android.net.wifi.WifiManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Helper functions for determining local IP address and host IP address on the network.
 */
public final class NetworkHelpers {
    private final WifiManager mWifiManager;

    /**
     * Constructor providing the wifi manager for determining hotspot leader address.
     *
     * @param wifiManager via '(WifiManager) context.getSystemService(Context.WIFI_SERVICE);''
     */
    public NetworkHelpers(WifiManager wifiManager) {
        mWifiManager = wifiManager;
    }

    /**
     * Determines if the device is a leader. Requires ACCESS_WIFI_STATE permission.
     *
     * @return true if the device is a leader, false if the device is a client.
     * @throws SocketException if neither WiFi nor hotspot is enabled.
     */
    public boolean isLeader() throws SocketException {
        boolean isWifiEnabled = mWifiManager.isWifiEnabled();
        boolean isHotspotEnabled = isHotspotEnabled();
        if (!isWifiEnabled && !isHotspotEnabled) {
            throw new SocketException("Neither WiFi nor hotspot is enabled.");
        }
        return isHotspotEnabled;
    }

    private boolean isHotspotEnabled() {
        // Use isWifiApEnabled() hidden in WifiManager to determine if hotspot is enabled.
        try {
            Method method = mWifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            return (boolean) method.invoke(mWifiManager);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot invoke isWifiApEnabled() on WifiManager.", e);
        }
    }

    /**
     * Returns the IP address of the hotspot host. Requires ACCESS_WIFI_STATE permission.
     *
     * @return IP address of the hotspot host.
     * @throws IllegalStateException when WiFi is disabled.
     */
    public InetAddress getHotspotServerAddress() throws UnknownHostException {
        if (mWifiManager.isWifiEnabled()) {
            // Return the DHCP server address, which is the hotspot ip address.
            int serverAddress = mWifiManager.getDhcpInfo().serverAddress;
            return InetAddress.getByAddress(addressIntToBytes(serverAddress));
        } else {
            throw new IllegalStateException("Cannot determine hotspot host's IP when WiFi is disabled.");
        }
    }

    /**
     * Returns this device's wlan IP address. Requires ACCESS_WIFI_STATE permission.
     *
     * @return wlan IP address of this device.
     * @throws IllegalStateException when WiFi is disabled.
     */
    public InetAddress getIPAddress() throws UnknownHostException {
        if (mWifiManager.isWifiEnabled()) {
            int wlanAddress = mWifiManager.getConnectionInfo().getIpAddress();
            return InetAddress.getByAddress(addressIntToBytes(wlanAddress));
        } else {
            throw new IllegalStateException("Cannot determine wlan IP when WiFi is disabled.");
        }
    }

    private byte[] addressIntToBytes(int address) {
        // DhcpInfo and ConnectionInfo integer addresses are Little Endian and
        // InetAddresses.fromInteger() are Big Endian so reverse the bytes before converting.
        return new byte[]{
                (byte) (0xff & address),
                (byte) (0xff & (address >> 8)),
                (byte) (0xff & (address >> 16)),
                (byte) (0xff & (address >> 24))
        };
    }
}
