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
class TimedUnblockWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DeviceRepository,
    private val notificationHelper: NotificationHelper,
    private val preferences: AppPreferences,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TimedUnblockWorker"
        const val KEY_MAC = "mac_address"
        const val KEY_DEVICE_NAME = "device_name"

        fun schedule(context: Context, mac: String, deviceName: String, delaySeconds: Long) {
            val workData = workDataOf(
                KEY_MAC to mac,
                KEY_DEVICE_NAME to deviceName,
            )
            val request = OneTimeWorkRequestBuilder<TimedUnblockWorker>()
                .setInitialDelay(delaySeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
                .setInputData(workData)
                .addTag("timed_unblock_$mac")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "timed_unblock_$mac",
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Log.d(TAG, "Scheduled unblock for $mac ($deviceName) in ${delaySeconds}s")
        }

        fun cancel(context: Context, mac: String) {
            WorkManager.getInstance(context).cancelUniqueWork("timed_unblock_$mac")
            Log.d(TAG, "Cancelled scheduled unblock for $mac")
        }
    }

    override suspend fun doWork(): Result {
        val mac = inputData.getString(KEY_MAC) ?: return Result.failure()
        val deviceName = inputData.getString(KEY_DEVICE_NAME) ?: mac

        Log.d(TAG, "Executing auto-unblock for $deviceName ($mac)…")

        return try {
            val password = preferences.routerPassword.firstOrNull() ?: ""
            if (password.isNotBlank()) {
                repository.login(password)
            }

            // Unblock on router and remove from database
            val success = repository.unblockTimedBlock(mac, deviceName)
            if (success) {
                val soundEnabled = preferences.soundEnabled.firstOrNull() ?: true
                notificationHelper.notifyTimedUnblock(deviceName, mac, soundEnabled)
                Log.d(TAG, "Successfully unblocked $mac and notified user")
                Result.success()
            } else {
                Log.w(TAG, "Unblock returned false for $mac — retrying")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during unblock for $mac: ${e.message}")
            Result.retry()
        }
    }
}
