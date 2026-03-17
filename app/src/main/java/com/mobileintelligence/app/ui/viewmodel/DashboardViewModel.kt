package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.analytics.AnalyticsEngine
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.dao.AppRanking
import com.mobileintelligence.app.data.database.entity.*
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)
    private val prefs = AppPreferences(application)

    // ── State Flows ──────────────────────────────────────────────────

    private val _todaySummary = MutableStateFlow<DailySummary?>(null)
    val todaySummary: StateFlow<DailySummary?> = _todaySummary.asStateFlow()

    private val _todayScreenTime = MutableStateFlow(0L)
    val todayScreenTime: StateFlow<Long> = _todayScreenTime.asStateFlow()

    private val _todayUnlocks = MutableStateFlow(0)
    val todayUnlocks: StateFlow<Int> = _todayUnlocks.asStateFlow()

    private val _todaySessions = MutableStateFlow(0)
    val todaySessions: StateFlow<Int> = _todaySessions.asStateFlow()

    private val _weeklyData = MutableStateFlow<List<DailySummary>>(emptyList())
    val weeklyData: StateFlow<List<DailySummary>> = _weeklyData.asStateFlow()

    private val _topApps = MutableStateFlow<List<AppRanking>>(emptyList())
    val topApps: StateFlow<List<AppRanking>> = _topApps.asStateFlow()

    private val _insights = MutableStateFlow<List<AnalyticsEngine.SmartInsight>>(emptyList())
    val insights: StateFlow<List<AnalyticsEngine.SmartInsight>> = _insights.asStateFlow()

    private val _predictedTomorrow = MutableStateFlow(0L)
    val predictedTomorrow: StateFlow<Long> = _predictedTomorrow.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _hourlyData = MutableStateFlow<List<HourlySummary>>(emptyList())
    val hourlyData: StateFlow<List<HourlySummary>> = _hourlyData.asStateFlow()

    init {
        loadDashboard()
        observeServiceState()
    }

    fun loadDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = DateUtils.today()

            // Generate live summary for today
            repository.generateTodaySummary()

            // Load today's data
            _todaySummary.value = repository.getDailySummary(today)
            _todayScreenTime.value = repository.getTotalScreenTimeForDate(today)
            _todayUnlocks.value = repository.getUnlockCountForDate(today)
            _todaySessions.value = repository.getSessionCountForDate(today)
            _topApps.value = repository.getAppRankingForDate(today).take(10)

            // Load hourly
            repository.getHourlySummaryForDate(today).collect { hourly ->
                _hourlyData.value = hourly
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val weekAgo = DateUtils.daysAgo(7)
            val today = DateUtils.today()
            _weeklyData.value = repository.getDailySummariesSync(weekAgo, today)
        }

        viewModelScope.launch(Dispatchers.IO) {
            _insights.value = repository.getSmartInsights()
        }

        viewModelScope.launch(Dispatchers.IO) {
            _predictedTomorrow.value = repository.getPredictedTomorrowUsage()
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            prefs.isServiceRunning.collect {
                _isServiceRunning.value = it
            }
        }
    }

    fun refresh() = loadDashboard()
}
