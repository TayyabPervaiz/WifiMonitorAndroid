package com.tenda.wifimonitor.data.repository

import android.content.Context
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.db.AppDatabase
import com.tenda.wifimonitor.data.db.DeviceDao
import com.tenda.wifimonitor.data.db.DeviceEntity
import com.tenda.wifimonitor.data.db.KnownMacDao
import com.tenda.wifimonitor.data.db.KnownMacEntity
import com.tenda.wifimonitor.data.network.ArpScanner
import com.tenda.wifimonitor.data.network.OuiLookup
import com.tenda.wifimonitor.data.network.RouterDevice
import com.tenda.wifimonitor.data.network.TendaApiService
import com.tenda.wifimonitor.service.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeviceRepository.performScan].
 *
 * They cover the three device-identity resolution paths:
 *  - an exact MAC match,
 *  - a rotated MAC correlated through the `known_macs` table,
 *  - a randomized (locally-administered) MAC correlated through a unique hostname.
 *
 * Plus intruder classification, auto-blocking and the ARP fallback.
 */
class DeviceRepositoryPerformScanTest {

    private lateinit var dao: DeviceDao
    private lateinit var knownMacDao: KnownMacDao
    private lateinit var db: AppDatabase
    private lateinit var apiService: TendaApiService
    private lateinit var arpScanner: ArpScanner
    private lateinit var ouiLookup: OuiLookup
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var preferences: AppPreferences
    private lateinit var context: Context

    /** A globally-administered MAC (bit 1 of the first octet is clear). */
    private val phone = RouterDevice(
        macAddress = "3C:5A:B4:11:22:33",
        ipAddress = "192.168.0.10",
        hostname = "Pixel-7",
        connectionType = "wifi_2G",
        connectedSeconds = 300,
    )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        knownMacDao = mockk(relaxed = true)
        db = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        arpScanner = mockk(relaxed = true)
        ouiLookup = mockk(relaxed = true)
        notificationHelper = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Sensible defaults for every collaborator touched by performScan.
        every { preferences.autoBlockUnknownDevices } returns flowOf(false)
        every { ouiLookup.lookup(any()) } returns "Unknown"
        every { apiService.isLocalDevice(any(), any()) } returns false

        coEvery { dao.getAllDevicesSync() } returns emptyList()
        coEvery { knownMacDao.getByMac(any()) } returns null
        coEvery { apiService.getConnectedDevices() } returns emptyList()
        coEvery { apiService.login(any()) } returns true
        coEvery { arpScanner.scanArpTable() } returns emptyList()
        coEvery { dao.getActiveSession(any()) } returns null
        coEvery { dao.insertSession(any()) } returns 0L
    }

    private fun repository() = DeviceRepository(
        dao = dao,
        knownMacDao = knownMacDao,
        db = db,
        apiService = apiService,
        arpScanner = arpScanner,
        ouiLookup = ouiLookup,
        notificationHelper = notificationHelper,
        preferences = preferences,
        context = context,
    )

    // ── Case A: brand new device ─────────────────────────────────────────────

    @Test
    fun `brand new unknown device is persisted and flagged as intruder`() = runBlocking {
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)

        val result = repository().performScan("pw")

        assertEquals(1, result.newDevices.size)
        assertEquals(phone.macAddress, result.newDevices.single().macAddress)
        assertEquals(1, result.intruders.size)
        assertEquals(phone.macAddress, result.intruders.single().macAddress)
        coVerify { dao.insertDevice(match { it.macAddress == phone.macAddress }) }
        coVerify { knownMacDao.upsert(match { it.mac == phone.macAddress }) }
    }

    @Test
    fun `local device is whitelisted and never classified as intruder`() = runBlocking {
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)
        every { apiService.isLocalDevice(phone.macAddress, phone.ipAddress) } returns true

        val result = repository().performScan("pw")

        assertEquals(1, result.newDevices.size)
        assertTrue(result.newDevices.single().isWhitelisted)
        assertTrue(result.intruders.isEmpty())
    }

    // ── Auto-block ───────────────────────────────────────────────────────────

    @Test
    fun `auto-block blocks unknown non-local devices`() = runBlocking {
        every { preferences.autoBlockUnknownDevices } returns flowOf(true)
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)

        val result = repository().performScan("pw")

        coVerify { apiService.blockDevice(phone.macAddress) }
        assertTrue(result.newDevices.single().isBlocked)
    }

    @Test
    fun `auto-block never blocks the local device`() = runBlocking {
        every { preferences.autoBlockUnknownDevices } returns flowOf(true)
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)
        every { apiService.isLocalDevice(phone.macAddress, phone.ipAddress) } returns true

        repository().performScan("pw")

        coVerify(exactly = 0) { apiService.blockDevice(any()) }
    }

    // ── Case C: exact MAC match ──────────────────────────────────────────────

    @Test
    fun `device that was offline is reported as reconnected`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = phone.macAddress,
            ipAddress = "192.168.0.99",
            hostname = phone.hostname,
            isOnline = false,
            isWhitelisted = true,
            deviceId = "device-1",
        )
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)

        val result = repository().performScan("pw")

        assertEquals(1, result.newDevices.size)
        assertEquals(phone.macAddress, result.newDevices.single().macAddress)
        assertTrue(result.newDevices.single().isOnline)
        coVerify { dao.insertDevice(match { it.macAddress == phone.macAddress && it.isOnline }) }
    }

    @Test
    fun `device already online is not reported again`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = phone.macAddress,
            ipAddress = "192.168.0.99",
            hostname = phone.hostname,
            isOnline = true,
            isWhitelisted = true,
            deviceId = "device-1",
        )
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)

        val result = repository().performScan("pw")

        assertTrue(result.newDevices.isEmpty())
        assertTrue(result.intruders.isEmpty())
    }

    // ── Case B: rotated MAC via known_macs ───────────────────────────────────

    @Test
    fun `rotated mac is correlated to the same device through known_macs`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = "3C:5A:B4:11:22:33",
            ipAddress = "192.168.0.10",
            hostname = "pixel",
            isOnline = true,
            isWhitelisted = true,
            deviceId = "device-1",
        )
        val rotated = phone.copy(macAddress = "3C:5A:B4:99:88:77")
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { knownMacDao.getByMac(rotated.macAddress) } returns
            KnownMacEntity(mac = rotated.macAddress, deviceId = "device-1")
        coEvery { apiService.getConnectedDevices() } returns listOf(rotated)

        repository().performScan("pw")

        // Old row is removed and the same logical device is written under the new MAC.
        coVerify { dao.deleteByMacAddress("3C:5A:B4:11:22:33") }
        coVerify {
            dao.insertDevice(
                match { it.macAddress == rotated.macAddress && it.deviceId == "device-1" }
            )
        }
        // No new device: the identity is already known.
        coVerify(exactly = 0) { dao.insertDevice(match { it.deviceId != "device-1" }) }
    }

    @Test
    fun `re-enforces block on a rotated mac`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = "3C:5A:B4:11:22:33",
            ipAddress = "192.168.0.10",
            hostname = "pixel",
            isOnline = true,
            isWhitelisted = true,
            isBlocked = true,
            deviceId = "device-1",
        )
        val rotated = phone.copy(macAddress = "3C:5A:B4:99:88:77")
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { knownMacDao.getByMac(rotated.macAddress) } returns
            KnownMacEntity(mac = rotated.macAddress, deviceId = "device-1")
        coEvery { apiService.getConnectedDevices() } returns listOf(rotated)

        repository().performScan("pw")

        coVerify { apiService.blockDevice(rotated.macAddress) }
    }

    // ── Case B (fallback): randomized MAC via unique hostname ────────────────

    @Test
    fun `randomized mac is correlated by unique hostname`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = "3C:5A:B4:11:22:33",
            ipAddress = "192.168.0.10",
            hostname = "Pixel-7",
            isOnline = true,
            isWhitelisted = true,
            deviceId = "device-1",
        )
        // 0x02 -> locally administered (randomized) MAC.
        val randomized = phone.copy(macAddress = "02:AA:BB:CC:DD:EE")
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { apiService.getConnectedDevices() } returns listOf(randomized)

        repository().performScan("pw")

        coVerify {
            dao.insertDevice(
                match { it.macAddress == randomized.macAddress && it.deviceId == "device-1" }
            )
        }
    }

    // ── Departed devices ─────────────────────────────────────────────────────

    @Test
    fun `device missing from scan is marked offline and reported as departed`() = runBlocking {
        val existing = DeviceEntity(
            macAddress = "10:20:30:40:50:60",
            ipAddress = "192.168.0.50",
            hostname = "laptop",
            isOnline = true,
            isWhitelisted = true,
            deviceId = "device-2",
        )
        coEvery { dao.getAllDevicesSync() } returns listOf(existing)
        coEvery { apiService.getConnectedDevices() } returns listOf(phone)

        val result = repository().performScan("pw")

        assertEquals(1, result.departed.size)
        assertEquals("10:20:30:40:50:60", result.departed.single().macAddress)
        coVerify { dao.markOffline(listOf(phone.macAddress)) }
        coVerify { notificationHelper.clearNotifiedDevice("10:20:30:40:50:60") }
        coVerify { dao.closeAllOpenSessionsForMac("10:20:30:40:50:60", any()) }
    }

    // ── ARP fallback ─────────────────────────────────────────────────────────

    @Test
    fun `falls back to the arp table when the router api returns nothing`() = runBlocking {
        coEvery { apiService.getConnectedDevices() } returns emptyList()
        coEvery { apiService.login(any()) } returns false
        coEvery { arpScanner.scanArpTable() } returns listOf(phone)

        val result = repository().performScan("pw")

        coVerify { arpScanner.scanArpTable() }
        assertEquals(1, result.newDevices.size)
    }
}
