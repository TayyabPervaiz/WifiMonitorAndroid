package com.tenda.wifimonitor.ui.map

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.NetworkDevice
import com.tenda.wifimonitor.ui.theme.*
import kotlin.math.*

@Composable
fun NetworkMapScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit,
    viewModel: NetworkMapViewModel = hiltViewModel(),
) {
    val devices by viewModel.onlineDevices.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "radar_angle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
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
                    "Network Map",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(color = DeviceOnline.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "${devices.size} online",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeviceOnline,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Network topology visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Background radar rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val maxR = size.width / 2

                // Concentric rings
                for (i in 1..4) {
                    val r = maxR * i / 4f
                    drawCircle(
                        color = Primary.copy(alpha = 0.08f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }

                // Radar sweep line
                val radians = Math.toRadians(radarAngle.toDouble())
                val sweepX = cx + maxR * cos(radians).toFloat()
                val sweepY = cy + maxR * sin(radians).toFloat()
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Primary.copy(0.6f)),
                        start = Offset(cx, cy),
                        end = Offset(sweepX, sweepY),
                    ),
                    start = Offset(cx, cy),
                    end = Offset(sweepX, sweepY),
                    strokeWidth = 2.dp.toPx(),
                )

                // Lines from center to devices
                val n = devices.size
                for (i in 0 until n) {
                    val angle = (360.0 / n * i) - 90.0
                    val rad = Math.toRadians(angle)
                    val r = maxR * 0.6f
                    val dx = cx + r * cos(rad).toFloat()
                    val dy = cy + r * sin(rad).toFloat()
                    drawLine(
                        color = Primary.copy(0.2f),
                        start = Offset(cx, cy),
                        end = Offset(dx, dy),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            // Router center node
            Box(
                modifier = Modifier
                    .size((56 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Primary.copy(0.4f), Color.Transparent)
                        )
                    )
                    .border(2.dp, Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("📡", fontSize = 28.sp)
            }

            // Device nodes arranged in a circle
            val n = devices.size
            if (n > 0) {
                val radius = 130.dp
                devices.forEachIndexed { i, device ->
                    val angle = (360.0 / n * i) - 90.0
                    val rad = Math.toRadians(angle)
                    val xOffset = (radius.value * cos(rad)).dp
                    val yOffset = (radius.value * sin(rad)).dp

                    Box(
                        modifier = Modifier
                            .offset(xOffset, yOffset)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (device.isWhitelisted) Primary.copy(0.25f) else Danger.copy(0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                if (device.isWhitelisted) Primary.copy(0.6f) else Danger.copy(0.6f),
                                CircleShape
                            )
                            .clickable { onDeviceClick(device.macAddress) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(device.displayIcon, fontSize = 22.sp)
                    }
                }
            }
        }

        // Device list below map
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tap a node to view device details",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .clickable { onDeviceClick(device.macAddress) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(device.displayIcon, fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text(device.displayName, style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.Medium)
                        Text(device.ipAddress, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (device.isWhitelisted) DeviceOnline else Danger))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
