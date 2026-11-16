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
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.domain.model.NetworkDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences
) {
    companion object {
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val CHANNEL_INTRUDER = "intruder_alert"
        const val CHANNEL_MONITORING = "monitoring_service"
        const val CHANNEL_TIMED_BLOCK = "timed_block"
        private const val TAG = "NotificationHelper"
    }

    private val cooldownMs = 5 * 60 * 1000L

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

        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_TIMED_BLOCK,
            "Timer & Auto-Unblock",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifies when a device block timer elapses and device is unblocked" })
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

    suspend fun notifyNewDevice(device: NetworkDevice, soundEnabled: Boolean = true) {
        // Skip if already notified within the cooldown period
        val now = System.currentTimeMillis()
        val key = "new:${device.macAddress}"
        val lastNotified = preferences.getLastNotified(key) ?: 0L
        if (now - lastNotified < cooldownMs) {
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
            preferences.setLastNotified(key, now)
            Log.d(TAG, "Sent new-device notification for ${device.macAddress}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending notification: ${e.message}")
        }
    }

    suspend fun notifyIntruder(device: NetworkDevice, soundEnabled: Boolean = true) {
        // Skip if already notified within the cooldown period
        val now = System.currentTimeMillis()
        val key = "intruder:${device.macAddress}"
        val lastNotified = preferences.getLastNotified(key) ?: 0L
        if (now - lastNotified < cooldownMs) {
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

        val title = if (device.isBlocked) "🛡️ Auto-Blocked Device: ${device.displayName}" else "🚨 Unknown Device on Network!"
        val bigText = if (device.isBlocked) {
            "A blocked or unknown device connected and was automatically blocked on the router.\n\nMAC: ${device.macAddress}\nIP: ${device.ipAddress}\nMaker: ${device.manufacturer}\n\nTap to review or unblock."
        } else {
            "An unrecognized device connected!\n\nMAC: ${device.macAddress}\nIP: ${device.ipAddress}\nMaker: ${device.manufacturer}\n\nTap to review and block."
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_INTRUDER)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText("${device.macAddress} (${device.ipAddress})")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(!soundEnabled)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
            preferences.setLastNotified(key, now)
            Log.d(TAG, "Sent intruder notification for ${device.macAddress}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending intruder notification: ${e.message}")
        }
    }

    /**
     * Clears the duplicate-tracking sets. Call when a device goes offline
     * so it can trigger a new notification if it reconnects later.
     */
    suspend fun clearNotifiedDevice(mac: String) {
        preferences.removeNotified("new:$mac")
        preferences.removeNotified("intruder:$mac")
    }

    fun buildMonitoringNotification() = NotificationCompat.Builder(context, CHANNEL_MONITORING)
        .setSmallIcon(R.drawable.ic_wifi)
        .setContentTitle("WiFi Monitor Active")
        .setContentText("Watching your Tenda network…")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    fun notifyTimedUnblock(deviceName: String, mac: String, soundEnabled: Boolean = true) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("mac", mac)
        }
        val pi = PendingIntent.getActivity(
            context, mac.hashCode() + 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifId = ("timed_unblock_" + mac).hashCode()

        val notif = NotificationCompat.Builder(context, CHANNEL_TIMED_BLOCK)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle("⏰ Timer Expired: Device Unblocked")
            .setContentText("$deviceName has been unblocked.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("The block timer for $deviceName ($mac) has elapsed. Internet access has been automatically restored."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(!soundEnabled)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
            Log.d(TAG, "Sent timed-unblock notification for $mac")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending notification: ${e.message}")
        }
    }
}
