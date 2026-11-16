package com.tenda.wifimonitor.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ROUTER_PASSWORD = stringPreferencesKey("router_password")
        val ROUTER_URL = stringPreferencesKey("router_url")
        val POLLING_INTERVAL_SECS = longPreferencesKey("polling_interval_secs")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val INTRUDER_ALERT_ENABLED = booleanPreferencesKey("intruder_alert_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val AUTO_BLOCK_UNKNOWN_DEVICES = booleanPreferencesKey("auto_block_unknown_devices")
        val BATTERY_OPT_REQUESTED = booleanPreferencesKey("battery_opt_requested")
        val NOTIFIED_TIMESTAMPS = stringPreferencesKey("notified_timestamps")
    }

    private val plainDataStore = context.dataStore

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val migrationDone = AtomicBoolean(false)

    val routerPassword: Flow<String> = flow {
        migratePasswordIfNeeded()
        val password = encryptedPrefs.getString(Keys.ROUTER_PASSWORD.name, "") ?: ""
        emit(password)
    }

    val routerUrl: Flow<String> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.ROUTER_URL] ?: "http://192.168.0.1" }

    val pollingIntervalSecs: Flow<Long> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.POLLING_INTERVAL_SECS] ?: 30L }

    val isMonitoringEnabled: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.IS_MONITORING_ENABLED] ?: true }

    val intruderAlertEnabled: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.INTRUDER_ALERT_ENABLED] ?: true }

    val soundEnabled: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.SOUND_ENABLED] ?: true }

    val isSetupComplete: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.IS_SETUP_COMPLETE] ?: false }

    val darkTheme: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.DARK_THEME] ?: true }

    val useDynamicColor: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.USE_DYNAMIC_COLOR] ?: false }

    val autoBlockUnknownDevices: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.AUTO_BLOCK_UNKNOWN_DEVICES] ?: false }

    val batteryOptRequested: Flow<Boolean> = plainDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.BATTERY_OPT_REQUESTED] ?: false }

    private suspend fun migratePasswordIfNeeded() {
        if (migrationDone.getAndSet(true)) return

        val plainPassword = plainDataStore.data.first()[Keys.ROUTER_PASSWORD]
        if (!plainPassword.isNullOrBlank()) {
            encryptedPrefs.edit()
                .putString(Keys.ROUTER_PASSWORD.name, plainPassword)
                .apply()
            plainDataStore.edit { it.remove(Keys.ROUTER_PASSWORD) }
        }
    }

    suspend fun setRouterPassword(password: String) {
        migratePasswordIfNeeded()
        encryptedPrefs.edit()
            .putString(Keys.ROUTER_PASSWORD.name, password)
            .apply()
        plainDataStore.edit { it.remove(Keys.ROUTER_PASSWORD) }
    }

    suspend fun setRouterUrl(url: String) {
        plainDataStore.edit { it[Keys.ROUTER_URL] = url }
    }

    suspend fun setPollingInterval(seconds: Long) {
        plainDataStore.edit { it[Keys.POLLING_INTERVAL_SECS] = seconds }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        plainDataStore.edit { it[Keys.IS_MONITORING_ENABLED] = enabled }
    }

    suspend fun setIntruderAlertEnabled(enabled: Boolean) {
        plainDataStore.edit { it[Keys.INTRUDER_ALERT_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        plainDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        plainDataStore.edit { it[Keys.IS_SETUP_COMPLETE] = complete }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        plainDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setUseDynamicColor(use: Boolean) {
        plainDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = use }
    }

    suspend fun setAutoBlockUnknownDevices(enabled: Boolean) {
        plainDataStore.edit { it[Keys.AUTO_BLOCK_UNKNOWN_DEVICES] = enabled }
    }

    suspend fun setBatteryOptRequested(requested: Boolean) {
        plainDataStore.edit { it[Keys.BATTERY_OPT_REQUESTED] = requested }
    }

    // --- Notification timestamp persistence (key -> epoch millis) ---
    // Keys are namespaced (e.g. "new:AA:BB:..", "intruder:AA:BB:..") so the
    // new-device and intruder cooldowns are tracked independently.

    private suspend fun getNotifiedTimestamps(): Map<String, Long> {
        val raw = plainDataStore.data.first()[Keys.NOTIFIED_TIMESTAMPS] ?: return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val sep = entry.lastIndexOf('|')
            if (sep <= 0) return@mapNotNull null
            entry.substring(sep + 1).toLongOrNull()?.let { ts ->
                entry.substring(0, sep) to ts
            }
        }.toMap()
    }

    suspend fun getLastNotified(key: String): Long? {
        return getNotifiedTimestamps()[key]
    }

    suspend fun setLastNotified(key: String, timestamp: Long) {
        plainDataStore.edit { prefs ->
            val current = (prefs[Keys.NOTIFIED_TIMESTAMPS] ?: "")
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableList()
            current.removeAll { it.substringBefore('|') == key }
            current.add("$key|$timestamp")
            prefs[Keys.NOTIFIED_TIMESTAMPS] = current.joinToString(",")
        }
    }

    suspend fun removeNotified(key: String) {
        plainDataStore.edit { prefs ->
            val current = (prefs[Keys.NOTIFIED_TIMESTAMPS] ?: "")
                .split(",")
                .filter { it.isNotBlank() && it.substringBefore('|') != key }
            prefs[Keys.NOTIFIED_TIMESTAMPS] = current.joinToString(",")
        }
    }
}
