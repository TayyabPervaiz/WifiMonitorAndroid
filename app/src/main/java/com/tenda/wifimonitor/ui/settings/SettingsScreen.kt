package com.tenda.wifimonitor.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    val pollingOptions = listOf(15L to "15 seconds", 30L to "30 seconds", 60L to "1 minute", 300L to "5 minutes")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1830), Background)))
                .padding(horizontal = 20.dp)
                .padding(top = 52.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = OnSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Router Settings ─────────────────────────────────────────────────
        SettingsSection(title = "Router") {
            OutlinedTextField(
                value = state.routerUrl,
                onValueChange = viewModel::updateUrl,
                label = { Text("Router URL") },
                leadingIcon = { Icon(Icons.Default.Router, null, tint = Primary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.routerPassword,
                onValueChange = viewModel::updatePassword,
                label = { Text("Admin Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Primary) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = OnSurfaceVariant
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
                subtitle = "Background network scanning",
                checked = state.monitoringEnabled,
                onCheckedChange = viewModel::setMonitoringEnabled,
            )
            HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Scan Interval",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
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
                        colors = RadioButtonDefaults.colors(selectedColor = Primary)
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                }
            }
        }

        // ── Alerts ──────────────────────────────────────────────────────────
        SettingsSection(title = "Alerts & Notifications") {
            ToggleRow(
                icon = Icons.Default.Warning,
                title = "Intruder Alerts",
                subtitle = "Get notified when unknown devices connect",
                checked = state.intruderAlertEnabled,
                onCheckedChange = viewModel::setIntruderAlertEnabled,
                iconTint = Danger,
            )
            HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            ToggleRow(
                icon = Icons.Default.VolumeUp,
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
            HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
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
            Text("Wifi Monitor", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            Text("Version 1.0 · Tenda F3 N300", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(0.5f))
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
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = Primary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = SurfaceVariant,
            )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    focusedLabelColor = Primary,
    unfocusedBorderColor = OnSurfaceVariant.copy(0.3f),
    unfocusedLabelColor = OnSurfaceVariant,
)
