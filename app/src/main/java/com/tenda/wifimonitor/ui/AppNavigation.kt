package com.tenda.wifimonitor.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tenda.wifimonitor.ui.theme.AppElevation
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tenda.wifimonitor.ui.bandwidth.BandwidthControlScreen
import com.tenda.wifimonitor.ui.dashboard.DashboardScreen
import com.tenda.wifimonitor.ui.detail.DeviceDetailScreen
import com.tenda.wifimonitor.ui.history.DeviceHistoryScreen
import com.tenda.wifimonitor.ui.settings.SettingsScreen
import com.tenda.wifimonitor.ui.setup.SetupScreen
import com.tenda.wifimonitor.ui.status.RouterStatusScreen

object Routes {
    const val SETUP = "setup"
    const val DASHBOARD = "dashboard"
    const val DETAIL = "detail/{mac}"
    const val HISTORY = "history"
    const val STATUS = "status"
    const val BANDWIDTH = "bandwidth"
    const val SETTINGS = "settings"

    fun detail(mac: String) = "detail/$mac"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    TopLevelDestination(Routes.STATUS, "Status", Icons.Filled.Router),
    TopLevelDestination(Routes.BANDWIDTH, "Bandwidth", Icons.Filled.SwapVert),
    TopLevelDestination(Routes.HISTORY, "History", Icons.Filled.History),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNavigation(
    startDestination: String,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    val navigateToTab: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        )))
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .shadow(AppElevation.raised, RoundedCornerShape(28.dp), clip = false)
                            .clip(RoundedCornerShape(28.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(28.dp),
                            ),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        topLevelDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateToTab(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = {
                                    Text(
                                        text = destination.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Routes.SETUP) {
                    SetupScreen(
                        onSetupComplete = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
                        onNavigateToHistory = { navigateToTab(Routes.HISTORY) },
                        onNavigateToStatus = { navigateToTab(Routes.STATUS) },
                        onNavigateToBandwidth = { navigateToTab(Routes.BANDWIDTH) },
                        onNavigateToSettings = { navigateToTab(Routes.SETTINGS) },
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
                        showBack = false,
                        onBack = { navController.popBackStack() },
                        onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
                    )
                }

                composable(Routes.STATUS) {
                    RouterStatusScreen(
                        showBack = false,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.BANDWIDTH) {
                    BandwidthControlScreen(
                        showBack = false,
                        onBack = { navController.popBackStack() },
                        onDeviceClick = { mac -> navController.navigate(Routes.detail(mac)) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        showBack = false,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Keeps the status-bar strip legible as screen content scrolls under it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                            ),
                        ),
                    )
                    .align(Alignment.TopCenter),
            )
        }
    }
}
