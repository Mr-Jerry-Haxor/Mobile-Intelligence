package com.mobileintelligence.app.worker

import android.content.Context
import androidx.work.*
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.entity.AppUsageDaily
import com.mobileintelligence.app.data.database.entity.DailySummary
import com.mobileintelligence.app.data.database.entity.HourlySummary
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.util.DateUtils

class MidnightRolloverWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME = "midnight_rollover"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MidnightRolloverWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        val db = IntelligenceDatabase.getInstance(applicationContext)
        val prefs = AppPreferences(applicationContext)

        // Generate summary for yesterday
        val yesterday = DateUtils.daysAgo(1)
        generateDailySummary(db, yesterday)
        generateHourlySummary(db, yesterday)
        generateAppUsageSummary(db, yesterday)

        // Cleanup old data (older than 3 years)
        val cutoffDate = DateUtils.yearsAgo(3)
        val today = DateUtils.today()
        val lastCleanup = prefs.getLastCleanupDate()

        if (lastCleanup != today) {
            db.screenSessionDao().deleteOlderThan(cutoffDate)
            db.appSessionDao().deleteOlderThan(cutoffDate)
            db.dailySummaryDao().deleteOlderThan(cutoffDate)
            db.hourlySummaryDao().deleteOlderThan(cutoffDate)
            db.appUsageDailyDao().deleteOlderThan(cutoffDate)
            db.unlockEventDao().deleteOlderThan(cutoffDate)
            prefs.setLastCleanupDate(today)
        }

        return Result.success()
    }

    private suspend fun generateDailySummary(db: IntelligenceDatabase, date: String) {
        val totalScreenTime = db.screenSessionDao().getTotalScreenTimeForDate(date) ?: 0
        val sessionCount = db.screenSessionDao().getSessionCountForDate(date)
        val longestSession = db.screenSessionDao().getLongestSessionForDate(date) ?: 0
        val avgSession = db.screenSessionDao().getAverageSessionForDate(date) ?: 0
        val nightUsage = db.screenSessionDao().getNightUsageForDate(date) ?: 0
        val nightSessions = db.screenSessionDao().getNightSessionCountForDate(date)
        val unlockCount = db.unlockEventDao().getCountForDate(date)
        val firstUnlock = db.unlockEventDao().getFirstUnlockOfDay(date)
        val lastScreen = db.screenSessionDao().getLastSessionOfDay(date)
        val uniqueApps = db.appSessionDao().getUniqueAppCountForDate(date)
        val ranking = db.appSessionDao().getAppRankingForDate(date)
        val topApp = ranking.firstOrNull()

        // Compute behavioral scores
        val addictionScore = computeAddictionScore(totalScreenTime, unlockCount, sessionCount)
        val focusScore = computeFocusScore(avgSession, sessionCount, uniqueApps)
        val sleepIndex = computeSleepDisturbanceIndex(nightUsage, nightSessions, totalScreenTime)

        val summary = DailySummary(
            date = date,
            totalScreenTimeMs = totalScreenTime,
            totalSessions = sessionCount,
            totalUnlocks = unlockCount,
            firstUnlockTime = firstUnlock?.timestamp,
            lastScreenOnTime = lastScreen?.screenOnTime,
            longestSessionMs = longestSession,
            averageSessionMs = avgSession,
            nightUsageMs = nightUsage,
            nightSessions = nightSessions,
            mostUsedApp = topApp?.appName,
            mostUsedAppTimeMs = topApp?.totalTimeMs ?: 0,
            uniqueAppsUsed = uniqueApps,
            addictionScore = addictionScore,
            focusScore = focusScore,
            sleepDisturbanceIndex = sleepIndex
        )
        db.dailySummaryDao().insertOrReplace(summary)
    }

    private suspend fun generateHourlySummary(db: IntelligenceDatabase, date: String) {
        val sessions = db.screenSessionDao().getByDateSync(date)
        val unlocks = db.unlockEventDao().getByDateSync(date)

        for (hour in 0..23) {
            val hourlySessions = sessions.filter { it.hourOfDay == hour }
            val hourlyUnlocks = unlocks.filter { it.hourOfDay == hour }
            val totalTime = hourlySessions.sumOf { it.durationMs }

            if (totalTime > 0 || hourlySessions.isNotEmpty()) {
                db.hourlySummaryDao().insertOrReplace(
                    HourlySummary(
                        date = date,
                        hour = hour,
                        screenTimeMs = totalTime,
                        sessionCount = hourlySessions.size,
                        unlockCount = hourlyUnlocks.size
                    )
                )
            }
        }
    }

    private suspend fun generateAppUsageSummary(db: IntelligenceDatabase, date: String) {
        val ranking = db.appSessionDao().getAppRankingForDate(date)
        for (app in ranking) {
            val sessions = db.appSessionDao().getByAppAndDateRange(app.packageName, date, date)
            val totalForeground = sessions.filter { it.isForeground }.sumOf { it.durationMs }
            val lastUsed = sessions.maxOfOrNull { it.startTime }

            db.appUsageDailyDao().insertOrReplace(
                AppUsageDaily(
                    date = date,
                    packageName = app.packageName,
                    appName = app.appName,
                    totalTimeMs = app.totalTimeMs,
                    sessionCount = app.sessionCount,
                    foregroundTimeMs = totalForeground,
                    lastUsedTime = lastUsed,
                    openCount = app.sessionCount
                )
            )
        }
    }

    private fun computeAddictionScore(screenTimeMs: Long, unlocks: Int, sessions: Int): Float {
        // Normalized score 0-100
        val timeScore = (screenTimeMs.toFloat() / (8 * 3600_000)).coerceIn(0f, 1f) * 40
        val unlockScore = (unlocks.toFloat() / 150).coerceIn(0f, 1f) * 30
        val sessionScore = (sessions.toFloat() / 100).coerceIn(0f, 1f) * 30
        return (timeScore + unlockScore + sessionScore).coerceIn(0f, 100f)
    }

    private fun computeFocusScore(avgSessionMs: Long, sessions: Int, uniqueApps: Int): Float {
        // Higher = more focused (long sessions, few apps, few switches)
        val sessionLenScore = (avgSessionMs.toFloat() / 600_000).coerceIn(0f, 1f) * 40
        val appScore = (1f - (uniqueApps.toFloat() / 30).coerceIn(0f, 1f)) * 30
        val switchScore = (1f - (sessions.toFloat() / 80).coerceIn(0f, 1f)) * 30
        return (sessionLenScore + appScore + switchScore).coerceIn(0f, 100f)
    }

    private fun computeSleepDisturbanceIndex(nightMs: Long, nightSessions: Int, totalMs: Long): Float {
        if (totalMs == 0L) return 0f
        val nightRatio = nightMs.toFloat() / totalMs
        val nightScore = nightRatio * 50
        val nightSessionScore = (nightSessions.toFloat() / 10).coerceIn(0f, 1f) * 50
        return (nightScore + nightSessionScore).coerceIn(0f, 100f)
    }
}
