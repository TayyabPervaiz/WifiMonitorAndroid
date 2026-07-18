package com.tenda.wifimonitor.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
    }

    val routerPassword: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.ROUTER_PASSWORD] ?: "" }

    val routerUrl: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.ROUTER_URL] ?: "http://192.168.0.1" }

    val pollingIntervalSecs: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.POLLING_INTERVAL_SECS] ?: 30L }

    val isMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.IS_MONITORING_ENABLED] ?: true }

    val intruderAlertEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.INTRUDER_ALERT_ENABLED] ?: true }

    val soundEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.SOUND_ENABLED] ?: true }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.IS_SETUP_COMPLETE] ?: false }

    val darkTheme: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.DARK_THEME] ?: true }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.USE_DYNAMIC_COLOR] ?: false }

    suspend fun setRouterPassword(password: String) {
        context.dataStore.edit { it[Keys.ROUTER_PASSWORD] = password }
    }

    suspend fun setRouterUrl(url: String) {
        context.dataStore.edit { it[Keys.ROUTER_URL] = url }
    }

    suspend fun setPollingInterval(seconds: Long) {
        context.dataStore.edit { it[Keys.POLLING_INTERVAL_SECS] = seconds }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_MONITORING_ENABLED] = enabled }
    }

    suspend fun setIntruderAlertEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INTRUDER_ALERT_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.IS_SETUP_COMPLETE] = complete }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setUseDynamicColor(use: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = use }
    }
}
