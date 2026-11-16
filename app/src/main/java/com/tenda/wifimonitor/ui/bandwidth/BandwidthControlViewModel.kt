package com.tenda.wifimonitor.ui.bandwidth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.network.DeviceBandwidthInfo
import com.tenda.wifimonitor.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.tenda.wifimonitor.domain.model.TimedBlock

/** A staged bandwidth change for a single device (not yet applied to the router). */
data class BandwidthChange(
    val downloadLimitRaw: Float,
    val uploadLimitRaw: Float,
)

data class BandwidthUiState(
    val devices: List<DeviceBandwidthInfo> = emptyList(),
    val blacklisted: List<DeviceBandwidthInfo> = emptyList(),
    /** MAC → active TimedBlock details */
    val timedBlocks: Map<String, TimedBlock> = emptyMap(),
    /** Selected device for TimedBlockDialog, null when dialog is closed */
    val timedBlockTargetDevice: DeviceBandwidthInfo? = null,
    /** MAC → pending (not-yet-saved) limit changes */
    val pendingChanges: Map<String, BandwidthChange> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BandwidthControlViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(BandwidthUiState())
    val state = _state.asStateFlow()

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }

    init {
        observeTimedBlocks()
        startPolling()
        startTimerWatcher()
    }

    private fun observeTimedBlocks() {
        viewModelScope.launch {
            repository.observeTimedBlocks().collect { blocks ->
                _state.update { it.copy(timedBlocks = blocks) }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                loadDevices()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Watches active timed blocks every second. As soon as a block expires,
     * immediately triggers the unblock process so the UI updates in real-time.
     */
    private fun startTimerWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val hasExpired = _state.value.timedBlocks.values.any { it.isExpired }
                if (hasExpired) {
                    val unblocked = repository.checkAndProcessExpiredTimedBlocks()
                    if (unblocked.isNotEmpty()) {
                        loadDevices()
                    }
                }
            }
        }
    }

    private suspend fun loadDevices() {
        repository.checkAndProcessExpiredTimedBlocks()
        val password = preferences.routerPassword.firstOrNull() ?: ""
        if (password.isNotBlank()) repository.login(password)

        try {
            val all = repository.getDeviceBandwidthList()
            val online = all.filter { it.internetAccessEnabled }
                .sortedWith(compareByDescending<DeviceBandwidthInfo> { it.isLocal }.thenBy { it.hostname })
            val blocked = all.filter { !it.internetAccessEnabled }
            _state.update {
                it.copy(
                    devices = online,
                    blacklisted = blocked,
                    isLoading = false,
                    error = null,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Could not load devices: ${e.message}",
                )
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadDevices() }
    }

    /** Stage a download/upload limit change for a device (not sent to router yet). */
    fun stageLimit(mac: String, downloadLimitRaw: Float, uploadLimitRaw: Float) {
        _state.update {
            it.copy(
                pendingChanges = it.pendingChanges + (mac to BandwidthChange(downloadLimitRaw, uploadLimitRaw))
            )
        }
    }

    /** Apply all staged limit changes to the router — sends ALL devices in one batch request. */
    fun applyChanges() {
        val changes = _state.value.pendingChanges
        if (changes.isEmpty()) {
            _state.update { it.copy(snackbarMessage = "No changes to apply") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }

            // Merge all devices with all pending changes.
            val allDevices = (_state.value.devices + _state.value.blacklisted).map { device ->
                val pending = changes[device.macAddress]
                if (pending != null) {
                    device.copy(
                        downloadLimitRaw = pending.downloadLimitRaw,
                        uploadLimitRaw = pending.uploadLimitRaw,
                    )
                } else device
            }

            val ok = repository.applyAllBandwidthSettings(allDevices)
            _state.update {
                it.copy(
                    isSaving = false,
                    pendingChanges = emptyMap(),
                    snackbarMessage = if (ok)
                        "✅ Limits applied to ${changes.size} device(s)"
                    else
                        "❌ Failed to apply limits. Check router connection.",
                )
            }
            delay(500)
            loadDevices()
        }
    }

    /** Discard all staged changes. */
    fun cancelChanges() {
        _state.update { it.copy(pendingChanges = emptyMap()) }
    }

    /** Immediately toggle internet access for a device at the router level. */
    fun toggleInternetAccess(mac: String, enabled: Boolean) {
        val target = (_state.value.devices + _state.value.blacklisted)
            .firstOrNull { it.macAddress.equals(mac, ignoreCase = true) }
        if (target?.isLocal == true && !enabled) {
            _state.update { it.copy(snackbarMessage = "⚠️ Cannot disable internet for this device (Local)") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val allDevices = (_state.value.devices + _state.value.blacklisted).map { device ->
                if (device.macAddress.equals(mac, ignoreCase = true)) {
                    device.copy(internetAccessEnabled = enabled)
                } else device
            }
            val ok = repository.applyAllBandwidthSettings(allDevices)
            _state.update {
                it.copy(
                    isSaving = false,
                    snackbarMessage = when {
                        ok && enabled -> "✅ Internet access restored"
                        ok && !enabled -> "🚫 Internet access blocked"
                        else -> "❌ Action failed. Check router connection."
                    }
                )
            }
            delay(500)
            loadDevices()
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    // ── Timed Block Actions ───────────────────────────────────────────────────

    fun openTimedBlockDialog(device: DeviceBandwidthInfo) {
        if (device.isLocal) return
        _state.update { it.copy(timedBlockTargetDevice = device) }
    }

    fun closeTimedBlockDialog() {
        _state.update { it.copy(timedBlockTargetDevice = null) }
    }

    fun setTimedBlock(durationMinutes: Long) {
        val target = _state.value.timedBlockTargetDevice ?: return
        closeTimedBlockDialog()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val ok = repository.setTimedBlock(
                mac = target.macAddress,
                deviceName = target.hostname,
                ipAddress = target.ipAddress,
                durationMinutes = durationMinutes,
            )
            _state.update {
                it.copy(
                    isSaving = false,
                    snackbarMessage = if (ok) "⏱️ Block timer set for ${target.hostname} ($durationMinutes min)"
                    else "❌ Failed to set block timer"
                )
            }
            delay(500)
            loadDevices()
        }
    }

    fun cancelTimedBlock(mac: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val ok = repository.cancelTimedBlock(mac)
            _state.update {
                it.copy(
                    isSaving = false,
                    snackbarMessage = if (ok) "✅ Timer cancelled & internet restored"
                    else "❌ Failed to cancel timer"
                )
            }
            delay(500)
            loadDevices()
        }
    }
}
