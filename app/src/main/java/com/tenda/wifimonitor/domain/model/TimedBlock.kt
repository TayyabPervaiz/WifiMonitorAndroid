package com.tenda.wifimonitor.domain.model

import java.util.Date

data class TimedBlock(
    val macAddress: String,
    val deviceName: String,
    val ipAddress: String,
    val blockedAt: Date,
    val unblockAt: Date,
    val durationMinutes: Long,
) {
    val remainingMillis: Long
        get() = (unblockAt.time - System.currentTimeMillis()).coerceAtLeast(0L)

    val isExpired: Boolean
        get() = remainingMillis <= 0L

    val formattedRemaining: String
        get() {
            val totalSeconds = remainingMillis / 1000
            if (totalSeconds <= 0) return "Expired"
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return buildString {
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0) append("${minutes}m ")
                append("${seconds}s")
            }.trim()
        }
}
