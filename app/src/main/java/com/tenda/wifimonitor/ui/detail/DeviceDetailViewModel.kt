package com.tenda.wifimonitor.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.DeviceSession
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.domain.model.NetworkDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val device: NetworkDevice? = null,
    val sessions: List<DeviceSession> = emptyList(),
    val isBlockLoading: Boolean = false,
    val blockSuccess: String? = null,
    val blockError: String? = null,
    val isEditingName: Boolean = false,
    val editName: String = "",
    val showIconPicker: Boolean = false,
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repository: DeviceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mac: String = savedStateHandle["mac"] ?: ""

    private val _state = MutableStateFlow(DetailUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDevice(mac).collect { device ->
                _state.update { it.copy(device = device, editName = device?.customName ?: device?.hostname ?: "") }
            }
        }
        viewModelScope.launch {
            repository.observeSessionsForDevice(mac).collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
    }

    fun whitelist() = viewModelScope.launch {
        repository.whitelistDevice(mac)
    }

    fun removeWhitelist() = viewModelScope.launch {
        repository.removeFromWhitelist(mac)
    }

    fun blockDevice() = viewModelScope.launch {
        _state.update { it.copy(isBlockLoading = true, blockError = null) }
        val ok = repository.blockDevice(mac)
        _state.update { it.copy(
            isBlockLoading = false,
            blockSuccess = if (ok) "Device blocked" else null,
            blockError = if (!ok) "Block failed — your firmware may not support this" else null,
        )}
    }

    fun unblockDevice() = viewModelScope.launch {
        _state.update { it.copy(isBlockLoading = true, blockError = null) }
        val ok = repository.unblockDevice(mac)
        _state.update { it.copy(
            isBlockLoading = false,
            blockSuccess = if (ok) "Device unblocked" else null,
            blockError = if (!ok) "Unblock failed" else null,
        )}
    }

    fun startEditName() = _state.update { it.copy(isEditingName = true) }
    fun onNameChange(name: String) = _state.update { it.copy(editName = name) }
    fun saveCustomName() = viewModelScope.launch {
        val name = _state.value.editName.trim().ifBlank { null }
        repository.updateCustomName(mac, name)
        _state.update { it.copy(isEditingName = false) }
    }

    fun setIcon(emoji: String) = viewModelScope.launch {
        repository.updateCustomIcon(mac, emoji)
        _state.update { it.copy(showIconPicker = false) }
    }

    fun showIconPicker() = _state.update { it.copy(showIconPicker = true) }
    fun hideIconPicker() = _state.update { it.copy(showIconPicker = false) }

    fun setDeviceType(type: DeviceType) = viewModelScope.launch {
        repository.updateDeviceType(mac, type)
    }

    fun dismissMessages() = _state.update { it.copy(blockSuccess = null, blockError = null) }
}
