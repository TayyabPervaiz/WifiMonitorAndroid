package com.tenda.wifimonitor.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.ui.components.DeviceIcon
import com.tenda.wifimonitor.ui.components.HeaderActionButton
import com.tenda.wifimonitor.ui.components.PulsingDot
import com.tenda.wifimonitor.ui.components.vectorIcon
import com.tenda.wifimonitor.ui.components.TimedBlockDialog
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
    val allDevices by viewModel.allDevices.collectAsState()
    val device = state.device
    val df = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    var showMergeDialog by remember { mutableStateOf(false) }
    var candidateToMerge by remember { mutableStateOf<NetworkDevice?>(null) }

    // Snackbar messages
    LaunchedEffect(state.blockSuccess, state.blockError) {
        if (state.blockSuccess != null || state.blockError != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissMessages()
        }
    }

    if (device == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // Timed block dialog
    if (state.showTimedBlockDialog) {
        TimedBlockDialog(
            deviceName = device.displayName,
            macAddress = device.macAddress,
            ipAddress = device.ipAddress,
            onDismiss = viewModel::closeTimedBlockDialog,
            onConfirm = { minutes ->
                viewModel.setTimedBlock(minutes)
            },
        )
    }

    // Icon picker dialog
    if (state.showIconPicker) {
        AlertDialog(
            onDismissRequest = viewModel::hideIconPicker,
            title = { Text("Choose Icon", color = MaterialTheme.colorScheme.onBackground) },
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }

    // Merge device picker dialog
    if (showMergeDialog) {
        val otherDevices = remember(allDevices, device.deviceId) {
            allDevices.filter { it.deviceId != device.deviceId }
        }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = {
                Text(
                    "Merge with Another Device",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (otherDevices.isEmpty()) {
                    Text(
                        "No other devices found to merge with.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(otherDevices, key = { it.deviceId }) { other ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        candidateToMerge = other
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (other.isOnline) MaterialTheme.colorScheme.primary.copy(0.2f) else AppColors.offline.copy(0.15f)
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        DeviceIcon(
                                            deviceType = other.deviceType,
                                            customIcon = other.customIcon,
                                            size = 20.dp,
                                            tint = if (other.isOnline) MaterialTheme.colorScheme.primary else AppColors.offline,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = other.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = other.ipAddress,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = other.macAddress,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (other.isOnline) {
                                        PulsingDot(color = MaterialTheme.colorScheme.primary, size = 6)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }

    // Merge confirmation dialog
    if (candidateToMerge != null) {
        val target = candidateToMerge!!
        AlertDialog(
            onDismissRequest = { candidateToMerge = null },
            title = { Text("Merge Device", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Merge into '${target.displayName}'? This device's history will be combined and this entry will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val keepId = target.deviceId
                        val mergeAwayId = device.deviceId
                        candidateToMerge = null
                        showMergeDialog = false
                        viewModel.mergeDevices(
                            keepDeviceId = keepId,
                            mergeAwayDeviceId = mergeAwayId,
                            onComplete = onBack,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Merge", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { candidateToMerge = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBrush())
                    .padding(horizontal = 20.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, bottom = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Back button
                    Row(Modifier.fillMaxWidth()) {
                        HeaderActionButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Device icon
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (device.isOnline) MaterialTheme.colorScheme.primary.copy(0.3f) else AppColors.offline.copy(0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(2.dp, if (device.isOnline) MaterialTheme.colorScheme.primary.copy(0.5f) else AppColors.offline.copy(0.3f), CircleShape)
                            .clickable { viewModel.showIconPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        DeviceIcon(
                            deviceType = device.deviceType,
                            customIcon = device.customIcon,
                            size = 44.dp,
                            tint = if (device.isOnline) MaterialTheme.colorScheme.primary else AppColors.offline,
                        )
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
                                        Icon(Icons.Default.Check, null, tint = AppColors.success)
                                    }
                                    IconButton(onClick = { viewModel.onNameChange(""); viewModel.saveCustomName() }) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
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
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = viewModel::startEditName, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Status badges
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (device.isBlocked) {
                            Surface(color = MaterialTheme.colorScheme.error.copy(0.2f), shape = RoundedCornerShape(20.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                    Text("Blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (device.isLocal) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PulsingDot(size = 6, color = MaterialTheme.colorScheme.primary)
                                    Text("Local (This Device)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (device.isOnline) {
                            Surface(color = AppColors.online.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PulsingDot(size = 6)
                                    Text("Online", style = MaterialTheme.typography.labelSmall, color = AppColors.online)
                                }
                            }
                        } else {
                            Surface(color = AppColors.offline.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("Offline", style = MaterialTheme.typography.labelSmall, color = AppColors.offline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (!device.isWhitelisted) {
                            Surface(color = MaterialTheme.colorScheme.error.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("Unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        } else {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("✓ Trusted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
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
                        .background(if (isError) MaterialTheme.colorScheme.error.copy(0.15f) else AppColors.success.copy(0.15f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        null,
                        tint = if (isError) MaterialTheme.colorScheme.error else AppColors.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        state.blockError ?: state.blockSuccess ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else AppColors.success,
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Device Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                InfoRow("IP Address", device.ipAddress, isMonospace = true)
                InfoRow("MAC Address", device.macAddress, isMonospace = true)
                InfoRow("Manufacturer", device.manufacturer)
                InfoRow("Device Type", device.deviceType.label)
                InfoRow("First Seen", df.format(device.firstSeen))
                InfoRow("Last Seen", df.format(device.lastSeen))
            }
        }

        // ── Device Type Override ────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Device Type", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
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
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(0.2f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.clickable { viewModel.setDeviceType(type) }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = type.vectorIcon(),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(type.label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Actions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

                if (!device.isLocal) {
                    val timedBlock = state.timedBlock
                    if (timedBlock != null) {
                        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(timedBlock) {
                            while (!timedBlock.isExpired) {
                                kotlinx.coroutines.delay(1000L)
                                now = System.currentTimeMillis()
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Timed Block Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Auto-unblock in: ${timedBlock.formattedRemaining}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                Button(
                                    onClick = { viewModel.cancelTimedBlock() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    enabled = !state.isBlockLoading,
                                ) {
                                    Text(
                                        "Cancel Timer",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Whitelist toggle
                        if (!device.isWhitelisted) {
                            Button(
                                onClick = { viewModel.whitelist() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Untrust", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Block / Unblock button
                        if (device.isBlocked) {
                            Button(
                                onClick = { viewModel.unblockDevice() },
                                enabled = !state.isBlockLoading,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (state.isBlockLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Unblock", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.blockDevice() },
                                enabled = !state.isBlockLoading,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (state.isBlockLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Block", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Timed Block button (when not already timed-blocked)
                    if (timedBlock == null) {
                        Button(
                            onClick = { viewModel.openTimedBlockDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Set Timed Block…", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                "This is your current device. It is trusted and cannot be blocked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.showIconPicker() },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change Icon", color = MaterialTheme.colorScheme.primary)
                }

                OutlinedButton(
                    onClick = { showMergeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Merge with another device", color = MaterialTheme.colorScheme.primary)
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Connection History (${state.sessions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )

                if (state.sessions.isEmpty()) {
                    Text("No history recorded yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (session.isActive) "Still connected" else "Duration: ${session.formattedDuration}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (session.isActive) AppColors.online else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                color = if (session.isActive) AppColors.online.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    session.ipAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (session.isActive) AppColors.online else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (isMonospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}
