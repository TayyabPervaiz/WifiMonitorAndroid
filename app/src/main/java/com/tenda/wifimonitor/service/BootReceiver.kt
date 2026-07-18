package com.tenda.wifimonitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.tenda.wifimonitor.data.AppPreferences

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val enabled = preferences.isMonitoringEnabled.firstOrNull() ?: true
                val interval = preferences.pollingIntervalSecs.firstOrNull() ?: 30L
                if (enabled) {
                    NetworkMonitorWorker.schedule(context, interval)
                    NetworkScanService.start(context)
                }
            }
        }
    }
}
