package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.data.repository.IntelligenceRepository
import com.mobileintelligence.app.service.MonitoringService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val db = IntelligenceDatabase.getInstance(application)
    private val repository = IntelligenceRepository(db)

    val isTrackingEnabled: StateFlow<Boolean> = prefs.isTrackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val themeMode: StateFlow<String> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    val isNumLockEnabled: StateFlow<Boolean> = prefs.isNumLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _totalRecords = MutableStateFlow(0)
    val totalRecords: StateFlow<Int> = _totalRecords.asStateFlow()

    private val _oldestDate = MutableStateFlow<String?>(null)
    val oldestDate: StateFlow<String?> = _oldestDate.asStateFlow()

    init {
        loadStats()
    }

    /** Check if a NumLock PIN has been set. */
    suspend fun isNumLockSet(): Boolean = prefs.isNumLockSet()

    /** Verify PIN against stored hash. */
    suspend fun verifyPin(pin: String): Boolean = prefs.verifyPin(pin)

    /** Save a new PIN. */
    fun setNumLockPin(pin: String) {
        viewModelScope.launch { prefs.setNumLockPin(pin) }
    }

    /** Remove the NumLock PIN. */
    fun removeNumLock() {
        viewModelScope.launch { prefs.removeNumLock() }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setTrackingEnabled(enabled)
            if (enabled) {
                MonitoringService.start(getApplication())
            } else {
                MonitoringService.stop(getApplication())
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun wipeAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.purgeAllData()
            loadStats()
        }
    }

    private fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _totalRecords.value = repository.getTotalRecordsCount()
            _oldestDate.value = repository.getOldestRecordDate()
        }
    }
}
