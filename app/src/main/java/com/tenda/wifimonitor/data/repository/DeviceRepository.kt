package com.tenda.wifimonitor.data.repository

import android.util.Log
import com.tenda.wifimonitor.data.db.DeviceDao
import com.tenda.wifimonitor.data.db.DeviceSessionEntity
import com.tenda.wifimonitor.data.db.toEntity
import com.tenda.wifimonitor.data.network.ArpScanner
import com.tenda.wifimonitor.data.network.OuiLookup
import com.tenda.wifimonitor.data.network.RouterDevice
import com.tenda.wifimonitor.data.network.TendaApiService
import com.tenda.wifimonitor.domain.model.DeviceSession
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(
    val newDevices: List<NetworkDevice>,
    val intruders: List<NetworkDevice>,
    val departed: List<NetworkDevice>,
    val allOnline: List<NetworkDevice>,
)


@Singleton
class DeviceRepository @Inject constructor(
    private val dao: DeviceDao,
    private val apiService: TendaApiService,
    private val arpScanner: ArpScanner,
    private val ouiLookup: OuiLookup,
    private val notificationHelper: NotificationHelper,
) {
    private val TAG = "DeviceRepository"

    // ── Observable Flows ─────────────────────────────────────────────────────────

    fun observeAllDevices(): Flow<List<NetworkDevice>> =
        dao.observeAllDevices().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeOnlineDevices(): Flow<List<NetworkDevice>> =
        dao.observeOnlineDevices().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeOnlineCount(): Flow<Int> = dao.observeOnlineCount()

    fun observeDevice(mac: String): Flow<NetworkDevice?> =
        dao.observeDevice(mac).map { it?.toDomain() }.flowOn(Dispatchers.Default)

    fun observeAllSessions(): Flow<List<DeviceSession>> =
        dao.observeAllSessions().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeSessionsForDevice(mac: String): Flow<List<DeviceSession>> =
        dao.observeSessionsForDevice(mac).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeUnknownDevices(): Flow<List<NetworkDevice>> =
        dao.observeUnknownDevices().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    // ── Scan & Diff ──────────────────────────────────────────────────────────────

    /**
     * Performs a full network scan using the router API (with ARP fallback),
     * persists changes to Room, and returns what's new, what left, and who is an intruder.
     */
    suspend fun performScan(password: String): ScanResult {
        val now = System.currentTimeMillis()
        
        // Clean up any duplicate active sessions caused by previous bugs
        dao.cleanupDuplicateActiveSessions(now)

        // 1. Fetch from router API — use existing session, re-login if session expired
        var routerDevices = try {
            var devices = apiService.getConnectedDevices()
            if (devices.isEmpty()) {
                // Empty list means either auth expired or truly no devices.
                // Force re-login and try exactly once more.
                Log.d(TAG, "Device list empty — attempting re-login")
                if (apiService.login(password)) {
                    devices = apiService.getConnectedDevices()
                    Log.d(TAG, "Post-login device count: ${devices.size}")
                } else {
                    Log.e(TAG, "Re-login failed — cannot fetch devices")
                }
            }
            devices
        } catch (e: Exception) {
            Log.e(TAG, "Router API error: ${e.message}")
            emptyList()
        }

        // 2. Fallback to ARP only if BOTH the API call AND re-login attempt returned nothing
        if (routerDevices.isEmpty()) {
            Log.d(TAG, "Router API empty after re-login — using ARP fallback")
            routerDevices = arpScanner.scanArpTable()
        }

        // 3. Get existing devices from DB into a map to prevent N+1 queries
        val existingDevices = dao.getAllDevicesSync().associateBy { it.macAddress }

        val newDevices = mutableListOf<NetworkDevice>()
        val intruders = mutableListOf<NetworkDevice>()

        // 4. Process each scanned device
        routerDevices.forEach { rd ->
            val existing = existingDevices[rd.macAddress]
            val manufacturer = ouiLookup.lookup(rd.macAddress)
            val deviceType = DeviceType.fromHostname(rd.hostname, manufacturer)

            if (existing == null) {
                // Brand new device
                val device = NetworkDevice(
                    macAddress = rd.macAddress,
                    ipAddress = rd.ipAddress,
                    hostname = rd.hostname,
                    manufacturer = manufacturer,
                    deviceType = deviceType,
                    isOnline = true,
                    isWhitelisted = false, // new = unknown until whitelisted
                    firstSeen = Date(now),
                    lastSeen = Date(now),
                )
                dao.insertDevice(device.toEntity())
                newDevices.add(device)
                if (!device.isWhitelisted) intruders.add(device)
                
                // Open session if none exists
                val activeSession = dao.getActiveSession(rd.macAddress)
                if (activeSession == null) {
                    dao.insertSession(DeviceSessionEntity(
                        macAddress = rd.macAddress,
                        ipAddress = rd.ipAddress,
                        hostname = rd.hostname,
                        connectedAt = now,
                    ))
                }
            } else {
                val wasOffline = !existing.isOnline
                // Update with fresh data
                dao.insertDevice(existing.copy(
                    ipAddress = rd.ipAddress,
                    hostname = rd.hostname.ifBlank { existing.hostname },
                    isOnline = true,
                    lastSeen = now,
                ))
                if (wasOffline) {
                    val domain = existing.toDomain().copy(isOnline = true)
                    newDevices.add(domain)
                    if (!existing.isWhitelisted) intruders.add(domain)
                    
                    // Open session if none exists
                    val activeSession = dao.getActiveSession(rd.macAddress)
                    if (activeSession == null) {
                        dao.insertSession(DeviceSessionEntity(
                            macAddress = rd.macAddress,
                            ipAddress = rd.ipAddress,
                            hostname = rd.hostname,
                            connectedAt = now,
                        ))
                    }
                }
            }
        }

        // 5. Mark missing devices as offline and close their sessions
        val onlineMacs = routerDevices.map { it.macAddress }
        // Find which devices went offline this scan so we can clear their notification guard
        val departedMacs = existingDevices.keys
            .filter { it !in onlineMacs }
            .filter { existingDevices[it]?.isOnline == true } // was online before
        dao.markOffline(onlineMacs)
        // Clear the notified-set so these devices trigger a fresh notification when they reconnect
        // and close ALL their open sessions to ensure no ghost "Active" sessions remain
        departedMacs.forEach { mac -> 
            notificationHelper.clearNotifiedDevice(mac) 
            dao.closeAllOpenSessionsForMac(mac, now)
        }

        val departed = departedMacs.mapNotNull { existingDevices[it]?.toDomain() }

        // 6. Build final online list
        val allOnline = routerDevices.map { rd ->
            val existingDomain = existingDevices[rd.macAddress]?.toDomain()
            existingDomain ?: NetworkDevice(
                macAddress = rd.macAddress,
                ipAddress = rd.ipAddress,
                hostname = rd.hostname,
            )
        }

        return ScanResult(
            newDevices = newDevices,
            intruders = intruders,
            departed = departed,
            allOnline = allOnline,
        )
    }

    // ── Device Management ────────────────────────────────────────────────────────

    suspend fun whitelistDevice(mac: String) = dao.updateWhitelist(mac, true)
    suspend fun removeFromWhitelist(mac: String) = dao.updateWhitelist(mac, false)
    suspend fun updateCustomName(mac: String, name: String?) = dao.updateCustomName(mac, name)
    suspend fun updateCustomIcon(mac: String, icon: String?) = dao.updateCustomIcon(mac, icon)
    suspend fun updateDeviceType(mac: String, type: DeviceType) =
        dao.updateDeviceType(mac, type.name)

    suspend fun blockDevice(mac: String) = apiService.blockDevice(mac)
    suspend fun unblockDevice(mac: String) = apiService.unblockDevice(mac)

    // ── Analytics ────────────────────────────────────────────────────────────────

    suspend fun getHourlyDeviceActivity(): Map<Int, Int> {
        return dao.getDeviceCountByHour().associate {
            (it.hour.toIntOrNull() ?: 0) to it.count
        }
    }

    suspend fun login(password: String): Boolean = apiService.login(password)

    suspend fun getRouterStatus() = apiService.getRouterStatus()

    suspend fun pruneOldHistory() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        dao.pruneOldSessions(thirtyDaysAgo)
    }
}
