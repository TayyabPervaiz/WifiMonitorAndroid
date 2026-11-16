package com.tenda.wifimonitor.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.ui.components.ScreenHeader
import com.tenda.wifimonitor.ui.components.SectionCard
import com.tenda.wifimonitor.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    val pollingOptions = listOf(15L to "15 seconds", 30L to "30 seconds", 60L to "1 minute", 300L to "5 minutes")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        ScreenHeader(
            title = "Settings",
            showBack = showBack,
            onBack = onBack,
        )

        Spacer(Modifier.height(8.dp))

        // ── Router Settings ─────────────────────────────────────────────────
        SettingsSection(title = "Router") {
            OutlinedTextField(
                value = state.routerUrl,
                onValueChange = viewModel::updateUrl,
                label = { Text("Router URL") },
                leadingIcon = { Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.routerPassword,
                onValueChange = viewModel::updatePassword,
                label = { Text("Admin Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors(),
            )
        }

        // ── Monitoring ──────────────────────────────────────────────────────
        SettingsSection(title = "Monitoring") {
            ToggleRow(
                icon = Icons.Default.Wifi,
                title = "Enable Monitoring",
                subtitle = "Scans your network in the background",
                checked = state.monitoringEnabled,
                onCheckedChange = viewModel::setMonitoringEnabled,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Scan Interval",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            pollingOptions.forEach { (secs, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.setPollingInterval(secs) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.pollingInterval == secs,
                        onClick = { viewModel.setPollingInterval(secs) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ── Security & Protection ───────────────────────────────────────────
        SettingsSection(title = "Security & Protection") {
            ToggleRow(
                icon = Icons.Default.Security,
                title = "Auto-Block Unknown Devices",
                subtitle = "Instantly block unrecognized MACs on the router to neutralize MAC randomization bypass",
                checked = state.autoBlockUnknownDevices,
                onCheckedChange = viewModel::setAutoBlockUnknownDevices,
                iconTint = MaterialTheme.colorScheme.primary,
            )
        }

        // ── Alerts ──────────────────────────────────────────────────────────
        SettingsSection(title = "Alerts & Notifications") {
            ToggleRow(
                icon = Icons.Default.Warning,
                title = "Intruder Alerts",
                subtitle = "Get notified when unknown devices connect",
                checked = state.intruderAlertEnabled,
                onCheckedChange = viewModel::setIntruderAlertEnabled,
                iconTint = MaterialTheme.colorScheme.error,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            ToggleRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Alert Sound",
                subtitle = "Play sound for new device notifications",
                checked = state.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
        }

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsSection(title = "Appearance") {
            ToggleRow(
                icon = Icons.Default.DarkMode,
                title = "Dark Theme",
                subtitle = "Use dark background (recommended)",
                checked = state.darkTheme,
                onCheckedChange = viewModel::setDarkTheme,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            ToggleRow(
                icon = Icons.Default.Palette,
                title = "Material You",
                subtitle = "Use system dynamic color (Android 12+)",
                checked = state.useDynamicColor,
                onCheckedChange = viewModel::setUseDynamicColor,
            )
        }

        // App info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Wifi Monitor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version 1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        SectionCard(contentPadding = PaddingValues(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
