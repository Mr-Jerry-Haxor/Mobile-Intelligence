package com.mobileintelligence.app.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Thread-safe date utilities using java.time (no SimpleDateFormat).
 * All formatters are immutable & thread-safe.
 */
object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val zone: ZoneId = ZoneId.systemDefault()

    fun today(): String = LocalDate.now().format(dateFormatter)

    fun formatDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().format(dateFormatter)

    fun formatTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime().format(timeFormatter)

    fun formatDateTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(dateTimeFormatter)

    fun getHourOfDay(timestamp: Long): Int =
        Instant.ofEpochMilli(timestamp).atZone(zone).hour

    fun isNightTime(timestamp: Long): Boolean {
        val hour = getHourOfDay(timestamp)
        return hour >= 23 || hour < 5
    }

    fun daysAgo(days: Int): String =
        LocalDate.now().minusDays(days.toLong()).format(dateFormatter)

    fun yearsAgo(years: Int): String =
        LocalDate.now().minusYears(years.toLong()).format(dateFormatter)

    fun getStartOfDay(timestamp: Long): Long {
        val zdt = Instant.ofEpochMilli(timestamp).atZone(zone)
        return zdt.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun getEndOfDay(timestamp: Long): Long {
        val zdt = Instant.ofEpochMilli(timestamp).atZone(zone)
        return zdt.toLocalDate().atTime(LocalTime.of(23, 59, 59, 999_000_000))
            .atZone(zone).toInstant().toEpochMilli()
    }

    fun getStartOfWeek(): String {
        val now = LocalDate.now()
        val start = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start.format(dateFormatter)
    }

    fun getStartOfMonth(): String {
        val now = LocalDate.now()
        return now.withDayOfMonth(1).format(dateFormatter)
    }

    fun isWeekend(date: String): Boolean {
        val d = LocalDate.parse(date, dateFormatter)
        return d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0m"
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (hours == 0L && minutes == 0L) append("${seconds}s")
        }.trim()
    }

    fun formatDurationShort(ms: Long): String {
        if (ms <= 0) return "0m"
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun getMidnightTriggerTime(): Long {
        val tomorrow = LocalDate.now().plusDays(1)
        val midnight = tomorrow.atTime(0, 0, 5)
        return midnight.atZone(zone).toInstant().toEpochMilli()
    }

    fun getDayOfWeek(date: String): String {
        val d = LocalDate.parse(date, dateFormatter)
        return d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    }
}
