package com.mobileintelligence.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.engine.EngineManager
import com.mobileintelligence.app.engine.HealthMonitor
import com.mobileintelligence.app.engine.dns.DnsFirewallEngine
import com.mobileintelligence.app.engine.intelligence.IntelligenceEngine
import com.mobileintelligence.app.engine.screen.ScreenStateEngine
import com.mobileintelligence.app.engine.storage.StorageEngine
import com.mobileintelligence.app.receiver.MidnightReceiver
import com.mobileintelligence.app.service.MonitoringService
import com.mobileintelligence.app.worker.PeriodicCheckpointWorker
import com.mobileintelligence.app.worker.ServiceWatchdogWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MobileIntelligenceApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "MobileIntelligenceApp"
    }

    lateinit var database: IntelligenceDatabase
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var engineManager: EngineManager
        private set

    private var healthMonitor: HealthMonitor? = null

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize database
        database = IntelligenceDatabase.getInstance(this)
        preferences = AppPreferences(this)

        // Initialize Engine Manager + all engines
        initializeEngines()

        // Start monitoring & scheduling
        appScope.launch {
            val isTrackingEnabled = preferences.isTrackingEnabled.first()
            if (isTrackingEnabled) {
                MonitoringService.start(this@MobileIntelligenceApp)
            }

            // Start engine system
            try {
                engineManager.startAll()
                Log.i(TAG, "All engines started successfully")

                // Start health monitor AFTER engines are initialized
                healthMonitor = HealthMonitor(engineManager)
                healthMonitor?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start engines", e)
            }

            // Schedule periodic checkpoint
            PeriodicCheckpointWorker.schedule(this@MobileIntelligenceApp)

            // Schedule watchdog that restarts service if killed
            ServiceWatchdogWorker.schedule(this@MobileIntelligenceApp)

            // Schedule midnight rollover alarm
            MidnightReceiver.schedule(this@MobileIntelligenceApp)

            // Mark first launch complete
            preferences.setFirstLaunchDone()
        }
    }

    private fun initializeEngines() {
        engineManager = EngineManager.getInstance(this)

        // Register engines in priority order (lower number = higher priority)
        engineManager
            .register(ScreenStateEngine(this), priority = 10)
            .register(DnsFirewallEngine(this), priority = 20)
            .register(StorageEngine(this), priority = 30)
            .register(IntelligenceEngine(this), priority = 40)

        Log.i(TAG, "Engine system initialized with ${engineManager.engines.size} engines")
    }

    override fun onTerminate() {
        healthMonitor?.stop()
        appScope.launch {
            engineManager.stopAll()
        }
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
