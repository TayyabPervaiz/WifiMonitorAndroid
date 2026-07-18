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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.DeviceSession
import com.tenda.wifimonitor.ui.components.EmptyState
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeviceHistoryScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Stable remembered formatters
    val dayFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val headerGradient = remember { Brush.verticalGradient(listOf(Color(0xFF0E1830), Background)) }

    // Group sessions by Device (MAC Address)
    val groupedByDevice: List<Pair<DeviceSession, List<DeviceSession>>> = remember(state.filteredSessions) {
        state.filteredSessions
            .groupBy { it.macAddress }
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

    val expandedMacs = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(horizontal = 20.dp)
                .padding(top = 52.dp, bottom = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Connection History",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(color = SurfaceCard, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "${groupedByDevice.size} devices",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // ── Search Bar ────────────────────────────────────────────────
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearch,
                    placeholder = {
                        Text("Search by name, MAC or IP…", color = OnSurfaceVariant.copy(0.5f))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = OnSurfaceVariant)
                    },
                    trailingIcon = if (state.searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Default.Clear, null, tint = OnSurfaceVariant)
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                    ),
                    singleLine = true,
                )
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            state.sessions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = "📋",
                        title = "No history yet",
                        subtitle = "Connection events will appear here as devices join and leave your network",
                    )
                }
            }
            state.filteredSessions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = "🔍",
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
                        key = { it.first.macAddress }
                    ) { (latestSession, sessions) ->
                        val isExpanded = expandedMacs.contains(latestSession.macAddress)
                        
                        DeviceHistoryAccordionCard(
                            latestSession = latestSession,
                            sessions = sessions,
                            isExpanded = isExpanded,
                            dayFormatter = dayFormatter,
                            timeFormatter = timeFormatter,
                            onToggleExpand = {
                                if (isExpanded) expandedMacs.remove(latestSession.macAddress)
                                else expandedMacs.add(latestSession.macAddress)
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
            .background(SurfaceCard)
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
            // Device Type Emoji Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant.copy(alpha = 0.5f))
                    .clickable { onNavigateToDetail() }, // Click icon to go to detail
                contentAlignment = Alignment.Center,
            ) {
                Text("📱", fontSize = 22.sp)
            }

            // Name / MAC
            Column(Modifier.weight(1f)) {
                Text(
                    latestSession.hostname.takeIf { it.isNotBlank() && it != "Unknown" }
                        ?: latestSession.macAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    latestSession.macAddress,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right-side Status & Chevron
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (latestSession.isActive) {
                    Surface(color = DeviceOnline.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "Active Now",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeviceOnline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${sessions.size} sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = OnSurfaceVariant.copy(0.7f),
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
                HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Sessions",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    TextButton(
                        onClick = onNavigateToDetail,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("View Device Info", fontSize = 12.sp, color = Primary)
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
                    .background(if (session.isActive) DeviceOnline else Primary.copy(0.5f))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp) // Approximate height for the connector
                        .background(SurfaceVariant)
                )
            }
        }

        // Session details
        Column(Modifier.weight(1f)) {
            Text(
                dayFormatter.format(session.connectedAt),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "⬆ ${timeFormatter.format(session.connectedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeviceOnline.copy(0.9f),
                )
                if (session.disconnectedAt != null) {
                    Text(
                        "⬇ ${timeFormatter.format(session.disconnectedAt!!)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeviceOffline.copy(0.9f),
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
                    color = DeviceOnline,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    session.formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
            Text(
                session.ipAddress,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = OnSurfaceVariant.copy(0.6f),
                fontSize = 10.sp,
            )
        }
    }
}
