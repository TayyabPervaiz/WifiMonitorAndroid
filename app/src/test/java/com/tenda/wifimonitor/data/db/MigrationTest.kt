package com.tenda.wifimonitor.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager
import java.util.UUID

class MigrationTest {

    @Test
    fun testMigrationFrom2To3Logic() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Set up Version 2 schema
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS `devices` (
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `manufacturer` TEXT NOT NULL,
                `deviceType` TEXT NOT NULL,
                `isOnline` INTEGER NOT NULL,
                `isWhitelisted` INTEGER NOT NULL,
                `customName` TEXT,
                `customIcon` TEXT,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                `signalStrength` INTEGER,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS `timed_blocks` (
                `macAddress` TEXT NOT NULL,
                `deviceName` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `blockedAt` INTEGER NOT NULL,
                `unblockAt` INTEGER NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent()
        )

        // Insert 3 existing devices
        stmt.execute("INSERT INTO `devices` VALUES ('11:22:33:44:55:66', '192.168.0.10', 'Device-1', 'Apple', 'PHONE', 1, 1, 'My iPhone', null, 1000, 2000, -60)")
        stmt.execute("INSERT INTO `devices` VALUES ('AA:BB:CC:DD:EE:FF', '192.168.0.11', 'Device-2', 'Samsung', 'PHONE', 1, 1, 'Galaxy', null, 3000, 4000, -50)")
        stmt.execute("INSERT INTO `devices` VALUES ('00:11:22:33:44:55', '192.168.0.12', 'Device-3', 'Dell', 'LAPTOP', 0, 1, null, null, 5000, 6000, null)")

        // 2. Perform Migration 2 -> 3 steps
        // a. Add deviceId column
        stmt.execute("ALTER TABLE `devices` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_devices_deviceId` ON `devices` (`deviceId`)")

        // b. Backfill UUIDs per row
        val rs = stmt.executeQuery("SELECT `macAddress` FROM `devices`")
        val macs = mutableListOf<String>()
        while (rs.next()) {
            macs.add(rs.getString("macAddress"))
        }
        rs.close()

        val updateStmt = conn.prepareStatement("UPDATE `devices` SET `deviceId` = ? WHERE `macAddress` = ?")
        for (mac in macs) {
            val uuid = UUID.randomUUID().toString()
            updateStmt.setString(1, uuid)
            updateStmt.setString(2, mac)
            updateStmt.executeUpdate()
        }
        updateStmt.close()

        // c. Create known_macs table
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS `known_macs` (
                `mac` TEXT NOT NULL,
                `deviceId` TEXT NOT NULL,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                PRIMARY KEY(`mac`)
            )
            """.trimIndent()
        )
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_known_macs_deviceId` ON `known_macs` (`deviceId`)")

        // d. Populate known_macs
        stmt.execute(
            """
            INSERT OR REPLACE INTO `known_macs` (`mac`, `deviceId`, `firstSeen`, `lastSeen`)
            SELECT `macAddress`, `deviceId`, `firstSeen`, `lastSeen` FROM `devices`
            """.trimIndent()
        )

        // 3. Assertions
        // Verify all 3 devices have unique non-empty UUIDs
        val devRs = stmt.executeQuery("SELECT `macAddress`, `deviceId`, `firstSeen`, `lastSeen` FROM `devices`")
        val deviceUuids = mutableMapOf<String, String>()
        while (devRs.next()) {
            val mac = devRs.getString("macAddress")
            val uuid = devRs.getString("deviceId")
            assertNotNull(uuid)
            assertTrue(uuid.isNotBlank())
            assertTrue(uuid.length == 36)
            deviceUuids[mac] = uuid
        }
        devRs.close()
        assertEquals(3, deviceUuids.size)
        // Ensure UUIDs are distinct
        assertEquals(3, deviceUuids.values.toSet().size)

        // Verify known_macs has matching rows
        val kmRs = stmt.executeQuery("SELECT `mac`, `deviceId`, `firstSeen`, `lastSeen` FROM `known_macs`")
        val knownMacCount = mutableMapOf<String, String>()
        while (kmRs.next()) {
            val mac = kmRs.getString("mac")
            val uuid = kmRs.getString("deviceId")
            assertEquals(deviceUuids[mac], uuid)
            knownMacCount[mac] = uuid
        }
        kmRs.close()
        assertEquals(3, knownMacCount.size)

        // Verify indices exist
        val idxRs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'")
        val indexNames = mutableSetOf<String>()
        while (idxRs.next()) {
            indexNames.add(idxRs.getString("name"))
        }
        idxRs.close()
        assertTrue("index_devices_deviceId exists", indexNames.contains("index_devices_deviceId"))
        assertTrue("index_known_macs_deviceId exists", indexNames.contains("index_known_macs_deviceId"))

        conn.close()
    }

    @Test
    fun testMigrationFrom3To4Logic() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Set up Version 3 schema
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS `known_macs` (
                `mac` TEXT NOT NULL,
                `deviceId` TEXT NOT NULL,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                PRIMARY KEY(`mac`)
            )
            """.trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS `device_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `connectedAt` INTEGER NOT NULL,
                `disconnectedAt` INTEGER
            )
            """.trimIndent()
        )

        // Seed known_macs with 2 MACs for device-1 and 1 MAC for device-2
        stmt.execute("INSERT INTO `known_macs` VALUES ('MAC_ROTATED_OLD', 'device-uuid-1', 1000, 2000)")
        stmt.execute("INSERT INTO `known_macs` VALUES ('MAC_ROTATED_NEW', 'device-uuid-1', 2001, 3000)")
        stmt.execute("INSERT INTO `known_macs` VALUES ('MAC_DEVICE_2', 'device-uuid-2', 1500, 2500)")

        // Seed device_sessions:
        // Session 1: under old MAC of device-1
        stmt.execute("INSERT INTO `device_sessions` (`macAddress`, `ipAddress`, `hostname`, `connectedAt`, `disconnectedAt`) VALUES ('MAC_ROTATED_OLD', '192.168.0.10', 'Phone', 1000, 2000)")
        // Session 2: under new rotated MAC of device-1
        stmt.execute("INSERT INTO `device_sessions` (`macAddress`, `ipAddress`, `hostname`, `connectedAt`, `disconnectedAt`) VALUES ('MAC_ROTATED_NEW', '192.168.0.10', 'Phone', 2001, null)")
        // Session 3: under device-2
        stmt.execute("INSERT INTO `device_sessions` (`macAddress`, `ipAddress`, `hostname`, `connectedAt`, `disconnectedAt`) VALUES ('MAC_DEVICE_2', '192.168.0.20', 'Laptop', 1500, 2500)")
        // Session 4: orphan MAC not in known_macs (should remain deviceId = null)
        stmt.execute("INSERT INTO `device_sessions` (`macAddress`, `ipAddress`, `hostname`, `connectedAt`, `disconnectedAt`) VALUES ('ORPHAN_MAC', '192.168.0.99', 'Unknown', 500, 800)")

        // 2. Perform Migration 3 -> 4
        stmt.execute("ALTER TABLE `device_sessions` ADD COLUMN `deviceId` TEXT")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_device_sessions_deviceId` ON `device_sessions` (`deviceId`)")
        stmt.execute(
            """
            UPDATE `device_sessions` 
            SET `deviceId` = (
                SELECT `deviceId` FROM `known_macs` 
                WHERE `known_macs`.`mac` = `device_sessions`.`macAddress`
            )
            WHERE EXISTS (
                SELECT 1 FROM `known_macs` 
                WHERE `known_macs`.`mac` = `device_sessions`.`macAddress`
            )
            """.trimIndent()
        )

        // 3. Assertions
        val rs = stmt.executeQuery("SELECT `id`, `macAddress`, `deviceId` FROM `device_sessions` ORDER BY `id` ASC")
        val sessionDevices = mutableMapOf<Int, String?>()
        while (rs.next()) {
            val id = rs.getInt("id")
            val deviceId = rs.getString("deviceId")
            sessionDevices[id] = deviceId
        }
        rs.close()

        // Session 1 (under old MAC) backfilled to device-uuid-1
        assertEquals("device-uuid-1", sessionDevices[1])
        // Session 2 (under new MAC) backfilled to device-uuid-1
        assertEquals("device-uuid-1", sessionDevices[2])
        // Session 3 backfilled to device-uuid-2
        assertEquals("device-uuid-2", sessionDevices[3])
        // Session 4 orphan has deviceId null
        assertNull(sessionDevices[4])

        conn.close()
    }

    @Test
    fun testHistoryContinuousAccordionAcrossMacRotation() {
        // Simulate sessions before and after a MAC address rotation
        val sessionOldMac = com.tenda.wifimonitor.domain.model.DeviceSession(
            id = 1,
            macAddress = "DA:A1:19:64:55:60",
            ipAddress = "192.168.0.100",
            hostname = "Tayyab-Phone",
            connectedAt = java.util.Date(10000),
            disconnectedAt = java.util.Date(20000),
            deviceId = "stable-phone-uuid"
        )
        val sessionNewMac = com.tenda.wifimonitor.domain.model.DeviceSession(
            id = 2,
            macAddress = "36:11:22:33:44:55",
            ipAddress = "192.168.0.100",
            hostname = "Tayyab-Phone",
            connectedAt = java.util.Date(30000),
            disconnectedAt = null, // active now
            deviceId = "stable-phone-uuid"
        )
        val sessionOtherDevice = com.tenda.wifimonitor.domain.model.DeviceSession(
            id = 3,
            macAddress = "00:11:22:33:44:55",
            ipAddress = "192.168.0.200",
            hostname = "Work-Laptop",
            connectedAt = java.util.Date(25000),
            disconnectedAt = java.util.Date(35000),
            deviceId = "stable-laptop-uuid"
        )
        val sessionLegacyOrphan = com.tenda.wifimonitor.domain.model.DeviceSession(
            id = 4,
            macAddress = "AA:BB:CC:DD:EE:FF",
            ipAddress = "192.168.0.250",
            hostname = "Guest",
            connectedAt = java.util.Date(5000),
            disconnectedAt = java.util.Date(8000),
            deviceId = null // legacy unbackfilled row
        )

        val allSessions = listOf(sessionOldMac, sessionNewMac, sessionOtherDevice, sessionLegacyOrphan)

        // Grouping logic used in DeviceHistoryScreen
        val groupedByDevice = allSessions
            .groupBy { it.deviceId ?: it.macAddress }
            .entries
            .map { (_, sessions) ->
                val sorted = sessions.sortedByDescending { it.connectedAt }
                sorted.first() to sorted
            }
            .sortedByDescending { it.first.connectedAt }

        // Must produce 3 device accordions: stable-phone-uuid, stable-laptop-uuid, AA:BB:CC:DD:EE:FF
        assertEquals(3, groupedByDevice.size)

        // Find the phone's accordion
        val phoneGroup = groupedByDevice.find { (latest, _) -> latest.deviceId == "stable-phone-uuid" }
        assertNotNull(phoneGroup)

        val (latestSession, sessions) = phoneGroup!!
        // Header card shows the most recent session (with the new rotated MAC and active state)
        assertEquals("36:11:22:33:44:55", latestSession.macAddress)
        assertTrue(latestSession.isActive)

        // Accordion body contains BOTH sessions for this physical device
        assertEquals(2, sessions.size)
        assertEquals(sessionNewMac.id, sessions[0].id)
        assertEquals(sessionOldMac.id, sessions[1].id)
        assertEquals("DA:A1:19:64:55:60", sessions[1].macAddress)

        // Legacy orphan row without deviceId is still visible under its macAddress
        val orphanGroup = groupedByDevice.find { (latest, _) -> latest.macAddress == "AA:BB:CC:DD:EE:FF" }
        assertNotNull(orphanGroup)
        assertEquals(1, orphanGroup!!.second.size)
    }

    @Test
    fun testManualMergeDevicesLogic() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Setup schema
        stmt.execute(
            """
            CREATE TABLE `devices` (
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `manufacturer` TEXT NOT NULL,
                `deviceType` TEXT NOT NULL,
                `isOnline` INTEGER NOT NULL,
                `isWhitelisted` INTEGER NOT NULL,
                `customName` TEXT,
                `customIcon` TEXT,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                `signalStrength` INTEGER,
                `deviceId` TEXT NOT NULL,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE `known_macs` (
                `mac` TEXT NOT NULL,
                `deviceId` TEXT NOT NULL,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                PRIMARY KEY(`mac`)
            )
            """.trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE `device_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `connectedAt` INTEGER NOT NULL,
                `disconnectedAt` INTEGER,
                `deviceId` TEXT
            )
            """.trimIndent()
        )

        // Insert initial data: Kept device and MergeAway device
        stmt.execute("INSERT INTO `devices` VALUES ('11:22:33:44:55:66', '192.168.0.10', 'Old-Phone', 'Apple', 'PHONE', 0, 1, 'My Special iPhone', '📱', 1000, 2000, -60, 'keep-uuid')")
        stmt.execute("INSERT INTO `devices` VALUES ('AA:BB:CC:DD:EE:FF', '192.168.0.20', 'android-xyz', 'Google', 'UNKNOWN', 1, 0, null, null, 3000, 5000, -50, 'merge-away-uuid')")

        stmt.execute("INSERT INTO `known_macs` VALUES ('11:22:33:44:55:66', 'keep-uuid', 1000, 2000)")
        stmt.execute("INSERT INTO `known_macs` VALUES ('AA:BB:CC:DD:EE:FF', 'merge-away-uuid', 3000, 5000)")

        stmt.execute("INSERT INTO `device_sessions` VALUES (1, '11:22:33:44:55:66', '192.168.0.10', 'Old-Phone', 1000, 2000, 'keep-uuid')")
        stmt.execute("INSERT INTO `device_sessions` VALUES (2, 'AA:BB:CC:DD:EE:FF', '192.168.0.20', 'android-xyz', 3000, null, 'merge-away-uuid')")

        // 2. Perform merge actions (as executed in DeviceRepository.mergeDevices)
        // c. Reassign sessions
        stmt.execute("UPDATE device_sessions SET deviceId = 'keep-uuid' WHERE deviceId = 'merge-away-uuid'")
        // d. Reassign known MACs
        stmt.execute("UPDATE known_macs SET deviceId = 'keep-uuid' WHERE deviceId = 'merge-away-uuid'")
        // e. Upsert merge-away MAC under keep-uuid
        stmt.execute("INSERT OR REPLACE INTO known_macs (mac, deviceId, firstSeen, lastSeen) VALUES ('AA:BB:CC:DD:EE:FF', 'keep-uuid', 3000, 5000)")
        // g & f. Delete old kept MAC row and insert updated kept device row under new live MAC
        stmt.execute("DELETE FROM devices WHERE macAddress = '11:22:33:44:55:66'")
        stmt.execute("INSERT OR REPLACE INTO `devices` VALUES ('AA:BB:CC:DD:EE:FF', '192.168.0.20', 'android-xyz', 'Google', 'PHONE', 1, 1, 'My Special iPhone', '📱', 1000, 5000, -50, 'keep-uuid')")

        // 3. Assertions
        // Exactly one device row remains
        val devRs = stmt.executeQuery("SELECT * FROM devices")
        assertTrue(devRs.next())
        assertEquals("AA:BB:CC:DD:EE:FF", devRs.getString("macAddress"))
        assertEquals("keep-uuid", devRs.getString("deviceId"))
        assertEquals("My Special iPhone", devRs.getString("customName")) // Preserved identity
        assertEquals(1, devRs.getInt("isWhitelisted")) // Preserved whitelist
        assertEquals(1000, devRs.getLong("firstSeen")) // Preserved firstSeen
        assertEquals(5000, devRs.getLong("lastSeen")) // Updated to latest
        assertEquals(1, devRs.getInt("isOnline")) // Online from active device
        assertTrue(!devRs.next())
        devRs.close()

        // Known MACs: both MACs now point to keep-uuid
        val kmRs = stmt.executeQuery("SELECT mac, deviceId FROM known_macs ORDER BY mac ASC")
        assertTrue(kmRs.next())
        assertEquals("11:22:33:44:55:66", kmRs.getString("mac"))
        assertEquals("keep-uuid", kmRs.getString("deviceId"))
        assertTrue(kmRs.next())
        assertEquals("AA:BB:CC:DD:EE:FF", kmRs.getString("mac"))
        assertEquals("keep-uuid", kmRs.getString("deviceId"))
        assertTrue(!kmRs.next())
        kmRs.close()

        // Sessions: both sessions now point to keep-uuid
        val sessRs = stmt.executeQuery("SELECT id, deviceId FROM device_sessions ORDER BY id ASC")
        assertTrue(sessRs.next())
        assertEquals(1, sessRs.getInt("id"))
        assertEquals("keep-uuid", sessRs.getString("deviceId"))
        assertTrue(sessRs.next())
        assertEquals(2, sessRs.getInt("id"))
        assertEquals("keep-uuid", sessRs.getString("deviceId"))
        assertTrue(!sessRs.next())
        sessRs.close()

        conn.close()
    }

    @Test
    fun testMigrationFrom4To5Logic() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Version 4 schema for devices
        stmt.execute(
            """
            CREATE TABLE `devices` (
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `manufacturer` TEXT NOT NULL,
                `deviceType` TEXT NOT NULL,
                `isOnline` INTEGER NOT NULL,
                `isWhitelisted` INTEGER NOT NULL,
                `customName` TEXT,
                `customIcon` TEXT,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                `signalStrength` INTEGER,
                `deviceId` TEXT NOT NULL,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent()
        )

        stmt.execute("INSERT INTO `devices` VALUES ('11:22:33:44:55:66', '192.168.0.10', 'Phone', 'Apple', 'PHONE', 1, 1, 'My iPhone', null, 1000, 2000, -60, 'uuid-1')")

        // 2. Perform Migration 4 -> 5
        stmt.execute("ALTER TABLE `devices` ADD COLUMN `isBlocked` INTEGER NOT NULL DEFAULT 0")
        stmt.execute("ALTER TABLE `devices` ADD COLUMN `downloadLimitKbps` REAL DEFAULT NULL")
        stmt.execute("ALTER TABLE `devices` ADD COLUMN `uploadLimitKbps` REAL DEFAULT NULL")

        // 3. Verify defaults
        val rs = stmt.executeQuery("SELECT `isBlocked`, `downloadLimitKbps`, `uploadLimitKbps` FROM `devices` WHERE `macAddress` = '11:22:33:44:55:66'")
        assertTrue(rs.next())
        assertEquals(0, rs.getInt("isBlocked"))
        rs.getDouble("downloadLimitKbps")
        assertTrue(rs.wasNull())
        rs.getDouble("uploadLimitKbps")
        assertTrue(rs.wasNull())
        rs.close()

        // 4. Update and verify
        stmt.execute("UPDATE `devices` SET `isBlocked` = 1, `downloadLimitKbps` = 256.0, `uploadLimitKbps` = 128.0 WHERE `macAddress` = '11:22:33:44:55:66'")
        val rsUpdated = stmt.executeQuery("SELECT `isBlocked`, `downloadLimitKbps`, `uploadLimitKbps` FROM `devices` WHERE `macAddress` = '11:22:33:44:55:66'")
        assertTrue(rsUpdated.next())
        assertEquals(1, rsUpdated.getInt("isBlocked"))
        assertEquals(256.0, rsUpdated.getDouble("downloadLimitKbps"), 0.001)
        assertEquals(128.0, rsUpdated.getDouble("uploadLimitKbps"), 0.001)
        rsUpdated.close()

        conn.close()
    }

    @Test
    fun testMigrationFrom5To6DropsSignalStrength() {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Set up the Version 5 `devices` schema, including signalStrength.
        stmt.execute(
            """
            CREATE TABLE `devices` (
                `macAddress` TEXT NOT NULL,
                `ipAddress` TEXT NOT NULL,
                `hostname` TEXT NOT NULL,
                `manufacturer` TEXT NOT NULL,
                `deviceType` TEXT NOT NULL,
                `isOnline` INTEGER NOT NULL,
                `isWhitelisted` INTEGER NOT NULL,
                `customName` TEXT,
                `customIcon` TEXT,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                `signalStrength` INTEGER,
                `deviceId` TEXT NOT NULL DEFAULT '',
                `isBlocked` INTEGER NOT NULL DEFAULT 0,
                `downloadLimitKbps` REAL DEFAULT NULL,
                `uploadLimitKbps` REAL DEFAULT NULL,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent()
        )
        stmt.execute("CREATE INDEX `index_devices_deviceId` ON `devices` (`deviceId`)")
        stmt.execute(
            "INSERT INTO `devices` VALUES " +
                "('AA:BB:CC:DD:EE:FF', '192.168.0.10', 'Pixel', 'Google', 'PHONE', 1, 1, " +
                "'My Phone', 'phone', 1000, 2000, -55, 'device-1', 1, 256.0, 128.0)"
        )

        // 2. Run the real migration statements.
        for (sql in AppDatabase.MIGRATION_5_6_STATEMENTS) {
            stmt.execute(sql)
        }

        // 3. The signalStrength column is gone...
        val columns = mutableSetOf<String>()
        val colRs = stmt.executeQuery("PRAGMA table_info(`devices`)")
        while (colRs.next()) {
            columns.add(colRs.getString("name"))
        }
        colRs.close()
        assertTrue(columns.isNotEmpty())
        assertFalse("signalStrength" in columns)
        assertTrue("deviceId" in columns)

        // ...but every other value survived.
        val rs = stmt.executeQuery("SELECT * FROM `devices` WHERE `macAddress` = 'AA:BB:CC:DD:EE:FF'")
        assertTrue(rs.next())
        assertEquals("192.168.0.10", rs.getString("ipAddress"))
        assertEquals("Pixel", rs.getString("hostname"))
        assertEquals("Google", rs.getString("manufacturer"))
        assertEquals("PHONE", rs.getString("deviceType"))
        assertEquals(1, rs.getInt("isOnline"))
        assertEquals(1, rs.getInt("isWhitelisted"))
        assertEquals("My Phone", rs.getString("customName"))
        assertEquals("phone", rs.getString("customIcon"))
        assertEquals(1000L, rs.getLong("firstSeen"))
        assertEquals(2000L, rs.getLong("lastSeen"))
        assertEquals("device-1", rs.getString("deviceId"))
        assertEquals(1, rs.getInt("isBlocked"))
        assertEquals(256.0, rs.getDouble("downloadLimitKbps"), 0.001)
        assertEquals(128.0, rs.getDouble("uploadLimitKbps"), 0.001)
        rs.close()

        // 4. The deviceId index still exists.
        var indexFound = false
        val idxRs = stmt.executeQuery("PRAGMA index_list(`devices`)")
        while (idxRs.next()) {
            if (idxRs.getString("name") == "index_devices_deviceId") indexFound = true
        }
        idxRs.close()
        assertTrue(indexFound)

        conn.close()
    }
}


