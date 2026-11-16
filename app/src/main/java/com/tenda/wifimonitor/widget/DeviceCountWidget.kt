package com.tenda.wifimonitor.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tenda.wifimonitor.MainActivity
import com.tenda.wifimonitor.R
import com.tenda.wifimonitor.data.db.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class DeviceCountWidget : GlanceAppWidget() {

    @android.annotation.SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val count = try {
            // We directly query the DB since Hilt injection in Glance is tricky without custom workers
            val db = androidx.room.Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "wifi_monitor.db"
            )
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                )
                .build()
            db.deviceDao().observeOnlineCount().firstOrNull() ?: 0
        } catch (_: Exception) {
            0
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2A3D))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.glance.Image(
                        provider = ImageProvider(R.drawable.ic_wifi),
                        contentDescription = null,
                        modifier = GlanceModifier.size(24.dp)
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = count.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00C8B4)),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Online",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8B9AB5)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
