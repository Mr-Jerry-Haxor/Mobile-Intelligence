package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.analytics.AnalyticsEngine
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.dao.AppRanking
import com.mobileintelligence.app.data.database.entity.AppUsageDaily
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)

    private val _period = MutableStateFlow("today") // today, week, month
    val period: StateFlow<String> = _period.asStateFlow()

    private val _appRankings = MutableStateFlow<List<AppRanking>>(emptyList())
    val appRankings: StateFlow<List<AppRanking>> = _appRankings.asStateFlow()

    private val _selectedAppTrend = MutableStateFlow<List<AppUsageDaily>>(emptyList())
    val selectedAppTrend: StateFlow<List<AppUsageDaily>> = _selectedAppTrend.asStateFlow()

    init {
        loadAppStats()
    }

    fun setPeriod(period: String) {
        _period.value = period
        loadAppStats()
    }

    private fun loadAppStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = DateUtils.today()
            val (startDate, endDate) = when (_period.value) {
                "week" -> DateUtils.daysAgo(7) to today
                "month" -> DateUtils.daysAgo(30) to today
                else -> today to today
            }
            _appRankings.value = repository.getAppRankingForRange(startDate, endDate)
        }
    }

    fun loadAppTrend(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = DateUtils.today()
            val startDate = DateUtils.daysAgo(30)
            _selectedAppTrend.value = repository.getAppUsageTrend(packageName, startDate, today)
        }
    }
}
