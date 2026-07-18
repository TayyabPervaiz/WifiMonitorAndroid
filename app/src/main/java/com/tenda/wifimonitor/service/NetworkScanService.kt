package com.tenda.wifimonitor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * A foreground service that scans the network at a configurable interval
 * (default 30s) and fires notifications instantly when new/unknown devices appear.
 *
 * WorkManager's PeriodicWork has a hard 15-minute minimum on Android,
 * so we use a foreground service instead for near real-time monitoring.
 */
@AndroidEntryPoint
class NetworkScanService : Service() {

    companion object {
        private const val TAG = "NetworkScanService"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, NetworkScanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NetworkScanService::class.java))
        }
    }

    @Inject lateinit var repository: DeviceRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferences: AppPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately with a persistent notification
        val notification = notificationHelper.buildMonitoringNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Cancel any existing scan loop and start a fresh one
        scanJob?.cancel()
        var scanCount = 0
        scanJob = serviceScope.launch {
            while (isActive) {
                try {
                    val enabled = preferences.isMonitoringEnabled.firstOrNull() ?: true
                    if (!enabled) {
                        Log.d(TAG, "Monitoring disabled — stopping service")
                        stopSelf()
                        return@launch
                    }

                    val password = preferences.routerPassword.firstOrNull()
                    if (password.isNullOrBlank()) {
                        Log.w(TAG, "No router password saved — skipping scan")
                        delay(10_000)
                        continue
                    }

                    val intruderAlert = preferences.intruderAlertEnabled.firstOrNull() ?: true
                    val soundEnabled = preferences.soundEnabled.firstOrNull() ?: true
                    val intervalSecs = preferences.pollingIntervalSecs.firstOrNull() ?: 30L

                    // Proactively re-login every 10 scans (~5 min) to keep the Tenda
                    // session alive. Tenda sessions expire silently, causing empty device lists.
                    scanCount++
                    if (scanCount % 10 == 1) {
                        Log.d(TAG, "Proactive re-login (scan #$scanCount)…")
                        repository.login(password)
                    }

                    Log.d(TAG, "Scan #$scanCount starting…")
                    val result = repository.performScan(password)
                    Log.d(TAG, "Scan #$scanCount done: ${result.allOnline.size} online, " +
                            "${result.newDevices.size} new, ${result.intruders.size} intruders")

                    // Fire notifications for new devices
                    result.newDevices.forEach { device ->
                        Log.d(TAG, "New device found: ${device.macAddress} (${device.displayName})")
                        notificationHelper.notifyNewDevice(device, soundEnabled)
                    }

                    // Fire intruder alerts
                    if (intruderAlert) {
                        result.intruders.forEach { device ->
                            Log.w(TAG, "INTRUDER: ${device.macAddress} (${device.displayName})")
                            notificationHelper.notifyIntruder(device, soundEnabled)
                        }
                    }

                    // Wait for the configured interval before next scan
                    delay(intervalSecs * 1000L)
                } catch (e: CancellationException) {
                    throw e // Don't catch cancellation
                } catch (e: Exception) {
                    Log.e(TAG, "Scan error: ${e.message}")
                    delay(15_000) // Back off on error
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed from recents. Restarting service to maintain monitoring...")
        // Restart the service explicitly
        start(applicationContext)
    }

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}
