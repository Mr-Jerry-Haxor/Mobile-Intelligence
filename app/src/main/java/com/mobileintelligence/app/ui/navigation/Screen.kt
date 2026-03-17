package com.mobileintelligence.app.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Timeline : Screen("timeline")
    data object AppStats : Screen("app_stats")
    data object Insights : Screen("insights")
    data object Settings : Screen("settings")

    // DNS Firewall screens
    data object NetworkProtection : Screen("network_protection")
    data object DnsQueryLog : Screen("dns_query_log")
    data object DnsStats : Screen("dns_stats")
    data object DnsSettings : Screen("dns_settings")
    data object BlocklistManagement : Screen("blocklist_management")

    // Intelligence Engine screens
    data object IntelligenceConsole : Screen("intelligence_console")
    data object Heatmap : Screen("heatmap")
    data object TimelineReplay : Screen("timeline_replay")
    data object Consent : Screen("consent")
    data object FocusMode : Screen("focus_mode")
}
