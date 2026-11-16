package com.tenda.wifimonitor.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tenda.wifimonitor.widget.DeviceCountWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

@HiltWorker
class NetworkMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DeviceRepository,
    private val notificationHelper: NotificationHelper,
    private val preferences: AppPreferences,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "NetworkMonitorWorker"
        const val WORK_NAME = "network_monitor"

        /**
         * Schedules the worker as a low-frequency backup scanner.
         *
         * NOTE: androidx.work enforces a hard minimum repeat interval of 15 minutes
         * for PeriodicWorkRequest, regardless of the value passed here. The
         * foreground [NetworkScanService] is the primary scanner and honors the
         * user's configured polling interval (default 30 s). This worker only steps
         * in via [doWork] when the foreground service is NOT running, so it does
         * not duplicate the service's API calls.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NetworkMonitorWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Log.d(TAG, "Scheduled monitor (backup path, runs only when the foreground service is down)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Monitor cancelled")
        }
    }

    override suspend fun doWork(): Result {
        // Backup path: only scan if the foreground service is NOT running.
        if (isForegroundScanServiceRunning(applicationContext)) {
            Log.d(TAG, "Foreground scan service is running — skipping backup scan")
            return Result.success()
        }

        return try {
            val enabled = preferences.isMonitoringEnabled.firstOrNull() ?: true
            if (!enabled) return Result.success()

            val password = preferences.routerPassword.firstOrNull() ?: return Result.success()
            val intruderAlert = preferences.intruderAlertEnabled.firstOrNull() ?: true

            Log.d(TAG, "Starting network scan…")
            val result = repository.performScan(password)

            Log.d(TAG, "Scan done: ${result.allOnline.size} online, " +
                    "${result.newDevices.size} new, ${result.intruders.size} intruders")

            // Notify new devices
            result.newDevices.forEach { device ->
                notificationHelper.notifyNewDevice(device)
            }

            // Intruder alerts
            if (intruderAlert) {
                result.intruders.forEach { device ->
                    notificationHelper.notifyIntruder(device)
                }
            }

            // Update widget
            updateWidget()

            // Prune old data monthly-ish
            if (Math.random() < 0.01) repository.pruneOldHistory()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker error: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Checks whether the foreground [NetworkScanService] is currently running,
     * so this backup worker avoids duplicating its scans.
     */
    private fun isForegroundScanServiceRunning(context: Context): Boolean {
        return try {
            val manager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.getRunningServices(Integer.MAX_VALUE).any { info ->
                info.service.className.endsWith("NetworkScanService")
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun updateWidget() {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(DeviceCountWidget::class.java)
            glanceIds.forEach { id ->
                DeviceCountWidget().update(applicationContext, id)
            }
        } catch (_: Exception) {}
    }
}
