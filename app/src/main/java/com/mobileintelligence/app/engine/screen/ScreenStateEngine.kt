package com.mobileintelligence.app.engine.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.database.entity.ScreenSession
import com.mobileintelligence.app.data.database.entity.UnlockEvent
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.engine.*
import com.mobileintelligence.app.util.DateUtils
import com.mobileintelligence.app.util.UsageStatsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Engine 1 — Screen State Engine
 *
 * Advanced behavioral session modeling built on top of BroadcastReceiver
 * and UsageStatsManager. Goes far beyond simple ON/OFF logging.
 *
 * Detects:
 * - Micro sessions (<10s), compulsive unlock loops, rapid app switching
 * - Doom scroll patterns (long continuous sessions)
 * - Night wakeups, wake source inference
 * - Screen brightness correlation, charging state during usage
 * - Lock reason inference
 *
 * Publishes events to EngineEventBus for other engines to consume.
 */
class ScreenStateEngine(private val context: Context) : Engine {

    companion object {
        private const val TAG = "ScreenStateEngine"
        private const val COMPULSIVE_RECHECK_THRESHOLD_MS = 60_000L // 60s
        private const val MICRO_SESSION_THRESHOLD_MS = 10_000L
        private const val QUICK_CHECK_THRESHOLD_MS = 30_000L
        private const val SHORT_SESSION_THRESHOLD_MS = 120_000L
        private const val NORMAL_SESSION_THRESHOLD_MS = 900_000L
        private const val EXTENDED_SESSION_THRESHOLD_MS = 2_700_000L
        private const val LONG_SESSION_THRESHOLD_MS = 7_200_000L
        private const val APP_POLL_INITIAL_MS = 2_000L
        private const val APP_POLL_MAX_MS = 8_000L
    }

    override val name = "ScreenStateEngine"
    private val _state = MutableStateFlow(EngineState.Created)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private lateinit var eventBus: EngineEventBus
    private lateinit var db: IntelligenceDatabase
    private lateinit var prefs: AppPreferences
    private lateinit var sessionClassifier: SessionClassifier
    private lateinit var deviceStateTracker: DeviceStateTracker
    private val unlockPatternAnalyzer = UnlockPatternAnalyzer()

    private var screenReceiver: BroadcastReceiver? = null
    private var appPollingJob: Job? = null
    private var isScreenOn = false
    private var currentSessionStart = 0L
    private var lastLockTimestamp = 0L
    private var lastUnlockTimestamp = 0L
    private var appSwitchCount = 0
    private var startTimeMs = 0L
    private var eventsProcessed = 0L
    private var lastError: String? = null

    // ── Engine Lifecycle ──────────────────────────────────────────

    override suspend fun initialize(scope: CoroutineScope, eventBus: EngineEventBus) {
        _state.value = EngineState.Initializing
        this.scope = scope
        this.eventBus = eventBus
        db = IntelligenceDatabase.getInstance(context)
        prefs = AppPreferences(context)
        sessionClassifier = SessionClassifier()
        deviceStateTracker = DeviceStateTracker(context)
        _state.value = EngineState.Stopped
    }

    override suspend fun start() {
        if (_state.value == EngineState.Running) return
        try {
            _state.value = EngineState.Running
            startTimeMs = System.currentTimeMillis()
            registerScreenReceiver()
            deviceStateTracker.start()
            checkInitialScreenState()
            Log.i(TAG, "Screen engine started")
        } catch (e: Exception) {
            lastError = e.message
            _state.value = EngineState.Error
            Log.e(TAG, "Failed to start", e)
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopping
        appPollingJob?.cancel()
        appPollingJob = null
        unregisterScreenReceiver()
        deviceStateTracker.stop()
        closeActiveSession()
        _state.value = EngineState.Stopped
        Log.i(TAG, "Screen engine stopped")
    }

    override suspend fun recover() {
        Log.w(TAG, "Recovering screen engine...")
        try {
            unregisterScreenReceiver()
            appPollingJob?.cancel()
        } catch (_: Exception) {}
        _state.value = EngineState.Stopped
        lastError = null
    }

    override fun diagnostics(): EngineDiagnostics = EngineDiagnostics(
        engineName = name,
        state = _state.value,
        uptimeMs = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0,
        lastError = lastError,
        eventsProcessed = eventsProcessed,
        customMetrics = buildMap {
            put("isScreenOn", isScreenOn)
            put("appSwitchCount", appSwitchCount)
            try {
                put("brightness", deviceStateTracker.currentBrightness)
                put("charging", deviceStateTracker.isCharging)
            } catch (_: UninitializedPropertyAccessException) {
                put("brightness", -1)
                put("charging", false)
            }
        }
    )

    // ── Screen Event Handling ────────────────────────────────────

    private fun registerScreenReceiver() {
        unregisterScreenReceiver()
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_USER_PRESENT -> onUserUnlocked()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenReceiver, filter)
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        screenReceiver = null
    }

    private fun checkInitialScreenState() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            onScreenOn()
        }
    }

    private fun onScreenOn() {
        isScreenOn = true
        val now = System.currentTimeMillis()
        currentSessionStart = now
        appSwitchCount = 0

        scope.launch(Dispatchers.IO) {
            try {
                val date = DateUtils.formatDate(now)
                val hour = DateUtils.getHourOfDay(now)

                val session = ScreenSession(
                    screenOnTime = now,
                    date = date,
                    hourOfDay = hour,
                    isNightUsage = DateUtils.isNightTime(now)
                )
                val id = db.screenSessionDao().insert(session)
                prefs.setCurrentScreenSessionId(id)

                // Publish event
                eventBus.publish(ScreenOnEvent(timestamp = now))
                eventsProcessed++

                startAppPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onScreenOn", e)
                lastError = e.message
            }
        }
    }

    private fun onScreenOff() {
        isScreenOn = false
        val now = System.currentTimeMillis()
        lastLockTimestamp = now
        val sessionDuration = if (currentSessionStart > 0) now - currentSessionStart else 0

        scope.launch(Dispatchers.IO) {
            try {
                closeActiveSession(now)
                stopAppPolling()

                // Classify the session
                val sessionType = sessionClassifier.classify(
                    durationMs = sessionDuration,
                    appSwitchCount = appSwitchCount,
                    timeSinceLastLock = if (lastUnlockTimestamp > 0) {
                        currentSessionStart - lastLockTimestamp
                    } else null
                )

                // Publish events
                eventBus.publish(ScreenOffEvent(timestamp = now, sessionDurationMs = sessionDuration))
                eventBus.publish(
                    SessionClassifiedEvent(
                        timestamp = now,
                        sessionType = sessionType,
                        durationMs = sessionDuration,
                        appCount = appSwitchCount
                    )
                )
                eventsProcessed += 2
            } catch (e: Exception) {
                Log.e(TAG, "Error in onScreenOff", e)
                lastError = e.message
            }
        }
    }

    private fun onUserUnlocked() {
        val now = System.currentTimeMillis()
        val timeSinceLastUnlock = if (lastUnlockTimestamp > 0) now - lastUnlockTimestamp else null
        lastUnlockTimestamp = now

        // Infer wake source
        val wakeSource = inferWakeSource(timeSinceLastUnlock)
        val hour = DateUtils.getHourOfDay(now)

        // Feed unlock pattern analyzer
        unlockPatternAnalyzer.recordUnlock(
            UnlockPatternAnalyzer.UnlockRecord(
                timestamp = now,
                hourOfDay = hour,
                wakeSource = wakeSource,
                sessionType = null,
                sessionDurationMs = 0
            )
        )

        scope.launch(Dispatchers.IO) {
            try {
                val date = DateUtils.formatDate(now)

                db.unlockEventDao().insert(
                    UnlockEvent(
                        timestamp = now,
                        date = date,
                        hourOfDay = hour,
                        timeSinceLastUnlockMs = timeSinceLastUnlock
                    )
                )

                // Update screen session as unlocked
                val sessionId = prefs.getCurrentScreenSessionId()
                if (sessionId > 0) {
                    db.screenSessionDao().getById(sessionId)?.let {
                        db.screenSessionDao().update(it.copy(wasUnlocked = true))
                    }
                }

                eventBus.publish(
                    UserUnlockedEvent(
                        timestamp = now,
                        timeSinceLastUnlockMs = timeSinceLastUnlock,
                        wakeSource = wakeSource
                    )
                )
                eventsProcessed++
            } catch (e: Exception) {
                Log.e(TAG, "Error in onUserUnlocked", e)
                lastError = e.message
            }
        }
    }

    // ── Wake Source Inference ─────────────────────────────────────

    private fun inferWakeSource(timeSinceLastUnlockMs: Long?): WakeSource {
        // Heuristic-based inference
        return when {
            // Compulsive recheck — unlocked again very quickly
            timeSinceLastUnlockMs != null && timeSinceLastUnlockMs < COMPULSIVE_RECHECK_THRESHOLD_MS ->
                WakeSource.MANUAL_UNLOCK

            // Night wake — likely notification
            DateUtils.isNightTime(System.currentTimeMillis()) ->
                WakeSource.NOTIFICATION

            else -> WakeSource.UNKNOWN
        }
    }

    // ── App Polling (Adaptive) ───────────────────────────────────

    private fun startAppPolling() {
        stopAppPolling()
        appPollingJob = scope.launch(Dispatchers.IO) {
            checkForegroundApp()
            var interval = APP_POLL_INITIAL_MS
            while (isActive && isScreenOn) {
                delay(interval)
                if (isScreenOn) {
                    checkForegroundApp()
                    if (interval < APP_POLL_MAX_MS) interval += 500
                }
            }
        }
    }

    private fun stopAppPolling() {
        appPollingJob?.cancel()
        appPollingJob = null
    }

    private suspend fun checkForegroundApp() {
        val info = UsageStatsHelper.getForegroundApp(context) ?: return
        val lastApp = prefs.getLastForegroundApp()

        if (info.packageName != lastApp && info.packageName != context.packageName) {
            // Close previous app session
            closeActiveAppSession(info.timestamp)

            // Start new app session
            val now = System.currentTimeMillis()
            val date = DateUtils.formatDate(now)
            val sessionId = prefs.getCurrentScreenSessionId()

            val appSession = com.mobileintelligence.app.data.database.entity.AppSession(
                packageName = info.packageName,
                appName = info.appName,
                startTime = now,
                date = date,
                screenSessionId = if (sessionId > 0) sessionId else null
            )
            val id = db.appSessionDao().insert(appSession)
            prefs.setCurrentAppSessionId(id)
            prefs.setLastForegroundApp(info.packageName)

            // Publish app transition
            appSwitchCount++
            eventBus.publish(
                AppTransitionEvent(
                    timestamp = now,
                    fromPackage = lastApp.takeIf { it.isNotBlank() },
                    toPackage = info.packageName,
                    toAppName = info.appName
                )
            )
            eventsProcessed++
        }
    }

    // ── Active Session Close ─────────────────────────────────────

    private suspend fun closeActiveSession(now: Long = System.currentTimeMillis()) {
        val sessionId = prefs.getCurrentScreenSessionId()
        if (sessionId > 0) {
            db.screenSessionDao().getById(sessionId)?.let {
                if (it.screenOffTime == null) {
                    db.screenSessionDao().update(
                        it.copy(screenOffTime = now, durationMs = now - it.screenOnTime)
                    )
                }
            }
            prefs.setCurrentScreenSessionId(-1)
        }
        closeActiveAppSession(now)
    }

    private suspend fun closeActiveAppSession(now: Long = System.currentTimeMillis()) {
        val appSessionId = prefs.getCurrentAppSessionId()
        if (appSessionId > 0) {
            db.appSessionDao().getActiveSession()?.let {
                if (it.endTime == null) {
                    db.appSessionDao().update(
                        it.copy(endTime = now, durationMs = now - it.startTime)
                    )
                }
            }
            prefs.setCurrentAppSessionId(-1)
            prefs.setLastForegroundApp("")
        }
    }
}
