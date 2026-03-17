package com.mobileintelligence.app.analytics

import com.mobileintelligence.app.data.database.entity.DailySummary

/**
 * Analytics engine for behavioral insights and predictions.
 */
object AnalyticsEngine {

    // ── Behavioral Insights ──────────────────────────────────────────

    data class WeeklyReport(
        val totalScreenTime: Long,
        val avgDailyScreenTime: Long,
        val totalUnlocks: Int,
        val avgDailyUnlocks: Float,
        val avgAddictionScore: Float,
        val avgFocusScore: Float,
        val avgSleepDisturbance: Float,
        val screenTimeChange: Float, // % change from previous week
        val mostActiveDay: String?,
        val leastActiveDay: String?,
        val insights: List<String>
    )

    fun generateWeeklyReport(
        thisWeek: List<DailySummary>,
        lastWeek: List<DailySummary>
    ): WeeklyReport {
        val totalScreenTime = thisWeek.sumOf { it.totalScreenTimeMs }
        val avgDaily = if (thisWeek.isNotEmpty()) totalScreenTime / thisWeek.size else 0L
        val totalUnlocks = thisWeek.sumOf { it.totalUnlocks }
        val avgUnlocks = if (thisWeek.isNotEmpty()) totalUnlocks.toFloat() / thisWeek.size else 0f

        val avgAddiction = thisWeek.map { it.addictionScore }.average().toFloat()
        val avgFocus = thisWeek.map { it.focusScore }.average().toFloat()
        val avgSleep = thisWeek.map { it.sleepDisturbanceIndex }.average().toFloat()

        val lastWeekTotal = lastWeek.sumOf { it.totalScreenTimeMs }
        val changePercent = if (lastWeekTotal > 0) {
            ((totalScreenTime - lastWeekTotal).toFloat() / lastWeekTotal) * 100
        } else 0f

        val mostActive = thisWeek.maxByOrNull { it.totalScreenTimeMs }?.date
        val leastActive = thisWeek.minByOrNull { it.totalScreenTimeMs }?.date

        val insights = generateInsights(thisWeek, lastWeek, changePercent)

        return WeeklyReport(
            totalScreenTime = totalScreenTime,
            avgDailyScreenTime = avgDaily,
            totalUnlocks = totalUnlocks,
            avgDailyUnlocks = avgUnlocks,
            avgAddictionScore = avgAddiction,
            avgFocusScore = avgFocus,
            avgSleepDisturbance = avgSleep,
            screenTimeChange = changePercent,
            mostActiveDay = mostActive,
            leastActiveDay = leastActive,
            insights = insights
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateInsights(
        thisWeek: List<DailySummary>,
        lastWeek: List<DailySummary>,
        changePercent: Float
    ): List<String> {
        val insights = mutableListOf<String>()

        // Screen time trend
        if (changePercent > 15) {
            insights.add("Screen time increased ${changePercent.toInt()}% compared to last week")
        } else if (changePercent < -15) {
            insights.add("Great! Screen time decreased ${(-changePercent).toInt()}% from last week")
        }

        // Night usage detection
        val totalNight = thisWeek.sumOf { it.nightUsageMs }
        val totalScreen = thisWeek.sumOf { it.totalScreenTimeMs }
        if (totalScreen > 0 && totalNight.toFloat() / totalScreen > 0.2f) {
            insights.add("${((totalNight.toFloat() / totalScreen) * 100).toInt()}% of usage happens at night (11PM-5AM)")
        }

        // Binge detection
        val bingeDays = thisWeek.count { it.totalScreenTimeMs > 6 * 3600_000 }
        if (bingeDays > 0) {
            insights.add("$bingeDays day(s) with 6+ hours of screen time detected")
        }

        // Compulsive checking
        val avgUnlocks = thisWeek.map { it.totalUnlocks }.average()
        if (avgUnlocks > 100) {
            insights.add("Average ${avgUnlocks.toInt()} unlocks per day — consider reducing pickups")
        }

        // Focus insight
        val avgFocus = thisWeek.map { it.focusScore }.average()
        if (avgFocus < 30) {
            insights.add("Low focus score — many short sessions and frequent app switching")
        } else if (avgFocus > 70) {
            insights.add("High focus score — fewer distractions and longer focused sessions")
        }

        // Most used app
        val topAppCounts = thisWeek.mapNotNull { it.mostUsedApp }.groupingBy { it }.eachCount()
        val topApp = topAppCounts.maxByOrNull { it.value }
        topApp?.let {
            insights.add("${it.key} was your most used app on ${it.value} of ${thisWeek.size} days")
        }

        return insights
    }

    // ── Prediction ──────────────────────────────────────────────────

    fun predictTomorrowUsage(recentDays: List<DailySummary>): Long {
        if (recentDays.isEmpty()) return 0

        // Weighted moving average (recent days weighted more)
        val weights = recentDays.mapIndexed { i, _ -> (i + 1).toFloat() }
        val totalWeight = weights.sum()
        val weightedSum = recentDays.zip(weights).sumOf { (summary, weight) ->
            (summary.totalScreenTimeMs * weight).toLong()
        }

        return (weightedSum / totalWeight).toLong()
    }

    // ── Binge Detection ─────────────────────────────────────────────

    data class BingeSession(
        val date: String,
        val durationMs: Long,
        val startHour: Int
    )

    fun detectBingeSessions(summaries: List<DailySummary>, thresholdMs: Long = 4 * 3600_000): List<BingeSession> {
        return summaries
            .filter { it.longestSessionMs > thresholdMs }
            .map { BingeSession(it.date, it.longestSessionMs, 0) }
    }

    // ── Smart Insights (Natural Language) ───────────────────────────

    data class SmartInsight(
        val message: String,
        val category: InsightCategory,
        val severity: InsightSeverity
    )

    enum class InsightCategory { USAGE, SLEEP, PATTERN, APP, POSITIVE }
    enum class InsightSeverity { INFO, WARNING, ALERT, POSITIVE }

    fun generateSmartInsights(
        today: DailySummary?,
        recentWeek: List<DailySummary>,
        recentMonth: List<DailySummary>
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()

        today?.let { t ->
            // Current day insight
            if (t.totalScreenTimeMs > 5 * 3600_000) {
                insights.add(
                    SmartInsight(
                        "You've used your phone for ${formatHours(t.totalScreenTimeMs)} today",
                        InsightCategory.USAGE,
                        InsightSeverity.WARNING
                    )
                )
            }
        }

        if (recentWeek.isNotEmpty()) {
            // Peak unlock hour
            val peakHour = recentWeek
                .filter { it.lastScreenOnTime != null }
                .groupingBy { 
                    val hour = com.mobileintelligence.app.util.DateUtils.getHourOfDay(it.lastScreenOnTime!!)
                    hour
                }
                .eachCount()
                .maxByOrNull { it.value }

            peakHour?.let {
                insights.add(
                    SmartInsight(
                        "You use your phone most around ${formatHour(it.key)}",
                        InsightCategory.PATTERN,
                        InsightSeverity.INFO
                    )
                )
            }

            // Weekend vs weekday
            val weekdays = recentWeek.filter { !com.mobileintelligence.app.util.DateUtils.isWeekend(it.date) }
            val weekends = recentWeek.filter { com.mobileintelligence.app.util.DateUtils.isWeekend(it.date) }
            
            if (weekdays.isNotEmpty() && weekends.isNotEmpty()) {
                val avgWeekday = weekdays.map { it.totalScreenTimeMs }.average()
                val avgWeekend = weekends.map { it.totalScreenTimeMs }.average()
                if (avgWeekend > avgWeekday * 1.3) {
                    insights.add(
                        SmartInsight(
                            "Weekend screen time is ${((avgWeekend / avgWeekday - 1) * 100).toInt()}% higher than weekdays",
                            InsightCategory.PATTERN,
                            InsightSeverity.INFO
                        )
                    )
                }
            }
        }

        // Monthly comparison
        if (recentMonth.size >= 14) {
            val firstHalf = recentMonth.take(recentMonth.size / 2)
            val secondHalf = recentMonth.drop(recentMonth.size / 2)
            val firstAvg = firstHalf.map { it.totalScreenTimeMs }.average()
            val secondAvg = secondHalf.map { it.totalScreenTimeMs }.average()

            if (firstAvg > 0) {
                val change = ((secondAvg - firstAvg) / firstAvg * 100).toInt()
                if (change > 20) {
                    insights.add(
                        SmartInsight(
                            "Average session time increased ${change}% this month",
                            InsightCategory.USAGE,
                            InsightSeverity.WARNING
                        )
                    )
                } else if (change < -20) {
                    insights.add(
                        SmartInsight(
                            "Usage decreased ${-change}% — keep it up!",
                            InsightCategory.POSITIVE,
                            InsightSeverity.POSITIVE
                        )
                    )
                }
            }
        }

        return insights
    }

    private fun formatHours(ms: Long): String {
        val hours = ms / 3600_000f
        return "%.1f hours".format(hours)
    }

    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }
}
