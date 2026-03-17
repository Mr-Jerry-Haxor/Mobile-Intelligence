package com.mobileintelligence.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mobileintelligence.app.R
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitoringService : LifecycleService() {

    companion object {
        private const val TAG = "MonitoringService"
        const val CHANNEL_ID = "mi_monitoring_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.mobileintelligence.ACTION_STOP"
        private const val WAKELOCK_TAG = "MobileIntelligence::MonitoringWakeLock"

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }

        /**
         * Schedule a restart via AlarmManager as a safety net.
         * Used when the service is killed by the system.
         */
        fun scheduleRestart(context: Context, delayMs: Long = 3000L) {
            val intent = Intent(context, MonitoringService::class.java)
            val pendingIntent = PendingIntent.getService(
                context, 999, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pendingIntent
            )
            Log.i(TAG, "Scheduled service restart in ${delayMs}ms")
        }
    }

    private lateinit var prefs: AppPreferences
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        prefs = AppPreferences(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Activity monitoring active"))

        // Acquire a partial wake lock to prevent CPU from sleeping
        acquireWakeLock()

        lifecycleScope.launch(Dispatchers.IO) {
            prefs.setServiceRunning(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }

        // Re-acquire wake lock on every start command (covers restart scenarios)
        acquireWakeLock()

        // Return START_STICKY so the system restarts the service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Log.w(TAG, "Service onDestroy — scheduling restart")
        releaseWakeLock()

        // Save state synchronously so it completes before process death
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                prefs.setServiceRunning(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDestroy cleanup", e)
            }
        }

        // Schedule a restart as safety net — the system may kill us
        scheduleRestart(applicationContext, 3000L)

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed from recents — scheduling restart")
        // When user swipes away from recents, schedule aggressive restart
        scheduleRestart(applicationContext, 1000L)
    }

    // ── Wake Lock ────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock == null || wakeLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                // Acquire with 10-minute timeout; re-acquired on every onStartCommand
                acquire(10 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    // ── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Activity Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for screen activity monitoring"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mobile Intelligence")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
