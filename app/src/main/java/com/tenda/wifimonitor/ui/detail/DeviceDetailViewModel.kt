package com.tenda.wifimonitor.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.DeviceSession
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.domain.model.TimedBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val device: NetworkDevice? = null,
    val sessions: List<DeviceSession> = emptyList(),
    val timedBlock: TimedBlock? = null,
    val showTimedBlockDialog: Boolean = false,
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

    val allDevices: StateFlow<List<NetworkDevice>> = repository.observeAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mergeDevices(keepDeviceId: String, mergeAwayDeviceId: String, onComplete: () -> Unit) = viewModelScope.launch {
        repository.mergeDevices(keepDeviceId = keepDeviceId, mergeAwayDeviceId = mergeAwayDeviceId)
        onComplete()
    }

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
        viewModelScope.launch {
            repository.observeTimedBlocks().collect { blocks ->
                _state.update { it.copy(timedBlock = blocks[mac]) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val block = _state.value.timedBlock
                if (block != null && block.isExpired) {
                    repository.checkAndProcessExpiredTimedBlocks()
                }
            }
        }
    }

    fun openTimedBlockDialog() = _state.update { it.copy(showTimedBlockDialog = true) }
    fun closeTimedBlockDialog() = _state.update { it.copy(showTimedBlockDialog = false) }

    fun setTimedBlock(durationMinutes: Long) = viewModelScope.launch {
        val dev = _state.value.device ?: return@launch
        closeTimedBlockDialog()
        _state.update { it.copy(isBlockLoading = true, blockError = null) }
        val ok = repository.setTimedBlock(
            mac = mac,
            deviceName = dev.displayName,
            ipAddress = dev.ipAddress,
            durationMinutes = durationMinutes,
        )
        _state.update {
            it.copy(
                isBlockLoading = false,
                blockSuccess = if (ok) "⏱️ Timed block set ($durationMinutes min)" else null,
                blockError = if (!ok) "Failed to set block timer" else null,
            )
        }
    }

    fun cancelTimedBlock() = viewModelScope.launch {
        _state.update { it.copy(isBlockLoading = true, blockError = null) }
        val ok = repository.cancelTimedBlock(mac)
        _state.update {
            it.copy(
                isBlockLoading = false,
                blockSuccess = if (ok) "✅ Timer cancelled & internet restored" else null,
                blockError = if (!ok) "Failed to cancel timer" else null,
            )
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
