package com.tenda.wifimonitor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenda.wifimonitor.domain.model.DeviceSession
import java.util.Date

@Entity(
    tableName = "device_sessions",
    indices = [Index(value = ["deviceId"])]
)
data class DeviceSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val connectedAt: Long,
    val disconnectedAt: Long? = null,
    val deviceId: String? = null,
) {
    fun toDomain() = DeviceSession(
        id = id,
        macAddress = macAddress,
        ipAddress = ipAddress,
        hostname = hostname,
        connectedAt = Date(connectedAt),
        disconnectedAt = disconnectedAt?.let { Date(it) },
        durationMs = disconnectedAt?.let { it - connectedAt },
        deviceId = deviceId,
    )
}

fun DeviceSession.toEntity() = DeviceSessionEntity(
    id = id,
    macAddress = macAddress,
    ipAddress = ipAddress,
    hostname = hostname,
    connectedAt = connectedAt.time,
    disconnectedAt = disconnectedAt?.time,
    deviceId = deviceId,
)
