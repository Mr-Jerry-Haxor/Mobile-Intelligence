package com.mobileintelligence.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

data class ForegroundAppInfo(
    val packageName: String,
    val appName: String,
    val timestamp: Long
)

object UsageStatsHelper {

    fun getForegroundApp(context: Context): ForegroundAppInfo? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        val event = UsageEvents.Event()
        var lastForeground: ForegroundAppInfo? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForeground = ForegroundAppInfo(
                    packageName = event.packageName,
                    appName = getAppName(context, event.packageName),
                    timestamp = event.timeStamp
                )
            }
        }
        return lastForeground
    }

    fun getRecentAppTimeline(context: Context, startTime: Long, endTime: Long): List<ForegroundAppInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val events = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val timeline = mutableListOf<ForegroundAppInfo>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                timeline.add(
                    ForegroundAppInfo(
                        packageName = event.packageName,
                        appName = getAppName(context, event.packageName),
                        timestamp = event.timeStamp
                    )
                )
            }
        }
        return timeline
    }

    fun hasUsagePermission(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000, now)
        return stats != null && stats.isNotEmpty()
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
