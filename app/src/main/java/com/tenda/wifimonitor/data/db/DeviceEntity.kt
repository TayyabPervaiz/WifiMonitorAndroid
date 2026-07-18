package com.tenda.wifimonitor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.domain.model.NetworkDevice
import java.util.Date

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val manufacturer: String = "Unknown",
    val deviceType: String = DeviceType.UNKNOWN.name,
    val isOnline: Boolean = true,
    val isWhitelisted: Boolean = true,
    val customName: String? = null,
    val customIcon: String? = null,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val signalStrength: Int? = null,
) {
    fun toDomain() = NetworkDevice(
        macAddress = macAddress,
        ipAddress = ipAddress,
        hostname = hostname,
        manufacturer = manufacturer,
        deviceType = DeviceType.valueOf(deviceType),
        isOnline = isOnline,
        isWhitelisted = isWhitelisted,
        customName = customName,
        customIcon = customIcon,
        firstSeen = Date(firstSeen),
        lastSeen = Date(lastSeen),
        signalStrength = signalStrength,
    )
}

fun NetworkDevice.toEntity() = DeviceEntity(
    macAddress = macAddress,
    ipAddress = ipAddress,
    hostname = hostname,
    manufacturer = manufacturer,
    deviceType = deviceType.name,
    isOnline = isOnline,
    isWhitelisted = isWhitelisted,
    customName = customName,
    customIcon = customIcon,
    firstSeen = firstSeen.time,
    lastSeen = lastSeen.time,
    signalStrength = signalStrength,
)
