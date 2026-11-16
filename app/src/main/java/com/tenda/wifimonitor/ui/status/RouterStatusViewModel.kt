package com.tenda.wifimonitor.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.network.RouterFullStatus
import com.tenda.wifimonitor.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouterStatusUiState(
    val status: RouterFullStatus? = null,
    val isLoading: Boolean = true,
    val isConnected: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L,
)

@HiltViewModel
class RouterStatusViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(RouterStatusUiState())
    val state = _state.asStateFlow()

    companion object {
        /** Poll interval while the Status screen is visible */
        private const val POLL_INTERVAL_MS = 3_000L
    }

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                fetchStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchStatus() {
        // Ensure we're logged in before fetching
        val password = preferences.routerPassword.firstOrNull() ?: ""
        if (password.isNotBlank()) {
            repository.login(password) // re-use cached session; only logs in if needed
        }

        try {
            val result = repository.getFullRouterStatus()
            _state.update {
                it.copy(
                    status = result,
                    isLoading = false,
                    isConnected = result?.isConnected ?: false,
                    error = if (result == null) "Unable to reach router" else null,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Connection error: ${e.message}",
                )
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { fetchStatus() }
    }
}
