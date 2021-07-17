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
 */

package com.googleresearch.capturesync.softwaresync;

import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

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
  public InetAddress getHotspotServerAddress() throws SocketException, UnknownHostException {
    if (wifiManager.isWifiEnabled()) {
      // Return the DHCP server address, which is the hotspot ip address.
      int serverAddress = wifiManager.getDhcpInfo().serverAddress;
      // DhcpInfo integer addresses are Little Endian and InetAddresses.fromInteger() are Big Endian
      // so reverse the bytes before converting from Integer.
      byte[] addressBytes = {
        (byte) (0xff & serverAddress),
        (byte) (0xff & (serverAddress >> 8)),
        (byte) (0xff & (serverAddress >> 16)),
        (byte) (0xff & (serverAddress >> 24))
      };
      return InetAddress.getByAddress(addressBytes);
    }
    // If wifi is disabled, then this is the hotspot host, return the local ip address.
    return getIPAddress();
  }

  /**
   * Finds this devices's IPv4 address that is not localhost and is on a wlan interface.
   *
   * @return the String IP address on success.
   * @throws SocketException on failure to find a suitable IP address.
   */
  public static InetAddress getIPAddress() throws SocketException {
    List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
    for (NetworkInterface intf : interfaces) {
      for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
        if (!addr.isLoopbackAddress()
            && intf.getName().startsWith("wlan")
            && addr instanceof Inet4Address) {
          return addr;
        }
      }
    }
    throw new SocketException("No viable IP Network addresses found.");
  }
}
