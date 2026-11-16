package com.tenda.wifimonitor.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.service.NetworkMonitorWorker
import com.tenda.wifimonitor.service.NetworkScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class SetupUiState(
    val password: String = "",
    val routerUrl: String = "http://192.168.0.1",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    fun onPasswordChange(pw: String) {
        _uiState.value = _uiState.value.copy(password = pw, error = null)
    }

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(routerUrl = url)
    }

    fun connect() {
        val state = _uiState.value
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your router password")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val ok = repository.login(state.password)
                if (ok) {
                    preferences.setRouterPassword(state.password)
                    preferences.setRouterUrl(state.routerUrl)
                    preferences.setSetupComplete(true)
                    NetworkMonitorWorker.schedule(context)
                    NetworkScanService.start(context)
                    _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Login failed. Check your password and make sure you're on Tenda WiFi."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Cannot reach router at ${state.routerUrl}. Make sure you're connected to Tenda WiFi."
                )
            }
        }
    }
}
