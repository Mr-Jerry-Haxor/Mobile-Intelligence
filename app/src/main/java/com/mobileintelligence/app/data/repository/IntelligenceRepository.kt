package com.mobileintelligence.app.data.repository

import com.mobileintelligence.app.analytics.AnalyticsEngine
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.dao.AppRanking
import com.mobileintelligence.app.data.database.dao.HourlyAverage
import com.mobileintelligence.app.data.database.dao.UnlockHourCount
import com.mobileintelligence.app.data.database.entity.*
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

class IntelligenceRepository(private val db: IntelligenceDatabase) {

    // ── Screen Sessions ──────────────────────────────────────────────

    fun getScreenSessionsForDate(date: String): Flow<List<ScreenSession>> =
        db.screenSessionDao().getByDate(date)

    fun getScreenSessionsForRange(startDate: String, endDate: String): Flow<List<ScreenSession>> =
        db.screenSessionDao().getByDateRange(startDate, endDate)

    suspend fun getTotalScreenTimeForDate(date: String): Long =
        db.screenSessionDao().getTotalScreenTimeForDate(date) ?: 0

    suspend fun getSessionCountForDate(date: String): Int =
        db.screenSessionDao().getSessionCountForDate(date)

    // ── App Sessions ─────────────────────────────────────────────────

    fun getAppSessionsForDate(date: String): Flow<List<AppSession>> =
        db.appSessionDao().getByDate(date)

    suspend fun getAppRankingForDate(date: String): List<AppRanking> =
        db.appSessionDao().getAppRankingForDate(date)

    suspend fun getAppRankingForRange(startDate: String, endDate: String): List<AppRanking> =
        db.appSessionDao().getAppRankingForRange(startDate, endDate)

    suspend fun getAppSessionsByApp(packageName: String, startDate: String, endDate: String): List<AppSession> =
        db.appSessionDao().getByAppAndDateRange(packageName, startDate, endDate)

    // ── Daily Summary ────────────────────────────────────────────────

    fun getDailySummaryFlow(date: String): Flow<DailySummary?> =
        db.dailySummaryDao().getByDateFlow(date)

    suspend fun getDailySummary(date: String): DailySummary? =
        db.dailySummaryDao().getByDate(date)

    fun getDailySummariesForRange(startDate: String, endDate: String): Flow<List<DailySummary>> =
        db.dailySummaryDao().getByDateRange(startDate, endDate)

    suspend fun getDailySummariesSync(startDate: String, endDate: String): List<DailySummary> =
        db.dailySummaryDao().getByDateRangeSync(startDate, endDate)

    suspend fun getRecentDailySummaries(days: Int): List<DailySummary> =
        db.dailySummaryDao().getRecentDays(days)

    // ── Hourly Summary ───────────────────────────────────────────────

    fun getHourlySummaryForDate(date: String): Flow<List<HourlySummary>> =
        db.hourlySummaryDao().getByDate(date)

    suspend fun getAverageHourlyPattern(startDate: String, endDate: String): List<HourlyAverage> =
        db.hourlySummaryDao().getAverageHourlyPattern(startDate, endDate)

    // ── App Usage Daily ──────────────────────────────────────────────

    fun getAppUsageForDate(date: String): Flow<List<AppUsageDaily>> =
        db.appUsageDailyDao().getByDate(date)

    suspend fun getAppUsageForRange(startDate: String, endDate: String): List<AppUsageDaily> =
        db.appUsageDailyDao().getByDateRange(startDate, endDate)

    suspend fun getAppUsageTrend(packageName: String, startDate: String, endDate: String): List<AppUsageDaily> =
        db.appUsageDailyDao().getByAppAndDateRange(packageName, startDate, endDate)

    // ── Unlock Events ────────────────────────────────────────────────

    fun getUnlockEventsForDate(date: String): Flow<List<UnlockEvent>> =
        db.unlockEventDao().getByDate(date)

    suspend fun getUnlockCountForDate(date: String): Int =
        db.unlockEventDao().getCountForDate(date)

    suspend fun getUnlocksByHour(startDate: String, endDate: String): List<UnlockHourCount> =
        db.unlockEventDao().getUnlocksByHour(startDate, endDate)

    suspend fun getAverageDailyUnlocks(startDate: String, endDate: String): Float =
        db.unlockEventDao().getAverageDailyUnlocks(startDate, endDate) ?: 0f

    // ── Analytics ────────────────────────────────────────────────────

    suspend fun getWeeklyReport(): AnalyticsEngine.WeeklyReport {
        val today = DateUtils.today()
        val weekAgo = DateUtils.daysAgo(7)
        val twoWeeksAgo = DateUtils.daysAgo(14)

        val thisWeek = db.dailySummaryDao().getByDateRangeSync(weekAgo, today)
        val lastWeek = db.dailySummaryDao().getByDateRangeSync(twoWeeksAgo, weekAgo)

        return AnalyticsEngine.generateWeeklyReport(thisWeek, lastWeek)
    }

    suspend fun getSmartInsights(): List<AnalyticsEngine.SmartInsight> {
        val today = DateUtils.today()
        val todaySummary = db.dailySummaryDao().getByDate(today)
        val weekData = db.dailySummaryDao().getByDateRangeSync(DateUtils.daysAgo(7), today)
        val monthData = db.dailySummaryDao().getByDateRangeSync(DateUtils.daysAgo(30), today)

        return AnalyticsEngine.generateSmartInsights(todaySummary, weekData, monthData)
    }

    suspend fun getPredictedTomorrowUsage(): Long {
        val recentDays = db.dailySummaryDao().getRecentDays(14)
        return AnalyticsEngine.predictTomorrowUsage(recentDays)
    }

    suspend fun getBingeSessions(): List<AnalyticsEngine.BingeSession> {
        val summaries = db.dailySummaryDao().getRecentDays(30)
        return AnalyticsEngine.detectBingeSessions(summaries)
    }

    // ── Stats Queries ────────────────────────────────────────────────

    suspend fun getAverageScreenTime(days: Int): Long {
        val endDate = DateUtils.today()
        val startDate = DateUtils.daysAgo(days)
        return db.dailySummaryDao().getAverageScreenTime(startDate, endDate) ?: 0
    }

    suspend fun getMaxScreenTime(days: Int): Long {
        val endDate = DateUtils.today()
        val startDate = DateUtils.daysAgo(days)
        return db.dailySummaryDao().getMaxScreenTime(startDate, endDate) ?: 0
    }

    suspend fun getHighestUsageDay(): DailySummary? =
        db.dailySummaryDao().getHighestUsageDay()

    suspend fun getTotalRecordsCount(): Int =
        db.screenSessionDao().getTotalCount()

    suspend fun getOldestRecordDate(): String? =
        db.screenSessionDao().getOldestDate()

    // ── Data Management ──────────────────────────────────────────────

    suspend fun purgeAllData() {
        val farFuture = "9999-12-31"
        db.screenSessionDao().deleteOlderThan(farFuture)
        db.appSessionDao().deleteOlderThan(farFuture)
        db.dailySummaryDao().deleteOlderThan(farFuture)
        db.hourlySummaryDao().deleteOlderThan(farFuture)
        db.appUsageDailyDao().deleteOlderThan(farFuture)
        db.unlockEventDao().deleteOlderThan(farFuture)
    }

    // ── Generate today's summary on demand ───────────────────────────

    suspend fun generateTodaySummary() {
        val today = DateUtils.today()
        val totalScreenTime = db.screenSessionDao().getTotalScreenTimeForDate(today) ?: 0
        val sessionCount = db.screenSessionDao().getSessionCountForDate(today)
        val longestSession = db.screenSessionDao().getLongestSessionForDate(today) ?: 0
        val avgSession = db.screenSessionDao().getAverageSessionForDate(today) ?: 0
        val nightUsage = db.screenSessionDao().getNightUsageForDate(today) ?: 0
        val nightSessions = db.screenSessionDao().getNightSessionCountForDate(today)
        val unlockCount = db.unlockEventDao().getCountForDate(today)
        val firstUnlock = db.unlockEventDao().getFirstUnlockOfDay(today)
        val lastScreen = db.screenSessionDao().getLastSessionOfDay(today)
        val uniqueApps = db.appSessionDao().getUniqueAppCountForDate(today)
        val ranking = db.appSessionDao().getAppRankingForDate(today)
        val topApp = ranking.firstOrNull()

        val addictionScore = computeScore(totalScreenTime, unlockCount, sessionCount)
        val focusScore = computeFocus(avgSession, sessionCount, uniqueApps)
        val sleepIndex = computeSleep(nightUsage, nightSessions, totalScreenTime)

        val summary = DailySummary(
            date = today,
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

    private fun computeScore(screenTimeMs: Long, unlocks: Int, sessions: Int): Float {
        val t = (screenTimeMs.toFloat() / (8 * 3600_000)).coerceIn(0f, 1f) * 40
        val u = (unlocks.toFloat() / 150).coerceIn(0f, 1f) * 30
        val s = (sessions.toFloat() / 100).coerceIn(0f, 1f) * 30
        return (t + u + s).coerceIn(0f, 100f)
    }

    private fun computeFocus(avgMs: Long, sessions: Int, apps: Int): Float {
        val l = (avgMs.toFloat() / 600_000).coerceIn(0f, 1f) * 40
        val a = (1f - (apps.toFloat() / 30).coerceIn(0f, 1f)) * 30
        val s = (1f - (sessions.toFloat() / 80).coerceIn(0f, 1f)) * 30
        return (l + a + s).coerceIn(0f, 100f)
    }

    private fun computeSleep(nightMs: Long, nightSessions: Int, totalMs: Long): Float {
        if (totalMs == 0L) return 0f
        val r = nightMs.toFloat() / totalMs * 50
        val s = (nightSessions.toFloat() / 10).coerceIn(0f, 1f) * 50
        return (r + s).coerceIn(0f, 100f)
    }
}
