package com.tenda.wifimonitor.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.ui.components.CardSurface
import com.tenda.wifimonitor.ui.components.DeviceCard
import com.tenda.wifimonitor.ui.components.DeviceCardSkeleton
import com.tenda.wifimonitor.ui.components.EmptyState
import com.tenda.wifimonitor.ui.components.HeaderActionButton
import com.tenda.wifimonitor.ui.components.PulsingDot
import com.tenda.wifimonitor.ui.components.ScreenHeader
import com.tenda.wifimonitor.ui.components.SectionHeader
import com.tenda.wifimonitor.ui.components.StatCard
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onDeviceClick: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToBandwidth: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val filteredDevices by viewModel.filteredDevices.collectAsState()

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isScanning,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // ── Header ───────────────────────────────────────────────────
                item {
                    ScreenHeader(
                        title = "Network Monitor",
                        subtitle = "${state.allDevices.size} device${if (state.allDevices.size != 1) "s" else ""} on your network",
                        actions = {
                            HeaderActionButton(
                                icon = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                onClick = viewModel::refresh,
                                loading = state.isScanning,
                            )
                            HeaderActionButton(
                                icon = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                onClick = onNavigateToSettings,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                // ── KPI row ──────────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCard(
                            label = "Online",
                            value = animateCount(state.onlineCount),
                            icon = Icons.Filled.Devices,
                            tint = AppColors.online,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Unknown",
                            value = animateCount(state.unknownCount),
                            icon = Icons.Filled.WarningAmber,
                            tint = if (state.unknownCount > 0) MaterialTheme.colorScheme.error else AppColors.offline,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Total Seen",
                            value = animateCount(state.allDevices.size),
                            icon = Icons.Filled.History,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Router info card ─────────────────────────────────────────
                item {
                    CardSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Router,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "WAN IP: ${state.wanIp}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Uptime: ${state.routerUptime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.downloadSpeedKbps > 0f || state.uploadSpeedKbps > 0f) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "↓ ${formatDashSpeed(state.downloadSpeedKbps)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = AppColors.online,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "↑ ${formatDashSpeed(state.uploadSpeedKbps)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                if (state.lastScanTime > 0) {
                                    Text(
                                        "Last scan: ${timeFormatter.format(Date(state.lastScanTime))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PulsingDot(color = MaterialTheme.colorScheme.primary, size = 8)
                                Spacer(Modifier.height(4.dp))
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ── Quick actions ────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionButton(
                            icon = Icons.Filled.Router,
                            label = "Status",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateToStatus,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            icon = Icons.Filled.SwapVert,
                            label = "Bandwidth",
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = onNavigateToBandwidth,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            icon = Icons.Filled.History,
                            label = "History",
                            tint = AppColors.purple,
                            onClick = onNavigateToHistory,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Search & filter ──────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search devices…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = if (state.searchQuery.isNotBlank()) {
                                {
                                    IconButton(onClick = { viewModel.setSearch("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            singleLine = true,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DeviceFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.filter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.filter == filter,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                )
                            }
                        }
                    }
                }

                // ── Device list ──────────────────────────────────────────────
                item {
                    SectionHeader(
                        title = "Devices",
                        subtitle = "${filteredDevices.size} shown",
                        action = if (state.isScanning) "Scanning…" else null,
                        onAction = {},
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                if (filteredDevices.isEmpty() && state.isScanning) {
                    items(count = 4, key = { "skeleton_$it" }) {
                        DeviceCardSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                } else if (filteredDevices.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.WifiTethering,
                            title = "No devices found",
                            subtitle = "Pull to refresh or check your WiFi connection",
                        )
                    }
                } else {
                    itemsIndexed(
                        items = filteredDevices,
                        key = { _, device -> device.macAddress },
                    ) { index, device ->
                        StaggeredItem(index = index) {
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.macAddress) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardSurface(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fades + slides a list item in, staggered by its index, on first composition. */
@Composable
private fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val delay = (index % 10) * 35
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, delayMillis = delay),
        label = "item_alpha",
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = tween(durationMillis = 320, delayMillis = delay),
        label = "item_offset",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        },
    ) {
        content()
    }
}

/** Animates an integer stat to its new value and renders it as a string. */
@Composable
private fun animateCount(target: Int): String {
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "count",
    )
    return animated.toString()
}

/** Format a raw Kbps float value to a human-readable speed string. */
private fun formatDashSpeed(kbps: Float): String {
    return if (kbps >= 1024f) "%.2f Mbps".format(kbps / 1024f)
    else "%.0f Kbps".format(kbps)
}
