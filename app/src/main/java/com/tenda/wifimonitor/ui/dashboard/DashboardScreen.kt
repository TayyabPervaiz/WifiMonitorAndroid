package com.tenda.wifimonitor.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.ui.components.*
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onDeviceClick: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val filteredDevices by viewModel.filteredDevices.collectAsState()

    val headerGradient = remember {
        Brush.verticalGradient(listOf(Color(0xFF0E1830), Background))
    }
    
    val routerCardGradient = remember {
        Brush.linearGradient(listOf(Color(0xFF112240), Color(0xFF0D1B35)))
    }
    val routerCardBorder = remember {
        Brush.linearGradient(listOf(Primary.copy(0.3f), Color.Transparent))
    }
    
    val intruderBannerGradient = remember {
        Brush.linearGradient(listOf(Danger.copy(0.2f), Color(0xFF2A0D15)))
    }
    
    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerGradient)
                        .padding(horizontal = 20.dp)
                        .padding(top = 52.dp, bottom = 8.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Tenda Monitor",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = OnBackground,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    "Wireless N300 · F3",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Primary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = viewModel::refresh,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = SurfaceVariant,
                                    )
                                ) {
                                    if (state.isScanning) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            color = Primary, strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Refresh, null, tint = Primary)
                                    }
                                }
                                IconButton(
                                    onClick = onNavigateToSettings,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = SurfaceVariant,
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, null, tint = OnSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // ── Stats Row ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatChip(
                        label = "Online",
                        value = state.onlineCount.toString(),
                        icon = Icons.Default.Devices,
                        tint = DeviceOnline,
                        modifier = Modifier.weight(1f),
                    )
                    StatChip(
                        label = "Unknown",
                        value = state.unknownCount.toString(),
                        icon = Icons.Default.Warning,
                        tint = if (state.unknownCount > 0) Danger else DeviceOffline,
                        modifier = Modifier.weight(1f),
                    )
                    StatChip(
                        label = "Total Seen",
                        value = state.allDevices.size.toString(),
                        icon = Icons.Default.History,
                        tint = Accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Router Info Card ──────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(routerCardGradient)
                        .border(
                            1.dp,
                            routerCardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 32.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "WAN IP: ${state.wanIp}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = OnSurfaceVariant,
                            )
                            Text(
                                "Uptime: ${state.routerUptime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                            if (state.lastScanTime > 0) {
                                Text(
                                    "Last scan: ${timeFormatter.format(Date(state.lastScanTime))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant.copy(0.6f),
                                )
                            }
                        }
                        // Online status indicator
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PulsingDot(color = Primary, size = 8)
                            Spacer(Modifier.height(4.dp))
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                }
            }

            // ── Quick Actions ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionButton(
                        icon = "📊",
                        label = "Statistics",
                        onClick = onNavigateToStats,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                        icon = "🗺️",
                        label = "Network Map",
                        onClick = onNavigateToMap,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                        icon = "📋",
                        label = "History",
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Intruder Banner ───────────────────────────────────────────────
            if (state.unknownCount > 0) {
                item {
                    AnimatedVisibility(visible = true, enter = slideInVertically() + fadeIn()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(intruderBannerGradient)
                                .border(1.dp, Danger.copy(0.4f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                                .clickable { viewModel.setFilter(DeviceFilter.UNKNOWN) }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🚨", fontSize = 24.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${state.unknownCount} Unknown Device${if (state.unknownCount > 1) "s" else ""} Detected",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Danger,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Tap to review and whitelist or block",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Danger.copy(0.7f),
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Danger)
                            }
                        }
                    }
                }
            }

            // ── Search & Filter ───────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearch,
                        placeholder = { Text("Search devices…", color = OnSurfaceVariant.copy(0.5f)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = OnSurfaceVariant)
                        },
                        trailingIcon = if (state.searchQuery.isNotBlank()) {
                            { IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Default.Clear, null, tint = OnSurfaceVariant)
                            }}
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

                    // Filter chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(0.2f),
                                    selectedLabelColor = Primary,
                                    containerColor = SurfaceCard,
                                    labelColor = OnSurfaceVariant,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.filter == filter,
                                    selectedBorderColor = Primary,
                                    borderColor = Color.Transparent,
                                )
                            )
                        }
                    }
                }
            }

            // ── Device List ───────────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "${filteredDevices.size} Device${if (filteredDevices.size != 1) "s" else ""}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (filteredDevices.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (state.isScanning) "🔍" else "📡",
                        title = if (state.isScanning) "Scanning…" else "No devices found",
                        subtitle = if (state.isScanning) "Looking for devices on your network"
                        else "Pull to refresh or check your WiFi connection",
                    )
                }
            } else {
                items(
                    items = filteredDevices,
                    key = { it.macAddress }
                ) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.macAddress) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
    }
}
