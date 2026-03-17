package com.mobileintelligence.app.engine.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mobileintelligence.app.engine.AppTransitionEvent
import com.mobileintelligence.app.engine.EngineEventBus
import com.mobileintelligence.app.engine.ScreenOnEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Focus Mode — DND-like feature that combines screen monitoring
 * with DNS blocking to create "distraction-free" sessions.
 *
 * When activated:
 * - Screen events are tagged as "focus session"
 * - Configurable app allowlist (only permitted apps don't trigger break alerts)
 * - DNS blocks social media + entertainment domains on top of existing filtering
 * - Tracks focus duration, interruptions, and produces a focus report
 *
 * Supports:
 * - Manual toggle
 * - Scheduled focus windows (e.g., work hours 9-17)
 * - Pomodoro mode (25min focus, 5min break cycles)
 */
class FocusMode(
    private val context: Context,
    private val eventBus: EngineEventBus
) {

    companion object {
        private const val NOTIFICATION_CHANNEL = "focus_mode"
        private const val NOTIFICATION_ID = 4001
        private const val POMODORO_FOCUS_MS = 25 * 60_000L   // 25 min
        private const val POMODORO_BREAK_MS = 5 * 60_000L    // 5 min
        private const val POMODORO_LONG_BREAK_MS = 15 * 60_000L  // 15 min after 4 cycles
    }

    // ── State ────────────────────────────────────────────────────

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _mode = MutableStateFlow(FocusModeType.MANUAL)
    val mode: StateFlow<FocusModeType> = _mode.asStateFlow()

    private val _focusDurationMs = MutableStateFlow(0L)
    val focusDurationMs: StateFlow<Long> = _focusDurationMs.asStateFlow()

    private val _interruptionCount = MutableStateFlow(0)
    val interruptionCount: StateFlow<Int> = _interruptionCount.asStateFlow()

    private val _pomodoroState = MutableStateFlow(PomodoroState.IDLE)
    val pomodoroState: StateFlow<PomodoroState> = _pomodoroState.asStateFlow()

    private val _pomodoroTimeRemainingMs = MutableStateFlow(0L)
    val pomodoroTimeRemainingMs: StateFlow<Long> = _pomodoroTimeRemainingMs.asStateFlow()

    private val _completedPomodoros = MutableStateFlow(0)
    val completedPomodoros: StateFlow<Int> = _completedPomodoros.asStateFlow()

    // ── Configuration ────────────────────────────────────────────

    private val _allowedApps = MutableStateFlow<Set<String>>(emptySet())
    val allowedApps: StateFlow<Set<String>> = _allowedApps.asStateFlow()

    // Extra domains to block during focus (social/entertainment)
    val focusBlockDomains: Set<String> = setOf(
        "facebook.com", "instagram.com", "twitter.com", "x.com",
        "tiktok.com", "snapchat.com", "reddit.com",
        "youtube.com", "netflix.com", "twitch.tv", "discord.com",
        "pinterest.com", "tumblr.com", "linkedin.com",
        "news.ycombinator.com"
    )

    // ── Session tracking ─────────────────────────────────────────

    private var focusStartTime = 0L
    private var scope: CoroutineScope? = null
    private var pomodoroJob: Job? = null
    private val interruptedApps = ConcurrentHashMap<String, Int>()

    // ── Public API ───────────────────────────────────────────────

    fun startFocus(type: FocusModeType = FocusModeType.MANUAL) {
        if (_isActive.value) return

        _isActive.value = true
        _mode.value = type
        _interruptionCount.value = 0
        _focusDurationMs.value = 0
        focusStartTime = System.currentTimeMillis()
        interruptedApps.clear()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startDurationTracker()

        if (type == FocusModeType.POMODORO) {
            startPomodoro()
        }

        createNotificationChannel()
        showNotification("Focus Mode Active", "Stay focused — distractions are being blocked.")

        // Start observing screen events for interruption detection
        scope?.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is ScreenOnEvent -> {
                        // Screen on during focus = potential interruption
                    }
                    is AppTransitionEvent -> {
                        if (event.toPackage !in _allowedApps.value && _allowedApps.value.isNotEmpty()) {
                            _interruptionCount.value++
                            interruptedApps.merge(event.toPackage, 1) { a, b -> a + b }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun stopFocus(): FocusReport {
        val report = generateReport()

        _isActive.value = false
        _pomodoroState.value = PomodoroState.IDLE
        _pomodoroTimeRemainingMs.value = 0
        pomodoroJob?.cancel()
        scope?.cancel()
        scope = null

        cancelNotification()

        return report
    }

    fun setAllowedApps(apps: Set<String>) {
        _allowedApps.value = apps
    }

    fun isAppAllowedDuringFocus(packageName: String): Boolean {
        return !_isActive.value || _allowedApps.value.isEmpty() || packageName in _allowedApps.value
    }

    fun isDomainBlockedDuringFocus(domain: String): Boolean {
        if (!_isActive.value) return false
        val lower = domain.lowercase()
        return focusBlockDomains.any { blocked ->
            lower == blocked || lower.endsWith(".$blocked")
        }
    }

    // ── Pomodoro ─────────────────────────────────────────────────

    private fun startPomodoro() {
        _completedPomodoros.value = 0
        startPomodoroFocusPhase()
    }

    private fun startPomodoroFocusPhase() {
        _pomodoroState.value = PomodoroState.FOCUSING
        _pomodoroTimeRemainingMs.value = POMODORO_FOCUS_MS

        pomodoroJob?.cancel()
        pomodoroJob = scope?.launch {
            var remaining = POMODORO_FOCUS_MS
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _pomodoroTimeRemainingMs.value = remaining.coerceAtLeast(0)
            }
            if (isActive) {
                _completedPomodoros.value++
                showNotification("Focus Phase Complete", "Time for a break!")
                startPomodoroBreakPhase()
            }
        }
    }

    private fun startPomodoroBreakPhase() {
        val isLongBreak = _completedPomodoros.value % 4 == 0
        val breakDuration = if (isLongBreak) POMODORO_LONG_BREAK_MS else POMODORO_BREAK_MS
        _pomodoroState.value = if (isLongBreak) PomodoroState.LONG_BREAK else PomodoroState.BREAK
        _pomodoroTimeRemainingMs.value = breakDuration

        pomodoroJob?.cancel()
        pomodoroJob = scope?.launch {
            var remaining = breakDuration
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _pomodoroTimeRemainingMs.value = remaining.coerceAtLeast(0)
            }
            if (isActive) {
                showNotification("Break Over", "Back to focus!")
                startPomodoroFocusPhase()
            }
        }
    }

    // ── Duration Tracker ─────────────────────────────────────────

    private fun startDurationTracker() {
        scope?.launch {
            while (isActive) {
                _focusDurationMs.value = System.currentTimeMillis() - focusStartTime
                delay(1000)
            }
        }
    }

    // ── Report ───────────────────────────────────────────────────

    private fun generateReport(): FocusReport {
        return FocusReport(
            mode = _mode.value,
            totalDurationMs = System.currentTimeMillis() - focusStartTime,
            interruptionCount = _interruptionCount.value,
            completedPomodoros = _completedPomodoros.value,
            topInterruptingApps = interruptedApps.entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }
        )
    }

    // ── Notifications ────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Focus Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Focus mode status notifications"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, text: String) {
        try {
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun cancelNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    // ── Enums & Data Classes ─────────────────────────────────────

    enum class FocusModeType {
        MANUAL,
        SCHEDULED,
        POMODORO
    }

    enum class PomodoroState {
        IDLE,
        FOCUSING,
        BREAK,
        LONG_BREAK
    }

    data class FocusReport(
        val mode: FocusModeType,
        val totalDurationMs: Long,
        val interruptionCount: Int,
        val completedPomodoros: Int,
        val topInterruptingApps: Map<String, Int>
    ) {
        val focusQualityPercent: Float
            get() {
                val durationMin = totalDurationMs / 60_000f
                if (durationMin < 1) return 0f
                val interruptionsPerHour = interruptionCount / (durationMin / 60f)
                // 0 interruptions/hr = 100%, 10+ = 0%
                return ((1f - interruptionsPerHour / 10f) * 100f).coerceIn(0f, 100f)
            }
    }
}
