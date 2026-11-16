package com.tenda.wifimonitor.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tenda.wifimonitor.data.repository.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimedBlockReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: DeviceRepository

    companion object {
        const val TAG = "TimedBlockReceiver"
        const val ACTION_TIMED_UNBLOCK = "com.tenda.wifimonitor.ACTION_TIMED_UNBLOCK"
        const val KEY_MAC = "mac_address"
        const val KEY_DEVICE_NAME = "device_name"

        fun schedule(context: Context, mac: String, deviceName: String, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TimedBlockReceiver::class.java).apply {
                action = ACTION_TIMED_UNBLOCK
                putExtra(KEY_MAC, mac)
                putExtra(KEY_DEVICE_NAME, deviceName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                mac.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.d(TAG, "Scheduled exact alarm for $mac ($deviceName) at $triggerAtMillis")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule exact alarm for $mac: ${e.message}")
            }
        }

        fun cancel(context: Context, mac: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TimedBlockReceiver::class.java).apply {
                action = ACTION_TIMED_UNBLOCK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                mac.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled alarm for $mac")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mac = intent.getStringExtra(KEY_MAC) ?: return
        val deviceName = intent.getStringExtra(KEY_DEVICE_NAME) ?: mac

        Log.d(TAG, "Alarm triggered auto-unblock for $deviceName ($mac)")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.unblockTimedBlock(mac, deviceName)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing unblock in receiver for $mac: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
