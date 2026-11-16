package com.tenda.wifimonitor.data.network

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads /proc/net/arp to get all ARP table entries.
 * Also can ping-sweep the 192.168.0.x subnet to populate the ARP cache.
 * This works without root and is a reliable fallback when the router API fails.
 */
@Singleton
class ArpScanner @Inject constructor() {

    private val TAG = "ArpScanner"

    // 5-minute TTL hostname resolution cache to avoid repeated slow reverse-DNS lookups
    private val hostnameCache = mutableMapOf<String, Pair<String, Long>>()
    private val cacheTtlMs = 5 * 60 * 1000L

    /**
     * Reads /proc/net/arp and returns all valid (non-null MAC) entries.
     * ARP file format:
     * IP address       HW type  Flags  HW address         Mask  Device
     * 192.168.0.102    0x1      0x2    aa:bb:cc:dd:ee:ff  *     wlan0
     */
    fun scanArpTable(): List<RouterDevice> {
        val devices = mutableListOf<RouterDevice>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 6) {
                        val ip = parts[0]
                        val mac = parts[3].uppercase()
                        val iface = parts[5]
                        // Skip incomplete/invalid entries (MAC = 00:00:00:00:00:00)
                        if (mac != "00:00:00:00:00:00" && mac.matches(Regex("[0-9A-F:]{17}"))) {
                            devices.add(RouterDevice(
                                macAddress = mac,
                                ipAddress = ip,
                                hostname = tryResolveHostname(ip),
                            ))
                            Log.d(TAG, "ARP: $ip -> $mac [$iface]")
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ARP scan error: ${e.message}")
        }
        return devices
    }

    /**
     * Ping-sweeps 192.168.0.1–254 to populate the ARP cache.
     * After this completes, scanArpTable() will have fresh results.
     * Uses a short timeout (200ms) per host.
     */
    suspend fun pingSweep(subnet: String = "192.168.0", onProgress: (Int) -> Unit = {}) {
        coroutineScope {
            val semaphore = Semaphore(32)
            val deferreds = (1..254).map { i ->
                async {
                    semaphore.acquire()
                    try {
                        val ip = "$subnet.$i"
                        try {
                            InetAddress.getByName(ip).isReachable(200)
                        } catch (_: Exception) {}
                    } finally {
                        semaphore.release()
                    }
                    if (i % 25 == 0) onProgress(i)
                }
            }
            deferreds.awaitAll()
        }
    }

    private fun tryResolveHostname(ip: String): String {
        val now = System.currentTimeMillis()
        hostnameCache[ip]?.let { (name, timestamp) ->
            if (now - timestamp < cacheTtlMs) return name
        }
        return try {
            val addr = InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            val resolved = if (name == ip) "Unknown" else name
            hostnameCache[ip] = resolved to now
            resolved
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
