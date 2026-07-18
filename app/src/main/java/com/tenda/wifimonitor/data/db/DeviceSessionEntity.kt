package com.tenda.wifimonitor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tenda.wifimonitor.domain.model.DeviceSession
import java.util.Date

@Entity(tableName = "device_sessions")
data class DeviceSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val connectedAt: Long,
    val disconnectedAt: Long? = null,
) {
    fun toDomain() = DeviceSession(
        id = id,
        macAddress = macAddress,
        ipAddress = ipAddress,
        hostname = hostname,
        connectedAt = Date(connectedAt),
        disconnectedAt = disconnectedAt?.let { Date(it) },
        durationMs = disconnectedAt?.let { it - connectedAt },
    )
}

fun DeviceSession.toEntity() = DeviceSessionEntity(
    id = id,
    macAddress = macAddress,
    ipAddress = ipAddress,
    hostname = hostname,
    connectedAt = connectedAt.time,
    disconnectedAt = disconnectedAt?.time,
)
