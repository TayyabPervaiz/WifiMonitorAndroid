package com.tenda.wifimonitor.data.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    /**
     * Finds the IPv4 address of this Android device on the local network (Wi-Fi/Ethernet).
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize Wi-Fi (wlan) and Ethernet interfaces
            for (intf in interfaces) {
                if (intf.isUp && (intf.name.startsWith("wlan", ignoreCase = true) ||
                            intf.name.startsWith("eth", ignoreCase = true) ||
                            intf.name.startsWith("ap", ignoreCase = true))
                ) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (!ip.isNullOrBlank() && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            }
            // Fallback: any active non-loopback IPv4 interface
            for (intf in interfaces) {
                if (intf.isUp) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (!ip.isNullOrBlank() && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
