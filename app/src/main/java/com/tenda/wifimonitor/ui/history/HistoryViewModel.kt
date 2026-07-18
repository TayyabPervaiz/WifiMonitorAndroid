package com.tenda.wifimonitor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.domain.model.DeviceSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<DeviceSession> = emptyList(),
    val filteredSessions: List<DeviceSession> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllSessions().collect { sessions ->
                val query = _state.value.searchQuery
                _state.update { it.copy(
                    sessions = sessions,
                    filteredSessions = filterSessions(sessions, query),
                    isLoading = false,
                )}
            }
        }
    }

    fun setSearch(query: String) {
        val filtered = filterSessions(_state.value.sessions, query)
        _state.update { it.copy(searchQuery = query, filteredSessions = filtered) }
    }

    private fun filterSessions(sessions: List<DeviceSession>, query: String): List<DeviceSession> {
        if (query.isBlank()) return sessions
        val q = query.lowercase()
        return sessions.filter {
            it.hostname.contains(q, ignoreCase = true) ||
            it.macAddress.contains(q, ignoreCase = true) ||
            it.ipAddress.contains(q)
        }
    }
}
