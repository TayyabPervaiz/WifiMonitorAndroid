package com.tenda.wifimonitor.ui.stats

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tenda.wifimonitor.domain.model.DeviceType
import com.tenda.wifimonitor.ui.components.SectionHeader
import com.tenda.wifimonitor.ui.theme.*

@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

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
                    "Network Statistics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "Total Devices",
                value = state.allDevices.size.toString(),
                emoji = "📱",
                color = Primary,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Trusted",
                value = state.trustedCount.toString(),
                emoji = "✅",
                color = DeviceOnline,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Unknown",
                value = state.unknownCount.toString(),
                emoji = "⚠️",
                color = if (state.unknownCount > 0) Danger else DeviceOffline,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Hourly activity
        SectionHeader(
            title = "Activity by Hour",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp)
        ) {
            if (state.hourlyActivity.isEmpty()) {
                Text(
                    "No activity data yet. Check back after the app monitors your network for a while.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            } else {
                HourlyBarChart(state.hourlyActivity)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Device type breakdown
        SectionHeader(
            title = "Devices by Type",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val total = state.allDevices.size.coerceAtLeast(1)
            val byType = state.allDevices.groupBy { it.deviceType }.mapValues { it.value.size }

            if (byType.isEmpty()) {
                Text("No data yet", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            } else {
                byType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                    DeviceTypeBar(
                        type = type,
                        count = count,
                        total = total,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Online vs offline
        SectionHeader(
            title = "Network Status",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val online = state.allDevices.count { it.isOnline }
            val offline = state.allDevices.size - online
            StatusCard("Online Now", online, DeviceOnline, "📶", Modifier.weight(1f))
            StatusCard("Offline", offline, DeviceOffline, "📴", Modifier.weight(1f))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun HourlyBarChart(data: Map<Int, Int>) {
    val max = data.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val chartColors = listOf(ChartColor1, ChartColor2, ChartColor3, ChartColor4)

    Column {
        Text(
            "Connection count by hour of day",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (h in 0..23) {
                val count = data[h] ?: 0
                val fraction = count.toFloat() / max
                val color = chartColors[h % chartColors.size]
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                Brush.verticalGradient(listOf(color, color.copy(0.4f)))
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Hour labels (every 6h)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "23").forEachIndexed { i, label ->
                if (i == 0) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else if (i == 4) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                } else {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DeviceTypeBar(
    type: DeviceType,
    count: Int,
    total: Int,
) {
    val fraction = count.toFloat() / total
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(type.defaultEmoji, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(type.label, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                Text("$count", style = MaterialTheme.typography.bodySmall, color = Primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(listOf(Primary, Accent))
                        )
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 28.sp)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun StatusCard(
    title: String,
    count: Int,
    color: Color,
    emoji: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 28.sp)
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}
