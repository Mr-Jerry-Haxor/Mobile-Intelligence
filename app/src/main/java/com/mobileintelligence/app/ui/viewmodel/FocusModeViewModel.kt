package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mobileintelligence.app.engine.EngineManager
import com.mobileintelligence.app.engine.features.FocusMode
import kotlinx.coroutines.flow.*

/**
 * ViewModel bridging the Focus Mode engine feature with the UI.
 * Uses the global EngineEventBus from EngineManager so interruption
 * detection can consume real screen events.
 */
class FocusModeViewModel(application: Application) : AndroidViewModel(application) {

    private val eventBus = EngineManager.getInstance(application).eventBus
    private val focusMode = FocusMode(application, eventBus)

    val isActive: StateFlow<Boolean> = focusMode.isActive
    val mode: StateFlow<FocusMode.FocusModeType> = focusMode.mode
    val focusDurationMs: StateFlow<Long> = focusMode.focusDurationMs
    val interruptionCount: StateFlow<Int> = focusMode.interruptionCount
    val pomodoroState: StateFlow<FocusMode.PomodoroState> = focusMode.pomodoroState
    val pomodoroTimeRemainingMs: StateFlow<Long> = focusMode.pomodoroTimeRemainingMs
    val completedPomodoros: StateFlow<Int> = focusMode.completedPomodoros

    private val _lastReport = MutableStateFlow<FocusMode.FocusReport?>(null)
    val lastReport: StateFlow<FocusMode.FocusReport?> = _lastReport.asStateFlow()

    fun startFocus(type: FocusMode.FocusModeType) {
        focusMode.startFocus(type)
    }

    fun stopFocus() {
        val report = focusMode.stopFocus()
        _lastReport.value = report
    }
}
