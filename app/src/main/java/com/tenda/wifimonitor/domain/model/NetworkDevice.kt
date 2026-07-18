package com.tenda.wifimonitor.domain.model

import java.util.Date

data class NetworkDevice(
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val manufacturer: String = "Unknown",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val isOnline: Boolean = true,
    val isWhitelisted: Boolean = true,
    val customName: String? = null,
    val customIcon: String? = null, // emoji string
    val firstSeen: Date = Date(),
    val lastSeen: Date = Date(),
    val signalStrength: Int? = null, // ping ms
) {
    val displayName: String
        get() = customName ?: hostname.takeIf { it.isNotBlank() && it != "Unknown" } ?: macAddress

    val displayIcon: String
        get() = customIcon ?: deviceType.defaultEmoji
}

enum class DeviceType(val label: String, val defaultEmoji: String) {
    PHONE("Phone", "📱"),
    LAPTOP("Laptop", "💻"),
    TABLET("Tablet", "📟"),
    SMART_TV("Smart TV", "📺"),
    IOT("IoT Device", "🔌"),
    ROUTER("Router", "📡"),
    DESKTOP("Desktop", "🖥️"),
    GAME_CONSOLE("Game Console", "🎮"),
    UNKNOWN("Unknown", "❓");

    companion object {
        fun fromHostname(hostname: String, manufacturer: String): DeviceType {
            val h = hostname.lowercase()
            val m = manufacturer.lowercase()
            return when {
                h.contains("android") || h.contains("phone") || m.contains("samsung") ||
                        m.contains("xiaomi") || m.contains("oppo") || m.contains("realme") ||
                        m.contains("vivo") || m.contains("oneplus") -> PHONE
                h.contains("laptop") || h.contains("notebook") || m.contains("dell") ||
                        m.contains("lenovo") || m.contains("hp") || m.contains("acer") ||
                        m.contains("asus") -> LAPTOP
                h.contains("ipad") || h.contains("tablet") -> TABLET
                h.contains("tv") || h.contains("smart-tv") || m.contains("lg") ||
                        m.contains("sony") || m.contains("hisense") || m.contains("tcl") -> SMART_TV
                h.contains("ps") || h.contains("xbox") || h.contains("nintendo") -> GAME_CONSOLE
                h.contains("esp") || h.contains("arduino") || h.contains("raspberry") ||
                        h.contains("shelly") || h.contains("tasmota") -> IOT
                else -> UNKNOWN
            }
        }
    }
}
