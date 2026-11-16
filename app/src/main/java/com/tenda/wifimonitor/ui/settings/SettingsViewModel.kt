package com.tenda.wifimonitor.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.service.NetworkScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val routerPassword: String = "",
    val routerUrl: String = "http://192.168.0.1",
    val pollingInterval: Long = 30L,
    val monitoringEnabled: Boolean = true,
    val intruderAlertEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val darkTheme: Boolean = true,
    val useDynamicColor: Boolean = false,
    val autoBlockUnknownDevices: Boolean = false,
    val saveSuccess: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.routerPassword,
                preferences.routerUrl,
                preferences.pollingIntervalSecs,
                preferences.isMonitoringEnabled,
                preferences.intruderAlertEnabled,
            ) { pw, url, interval, monitoring, intruder ->
                _state.update { it.copy(
                    routerPassword = pw,
                    routerUrl = url,
                    pollingInterval = interval,
                    monitoringEnabled = monitoring,
                    intruderAlertEnabled = intruder,
                )}
            }.collect()
        }
        viewModelScope.launch {
            combine(
                preferences.soundEnabled,
                preferences.darkTheme,
                preferences.useDynamicColor,
                preferences.autoBlockUnknownDevices,
            ) { sound, dark, dynamic, autoBlock ->
                _state.update { it.copy(
                    soundEnabled = sound,
                    darkTheme = dark,
                    useDynamicColor = dynamic,
                    autoBlockUnknownDevices = autoBlock,
                )}
            }.collect()
        }
    }

    fun setAutoBlockUnknownDevices(enabled: Boolean) {
        _state.update { it.copy(autoBlockUnknownDevices = enabled) }
        viewModelScope.launch { preferences.setAutoBlockUnknownDevices(enabled) }
    }

    fun setPollingInterval(secs: Long) {
        _state.update { it.copy(pollingInterval = secs) }
        viewModelScope.launch {
            preferences.setPollingInterval(secs)
            if (_state.value.monitoringEnabled) {
                NetworkScanService.start(context)
            }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        _state.update { it.copy(monitoringEnabled = enabled) }
        viewModelScope.launch {
            preferences.setMonitoringEnabled(enabled)
            if (enabled) NetworkScanService.start(context)
            else NetworkScanService.stop(context)
        }
    }

    fun setIntruderAlertEnabled(enabled: Boolean) {
        _state.update { it.copy(intruderAlertEnabled = enabled) }
        viewModelScope.launch { preferences.setIntruderAlertEnabled(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(soundEnabled = enabled) }
        viewModelScope.launch { preferences.setSoundEnabled(enabled) }
    }

    fun setDarkTheme(dark: Boolean) {
        _state.update { it.copy(darkTheme = dark) }
        viewModelScope.launch { preferences.setDarkTheme(dark) }
    }

    fun setUseDynamicColor(use: Boolean) {
        _state.update { it.copy(useDynamicColor = use) }
        viewModelScope.launch { preferences.setUseDynamicColor(use) }
    }

    fun updatePassword(pw: String) {
        _state.update { it.copy(routerPassword = pw) }
        viewModelScope.launch { preferences.setRouterPassword(pw) }
    }

    fun updateUrl(url: String) {
        _state.update { it.copy(routerUrl = url) }
        viewModelScope.launch { preferences.setRouterUrl(url) }
    }
}
