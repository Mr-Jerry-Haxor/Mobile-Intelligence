package com.mobileintelligence.app.engine.features

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Silent Mode Scheduler — automated quiet-time windows.
 *
 * When active:
 * - Suppresses all intelligence notifications (patterns/alerts)
 * - Optionally pauses screen event bus emissions
 * - Maintains data collection (just stops notifications)
 * - Reports post-silent-period summary when window ends
 *
 * Supports:
 * - Daily recurring schedules (e.g., 22:00–07:00 every day)
 * - One-time silent periods (e.g., "silent for 2 hours")
 * - Weekday/weekend differentiation
 */
class SilentModeScheduler(
    private val context: Context
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L // Check every minute
    }

    // ── State ────────────────────────────────────────────────────

    private val _isSilent = MutableStateFlow(false)
    val isSilent: StateFlow<Boolean> = _isSilent.asStateFlow()

    private val _currentSchedule = MutableStateFlow<SilentSchedule?>(null)
    val currentSchedule: StateFlow<SilentSchedule?> = _currentSchedule.asStateFlow()

    private val _schedules = MutableStateFlow<List<SilentSchedule>>(emptyList())
    val schedules: StateFlow<List<SilentSchedule>> = _schedules.asStateFlow()

    // Events suppressed during silent mode (for post-period summary)
    private val _suppressedCount = MutableStateFlow(0)
    val suppressedCount: StateFlow<Int> = _suppressedCount.asStateFlow()

    private var scope: CoroutineScope? = null
    private var checkJob: Job? = null
    private var oneTimeEndMs: Long? = null

    // ── Lifecycle ────────────────────────────────────────────────

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        checkJob = coroutineScope.launch {
            while (isActive) {
                evaluateSchedules()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        checkJob?.cancel()
        _isSilent.value = false
    }

    // ── Schedule Management ──────────────────────────────────────

    fun addSchedule(schedule: SilentSchedule) {
        val current = _schedules.value.toMutableList()
        current.add(schedule)
        _schedules.value = current
    }

    fun removeSchedule(id: String) {
        _schedules.value = _schedules.value.filter { it.id != id }
    }

    fun clearSchedules() {
        _schedules.value = emptyList()
    }

    // ── One-Time Silent ──────────────────────────────────────────

    /**
     * Activate silent mode for the given duration.
     */
    fun silentFor(durationMs: Long) {
        oneTimeEndMs = System.currentTimeMillis() + durationMs
        _isSilent.value = true
        _currentSchedule.value = SilentSchedule(
            id = "one_time",
            name = "One-Time Silent",
            startHour = -1,
            startMinute = -1,
            endHour = -1,
            endMinute = -1,
            daysOfWeek = emptySet(),
            enabled = true
        )
    }

    /**
     * Cancel silent mode immediately.
     */
    fun cancelSilent() {
        oneTimeEndMs = null
        _isSilent.value = false
        _currentSchedule.value = null
    }

    /**
     * Call this before sending a notification — if true, suppress it.
     */
    fun shouldSuppressNotification(): Boolean {
        if (_isSilent.value) {
            _suppressedCount.value++
            return true
        }
        return false
    }

    fun resetSuppressedCount() {
        _suppressedCount.value = 0
    }

    // ── Schedule Evaluation ──────────────────────────────────────

    private fun evaluateSchedules() {
        // Check one-time silent
        oneTimeEndMs?.let { endMs ->
            if (System.currentTimeMillis() >= endMs) {
                oneTimeEndMs = null
                _isSilent.value = false
                _currentSchedule.value = null
                return
            }
            return // Still in one-time silent, don't evaluate schedules
        }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = calendarDayToEnum(now.get(Calendar.DAY_OF_WEEK))

        var matchedSchedule: SilentSchedule? = null

        for (schedule in _schedules.value) {
            if (!schedule.enabled) continue
            if (currentDay !in schedule.daysOfWeek) continue

            if (isTimeInWindow(
                    currentHour, currentMinute,
                    schedule.startHour, schedule.startMinute,
                    schedule.endHour, schedule.endMinute
                )
            ) {
                matchedSchedule = schedule
                break
            }
        }

        if (matchedSchedule != null) {
            if (!_isSilent.value) {
                _isSilent.value = true
                _currentSchedule.value = matchedSchedule
                resetSuppressedCount()
            }
        } else {
            if (_isSilent.value && oneTimeEndMs == null) {
                _isSilent.value = false
                _currentSchedule.value = null
            }
        }
    }

    /**
     * Handles overnight windows correctly (e.g., 22:00 → 07:00).
     */
    private fun isTimeInWindow(
        hour: Int, minute: Int,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ): Boolean {
        val currentMinutes = hour * 60 + minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            // Same-day window (e.g., 09:00 → 17:00)
            currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g., 22:00 → 07:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    private fun calendarDayToEnum(calDay: Int): DayOfWeek = when (calDay) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        else -> DayOfWeek.MONDAY
    }

    // ── Data Classes & Enums ─────────────────────────────────────

    data class SilentSchedule(
        val id: String,
        val name: String,
        val startHour: Int,    // 0-23
        val startMinute: Int,  // 0-59
        val endHour: Int,      // 0-23
        val endMinute: Int,    // 0-59
        val daysOfWeek: Set<DayOfWeek>,
        val enabled: Boolean = true
    ) {
        val timeRangeLabel: String
            get() = if (startHour < 0) "One-time"
            else "${"$startHour".padStart(2, '0')}:${"$startMinute".padStart(2, '0')} → " +
                    "${"$endHour".padStart(2, '0')}:${"$endMinute".padStart(2, '0')}"

        val daysLabel: String
            get() = when {
                daysOfWeek.size == 7 -> "Every day"
                daysOfWeek == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "Weekdays"
                daysOfWeek == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
                else -> daysOfWeek.joinToString(", ") { it.short }
            }
    }

    enum class DayOfWeek(val short: String) {
        MONDAY("Mon"),
        TUESDAY("Tue"),
        WEDNESDAY("Wed"),
        THURSDAY("Thu"),
        FRIDAY("Fri"),
        SATURDAY("Sat"),
        SUNDAY("Sun")
    }
}
