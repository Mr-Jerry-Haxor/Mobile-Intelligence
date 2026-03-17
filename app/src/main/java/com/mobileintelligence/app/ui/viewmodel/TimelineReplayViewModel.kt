package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for the Timeline Replay screen.
 *
 * Merges screen events, DNS events, and intelligence alerts
 * into a single chronological timeline for any selected day.
 */
class TimelineReplayViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)
    private val dnsDb = DnsDatabase.getInstance(application)
    private val dnsQueryDao = dnsDb.dnsQueryDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    // ── State ────────────────────────────────────────────────────

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _timelineEvents = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineEvents: StateFlow<List<TimelineEvent>> = _timelineEvents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _eventCounts = MutableStateFlow(EventCounts())
    val eventCounts: StateFlow<EventCounts> = _eventCounts.asStateFlow()

    init {
        loadTimeline()
    }

    fun previousDay() {
        shiftDate(-1)
    }

    fun nextDay() {
        shiftDate(1)
    }

    fun goToToday() {
        _selectedDate.value = DateUtils.today()
        loadTimeline()
    }

    private fun shiftDate(days: Int) {
        try {
            val cal = Calendar.getInstance()
            val current = dateFormat.parse(_selectedDate.value)
            if (current != null) {
                cal.time = current
                cal.add(Calendar.DAY_OF_YEAR, days)
                _selectedDate.value = dateFormat.format(cal.time)
                loadTimeline()
            }
        } catch (_: Exception) {}
    }

    private fun loadTimeline() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val date = _selectedDate.value
                val events = mutableListOf<TimelineEvent>()
                var screenCount = 0
                var dnsCount = 0
                var alertCount = 0
                var idCounter = 0L

                // ── Screen Sessions ─────────────────────────

                val sessions = db.screenSessionDao().getByDateSync(date)
                sessions.forEach { session ->
                    screenCount++
                    val duration = session.durationMs
                    events.add(
                        TimelineEvent(
                            id = idCounter++,
                            timestamp = session.screenOnTime,
                            timeLabel = formatTime(session.screenOnTime),
                            category = EventCategory.SESSION,
                            title = "Session: ${DateUtils.formatDurationShort(duration)}",
                            subtitle = if (session.isNightUsage) "Night" else "Normal",
                            details = buildMap {
                                put("Duration", DateUtils.formatDurationShort(duration))
                                put("Unlocked", if (session.wasUnlocked) "Yes" else "No")
                                if (session.isNightUsage) put("Night Usage", "Yes")
                            }
                        )
                    )
                }

                // ── Unlock Events ───────────────────────────

                val unlocks = db.unlockEventDao().getByDateSync(date)
                unlocks.forEach { unlock ->
                    screenCount++
                    events.add(
                        TimelineEvent(
                            id = idCounter++,
                            timestamp = unlock.timestamp,
                            timeLabel = formatTime(unlock.timestamp),
                            category = EventCategory.UNLOCK,
                            title = "Unlock",
                            subtitle = if (unlock.isFirstAfterBoot) "After boot" else "",
                            details = buildMap {
                                if (unlock.isFirstAfterBoot) put("Source", "After boot")
                                unlock.timeSinceLastUnlockMs?.let {
                                    put("Since last", DateUtils.formatDurationShort(it))
                                }
                            }
                        )
                    )
                }

                // ── DNS Queries (sampled — take notable ones) ──

                val dnsQueries = dnsQueryDao.getQueriesForDate(date).first()
                // Group by minute to avoid overwhelming the timeline
                val groupedDns = dnsQueries.groupBy { q ->
                    q.timestamp / 60_000 // group by minute
                }
                groupedDns.entries.take(200).forEach { (minuteKey, queries) ->
                    dnsCount += queries.size
                    val blocked = queries.count { it.blocked }
                    val sample = queries.first()
                    events.add(
                        TimelineEvent(
                            id = idCounter++,
                            timestamp = minuteKey * 60_000,
                            timeLabel = formatTime(minuteKey * 60_000),
                            category = EventCategory.DNS,
                            title = "${queries.size} DNS queries" +
                                    if (blocked > 0) " ($blocked blocked)" else "",
                            subtitle = sample.domain,
                            details = buildMap {
                                put("Total", "${queries.size} queries")
                                put("Blocked", "$blocked")
                                put("Sample", sample.domain)
                                val apps = queries.mapNotNull { it.appPackage }.distinct().take(3)
                                if (apps.isNotEmpty()) put("Apps", apps.joinToString(", "))
                            }
                        )
                    )
                }

                // Sort by timestamp
                events.sortBy { it.timestamp }

                _timelineEvents.value = events
                _eventCounts.value = EventCounts(
                    screen = screenCount,
                    dns = dnsCount,
                    alerts = alertCount
                )
            } catch (_: Exception) {
                _timelineEvents.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    private fun formatTime(timestampMs: Long): String {
        return try {
            timeFormat.format(Date(timestampMs))
        } catch (_: Exception) {
            ""
        }
    }

    // ── Data Classes ────────────────────────────────────────────

    data class TimelineEvent(
        val id: Long,
        val timestamp: Long,
        val timeLabel: String,
        val category: EventCategory,
        val title: String,
        val subtitle: String = "",
        val details: Map<String, String> = emptyMap()
    )

    data class EventCounts(
        val screen: Int = 0,
        val dns: Int = 0,
        val alerts: Int = 0
    )

    enum class EventCategory {
        SCREEN,
        DNS,
        ALERT,
        SESSION,
        UNLOCK
    }
}
