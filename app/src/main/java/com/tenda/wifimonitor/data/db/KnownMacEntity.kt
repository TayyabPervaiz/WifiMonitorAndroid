package com.tenda.wifimonitor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps every MAC address ever seen (including rotated randomized MACs) to a single
 * stable deviceId, allowing one logical device to be tracked across MAC address changes.
 */
@Entity(
    tableName = "known_macs",
    indices = [Index(value = ["deviceId"])]
)
data class KnownMacEntity(
    @PrimaryKey
    val mac: String,
    val deviceId: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
)
