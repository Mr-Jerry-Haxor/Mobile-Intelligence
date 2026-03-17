package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.entity.ScreenSession
import com.mobileintelligence.app.data.database.entity.AppSession
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _screenSessions = MutableStateFlow<List<ScreenSession>>(emptyList())
    val screenSessions: StateFlow<List<ScreenSession>> = _screenSessions.asStateFlow()

    private val _appSessions = MutableStateFlow<List<AppSession>>(emptyList())
    val appSessions: StateFlow<List<AppSession>> = _appSessions.asStateFlow()

    init {
        loadTimeline()
    }

    fun setDate(date: String) {
        _selectedDate.value = date
        loadTimeline()
    }

    private fun loadTimeline() {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            repository.getScreenSessionsForDate(date).collect {
                _screenSessions.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            repository.getAppSessionsForDate(date).collect {
                _appSessions.value = it
            }
        }
    }
}
