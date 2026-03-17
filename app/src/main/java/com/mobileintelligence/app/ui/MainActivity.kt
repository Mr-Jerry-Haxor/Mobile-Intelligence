package com.mobileintelligence.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.service.MonitoringService
import com.mobileintelligence.app.ui.navigation.Screen
import com.mobileintelligence.app.ui.screens.*
import com.mobileintelligence.app.dns.ui.screens.*
import com.mobileintelligence.app.ui.theme.MobileIntelligenceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        requestBatteryOptimizationExemption()

        setContent {
            val prefs = remember { AppPreferences(this) }
            val themeMode by prefs.themeMode.collectAsState(initial = "auto")

            MobileIntelligenceTheme(themeMode = themeMode) {
                MainApp()
            }
        }

        // Ensure service is running
        lifecycleScope.launch {
            val prefs = AppPreferences(this@MainActivity)
            val isEnabled = prefs.isTrackingEnabled.first()
            if (isEnabled) {
                MonitoringService.start(this@MainActivity)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Request battery optimization exemption so the app can run 24/7.
     * This shows a system dialog (not Play Store violation since it's a monitoring app).
     */
    private fun requestBatteryOptimizationExemption() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Log.i("MainActivity", "Requesting battery optimization exemption")
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not request battery optimization exemption", e)
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainApp() {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavItem(Screen.IntelligenceConsole, "Intelligence", Icons.Filled.Psychology, Icons.Outlined.Psychology),
        BottomNavItem(Screen.AppStats, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
        BottomNavItem(Screen.NetworkProtection, "Network", Icons.Filled.Shield, Icons.Outlined.Shield),
        BottomNavItem(Screen.Timeline, "Timeline", Icons.Filled.Timeline, Icons.Outlined.Timeline),
        BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                // Don't restore state for Intelligence Console tab —
                                // prevents sub-screens (Heatmap, Settings) from persisting
                                // when switching tabs and coming back
                                restoreState = item.screen != Screen.IntelligenceConsole
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Timeline.route) { TimelineScreen() }
            composable(Screen.AppStats.route) { AppStatsScreen() }
            composable(Screen.Insights.route) { InsightsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }

            // Intelligence Engine screens
            composable(Screen.IntelligenceConsole.route) {
                IntelligenceConsoleScreen(
                    onNavigateToHeatmap = {
                        navController.navigate(Screen.Heatmap.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            composable(Screen.Heatmap.route) {
                HeatmapScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.TimelineReplay.route) {
                TimelineReplayScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Consent.route) {
                ConsentScreen(
                    onConsentGranted = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Consent.route) { inclusive = true }
                        }
                    },
                    onDecline = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Consent.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.FocusMode.route) {
                FocusModeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // DNS Firewall screens
            composable(Screen.NetworkProtection.route) {
                DnsProtectionScreen(
                    onNavigateToQueryLog = {
                        navController.navigate(Screen.DnsQueryLog.route)
                    },
                    onNavigateToStats = {
                        navController.navigate(Screen.DnsStats.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.DnsSettings.route)
                    }
                )
            }
            composable(Screen.DnsQueryLog.route) {
                DnsQueryLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DnsStats.route) {
                DnsStatsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DnsSettings.route) {
                DnsSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBlocklists = {
                        navController.navigate(Screen.BlocklistManagement.route)
                    }
                )
            }
            composable(Screen.BlocklistManagement.route) {
                BlocklistManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
