package com.tenda.wifimonitor.data.network

/**
 * Utility functions for analyzing MAC addresses and hostnames.
 *
 * Used for correlating devices across MAC address randomization. When mobile operating
 * systems (such as Android and iOS) generate randomized MAC addresses to protect privacy,
 * these utilities help identify whether a MAC address is randomized and clean up device
 * hostnames to determine if they can be reliably used as cross-session identifiers.
 */
object MacFingerprint {

    private val GENERIC_HOSTNAMES = setOf(
        "unknown",
        "android",
        "localhost",
        "*"
    )

    private val ANDROID_GENERIC_HOSTNAME_REGEX = Regex("^android-[0-9a-f]+$")

    /**
     * Determines whether a given MAC address is locally administered (randomized).
     *
     * Used for correlating devices across MAC address randomization. Modern mobile operating
     * systems generate randomized, locally-administered MAC addresses per Wi-Fi network.
     * In IEEE 802 addressing, bit 1 (0x02) of the first octet indicates whether an address is
     * locally administered (set to 1) rather than a burned-in, universally unique hardware
     * address (set to 0).
     *
     * @param mac The MAC address string (e.g., "DA:A1:19:64:55:60", "da-a1-19-64-55-60", or "DAA119645560").
     * @return True if bit 1 (0x02) of the first octet is set; false if it is a burned-in hardware
     *         address, or if the MAC string is malformed or too short.
     */
    fun isLocallyAdministered(mac: String): Boolean {
        val clean = mac.replace(":", "").replace("-", "").trim()
        if (clean.length < 2) return false
        val firstOctet = clean.substring(0, 2).toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }

    /**
     * Normalizes a device hostname for reliable identification and matching.
     *
     * Used for correlating devices across MAC address randomization. When devices randomize
     * their MAC addresses, stable network identifiers such as hostnames can help associate
     * new sessions with existing device records. This function filters out non-identifying
     * placeholders (such as "unknown", "android", "localhost", "*") and generic Android hostnames
     * (e.g., "android-1a2b3c4d5e6f") that are assigned automatically and cannot reliably identify
     * an individual physical device.
     *
     * @param hostname The raw hostname string from DHCP or router tables, nullable.
     * @return The trimmed, lowercase normalized hostname, or an empty string if null, blank,
     *         or matching a known generic non-identifying pattern.
     */
    fun normalizeHostname(hostname: String?): String {
        if (hostname.isNullOrBlank()) return ""
        val normalized = hostname.trim().lowercase()
        if (normalized in GENERIC_HOSTNAMES || ANDROID_GENERIC_HOSTNAME_REGEX.matches(normalized)) {
            return ""
        }
        return normalized
    }
}
