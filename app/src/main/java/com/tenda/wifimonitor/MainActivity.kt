package com.tenda.wifimonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.data.repository.DeviceRepository
import com.tenda.wifimonitor.ui.AppNavigation
import com.tenda.wifimonitor.ui.Routes
import com.tenda.wifimonitor.ui.theme.WifiMonitorTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var repository: DeviceRepository

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            repository.checkAndProcessExpiredTimedBlocks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val startDest = mutableStateOf<String?>(null)

        // Keep splash screen visible until we know the start destination
        splashScreen.setKeepOnScreenCondition { startDest.value == null }

        // Handle navigation from notification intent
        val mac = intent.getStringExtra("mac")

        setContent {
            val useDarkTheme by preferences.darkTheme.collectAsState(initial = isSystemInDarkTheme())
            val useDynamicColor by preferences.useDynamicColor.collectAsState(initial = false)

            // Read setup state asynchronously once composable is active
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val isSetupComplete = preferences.isSetupComplete.first()
                val dest = if (isSetupComplete) Routes.DASHBOARD else Routes.SETUP
                startDest.value = if (mac != null && dest == Routes.DASHBOARD) Routes.detail(mac) else dest
            }

            // Request Notification Permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { /* Handle result if needed */ }
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Check and Request Ignore Battery Optimizations (only on first run)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = getSystemService(android.os.PowerManager::class.java)
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    val batteryRequested by preferences.batteryOptRequested.collectAsState(initial = false)
                    if (!batteryRequested) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:$packageName")
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback if the device doesn't support the direct intent
                                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(fallbackIntent)
                            }
                            preferences.setBatteryOptRequested(true)
                        }
                    }
                }
            }

            WifiMonitorTheme(
                darkTheme = useDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                startDest.value?.let { dest ->
                    AppNavigation(startDestination = dest)
                }
            }
        }
    }
}
