package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.analytics.AnalyticsEngine
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.entity.DailySummary
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)

    private val _weeklyReport = MutableStateFlow<AnalyticsEngine.WeeklyReport?>(null)
    val weeklyReport: StateFlow<AnalyticsEngine.WeeklyReport?> = _weeklyReport.asStateFlow()

    private val _smartInsights = MutableStateFlow<List<AnalyticsEngine.SmartInsight>>(emptyList())
    val smartInsights: StateFlow<List<AnalyticsEngine.SmartInsight>> = _smartInsights.asStateFlow()

    private val _predictedUsage = MutableStateFlow(0L)
    val predictedUsage: StateFlow<Long> = _predictedUsage.asStateFlow()

    private val _bingeSessions = MutableStateFlow<List<AnalyticsEngine.BingeSession>>(emptyList())
    val bingeSessions: StateFlow<List<AnalyticsEngine.BingeSession>> = _bingeSessions.asStateFlow()

    private val _monthlyData = MutableStateFlow<List<DailySummary>>(emptyList())
    val monthlyData: StateFlow<List<DailySummary>> = _monthlyData.asStateFlow()

    init {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch(Dispatchers.IO) {
            _weeklyReport.value = repository.getWeeklyReport()
        }
        viewModelScope.launch(Dispatchers.IO) {
            _smartInsights.value = repository.getSmartInsights()
        }
        viewModelScope.launch(Dispatchers.IO) {
            _predictedUsage.value = repository.getPredictedTomorrowUsage()
        }
        viewModelScope.launch(Dispatchers.IO) {
            _bingeSessions.value = repository.getBingeSessions()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val today = DateUtils.today()
            _monthlyData.value = repository.getDailySummariesSync(DateUtils.daysAgo(30), today)
        }
    }

    fun refresh() = loadInsights()
}
