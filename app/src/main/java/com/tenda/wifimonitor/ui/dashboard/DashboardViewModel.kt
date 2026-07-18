package com.tenda.wifimonitor.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.service.NetworkMonitorWorker
import com.tenda.wifimonitor.service.NetworkScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val onlineDevices: List<NetworkDevice> = emptyList(),
    val allDevices: List<NetworkDevice> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0,
    val onlineCount: Int = 0,
    val unknownCount: Int = 0,
    val wanIp: String = "…",
    val routerUptime: String = "…",
    val filter: DeviceFilter = DeviceFilter.ONLINE,
    val searchQuery: String = "",
    val error: String? = null,
)

enum class DeviceFilter(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    UNKNOWN("Unknown"),
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    val filteredDevices: StateFlow<List<NetworkDevice>> = _state.map { s ->
        val base = when (s.filter) {
            DeviceFilter.ONLINE -> s.onlineDevices
            DeviceFilter.ALL -> s.allDevices
            DeviceFilter.UNKNOWN -> s.allDevices.filter { !it.isWhitelisted }
        }
        if (s.searchQuery.isBlank()) base
        else base.filter { d ->
            d.displayName.contains(s.searchQuery, ignoreCase = true) ||
                    d.ipAddress.contains(s.searchQuery) ||
                    d.macAddress.contains(s.searchQuery, ignoreCase = true) ||
                    d.manufacturer.contains(s.searchQuery, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeDevices()
        fetchRouterStatus()
        refresh() // auto-scan on startup
    }

    private fun observeDevices() {
        viewModelScope.launch {
            repository.observeAllDevices().collect { all ->
                val online = all.filter { it.isOnline }
                val unknown = all.filter { !it.isWhitelisted }
                _state.update { it.copy(
                    allDevices = all,
                    onlineDevices = online,
                    onlineCount = online.size,
                    unknownCount = unknown.size,
                )}
            }
        }
    }

    private fun fetchRouterStatus() {
        viewModelScope.launch {
            try {
                repository.getRouterStatus()?.let { status ->
                    _state.update { it.copy(
                        wanIp = status.wanIp,
                        routerUptime = status.uptime,
                    )}
                }
            } catch (_: Exception) {}
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, error = null) }
            try {
                val password = preferences.routerPassword.firstOrNull() ?: return@launch
                repository.performScan(password)
                _state.update { it.copy(isScanning = false, lastScanTime = System.currentTimeMillis()) }
                fetchRouterStatus()
            } catch (e: Exception) {
                _state.update { it.copy(isScanning = false, error = "Scan failed: ${e.message}") }
            }
        }
    }

    fun setFilter(filter: DeviceFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    // getFilteredDevices is now a reactive StateFlow (filteredDevices) above

    fun whitelistDevice(mac: String) {
        viewModelScope.launch { repository.whitelistDevice(mac) }
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMonitoringEnabled(enabled)
            if (enabled) {
                val interval = preferences.pollingIntervalSecs.firstOrNull() ?: 30L
                NetworkMonitorWorker.schedule(context, interval)
                NetworkScanService.start(context)
            } else {
                NetworkMonitorWorker.cancel(context)
                NetworkScanService.stop(context)
            }
        }
    }
}
