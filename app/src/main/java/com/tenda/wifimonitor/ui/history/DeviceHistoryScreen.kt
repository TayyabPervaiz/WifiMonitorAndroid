package com.tenda.wifimonitor.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.DeviceSession
import com.tenda.wifimonitor.ui.components.EmptyState
import com.tenda.wifimonitor.ui.components.ScreenHeader
import com.tenda.wifimonitor.ui.components.SearchField
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeviceHistoryScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit,
    showBack: Boolean = true,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Stable remembered formatters
    val dayFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    // Group sessions by Device (deviceId falling back to macAddress)
    val groupedByDevice: List<Pair<DeviceSession, List<DeviceSession>>> = remember(state.filteredSessions) {
        state.filteredSessions
            .groupBy { it.deviceId ?: it.macAddress }
            .entries
            .map { (_, sessions) ->
                // Sort sessions for each device from newest to oldest
                val sortedSessions = sessions.sortedByDescending { it.connectedAt }
                // Use the most recent session's info for the header card
                sortedSessions.first() to sortedSessions
            }
            // Sort devices by their most recent activity
            .sortedByDescending { it.first.connectedAt }
    }

    val expandedDeviceKeys = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        ScreenHeader(
            title = "Connection History",
            showBack = showBack,
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${groupedByDevice.size} devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            footer = {
                SearchField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearch,
                    placeholder = "Search by name, MAC or IP…",
                )
            },
        )

        // ── Content ───────────────────────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.sessions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.History,
                        title = "No history yet",
                        subtitle = "Connection events will appear here as devices join and leave your network",
                    )
                }
            }
            state.filteredSessions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "No matches",
                        subtitle = "Try a different name, MAC address, or IP",
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = groupedByDevice,
                        key = { it.first.deviceId ?: it.first.macAddress }
                    ) { (latestSession, sessions) ->
                        val deviceKey = latestSession.deviceId ?: latestSession.macAddress
                        val isExpanded = expandedDeviceKeys.contains(deviceKey)
                        
                        DeviceHistoryAccordionCard(
                            latestSession = latestSession,
                            sessions = sessions,
                            isExpanded = isExpanded,
                            dayFormatter = dayFormatter,
                            timeFormatter = timeFormatter,
                            onToggleExpand = {
                                if (isExpanded) expandedDeviceKeys.remove(deviceKey)
                                else expandedDeviceKeys.add(deviceKey)
                            },
                            onNavigateToDetail = { onDeviceClick(latestSession.macAddress) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DeviceHistoryAccordionCard(
    latestSession: DeviceSession,
    sessions: List<DeviceSession>,
    isExpanded: Boolean,
    dayFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    onToggleExpand: () -> Unit,
    onNavigateToDetail: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize()
    ) {
        // ── Header Row (Always visible) ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Device type badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable { onNavigateToDetail() }, // Click icon to go to detail
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Name / MAC
            Column(Modifier.weight(1f)) {
                Text(
                    latestSession.hostname.takeIf { it.isNotBlank() && it != "Unknown" }
                        ?: latestSession.macAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    latestSession.macAddress,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right-side Status & Chevron
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (latestSession.isActive) {
                    Surface(color = AppColors.online.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "Active Now",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.online,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${sessions.size} sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }

        // ── Expanded Content (Sessions List) ──────────────────────────────────
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Sessions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    TextButton(
                        onClick = onNavigateToDetail,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            "View Device Info",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                sessions.forEachIndexed { index, session ->
                    SessionRow(
                        session = session,
                        dayFormatter = dayFormatter,
                        timeFormatter = timeFormatter,
                        isLast = index == sessions.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: DeviceSession,
    dayFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Timeline connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (session.isActive) AppColors.online else MaterialTheme.colorScheme.primary.copy(0.5f))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp) // Approximate height for the connector
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        // Session details
        Column(Modifier.weight(1f)) {
            Text(
                dayFormatter.format(session.connectedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = AppColors.online,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = timeFormatter.format(session.connectedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.online,
                )
                if (session.disconnectedAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = AppColors.offline,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = timeFormatter.format(session.disconnectedAt!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.offline,
                    )
                }
            }
        }

        // Duration / IP
        Column(horizontalAlignment = Alignment.End) {
            if (session.isActive) {
                Text(
                    "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.online,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    session.formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                session.ipAddress,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
