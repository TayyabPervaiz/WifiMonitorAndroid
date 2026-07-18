package com.tenda.wifimonitor.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.NetworkDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkMapViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {

    val onlineDevices: StateFlow<List<NetworkDevice>> = repository.observeOnlineDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
