package com.tenda.wifimonitor.ui.status

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.ui.components.HeaderActionButton
import com.tenda.wifimonitor.ui.components.InlineBanner
import com.tenda.wifimonitor.ui.components.ScreenHeader
import com.tenda.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RouterStatusScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: RouterStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        ScreenHeader(
            title = "Router Status",
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

        Spacer(Modifier.height(8.dp))

        // ── Error Banner ──────────────────────────────────────────────────────
        AnimatedVisibility(visible = state.error != null) {
            InlineBanner(
                message = state.error ?: "",
                icon = Icons.Default.Warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // ── Internet Connection Topology ───────────────────────────────────────
        ConnectionTopologyCard(
            isConnected = state.isConnected,
            onlineDeviceCount = state.status?.onlineDeviceCount ?: 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // ── Real-Time Speed & Devices Row ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedStatCard(
                value = state.status?.onlineDeviceCount?.toString() ?: "—",
                unit = "",
                label = "Online Devices",
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Devices,
                modifier = Modifier.weight(1f),
            )
            SpeedStatCard(
                value = formatSpeedValue(state.status?.downloadSpeedKbps),
                unit = formatSpeedUnit(state.status?.downloadSpeedKbps, isDownload = true),
                label = "Download Speed",
                color = AppColors.online,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
            SpeedStatCard(
                value = formatSpeedValue(state.status?.uploadSpeedKbps),
                unit = formatSpeedUnit(state.status?.uploadSpeedKbps, isDownload = false),
                label = "Upload Speed",
                color = MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── System Information ────────────────────────────────────────────────
        SectionLabel("System Information")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
        ) {
            Column {
                SystemInfoRow(
                    leftLabel = "Connection Type",
                    leftValue = state.status?.connectionType ?: "—",
                    rightLabel = "WAN IP Address",
                    rightValue = state.status?.wanIp ?: "—",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                SystemInfoRow(
                    leftLabel = "Connection Duration",
                    leftValue = state.status?.uptime ?: "—",
                    rightLabel = "Subnet Mask",
                    rightValue = state.status?.subnetMask ?: "—",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                SystemInfoRow(
                    leftLabel = "MAC Address",
                    leftValue = state.status?.routerMac ?: "—",
                    rightLabel = "Default Gateway",
                    rightValue = state.status?.defaultGateway ?: "—",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                SystemInfoRow(
                    leftLabel = "LAN IP Address",
                    leftValue = state.status?.lanIp ?: "—",
                    rightLabel = "Preferred DNS Server",
                    rightValue = state.status?.preferredDns ?: "—",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                SystemInfoRow(
                    leftLabel = "Firmware Version",
                    leftValue = state.status?.firmwareVersion ?: "—",
                    rightLabel = "Alternate DNS Server",
                    rightValue = state.status?.alternateDns ?: "—",
                )
            }
        }

        // ── Last Updated ──────────────────────────────────────────────────────
        if (state.lastUpdated > 0L) {
            val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            Text(
                "Updated at ${formatter.format(Date(state.lastUpdated))} · auto-refreshes every 3s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun ConnectionTopologyCard(
    isConnected: Boolean,
    onlineDeviceCount: Int,
    modifier: Modifier = Modifier,
) {
    val connectedColor = if (isConnected) AppColors.online else MaterialTheme.colorScheme.error
    val statusText = if (isConnected) "Connected" else "Disconnected"
    val statusMsg = if (isConnected)
        "Connected. You can access the internet."
    else
        "Not connected. Check your router."

    // Pulse animation for the connecting lines
    val infiniteTransition = rememberInfiniteTransition(label = "topology_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse_alpha",
    )
    val nodeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "node_scale",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBrush())
            .border(1.dp, connectedColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Section title
            Text(
                "Internet Connection Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            // Topology nodes row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Internet globe node
                TopologyNode(
                    icon = Icons.Default.Public,
                    label = "Internet",
                    color = connectedColor,
                    scale = if (isConnected) nodeScale else 1f,
                )

                // Connecting line Internet → Router
                ConnectingLine(
                    isActive = isConnected,
                    activeColor = connectedColor,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                )

                // Router node (always present)
                TopologyNode(
                    icon = Icons.Default.Router,
                    label = "My Router",
                    color = MaterialTheme.colorScheme.primary,
                    scale = nodeScale,
                )

                // Connecting line Router → Devices
                ConnectingLine(
                    isActive = true,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                )

                // Devices node
                TopologyNode(
                    icon = Icons.Default.Devices,
                    label = "Devices ($onlineDeviceCount)",
                    color = MaterialTheme.colorScheme.primary,
                    scale = 1f,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Status message
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(connectedColor.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    statusMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = connectedColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TopologyNode(
    icon: ImageVector,
    label: String,
    color: Color,
    scale: Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.5.dp, color.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConnectingLine(
    isActive: Boolean,
    activeColor: Color,
    pulseAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (isActive) activeColor.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(activeColor.copy(alpha = 0.3f), lineColor, activeColor.copy(alpha = 0.3f))
                )
            ),
    )
}

@Composable
private fun SpeedStatCard(
    value: String,
    unit: String,
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
            if (unit.isNotBlank()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SystemInfoRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Left cell
        Column(modifier = Modifier.weight(1f)) {
            Text(
                leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                leftValue,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Right cell
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                rightValue,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Format a Kbps float value into a display string (auto-converts to Mbps if > 1000). */
private fun formatSpeedValue(kbps: Float?): String {
    if (kbps == null) return "—"
    return if (kbps >= 1000f) {
        "%.2f".format(kbps / 1000f)
    } else {
        "%.0f".format(kbps)
    }
}

/** Returns the appropriate unit label (Mbps or Kbps). */
private fun formatSpeedUnit(kbps: Float?, isDownload: Boolean): String {
    if (kbps == null) return ""
    return if (kbps >= 1000f) "Mbps" else "Kbps"
}
