package com.tenda.wifimonitor.ui.detail

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.ui.components.PulsingDot
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

val ICON_EMOJIS = listOf("📱","💻","🖥️","📟","📺","🎮","🔌","📡","🖨️","⌨️","🖱️","📷","🎵","🏠","💡","🔒","🌡️","⌚","🤖","👾")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val device = state.device
    val df = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    // Snackbar messages
    LaunchedEffect(state.blockSuccess, state.blockError) {
        if (state.blockSuccess != null || state.blockError != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissMessages()
        }
    }

    if (device == null) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    // Icon picker dialog
    if (state.showIconPicker) {
        AlertDialog(
            onDismissRequest = viewModel::hideIconPicker,
            title = { Text("Choose Icon", color = OnBackground) },
            text = {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    modifier = Modifier.height(200.dp),
                ) {
                    items(ICON_EMOJIS.size) { i ->
                        Text(
                            ICON_EMOJIS[i],
                            fontSize = 32.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.setIcon(ICON_EMOJIS[i]) }
                                .padding(4.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            containerColor = SurfaceCard,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0E1830), Background))
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = 52.dp, bottom = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Back button
                    Row(Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = OnSurfaceVariant)
                        }
                    }

                    // Device icon
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (device.isOnline) Primary.copy(0.3f) else DeviceOffline.copy(0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(2.dp, if (device.isOnline) Primary.copy(0.5f) else DeviceOffline.copy(0.3f), CircleShape)
                            .clickable { viewModel.showIconPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(device.displayIcon, fontSize = 44.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Device name (editable)
                    if (state.isEditingName) {
                        OutlinedTextField(
                            value = state.editName,
                            onValueChange = viewModel::onNameChange,
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = viewModel::saveCustomName) {
                                        Icon(Icons.Default.Check, null, tint = Success)
                                    }
                                    IconButton(onClick = { viewModel.onNameChange(""); viewModel.saveCustomName() }) {
                                        Icon(Icons.Default.Close, null, tint = Danger)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = SurfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(0.8f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                device.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = OnBackground,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = viewModel::startEditName, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Status badges
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (device.isOnline) {
                            Surface(color = DeviceOnline.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PulsingDot(size = 6)
                                    Text("Online", style = MaterialTheme.typography.labelSmall, color = DeviceOnline)
                                }
                            }
                        } else {
                            Surface(color = DeviceOffline.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("Offline", style = MaterialTheme.typography.labelSmall, color = DeviceOffline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (!device.isWhitelisted) {
                            Surface(color = Danger.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("Unknown", style = MaterialTheme.typography.labelSmall, color = Danger,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        } else {
                            Surface(color = Primary.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("✓ Trusted", style = MaterialTheme.typography.labelSmall, color = Primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Alert messages ──────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = state.blockSuccess != null || state.blockError != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                val isError = state.blockError != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isError) Danger.copy(0.15f) else Success.copy(0.15f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        null,
                        tint = if (isError) Danger else Success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        state.blockError ?: state.blockSuccess ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) Danger else Success,
                    )
                }
            }
        }

        // ── Info section ────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Device Info", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                InfoRow("IP Address", device.ipAddress, isMonospace = true)
                InfoRow("MAC Address", device.macAddress, isMonospace = true)
                InfoRow("Manufacturer", device.manufacturer)
                InfoRow("Device Type", device.deviceType.label)
                InfoRow("First Seen", df.format(device.firstSeen))
                InfoRow("Last Seen", df.format(device.lastSeen))
                if (device.signalStrength != null) {
                    InfoRow("Ping", "${device.signalStrength}ms")
                }
            }
        }

        // ── Device Type Override ────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Device Type", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(DeviceType.entries.size) { i ->
                        val type = DeviceType.entries[i]
                        val selected = device.deviceType == type
                        Surface(
                            color = if (selected) Primary.copy(0.2f) else Surface,
                            shape = RoundedCornerShape(10.dp),
                            border = if (selected) BorderStroke(1.dp, Primary) else BorderStroke(1.dp, SurfaceVariant),
                            modifier = Modifier.clickable { viewModel.setDeviceType(type) }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(type.defaultEmoji, fontSize = 20.sp)
                                Text(type.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Actions ──────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Actions", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Whitelist toggle
                    if (!device.isWhitelisted) {
                        Button(
                            onClick = { viewModel.whitelist() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Trust", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.removeWhitelist() },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, OnSurfaceVariant.copy(0.4f)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.RemoveCircle, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Untrust", color = OnSurfaceVariant)
                        }
                    }

                    // Block button
                    Button(
                        onClick = { viewModel.blockDevice() },
                        enabled = !state.isBlockLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger.copy(0.8f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.isBlockLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Block", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.showIconPicker() },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Primary.copy(0.4f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Change Icon ${device.displayIcon}", color = Primary)
                }
            }
        }

        // ── Session History ──────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Connection History (${state.sessions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                )

                if (state.sessions.isEmpty()) {
                    Text("No history recorded yet", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                } else {
                    state.sessions.take(10).forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    df.format(session.connectedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurface,
                                )
                                Text(
                                    if (session.isActive) "Still connected" else "Duration: ${session.formattedDuration}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (session.isActive) DeviceOnline else OnSurfaceVariant,
                                )
                            }
                            Surface(
                                color = if (session.isActive) DeviceOnline.copy(0.15f) else SurfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    session.ipAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (session.isActive) DeviceOnline else OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        Text(
            value,
            style = if (isMonospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            color = OnBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}
