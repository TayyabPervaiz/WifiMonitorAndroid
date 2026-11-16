package com.tenda.wifimonitor.ui.bandwidth

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.data.network.DeviceBandwidthInfo
import com.tenda.wifimonitor.domain.model.TimedBlock
import com.tenda.wifimonitor.ui.components.HeaderActionButton
import com.tenda.wifimonitor.ui.components.InlineBanner
import com.tenda.wifimonitor.ui.components.ScreenHeader
import com.tenda.wifimonitor.ui.components.TimedBlockDialog
import com.tenda.wifimonitor.ui.theme.*

// ── Speed limit options (label → raw router value, 38528 = no limit) ─────────
// Matching Tenda router web UI net-control.js: raw = (mbps * 128)
private val DOWNLOAD_LIMIT_OPTIONS = listOf(
    "No limit" to DeviceBandwidthInfo.NO_LIMIT_RAW,
    "1 Mbps (Web)" to 128f,
    "2 Mbps (SD Videos)" to 256f,
    "4 Mbps (HD Videos)" to 512f,
    "8 Mbps" to 1024f,
    "16 Mbps" to 2048f,
)

private val UPLOAD_LIMIT_OPTIONS = listOf(
    "No limit" to DeviceBandwidthInfo.NO_LIMIT_RAW,
    "0.25 Mbps" to 32f,
    "0.5 Mbps" to 64f,
    "1 Mbps" to 128f,
    "2 Mbps" to 256f,
    "4 Mbps" to 512f,
)

private fun rawToDownloadLabel(raw: Float): String =
    DOWNLOAD_LIMIT_OPTIONS.firstOrNull { it.second == raw }?.first
        ?: DeviceBandwidthInfo.formatLimit(raw)

private fun rawToUploadLabel(raw: Float): String =
    UPLOAD_LIMIT_OPTIONS.firstOrNull { it.second == raw }?.first
        ?: DeviceBandwidthInfo.formatLimit(raw)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandwidthControlScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit,
    showBack: Boolean = true,
    viewModel: BandwidthControlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    // Timed block dialog
    val timedBlockTarget = state.timedBlockTargetDevice
    if (timedBlockTarget != null) {
        TimedBlockDialog(
            deviceName = timedBlockTarget.hostname,
            macAddress = timedBlockTarget.macAddress,
            ipAddress = timedBlockTarget.ipAddress,
            onDismiss = viewModel::closeTimedBlockDialog,
            onConfirm = { minutes ->
                viewModel.setTimedBlock(minutes)
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        bottomBar = {
            // Show Apply/Cancel only when there are pending changes
            AnimatedVisibility(
                visible = state.pendingChanges.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::cancelChanges,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = viewModel::applyChanges,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                "Apply ${state.pendingChanges.size} Change(s)",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            item {
                ScreenHeader(
                    title = "Bandwidth Control",
                    subtitle = "Set limits & control internet access per device",
                    showBack = showBack,
                    onBack = onBack,
                    actions = {
                        HeaderActionButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            onClick = viewModel::refresh,
                            loading = state.isLoading,
                        )
                    },
                )
            }

            // ── Error banner ───────────────────────────────────────────────────
            if (state.error != null) {
                item {
                    InlineBanner(
                        message = state.error ?: "",
                        icon = Icons.Default.Warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }

            // ── Online Devices section ─────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Online Devices (${state.devices.size})",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (state.devices.isEmpty() && !state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No online devices found.\nMake sure you are connected to the Tenda network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            items(state.devices, key = { it.macAddress }) { device ->
                val pending = state.pendingChanges[device.macAddress]
                BandwidthDeviceRow(
                    device = device,
                    pendingChange = pending,
                    onLimitChange = { dl, ul -> viewModel.stageLimit(device.macAddress, dl, ul) },
                    onInternetToggle = { enabled ->
                        viewModel.toggleInternetAccess(device.macAddress, enabled)
                    },
                    onTimedBlockClick = { viewModel.openTimedBlockDialog(device) },
                    onDeviceClick = { onDeviceClick(device.macAddress) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .animateItem(),
                )
            }

            // ── Blacklisted Devices section ────────────────────────────────────
            item {
                SectionHeader(
                    title = "Blacklisted Devices (${state.blacklisted.size})",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (state.blacklisted.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.blacklisted, key = { "bl_${it.macAddress}" }) { device ->
                val timedBlock = state.timedBlocks[device.macAddress]
                BlacklistedDeviceRow(
                    device = device,
                    timedBlock = timedBlock,
                    onRestore = { viewModel.toggleInternetAccess(device.macAddress, true) },
                    onCancelTimer = { viewModel.cancelTimedBlock(device.macAddress) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .animateItem(),
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── Device Row ────────────────────────────────────────────────────────────────

@Composable
private fun BandwidthDeviceRow(
    device: DeviceBandwidthInfo,
    pendingChange: BandwidthChange?,
    onLimitChange: (Float, Float) -> Unit,
    onInternetToggle: (Boolean) -> Unit,
    onTimedBlockClick: () -> Unit,
    onDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPending = pendingChange != null
    val currentDlLimit = pendingChange?.downloadLimitRaw ?: device.downloadLimitRaw
    val currentUlLimit = pendingChange?.uploadLimitRaw ?: device.uploadLimitRaw

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                if (hasPending) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row: device info + internet toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Device icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable { onDeviceClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = connectionTypeIcon(device.connectionType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Name + IP + MAC
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            device.hostname,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (device.isLocal) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        device.ipAddress,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        device.macAddress,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Internet access toggle or Local badge
                if (device.isLocal) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            "Local",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = onTimedBlockClick,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Timed Block",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = device.internetAccessEnabled,
                                onCheckedChange = onInternetToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = AppColors.online,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                            Text(
                                if (device.internetAccessEnabled) "ON" else "Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (device.internetAccessEnabled) AppColors.online else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Speed row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedChip(
                    label = "↓",
                    value = DeviceBandwidthInfo.formatSpeed(device.downloadSpeedRaw),
                    color = AppColors.online,
                    modifier = Modifier.weight(1f),
                )
                SpeedChip(
                    label = "↑",
                    value = DeviceBandwidthInfo.formatSpeed(device.uploadSpeedRaw),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)

            // Limit selectors row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitDropdown(
                    label = "↓ Download Limit",
                    selectedRaw = currentDlLimit,
                    options = DOWNLOAD_LIMIT_OPTIONS,
                    rawToLabel = ::rawToDownloadLabel,
                    onSelected = { dl -> onLimitChange(dl, currentUlLimit) },
                    modifier = Modifier.weight(1f),
                )
                LimitDropdown(
                    label = "↑ Upload Limit",
                    selectedRaw = currentUlLimit,
                    options = UPLOAD_LIMIT_OPTIONS,
                    rawToLabel = ::rawToUploadLabel,
                    onSelected = { ul -> onLimitChange(currentDlLimit, ul) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Pending indicator
            AnimatedVisibility(visible = hasPending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "Unsaved changes — tap Apply to send to router",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlacklistedDeviceRow(
    device: DeviceBandwidthInfo,
    timedBlock: TimedBlock?,
    onRestore: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timedBlock) {
        while (timedBlock != null && !timedBlock.isExpired) {
            kotlinx.coroutines.delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                if (timedBlock != null) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Blocked icon
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (timedBlock != null) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (timedBlock != null) Icons.Default.Schedule else Icons.Default.Block,
                null,
                tint = if (timedBlock != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.hostname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                device.macAddress,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (timedBlock != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            "Auto-unblock: ${timedBlock.formattedRemaining}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (timedBlock != null) {
            OutlinedButton(
                onClick = onCancelTimer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Cancel Timer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            // Restore button
            OutlinedButton(
                onClick = onRestore,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.online.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "Restore",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.online,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Reusable sub-components ───────────────────────────────────────────────────

@Composable
private fun SpeedChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitDropdown(
    label: String,
    selectedRaw: Float,
    options: List<Pair<String, Float>>,
    rawToLabel: (Float) -> String,
    onSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = rawToLabel(selectedRaw),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 10.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            options.forEach { (labelText, raw) ->
                val isSelected = (raw == selectedRaw) || (raw >= 38401f && selectedRaw >= 38401f)
                DropdownMenuItem(
                    text = {
                        Text(
                            labelText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelected(raw)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────


private fun connectionTypeIcon(type: String): ImageVector = when {
    type.contains("2G", ignoreCase = true) -> Icons.Default.SignalCellularAlt
    type.contains("5G", ignoreCase = true) -> Icons.Default.Wifi
    type.contains("wire", ignoreCase = true) -> Icons.Default.Lan
    else -> Icons.Default.DeviceUnknown
}
