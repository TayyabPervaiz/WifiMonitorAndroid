package com.tenda.wifimonitor.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tenda.wifimonitor.ui.dashboard.DashboardScreen
import com.tenda.wifimonitor.ui.detail.DeviceDetailScreen
import com.tenda.wifimonitor.ui.history.DeviceHistoryScreen
import com.tenda.wifimonitor.ui.map.NetworkMapScreen
import com.tenda.wifimonitor.ui.settings.SettingsScreen
import com.tenda.wifimonitor.ui.setup.SetupScreen
import com.tenda.wifimonitor.ui.stats.StatisticsScreen

object Routes {
    const val SETUP = "setup"
    const val DASHBOARD = "dashboard"
    const val DETAIL = "detail/{mac}"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val MAP = "map"
    const val SETTINGS = "settings"

    fun detail(mac: String) = "detail/$mac"
}

@Composable
fun AppNavigation(
    startDestination: String,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        },
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToMap = { navController.navigate(Routes.MAP) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("mac") { type = NavType.StringType }),
        ) {
            DeviceDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            DeviceHistoryScreen(
                onBack = { navController.popBackStack() },
                onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
            )
        }

        composable(Routes.STATS) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MAP) {
            NetworkMapScreen(
                onBack = { navController.popBackStack() },
                onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
