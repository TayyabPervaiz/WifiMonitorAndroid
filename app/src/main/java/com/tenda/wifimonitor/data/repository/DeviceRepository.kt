package com.tenda.wifimonitor.data.repository

import androidx.room.Transaction
import androidx.room.withTransaction
import com.tenda.wifimonitor.data.db.AppDatabase
import com.tenda.wifimonitor.data.db.DeviceDao
import com.tenda.wifimonitor.data.db.DeviceSessionEntity
import com.tenda.wifimonitor.data.db.KnownMacDao
import com.tenda.wifimonitor.data.db.KnownMacEntity
import com.tenda.wifimonitor.data.db.toEntity
import com.tenda.wifimonitor.data.network.DeviceBandwidthInfo
import com.tenda.wifimonitor.data.network.MacFingerprint
import com.tenda.wifimonitor.data.network.RouterFullStatus
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

import android.content.Context
import android.util.Log
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.db.TimedBlockEntity
import com.tenda.wifimonitor.domain.model.TimedBlock
import com.tenda.wifimonitor.service.TimedBlockReceiver
import com.tenda.wifimonitor.service.TimedUnblockWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull

data class ScanResult(
    val newDevices: List<NetworkDevice>,
    val intruders: List<NetworkDevice>,
    val departed: List<NetworkDevice>,
    val allOnline: List<NetworkDevice>,
)


@Singleton
class DeviceRepository @Inject constructor(
    private val dao: DeviceDao,
    private val knownMacDao: KnownMacDao,
    private val db: AppDatabase,
    private val apiService: TendaApiService,
    private val arpScanner: ArpScanner,
    private val ouiLookup: OuiLookup,
    private val notificationHelper: NotificationHelper,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) {
    private val TAG = "DeviceRepository"

    private fun mapToDomain(entity: com.tenda.wifimonitor.data.db.DeviceEntity): NetworkDevice {
        val domain = entity.toDomain()
        val isLoc = apiService.isLocalDevice(domain.macAddress, domain.ipAddress)
        return if (isLoc) domain.copy(isLocal = true, isWhitelisted = true) else domain
    }

    // ── Observable Flows ─────────────────────────────────────────────────────────

    fun observeAllDevices(): Flow<List<NetworkDevice>> =
        dao.observeAllDevices().map { list -> list.map { mapToDomain(it) } }.flowOn(Dispatchers.Default)

    fun observeOnlineDevices(): Flow<List<NetworkDevice>> =
        dao.observeOnlineDevices().map { list -> list.map { mapToDomain(it) } }.flowOn(Dispatchers.Default)

    fun observeOnlineCount(): Flow<Int> = dao.observeOnlineCount()

    fun observeDevice(mac: String): Flow<NetworkDevice?> =
        dao.observeDevice(mac).map { it?.let { mapToDomain(it) } }.flowOn(Dispatchers.Default)

    fun observeAllSessions(): Flow<List<DeviceSession>> =
        dao.observeAllSessions().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeSessionsForDevice(mac: String): Flow<List<DeviceSession>> =
        dao.observeSessionsForDevice(mac).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeSessionsForDeviceId(deviceId: String): Flow<List<DeviceSession>> =
        dao.observeSessionsForDeviceId(deviceId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeUnknownDevices(): Flow<List<NetworkDevice>> =
        dao.observeUnknownDevices().map { list -> list.map { mapToDomain(it) }.filter { !it.isLocal } }.flowOn(Dispatchers.Default)

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
        val autoBlockUnknown = preferences.autoBlockUnknownDevices.firstOrNull() ?: false
        val allExisting = dao.getAllDevicesSync()
        val existingDevices = allExisting.associateBy { it.macAddress }
        val existingByDeviceId = allExisting.associateBy { it.deviceId }

        // Group by normalized hostname and only keep unambiguous hostnames (count == 1)
        val unambiguousHostnameMatches: Map<String, com.tenda.wifimonitor.data.db.DeviceEntity> = allExisting
            .mapNotNull { dev ->
                val norm = MacFingerprint.normalizeHostname(dev.hostname)
                if (norm.isNotEmpty()) norm to dev else null
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }

        val claimedThisScan = mutableSetOf<String>()
        val resolvedEntities = mutableMapOf<String, com.tenda.wifimonitor.data.db.DeviceEntity>()

        val newDevices = mutableListOf<NetworkDevice>()
        val intruders = mutableListOf<NetworkDevice>()

        // 4. Process each scanned device
        routerDevices.forEach { rd ->
            val exactMatch = existingDevices[rd.macAddress]
            val resolvedDevice: com.tenda.wifimonitor.data.db.DeviceEntity? = exactMatch ?: run {
                // 1. Check known_macs table first (matches any previously seen or merged MAC)
                val knownMacEntry = knownMacDao.getByMac(rd.macAddress)
                if (knownMacEntry != null) {
                    val candidate = existingByDeviceId[knownMacEntry.deviceId]
                    if (candidate != null && candidate.deviceId !in claimedThisScan) {
                        claimedThisScan.add(candidate.deviceId)
                        return@run candidate
                    }
                }

                // 2. Fallback to unambiguous hostname match for locally-administered randomized MACs
                if (!MacFingerprint.isLocallyAdministered(rd.macAddress)) return@run null
                val normalizedHost = MacFingerprint.normalizeHostname(rd.hostname)
                if (normalizedHost.isEmpty()) return@run null
                val candidate = unambiguousHostnameMatches[normalizedHost] ?: return@run null
                if (candidate.deviceId in claimedThisScan) return@run null
                claimedThisScan.add(candidate.deviceId)
                candidate
            }

            val manufacturer = ouiLookup.lookup(rd.macAddress)
            val deviceType = DeviceType.fromHostname(rd.hostname, manufacturer)
            val isLoc = apiService.isLocalDevice(rd.macAddress, rd.ipAddress)

            if (resolvedDevice == null) {
                // Case A: Brand new device
                var isBlockedNew = false
                if (autoBlockUnknown && !isLoc) {
                    Log.w(TAG, "Auto-blocking unknown device: ${rd.macAddress} (${rd.hostname})")
                    apiService.blockDevice(rd.macAddress)
                    isBlockedNew = true
                }

                val device = NetworkDevice(
                    macAddress = rd.macAddress,
                    ipAddress = rd.ipAddress,
                    hostname = rd.hostname,
                    manufacturer = manufacturer,
                    deviceType = deviceType,
                    isOnline = true,
                    isWhitelisted = isLoc, // local device is always trusted
                    firstSeen = Date(now),
                    lastSeen = Date(now),
                    isLocal = isLoc,
                    isBlocked = isBlockedNew,
                )
                val entity = device.toEntity()
                dao.insertDevice(entity)
                resolvedEntities[rd.macAddress] = entity

                // Insert into known_macs for this new mac/deviceId pair
                knownMacDao.upsert(
                    KnownMacEntity(
                        mac = rd.macAddress,
                        deviceId = entity.deviceId,
                        firstSeen = now,
                        lastSeen = now,
                    )
                )

                newDevices.add(device)
                if (!device.isWhitelisted && !isLoc) intruders.add(device)
                
                // Open session if none exists
                val activeSession = dao.getActiveSession(rd.macAddress)
                if (activeSession == null) {
                    dao.insertSession(DeviceSessionEntity(
                        macAddress = rd.macAddress,
                        ipAddress = rd.ipAddress,
                        hostname = rd.hostname,
                        connectedAt = now,
                        deviceId = entity.deviceId,
                    ))
                }
            } else if (exactMatch == null) {
                // Case B: Fallback / known_macs / hostname match (MAC address rotated)
                val candidate = resolvedDevice
                Log.d(TAG, "Matched device ${candidate.deviceId} to new MAC ${rd.macAddress} (was ${candidate.macAddress})")

                // If candidate was marked as blocked, RE-ENFORCE block on router for this new MAC!
                if (candidate.isBlocked) {
                    Log.w(TAG, "Re-enforcing block on rotated MAC ${rd.macAddress} for device ${candidate.deviceId}")
                    apiService.blockDevice(rd.macAddress)
                }

                // Update the existing device row's macAddress field to raw.mac
                val updatedEntity = candidate.copy(
                    macAddress = rd.macAddress,
                    ipAddress = rd.ipAddress,
                    hostname = rd.hostname.ifBlank { candidate.hostname },
                    manufacturer = manufacturer,
                    deviceType = deviceType.name,
                    isOnline = true,
                    isWhitelisted = if (isLoc) true else candidate.isWhitelisted,
                    lastSeen = now,
                    isBlocked = candidate.isBlocked,
                    downloadLimitKbps = candidate.downloadLimitKbps,
                    uploadLimitKbps = candidate.uploadLimitKbps,
                )
                if (candidate.macAddress != rd.macAddress) {
                    dao.deleteByMacAddress(candidate.macAddress)
                }
                dao.insertDevice(updatedEntity)
                resolvedEntities[rd.macAddress] = updatedEntity

                // Upsert row into known_macs
                val existingKnownMac = knownMacDao.getByMac(rd.macAddress)
                knownMacDao.upsert(
                    KnownMacEntity(
                        mac = rd.macAddress,
                        deviceId = candidate.deviceId,
                        firstSeen = existingKnownMac?.firstSeen ?: now,
                        lastSeen = now,
                    )
                )

                val wasOffline = !candidate.isOnline
                if (wasOffline) {
                    val domain = updatedEntity.toDomain().copy(
                        isOnline = true,
                        isLocal = isLoc,
                        isWhitelisted = if (isLoc) true else candidate.isWhitelisted,
                        isBlocked = candidate.isBlocked,
                    )
                    newDevices.add(domain)
                    if (candidate.isBlocked) {
                        intruders.add(domain)
                    }
                }

                // Open session if none exists
                val activeSession = dao.getActiveSession(rd.macAddress)
                if (activeSession == null) {
                    dao.insertSession(DeviceSessionEntity(
                        macAddress = rd.macAddress,
                        ipAddress = rd.ipAddress,
                        hostname = rd.hostname,
                        connectedAt = now,
                        deviceId = candidate.deviceId,
                    ))
                }
            } else {
                // Case C: Exact MAC match
                if (exactMatch.isBlocked) {
                    apiService.blockDevice(rd.macAddress)
                }
                val wasOffline = !exactMatch.isOnline
                val updatedEntity = exactMatch.copy(
                    ipAddress = rd.ipAddress,
                    hostname = rd.hostname.ifBlank { exactMatch.hostname },
                    isOnline = true,
                    isWhitelisted = if (isLoc) true else exactMatch.isWhitelisted,
                    lastSeen = now,
                    isBlocked = exactMatch.isBlocked,
                    downloadLimitKbps = exactMatch.downloadLimitKbps,
                    uploadLimitKbps = exactMatch.uploadLimitKbps,
                )
                dao.insertDevice(updatedEntity)
                resolvedEntities[rd.macAddress] = updatedEntity

                // Keep known_macs updated
                val existingKnownMac = knownMacDao.getByMac(rd.macAddress)
                knownMacDao.upsert(
                    KnownMacEntity(
                        mac = rd.macAddress,
                        deviceId = exactMatch.deviceId,
                        firstSeen = existingKnownMac?.firstSeen ?: exactMatch.firstSeen,
                        lastSeen = now,
                    )
                )

                if (wasOffline) {
                    val domain = updatedEntity.toDomain().copy(
                        isOnline = true,
                        isLocal = isLoc,
                        isWhitelisted = if (isLoc) true else exactMatch.isWhitelisted,
                        isBlocked = exactMatch.isBlocked,
                    )
                    newDevices.add(domain)
                    if (exactMatch.isBlocked) {
                        intruders.add(domain)
                    }
                }

                // Open session if none exists
                val activeSession = dao.getActiveSession(rd.macAddress)
                if (activeSession == null) {
                    dao.insertSession(DeviceSessionEntity(
                        macAddress = rd.macAddress,
                        ipAddress = rd.ipAddress,
                        hostname = rd.hostname,
                        connectedAt = now,
                        deviceId = exactMatch.deviceId,
                    ))
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

        val departed = departedMacs.mapNotNull { existingDevices[it]?.let { dev -> mapToDomain(dev) } }

        // 6. Build final online list
        val allOnline = routerDevices.map { rd ->
            val existingDomain = (resolvedEntities[rd.macAddress] ?: existingDevices[rd.macAddress])?.let { mapToDomain(it) }
            val isLoc = apiService.isLocalDevice(rd.macAddress, rd.ipAddress)
            existingDomain ?: NetworkDevice(
                macAddress = rd.macAddress,
                ipAddress = rd.ipAddress,
                hostname = rd.hostname,
                isLocal = isLoc,
                isWhitelisted = isLoc,
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

    suspend fun blockDevice(mac: String): Boolean {
        val ok = apiService.blockDevice(mac)
        dao.updateBlockedStatus(mac, true)
        val dev = dao.getDevice(mac)
        if (dev != null) {
            dao.updateBlockedStatusByDeviceId(dev.deviceId, true)
        }
        return ok
    }

    suspend fun unblockDevice(mac: String): Boolean {
        val ok = apiService.unblockDevice(mac)
        dao.updateBlockedStatus(mac, false)
        val dev = dao.getDevice(mac)
        if (dev != null) {
            dao.updateBlockedStatusByDeviceId(dev.deviceId, false)
        }
        return ok
    }

    @Transaction
    suspend fun mergeDevices(keepDeviceId: String, mergeAwayDeviceId: String) {
        // a. Guard: if keepDeviceId == mergeAwayDeviceId, return immediately (no-op) — do not throw.
        if (keepDeviceId == mergeAwayDeviceId) return

        db.withTransaction {
            // b. Fetch both DeviceEntity rows via getByDeviceId. If either is null, return immediately — do not throw.
            val kept = dao.getByDeviceId(keepDeviceId) ?: return@withTransaction
            val mergeAway = dao.getByDeviceId(mergeAwayDeviceId) ?: return@withTransaction

            // c. Reassign all sessions: dao.reassignSessions(mergeAwayDeviceId, keepDeviceId)
            dao.reassignSessions(mergeAwayDeviceId, keepDeviceId)

            // d. Reassign all known MACs: knownMacDao.reassignKnownMacs(mergeAwayDeviceId, keepDeviceId)
            knownMacDao.reassignKnownMacs(mergeAwayDeviceId, keepDeviceId)

            // e. Upsert the merge-away device's current macAddress into known_macs pointed at keepDeviceId
            val now = System.currentTimeMillis()
            val existingKm = knownMacDao.getByMac(mergeAway.macAddress)
            knownMacDao.upsert(
                KnownMacEntity(
                    mac = mergeAway.macAddress,
                    deviceId = keepDeviceId,
                    firstSeen = existingKm?.firstSeen ?: mergeAway.firstSeen,
                    lastSeen = maxOf(mergeAway.lastSeen, now),
                )
            )

            // f. Update the kept device row: set macAddress = merge-away device's macAddress, isOnline and lastSeen
            // Do NOT change customName, isWhitelisted, firstSeen, or deviceId on the kept row.
            val updatedKept = kept.copy(
                macAddress = mergeAway.macAddress,
                ipAddress = mergeAway.ipAddress,
                hostname = mergeAway.hostname.ifBlank { kept.hostname },
                manufacturer = if (mergeAway.manufacturer != "Unknown") mergeAway.manufacturer else kept.manufacturer,
                deviceType = if (mergeAway.deviceType != DeviceType.UNKNOWN.name) mergeAway.deviceType else kept.deviceType,
                isOnline = mergeAway.isOnline || kept.isOnline,
                lastSeen = maxOf(kept.lastSeen, mergeAway.lastSeen),
                isBlocked = kept.isBlocked || mergeAway.isBlocked,
                downloadLimitKbps = kept.downloadLimitKbps ?: mergeAway.downloadLimitKbps,
                uploadLimitKbps = kept.uploadLimitKbps ?: mergeAway.uploadLimitKbps,
            )

            // If the kept device is blocked, re-enforce the block on the router for the merge-away live MAC!
            if (updatedKept.isBlocked) {
                apiService.blockDevice(mergeAway.macAddress)
            }

            // g. Delete merge-away row / old kept row safely and insert updated kept device
            if (kept.macAddress != mergeAway.macAddress) {
                dao.deleteByMacAddress(kept.macAddress)
            }
            dao.insertDevice(updatedKept)

            // h. Do not touch timed_blocks — router enforced against specific live MAC
        }
    }

    // ── Analytics ────────────────────────────────────────────────────────────────

    suspend fun getHourlyDeviceActivity(): Map<Int, Int> {
        return dao.getDeviceCountByHour().associate {
            (it.hour.toIntOrNull() ?: 0) to it.count
        }
    }

    suspend fun login(password: String): Boolean = apiService.login(password)

    suspend fun getRouterStatus() = apiService.getRouterStatus()

    // ── Status Screen ─────────────────────────────────────────────────────────

    /** Full router status for the Status screen (WAN IP, speeds, uptime, etc.) */
    suspend fun getFullRouterStatus(): RouterFullStatus? = apiService.getFullRouterStatus()

    // ── Bandwidth Control ─────────────────────────────────────────────────────

    /** Per-device bandwidth data (live speeds + QoS limits) for the Bandwidth Control screen. */
    suspend fun getDeviceBandwidthList(): List<DeviceBandwidthInfo> =
        apiService.getDeviceBandwidthList()

    /**
     * Applies ALL devices' bandwidth settings in one batch to the router.
     * Matches the web UI's "OK" button behavior: one POST with all devices.
     */
    suspend fun applyAllBandwidthSettings(devices: List<DeviceBandwidthInfo>): Boolean =
        apiService.applyAllBandwidthSettings(devices)

    /**
     * Applies a download/upload speed limit to a specific device.
     * Pass 0 for either value to remove the limit.
     */
    suspend fun setDeviceBandwidthLimit(
        mac: String,
        downloadLimitRaw: Float,
        uploadLimitRaw: Float,
    ): Boolean {
        val ok = apiService.setDeviceBandwidthLimit(mac, downloadLimitRaw, uploadLimitRaw)
        if (ok) {
            dao.updateBandwidthLimitsByMac(mac, downloadLimitRaw, uploadLimitRaw)
            val dev = dao.getDevice(mac)
            if (dev != null) {
                dao.updateBandwidthLimits(dev.deviceId, downloadLimitRaw, uploadLimitRaw)
            }
        }
        return ok
    }

    /** Ensure we have an active, authenticated session with the Tenda router. */
    suspend fun ensureRouterLogin() {
        val password = preferences.routerPassword.firstOrNull() ?: ""
        if (password.isNotBlank()) {
            apiService.login(password)
        }
    }

    /**
     * Enables or disables internet access for a device via QoS batch (/goform/setQos).
     * Ensures an active router session first, fetches the device list, flips WAN access,
     * and sends a batch request matching the official Tenda web UI.
     */
    suspend fun setDeviceInternetAccess(mac: String, enabled: Boolean): Boolean {
        ensureRouterLogin()
        var current = apiService.getDeviceBandwidthList()
        if (current.isEmpty()) {
            Log.w(TAG, "Device list empty, re-authenticating with router...")
            ensureRouterLogin()
            current = apiService.getDeviceBandwidthList()
        }
        if (current.isEmpty()) {
            Log.e(TAG, "Cannot set internet access: router returned empty device list")
            return false
        }

        val found = current.any { it.macAddress.equals(mac, ignoreCase = true) }
        val updated = if (found) {
            current.map { d ->
                if (d.macAddress.equals(mac, ignoreCase = true)) d.copy(internetAccessEnabled = enabled)
                else d
            }
        } else {
            if (!enabled) {
                current + DeviceBandwidthInfo(
                    macAddress = mac,
                    hostname = "Blocked Device",
                    rawHostname = "Unknown",
                    ipAddress = "",
                    internetAccessEnabled = false,
                )
            } else current
        }

        val success = apiService.applyAllBandwidthSettings(updated)
        Log.d(TAG, "setDeviceInternetAccess for $mac (enabled=$enabled) -> $success")
        if (success) {
            dao.updateBlockedStatus(mac, !enabled)
            val dev = dao.getDevice(mac)
            if (dev != null) {
                dao.updateBlockedStatusByDeviceId(dev.deviceId, !enabled)
            }
        }
        return success
    }

    suspend fun pruneOldHistory() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        dao.pruneOldSessions(thirtyDaysAgo)
    }

    // ── Timed Block Management ────────────────────────────────────────────────

    /**
     * Emits a map of MAC -> TimedBlock for all active block timers.
     */
    fun observeTimedBlocks(): Flow<Map<String, TimedBlock>> =
        dao.observeAllTimedBlocks().map { list ->
            list.map { it.toDomain() }.associateBy { it.macAddress }
        }.flowOn(Dispatchers.Default)

    /**
     * Sets a block timer on an online device. Immediately disables internet access
     * on the router, saves the timed block to Room DB, and schedules automatic unblock
     * via both AlarmManager (exact wakeup) and WorkManager (background persistence).
     */
    suspend fun setTimedBlock(
        mac: String,
        deviceName: String,
        ipAddress: String,
        durationMinutes: Long,
    ): Boolean {
        // Safety guard: never block local client device
        if (apiService.isLocalDevice(mac, ipAddress)) {
            Log.w(TAG, "Cannot set timed block on local device: $mac")
            return false
        }

        // 1. Immediately cut off internet access via router QoS batch
        val routerSuccess = setDeviceInternetAccess(mac, enabled = false)
        if (!routerSuccess) {
            Log.e(TAG, "Failed to block device on router for timed block: $mac")
            return false
        }

        // 2. Persist timed block in Room DB
        val now = System.currentTimeMillis()
        val unblockAt = now + (durationMinutes * 60 * 1000L)
        val entity = TimedBlockEntity(
            macAddress = mac,
            deviceName = deviceName,
            ipAddress = ipAddress,
            blockedAt = now,
            unblockAt = unblockAt,
            durationMinutes = durationMinutes,
        )
        dao.insertTimedBlock(entity)

        // 3. Schedule AlarmManager (exact alarm) AND WorkManager
        val delaySeconds = durationMinutes * 60L
        TimedBlockReceiver.schedule(context, mac, deviceName, unblockAt)
        TimedUnblockWorker.schedule(context, mac, deviceName, delaySeconds)
        Log.d(TAG, "Timed block set for $mac: duration=${durationMinutes}m, unblockAt=$unblockAt")
        return true
    }

    /**
     * Cancels an active timed block early, immediately restoring internet access.
     */
    suspend fun cancelTimedBlock(mac: String): Boolean {
        TimedBlockReceiver.cancel(context, mac)
        TimedUnblockWorker.cancel(context, mac)
        dao.deleteTimedBlock(mac)
        return setDeviceInternetAccess(mac, enabled = true)
    }

    /**
     * Executed by TimedBlockReceiver or TimedUnblockWorker when timer elapses.
     */
    suspend fun unblockTimedBlock(mac: String, deviceName: String): Boolean {
        TimedBlockReceiver.cancel(context, mac)
        TimedUnblockWorker.cancel(context, mac)
        ensureRouterLogin()
        val success = setDeviceInternetAccess(mac, enabled = true)
        if (success) {
            dao.deleteTimedBlock(mac)
            val soundEnabled = preferences.soundEnabled.firstOrNull() ?: true
            notificationHelper.notifyTimedUnblock(deviceName, mac, soundEnabled)
            Log.d(TAG, "Successfully unblocked timed device: $mac ($deviceName)")
        } else {
            Log.w(TAG, "Failed to unblock timed device on router: $mac")
        }
        return success
    }

    /**
     * Scans for any timed blocks whose unblock timestamp has already elapsed.
     * Restores internet access, cleans up DB, and notifies user.
     */
    suspend fun checkAndProcessExpiredTimedBlocks(): List<String> {
        val now = System.currentTimeMillis()
        val expired = dao.getExpiredTimedBlocks(now)
        if (expired.isEmpty()) return emptyList()

        ensureRouterLogin()
        val unblocked = mutableListOf<String>()
        for (item in expired) {
            Log.d(TAG, "Processing expired timed block for ${item.deviceName} (${item.macAddress})")
            val success = setDeviceInternetAccess(item.macAddress, enabled = true)
            if (success) {
                TimedBlockReceiver.cancel(context, item.macAddress)
                TimedUnblockWorker.cancel(context, item.macAddress)
                dao.deleteTimedBlock(item.macAddress)
                val soundEnabled = preferences.soundEnabled.firstOrNull() ?: true
                notificationHelper.notifyTimedUnblock(item.deviceName, item.macAddress, soundEnabled)
                unblocked.add(item.macAddress)
                Log.d(TAG, "Auto-unblocked expired device: ${item.macAddress}")
            } else {
                Log.w(TAG, "Failed to unblock expired device on router: ${item.macAddress}")
            }
        }
        return unblocked
    }
}
