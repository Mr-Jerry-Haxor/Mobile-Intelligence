package com.mobileintelligence.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.service.MonitoringService
import com.mobileintelligence.app.worker.ServiceWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the monitoring service on device boot, app update, or quick-boot.
 * Uses goAsync() so we can do a brief coroutine check before completing.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received boot intent: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = AppPreferences(context)
                        val isTrackingEnabled = prefs.isTrackingEnabled.first()
                        if (isTrackingEnabled) {
                            Log.i(TAG, "Starting monitoring service on boot")
                            MonitoringService.start(context)
                        } else {
                            Log.i(TAG, "Tracking disabled, not starting service on boot")
                        }

                        // Always schedule the watchdog worker
                        ServiceWatchdogWorker.schedule(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling boot intent", e)
                        // Start service anyway as safety net
                        MonitoringService.start(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
