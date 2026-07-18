package com.tenda.wifimonitor.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.NetworkDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val allDevices: List<NetworkDevice> = emptyList(),
    val hourlyActivity: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val onlineByType: Map<String, Int> = emptyMap(),
    val trustedCount: Int = 0,
    val unknownCount: Int = 0,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllDevices().collect { devices ->
                val byType = devices.filter { it.isOnline }
                    .groupBy { it.deviceType.label }
                    .mapValues { it.value.size }
                _state.update { it.copy(
                    allDevices = devices,
                    onlineByType = byType,
                    trustedCount = devices.count { d -> d.isWhitelisted },
                    unknownCount = devices.count { d -> !d.isWhitelisted },
                    isLoading = false,
                )}
            }
        }
        loadHourlyActivity()
    }

    private fun loadHourlyActivity() {
        viewModelScope.launch {
            val hourly = repository.getHourlyDeviceActivity()
            _state.update { it.copy(hourlyActivity = hourly) }
        }
    }
}
