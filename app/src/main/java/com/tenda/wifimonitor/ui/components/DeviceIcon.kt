package com.tenda.wifimonitor.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.tenda.wifimonitor.domain.model.DeviceType

/** Crisp vector icon for each detected device type. */
fun DeviceType.vectorIcon(): ImageVector = when (this) {
    DeviceType.PHONE -> Icons.Filled.Smartphone
    DeviceType.LAPTOP -> Icons.Filled.Laptop
    DeviceType.TABLET -> Icons.Filled.Tablet
    DeviceType.SMART_TV -> Icons.Filled.Tv
    DeviceType.IOT -> Icons.Filled.Memory
    DeviceType.ROUTER -> Icons.Filled.Router
    DeviceType.DESKTOP -> Icons.Filled.DesktopWindows
    DeviceType.GAME_CONSOLE -> Icons.Filled.SportsEsports
    DeviceType.UNKNOWN -> Icons.Filled.DeviceUnknown
}

/**
 * Renders a device's icon. If the user has explicitly chosen an emoji icon we
 * honour it; otherwise we draw a clean vector icon for the detected type.
 */
@Composable
fun DeviceIcon(
    deviceType: DeviceType,
    customIcon: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    if (!customIcon.isNullOrBlank()) {
        Text(
            text = customIcon,
            fontSize = (size.value * 0.8f).sp,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = deviceType.vectorIcon(),
            contentDescription = deviceType.label,
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}
