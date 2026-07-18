package com.tenda.wifimonitor.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
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

        fun schedule(context: Context, intervalSeconds: Long) {
            val request = PeriodicWorkRequestBuilder<NetworkMonitorWorker>(
                repeatInterval = intervalSeconds.coerceAtLeast(15),
                repeatIntervalTimeUnit = TimeUnit.SECONDS,
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
            Log.d(TAG, "Scheduled monitor every ${intervalSeconds}s")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Monitor cancelled")
        }
    }

    override suspend fun doWork(): Result {
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

    private fun updateWidget() {
        // Trigger Glance widget update
        try {
            val intent = android.content.Intent("com.tenda.wifimonitor.UPDATE_WIDGET")
            applicationContext.sendBroadcast(intent)
        } catch (_: Exception) {}
    }
}
