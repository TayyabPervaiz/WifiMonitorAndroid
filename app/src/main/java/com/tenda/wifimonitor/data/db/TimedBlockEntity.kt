package com.tenda.wifimonitor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tenda.wifimonitor.domain.model.TimedBlock
import java.util.Date

@Entity(tableName = "timed_blocks")
data class TimedBlockEntity(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String,
    val ipAddress: String,
    val blockedAt: Long,
    val unblockAt: Long,
    val durationMinutes: Long,
) {
    fun toDomain() = TimedBlock(
        macAddress = macAddress,
        deviceName = deviceName,
        ipAddress = ipAddress,
        blockedAt = Date(blockedAt),
        unblockAt = Date(unblockAt),
        durationMinutes = durationMinutes,
    )
}

fun TimedBlock.toEntity() = TimedBlockEntity(
    macAddress = macAddress,
    deviceName = deviceName,
    ipAddress = ipAddress,
    blockedAt = blockedAt.time,
    unblockAt = unblockAt.time,
    durationMinutes = durationMinutes,
)
