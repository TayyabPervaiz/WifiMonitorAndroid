package com.tenda.wifimonitor.data

import com.tenda.wifimonitor.data.db.DeviceEntity
import com.tenda.wifimonitor.data.network.MacFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacFingerprintAndMatchingTest {

    @Test
    fun testMacFingerprint_isLocallyAdministered() {
        // Bit 1 (0x02) of first octet set:
        // 0xDA = 1101 1010 -> bit 1 is 1 -> locally administered
        assertTrue(MacFingerprint.isLocallyAdministered("DA:A1:19:64:55:60"))
        assertTrue(MacFingerprint.isLocallyAdministered("da-a1-19-64-55-60"))
        assertTrue(MacFingerprint.isLocallyAdministered("02:00:00:00:00:00"))
        assertTrue(MacFingerprint.isLocallyAdministered("36:11:22:33:44:55"))

        // Bit 1 (0x02) not set (burned-in hardware OUI):
        // 0x00, 0xF4, 0x04, etc.
        assertFalse(MacFingerprint.isLocallyAdministered("00:1A:2B:3C:4D:5E"))
        assertFalse(MacFingerprint.isLocallyAdministered("F4:6B:8C:11:22:33"))
        assertFalse(MacFingerprint.isLocallyAdministered("04:CF:8C:AA:BB:CC"))

        // Malformed / too short
        assertFalse(MacFingerprint.isLocallyAdministered(""))
        assertFalse(MacFingerprint.isLocallyAdministered("D"))
        assertFalse(MacFingerprint.isLocallyAdministered("ZZ:AA:BB"))
    }

    @Test
    fun testMacFingerprint_normalizeHostname() {
        // Null / blank
        assertEquals("", MacFingerprint.normalizeHostname(null))
        assertEquals("", MacFingerprint.normalizeHostname(""))
        assertEquals("", MacFingerprint.normalizeHostname("   "))

        // Known placeholders
        assertEquals("", MacFingerprint.normalizeHostname("unknown"))
        assertEquals("", MacFingerprint.normalizeHostname("UNKNOWN"))
        assertEquals("", MacFingerprint.normalizeHostname("android"))
        assertEquals("", MacFingerprint.normalizeHostname("ANDROID"))
        assertEquals("", MacFingerprint.normalizeHostname("localhost"))
        assertEquals("", MacFingerprint.normalizeHostname("*"))

        // Generic Android hostnames (android- followed only by hex digits)
        assertEquals("", MacFingerprint.normalizeHostname("android-1a2b3c4d5e6f"))
        assertEquals("", MacFingerprint.normalizeHostname("Android-1A2B3C4D5E6F"))
        assertEquals("", MacFingerprint.normalizeHostname("android-abcdef0123456789"))

        // Valid custom / identifying hostnames
        assertEquals("tayyab-iphone", MacFingerprint.normalizeHostname("Tayyab-iPhone"))
        assertEquals("galaxy-s24", MacFingerprint.normalizeHostname("  Galaxy-S24  "))
        assertEquals("macbook-pro", MacFingerprint.normalizeHostname("MacBook-Pro"))
        assertEquals("android-tv", MacFingerprint.normalizeHostname("android-tv"))
    }

    @Test
    fun testFallbackMatchingLogic() {
        val dev1 = DeviceEntity(
            macAddress = "DA:A1:19:64:55:60",
            ipAddress = "192.168.0.10",
            hostname = "Tayyab-Phone",
            isWhitelisted = true,
            customName = "My Primary Phone",
            deviceId = "uuid-1111",
        )
        val dev2 = DeviceEntity(
            macAddress = "00:11:22:33:44:55",
            ipAddress = "192.168.0.20",
            hostname = "Ambiguous-Device",
            deviceId = "uuid-2222",
        )
        val dev3 = DeviceEntity(
            macAddress = "00:11:22:33:44:66",
            ipAddress = "192.168.0.21",
            hostname = "Ambiguous-Device",
            deviceId = "uuid-3333",
        )

        val allExisting = listOf(dev1, dev2, dev3)
        val existingDevices = allExisting.associateBy { it.macAddress }

        // Group by normalized hostname and only keep unambiguous hostnames (count == 1)
        val unambiguousHostnameMatches: Map<String, DeviceEntity> = allExisting
            .mapNotNull { dev ->
                val norm = MacFingerprint.normalizeHostname(dev.hostname)
                if (norm.isNotEmpty()) norm to dev else null
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }

        // Tayyab-Phone should be unambiguous
        assertTrue(unambiguousHostnameMatches.containsKey("tayyab-phone"))
        assertEquals("uuid-1111", unambiguousHostnameMatches["tayyab-phone"]?.deviceId)

        // Ambiguous-Device has count 2, so it MUST be excluded
        assertFalse(unambiguousHostnameMatches.containsKey("ambiguous-device"))

        val claimedThisScan = mutableSetOf<String>()

        // Simulate incoming rotated MAC for dev1
        // Rotated MAC: "36:11:22:33:44:55" (locally administered, bit 1 is set)
        val rotatedMac = "36:11:22:33:44:55"
        val exactMatch = existingDevices[rotatedMac]
        assertNull(exactMatch)

        val resolvedDevice: DeviceEntity? = exactMatch ?: run {
            if (!MacFingerprint.isLocallyAdministered(rotatedMac)) return@run null
            val normalizedHost = MacFingerprint.normalizeHostname("Tayyab-Phone")
            if (normalizedHost.isEmpty()) return@run null
            val candidate = unambiguousHostnameMatches[normalizedHost] ?: return@run null
            if (candidate.deviceId in claimedThisScan) return@run null
            claimedThisScan.add(candidate.deviceId)
            candidate
        }

        assertNotNull(resolvedDevice)
        assertEquals("uuid-1111", resolvedDevice?.deviceId)
        assertEquals("My Primary Phone", resolvedDevice?.customName)
        assertTrue(resolvedDevice!!.isWhitelisted)

        // If another device with the same hostname comes in during the same scan:
        val secondResolved = run {
            val candidate = unambiguousHostnameMatches["tayyab-phone"] ?: return@run null
            if (candidate.deviceId in claimedThisScan) return@run null
            claimedThisScan.add(candidate.deviceId)
            candidate
        }
        // Must return null because claimedThisScan prevents double-claiming
        assertNull(secondResolved)
    }
}
