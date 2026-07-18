package com.tenda.wifimonitor.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.ui.theme.*

// ── Pulsing Online Dot ────────────────────────────────────────────────────────

@Composable
fun PulsingDot(
    color: Color = DeviceOnline,
    size: Int = 10,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((size * 2).dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ── Gradient Card ─────────────────────────────────────────────────────────────

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(listOf(SurfaceCard, SurfaceVariant)),
                shape
            )
    } else {
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(SurfaceCard, SurfaceVariant)),
                shape
            )
    }
    Column(modifier = cardModifier, content = content)
}

// ── Device Card ───────────────────────────────────────────────────────────────

@Composable
fun DeviceCard(
    device: NetworkDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "card_scale"
    )

    val borderColor = when {
        !device.isWhitelisted -> DeviceIntruder
        device.isOnline -> Primary.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(borderColor, borderColor.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                Brush.linearGradient(listOf(SurfaceCard, Surface)),
                RoundedCornerShape(16.dp)
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Device icon circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (device.isOnline) Primary.copy(0.25f) else DeviceOffline.copy(0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = device.displayIcon,
                    fontSize = 26.sp
                )
            }

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!device.isWhitelisted) {
                        Surface(
                            color = DeviceIntruder.copy(0.2f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "UNKNOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeviceIntruder,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = OnSurfaceVariant,
                )
                Text(
                    text = device.manufacturer,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Status
            Column(horizontalAlignment = Alignment.End) {
                if (device.isOnline) {
                    PulsingDot()
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DeviceOffline)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (device.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.isOnline) DeviceOnline else DeviceOffline,
                )
            }
        }
    }
}

// ── Stat Chip ─────────────────────────────────────────────────────────────────

@Composable
fun StatChip(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = Primary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
    }
}

// ── Section Header ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = action, color = Primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Loading Shimmer ────────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceVariant.copy(alpha = alpha))
    )
}

// ── Empty State ────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: String = "📡",
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 56.sp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )
    }
}
