package com.tenda.wifimonitor.data.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up the hardware manufacturer from a device's MAC address
 * using the IEEE OUI (Organizationally Unique Identifier) database.
 *
 * We bundle a compressed OUI list as a raw asset (oui_compact.txt).
 * Format: first 6 hex chars (uppercase, no colon) TAB manufacturer name
 * Example: AABBCC\tSamsung Electronics Co.,Ltd
 */
@Singleton
class OuiLookup @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "OuiLookup"
    private val ouiMap = mutableMapOf<String, String>()
    private var loaded = false

    fun lookup(macAddress: String): String {
        ensureLoaded()
        val prefix = macAddress.uppercase()
            .replace(":", "")
            .replace("-", "")
            .take(6)
        return ouiMap[prefix] ?: "Unknown"
    }

    private fun ensureLoaded() {
        if (loaded) return
        try {
            val stream = context.assets.open("oui_compact.txt")
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split("\t")
                    if (parts.size == 2) {
                        ouiMap[parts[0].uppercase()] = parts[1]
                    }
                }
            }
            loaded = true
            Log.d(TAG, "OUI database loaded: ${ouiMap.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OUI database: ${e.message}")
        }
    }
}
