package com.tenda.wifimonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tenda.wifimonitor.data.AppPreferences
import com.tenda.wifimonitor.ui.AppNavigation
import com.tenda.wifimonitor.ui.Routes
import com.tenda.wifimonitor.ui.theme.WifiMonitorTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen visible until we know the start destination
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        val startDest = runBlocking {
            val isSetupComplete = preferences.isSetupComplete.first()
            isReady = true
            if (isSetupComplete) Routes.DASHBOARD else Routes.SETUP
        }

        // Handle navigation from notification intent
        var actualStartDest = startDest
        val mac = intent.getStringExtra("mac")
        if (mac != null && startDest == Routes.DASHBOARD) {
            actualStartDest = Routes.detail(mac)
        }

        setContent {
            val useDarkTheme by preferences.darkTheme.collectAsState(initial = isSystemInDarkTheme())
            val useDynamicColor by preferences.useDynamicColor.collectAsState(initial = false)

            // Request Notification Permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { /* Handle result if needed */ }
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Check and Request Ignore Battery Optimizations
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = getSystemService(android.os.PowerManager::class.java)
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
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
                    }
                }
            }

            WifiMonitorTheme(
                darkTheme = useDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                AppNavigation(startDestination = actualStartDest)
            }
        }
    }
}
