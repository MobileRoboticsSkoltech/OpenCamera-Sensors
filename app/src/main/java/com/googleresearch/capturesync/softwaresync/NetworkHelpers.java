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
 *
 *
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech
 */

package com.googleresearch.capturesync.softwaresync;

import android.net.wifi.WifiManager;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Helper functions for determining local IP address and host IP address on the network. */
public final class NetworkHelpers {
  private final WifiManager wifiManager;

  /**
   * Constructor providing the wifi manager for determining hotspot leader address.
   *
   * @param wifiManager via '(WifiManager) context.getSystemService(Context.WIFI_SERVICE);''
   */
  public NetworkHelpers(WifiManager wifiManager) {
    this.wifiManager = wifiManager;
  }

  /**
   * Returns the IP address of the hotspot host. Requires ACCESS_WIFI_STATE permission. Note: This
   * may not work on several devices.
   *
   * @return IP address of the hotspot host.
   */
  public InetAddress getHotspotServerAddress() throws UnknownHostException {
    if (wifiManager.isWifiEnabled()) {
      // Return the DHCP server address, which is the hotspot ip address.
      int serverAddress = wifiManager.getDhcpInfo().serverAddress;
      return InetAddress.getByAddress(addressIntToBytes(serverAddress));
    }
    // If wifi is disabled, then this is the hotspot host, return the local ip address.
    return getIPAddress();
  }

  /**
   * Finds this device's wlan IP address. Requires ACCESS_WIFI_STATE permission.
   *
   * @return wlan IP address of this device.
   */
  public InetAddress getIPAddress() throws UnknownHostException {
    int address = wifiManager.getConnectionInfo().getIpAddress();
    return InetAddress.getByAddress(addressIntToBytes(address));
  }

  private byte[] addressIntToBytes(int address) {
    // DhcpInfo and ConnectionInfo integer addresses are Little Endian and
    // InetAddresses.fromInteger() are Big Endian so reverse the bytes before converting.
    return new byte[] {
            (byte) (0xff & address),
            (byte) (0xff & (address >> 8)),
            (byte) (0xff & (address >> 16)),
            (byte) (0xff & (address >> 24))
    };
  }
}
