package com.tenda.wifimonitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnownMacDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: KnownMacEntity)

    @Query("SELECT * FROM known_macs WHERE mac = :mac")
    suspend fun getByMac(mac: String): KnownMacEntity?

    @Query("SELECT * FROM known_macs WHERE deviceId = :deviceId")
    suspend fun getAllForDevice(deviceId: String): List<KnownMacEntity>

    @Query("UPDATE known_macs SET deviceId = :newDeviceId WHERE deviceId = :oldDeviceId")
    suspend fun reassignKnownMacs(oldDeviceId: String, newDeviceId: String)
}
