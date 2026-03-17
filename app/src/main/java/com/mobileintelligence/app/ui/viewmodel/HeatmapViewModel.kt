package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * ViewModel for the Heatmap screen.
 *
 * Provides a 7-day × 24-hour grid of screen time intensity
 * plus hourly DNS query density for the privacy overlay.
 */
class HeatmapViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HeatmapVM"
    }

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)
    private val dnsDb = DnsDatabase.getInstance(application)

    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 7 days × 24 hours — value = minutes of screen time in that slot
    private val _screenHeatmap = MutableStateFlow<List<List<Float>>>(
        List(7) { List(24) { 0f } }
    )
    val screenHeatmap: StateFlow<List<List<Float>>> = _screenHeatmap.asStateFlow()

    // 7 days × 24 hours — value = number of DNS queries in that slot
    private val _dnsHeatmap = MutableStateFlow<List<List<Float>>>(
        List(7) { List(24) { 0f } }
    )
    val dnsHeatmap: StateFlow<List<List<Float>>> = _dnsHeatmap.asStateFlow()

    // Which overlay is active
    private val _overlayMode = MutableStateFlow(HeatmapOverlay.SCREEN_TIME)
    val overlayMode: StateFlow<HeatmapOverlay> = _overlayMode.asStateFlow()

    // Day labels (Mon, Tue, …)
    private val _dayLabels = MutableStateFlow<List<String>>(emptyList())
    val dayLabels: StateFlow<List<String>> = _dayLabels.asStateFlow()

    // Peak usage hour for the week
    private val _peakHour = MutableStateFlow(-1)
    val peakHour: StateFlow<Int> = _peakHour.asStateFlow()

    // Total weekly screen time (ms)
    private val _weeklyTotal = MutableStateFlow(0L)
    val weeklyTotal: StateFlow<Long> = _weeklyTotal.asStateFlow()

    init {
        loadHeatmapData()
    }

    fun loadHeatmapData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val today = DateUtils.today()
                val weekAgo = DateUtils.daysAgo(7)
                val days = repository.getDailySummariesSync(weekAgo, today)
                val labels = days.map { it.date.takeLast(5) }.toMutableList()
                // Ensure exactly 7 entries
                while (labels.size < 7) labels.add(0, "")
                _dayLabels.value = labels.takeLast(7)

                // total
                _weeklyTotal.value = days.sumOf { it.totalScreenTimeMs }

                // Build hourly grid from HourlySummary for each day
                val screenGrid = mutableListOf<List<Float>>()

                for (day in days.takeLast(7)) {
                    try {
                        val hourlyFlow = repository.getHourlySummaryForDate(day.date)
                        hourlyFlow.firstOrNull()?.let { hourlyList ->
                            val row = MutableList(24) { 0f }
                            hourlyList.forEach { h ->
                                if (h.hour in 0..23) {
                                    row[h.hour] = h.screenTimeMs / 60_000f  // minutes
                                }
                            }
                            screenGrid.add(row)
                        } ?: screenGrid.add(List(24) { 0f })
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load hourly data for ${day.date}", e)
                        screenGrid.add(List(24) { 0f })
                    }
                }
                while (screenGrid.size < 7) screenGrid.add(0, List(24) { 0f })
                _screenHeatmap.value = screenGrid.takeLast(7)

                // Find peak hour across entire grid
                var maxVal = 0f
                var maxHour = 0
                screenGrid.forEach { row ->
                    row.forEachIndexed { hour, v ->
                        if (v > maxVal) {
                            maxVal = v
                            maxHour = hour
                        }
                    }
                }
                _peakHour.value = maxHour

                // Build DNS heatmap from DnsQueryDao
                loadDnsHeatmap(days.takeLast(7).map { it.date })

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load heatmap data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadDnsHeatmap(dates: List<String>) {
        try {
            val dnsGrid = mutableListOf<List<Float>>()
            val utcOffsetHours = TimeZone.getDefault().rawOffset / 3_600_000

            for (date in dates) {
                try {
                    val hourlyCounts = dnsDb.dnsQueryDao().getHourlyQueryCountsForDate(date, utcOffsetHours)
                    val row = MutableList(24) { 0f }
                    hourlyCounts.forEach { hc ->
                        val hour = hc.hour.coerceIn(0, 23)
                        row[hour] = hc.count.toFloat()
                    }
                    dnsGrid.add(row)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load DNS data for $date", e)
                    dnsGrid.add(List(24) { 0f })
                }
            }

            // Pad to 7 rows
            while (dnsGrid.size < 7) dnsGrid.add(0, List(24) { 0f })
            _dnsHeatmap.value = dnsGrid.takeLast(7)

            Log.d(TAG, "DNS heatmap loaded: ${dnsGrid.sumOf { row -> row.sumOf { it.toDouble() } }.toInt()} total queries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load DNS heatmap", e)
        }
    }

    fun setOverlay(mode: HeatmapOverlay) {
        _overlayMode.value = mode
    }

    enum class HeatmapOverlay {
        SCREEN_TIME,
        DNS_QUERIES,
        PRIVACY_EXPOSURE
    }
}
