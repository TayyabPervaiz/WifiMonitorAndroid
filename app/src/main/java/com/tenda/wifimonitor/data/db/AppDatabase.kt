package com.tenda.wifimonitor.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [
        DeviceEntity::class,
        DeviceSessionEntity::class,
        TimedBlockEntity::class,
        KnownMacEntity::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun knownMacDao(): KnownMacDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `isBlocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `downloadLimitKbps` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `uploadLimitKbps` REAL DEFAULT NULL")
            }
        }

        /**
         * The exact statements run by [MIGRATION_5_6]. Exposed so the JVM migration
         * test can execute the same SQL against an in-memory SQLite database.
         */
        internal val MIGRATION_5_6_STATEMENTS: List<String> = listOf(
            // SQLite < 3.35 has no ALTER TABLE ... DROP COLUMN, so recreate the table.
            """
            CREATE TABLE IF NOT EXISTS `devices_new` (
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
                `deviceId` TEXT NOT NULL,
                `isBlocked` INTEGER NOT NULL,
                `downloadLimitKbps` REAL,
                `uploadLimitKbps` REAL,
                PRIMARY KEY(`macAddress`)
            )
            """.trimIndent(),
            """
            INSERT INTO `devices_new` (
                `macAddress`, `ipAddress`, `hostname`, `manufacturer`, `deviceType`,
                `isOnline`, `isWhitelisted`, `customName`, `customIcon`,
                `firstSeen`, `lastSeen`, `deviceId`, `isBlocked`,
                `downloadLimitKbps`, `uploadLimitKbps`
            )
            SELECT
                `macAddress`, `ipAddress`, `hostname`, `manufacturer`, `deviceType`,
                `isOnline`, `isWhitelisted`, `customName`, `customIcon`,
                `firstSeen`, `lastSeen`, `deviceId`, `isBlocked`,
                `downloadLimitKbps`, `uploadLimitKbps`
            FROM `devices`
            """.trimIndent(),
            "DROP TABLE `devices`",
            "ALTER TABLE `devices_new` RENAME TO `devices`",
            "CREATE INDEX IF NOT EXISTS `index_devices_deviceId` ON `devices` (`deviceId`)",
        )

        /** Drops the unused `signalStrength` column (schema v5 -> v6). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_STATEMENTS.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // a. Adds the new `deviceId` TEXT column to the `devices` table
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_deviceId` ON `devices` (`deviceId`)")

                // b. Backfills deviceId for every existing row with a random UUID
                val cursor = db.query("SELECT `macAddress` FROM `devices`")
                try {
                    while (cursor.moveToNext()) {
                        val mac = cursor.getString(0)
                        val uuid = UUID.randomUUID().toString()
                        db.execSQL(
                            "UPDATE `devices` SET `deviceId` = ? WHERE `macAddress` = ?",
                            arrayOf(uuid, mac)
                        )
                    }
                } finally {
                    cursor.close()
                }

                // c. Creates the new `known_macs` table
                db.execSQL(
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_macs_deviceId` ON `known_macs` (`deviceId`)")

                // d. Populates known_macs with one row per existing device
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `known_macs` (`mac`, `deviceId`, `firstSeen`, `lastSeen`)
                    SELECT `macAddress`, `deviceId`, `firstSeen`, `lastSeen` FROM `devices`
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // a. Adds the deviceId TEXT column to device_sessions
                db.execSQL("ALTER TABLE `device_sessions` ADD COLUMN `deviceId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_sessions_deviceId` ON `device_sessions` (`deviceId`)")

                // b. Backfills deviceId via correlated subquery from known_macs
                db.execSQL(
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
            }
        }
    }
}
