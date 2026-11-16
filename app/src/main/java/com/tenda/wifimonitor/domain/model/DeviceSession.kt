package com.tenda.wifimonitor.domain.model

import java.util.Date

data class DeviceSession(
    val id: Long = 0,
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val connectedAt: Date,
    val disconnectedAt: Date? = null,
    val durationMs: Long? = null,
    val deviceId: String? = null,
) {
    val isActive: Boolean get() = disconnectedAt == null

    val formattedDuration: String
        get() {
            val end = disconnectedAt?.time ?: System.currentTimeMillis()
            val ms = end - connectedAt.time
            val hours = ms / 3_600_000
            val minutes = (ms % 3_600_000) / 60_000
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }
}
