package com.tenda.wifimonitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tenda.wifimonitor.MainActivity
import com.tenda.wifimonitor.R
import com.tenda.wifimonitor.domain.model.NetworkDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val CHANNEL_INTRUDER = "intruder_alert"
        const val CHANNEL_MONITORING = "monitoring_service"
        private const val TAG = "NotificationHelper"
    }

    // Track which MACs we've already notified about in this app session
    // to prevent duplicate notifications across rapid scans.
    private val notifiedNewDevices = mutableSetOf<String>()
    private val notifiedIntruders = mutableSetOf<String>()

    fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_NEW_DEVICE,
            "New Device Connected",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifies when a new device joins your network" })

        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_INTRUDER,
            "⚠️ Intruder Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when an unknown device joins your network"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 600)
        })

        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_MONITORING,
            "Background Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Persistent notification for network monitoring" })
    }

    /**
     * Checks whether the app has notification permission (required on Android 13+).
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun notifyNewDevice(device: NetworkDevice, soundEnabled: Boolean = true) {
        // Skip if already notified for this MAC in this session
        if (!notifiedNewDevices.add(device.macAddress)) {
            Log.d(TAG, "Already notified new device: ${device.macAddress}")
            return
        }

        if (!hasNotificationPermission()) {
            Log.w(TAG, "Cannot send notification — POST_NOTIFICATIONS not granted")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("mac", device.macAddress)
        }
        val pi = PendingIntent.getActivity(
            context, device.macAddress.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use a stable notification ID derived from MAC so the same device
        // doesn't pile up multiple notifications.
        val notifId = ("new_" + device.macAddress).hashCode()

        val notif = NotificationCompat.Builder(context, CHANNEL_NEW_DEVICE)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle("📶 New Device Connected")
            .setContentText("${device.displayName} (${device.ipAddress})")
            .setSubText(device.manufacturer)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${device.displayIcon} ${device.displayName}\nIP: ${device.ipAddress}\nMAC: ${device.macAddress}\nMaker: ${device.manufacturer}"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(!soundEnabled)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
            Log.d(TAG, "Sent new-device notification for ${device.macAddress}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending notification: ${e.message}")
        }
    }

    fun notifyIntruder(device: NetworkDevice, soundEnabled: Boolean = true) {
        // Skip if already notified for this MAC in this session
        if (!notifiedIntruders.add(device.macAddress)) {
            Log.d(TAG, "Already notified intruder: ${device.macAddress}")
            return
        }

        if (!hasNotificationPermission()) {
            Log.w(TAG, "Cannot send intruder notification — POST_NOTIFICATIONS not granted")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("mac", device.macAddress)
            putExtra("screen", "intruder")
        }
        val pi = PendingIntent.getActivity(
            context, device.macAddress.hashCode() + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifId = ("intruder_" + device.macAddress).hashCode()

        val notif = NotificationCompat.Builder(context, CHANNEL_INTRUDER)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("🚨 Unknown Device on Network!")
            .setContentText("${device.macAddress} (${device.ipAddress})")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("An unrecognized device connected!\n\nMAC: ${device.macAddress}\nIP: ${device.ipAddress}\nMaker: ${device.manufacturer}\n\nTap to review and block."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(!soundEnabled)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
            Log.d(TAG, "Sent intruder notification for ${device.macAddress}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending intruder notification: ${e.message}")
        }
    }

    /**
     * Clears the duplicate-tracking sets. Call when a device goes offline
     * so it can trigger a new notification if it reconnects later.
     */
    fun clearNotifiedDevice(mac: String) {
        notifiedNewDevices.remove(mac)
        notifiedIntruders.remove(mac)
    }

    fun buildMonitoringNotification() = NotificationCompat.Builder(context, CHANNEL_MONITORING)
        .setSmallIcon(R.drawable.ic_wifi)
        .setContentTitle("WiFi Monitor Active")
        .setContentText("Watching your Tenda network…")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
