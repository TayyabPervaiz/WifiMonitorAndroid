package com.tenda.wifimonitor.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isOnline = 1 ORDER BY lastSeen DESC")
    fun observeOnlineDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevicesSync(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE macAddress = :mac")
    suspend fun getDevice(mac: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE macAddress = :mac")
    fun observeDevice(mac: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isOnline = 0 WHERE macAddress NOT IN (:onlineMacs)")
    suspend fun markOffline(onlineMacs: List<String>)

    @Query("UPDATE devices SET isOnline = 0")
    suspend fun markAllOffline()

    @Query("UPDATE devices SET customName = :name WHERE macAddress = :mac")
    suspend fun updateCustomName(mac: String, name: String?)

    @Query("UPDATE devices SET customIcon = :icon WHERE macAddress = :mac")
    suspend fun updateCustomIcon(mac: String, icon: String?)

    @Query("UPDATE devices SET isWhitelisted = :whitelisted WHERE macAddress = :mac")
    suspend fun updateWhitelist(mac: String, whitelisted: Boolean)

    @Query("UPDATE devices SET deviceType = :type WHERE macAddress = :mac")
    suspend fun updateDeviceType(mac: String, type: String)

    @Query("SELECT COUNT(*) FROM devices WHERE isOnline = 1")
    fun observeOnlineCount(): Flow<Int>

    @Query("SELECT * FROM devices WHERE isWhitelisted = 0")
    fun observeUnknownDevices(): Flow<List<DeviceEntity>>

    // Sessions
    @Query("SELECT * FROM device_sessions ORDER BY connectedAt DESC")
    fun observeAllSessions(): Flow<List<DeviceSessionEntity>>

    @Query("SELECT * FROM device_sessions WHERE macAddress = :mac ORDER BY connectedAt DESC")
    fun observeSessionsForDevice(mac: String): Flow<List<DeviceSessionEntity>>

    @Insert
    suspend fun insertSession(session: DeviceSessionEntity): Long

    @Query("UPDATE device_sessions SET disconnectedAt = :disconnectedAt WHERE id = :id")
    suspend fun closeSession(id: Long, disconnectedAt: Long)

    @Query("UPDATE device_sessions SET disconnectedAt = :disconnectedAt WHERE macAddress = :mac AND disconnectedAt IS NULL")
    suspend fun closeAllOpenSessionsForMac(mac: String, disconnectedAt: Long)

    @Query("UPDATE device_sessions SET disconnectedAt = :now WHERE disconnectedAt IS NULL AND id NOT IN (SELECT MAX(id) FROM device_sessions WHERE disconnectedAt IS NULL GROUP BY macAddress)")
    suspend fun cleanupDuplicateActiveSessions(now: Long)

    @Query("SELECT * FROM device_sessions WHERE macAddress = :mac AND disconnectedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(mac: String): DeviceSessionEntity?

    @Query("SELECT strftime('%H', connectedAt/1000, 'unixepoch') as hour, COUNT(*) as count FROM device_sessions GROUP BY hour")
    suspend fun getDeviceCountByHour(): List<HourlyCount>

    @Query("DELETE FROM device_sessions WHERE connectedAt < :cutoff")
    suspend fun pruneOldSessions(cutoff: Long)
}

data class HourlyCount(val hour: String, val count: Int)
