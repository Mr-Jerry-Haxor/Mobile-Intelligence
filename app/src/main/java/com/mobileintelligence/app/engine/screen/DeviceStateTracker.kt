package com.mobileintelligence.app.engine.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Tracks device hardware state that correlates with usage patterns:
 * - Screen brightness (0-255)
 * - Charging state (plugged/unplugged, charge level)
 * - Auto-brightness status
 *
 * These signals enrich session data for the Intelligence Engine.
 */
class DeviceStateTracker(private val context: Context) {

    companion object {
        private const val TAG = "DeviceStateTracker"
    }

    // ── Observable State ─────────────────────────────────────────

    @Volatile var currentBrightness: Int = 0
        private set

    @Volatile var isAutoBrightness: Boolean = false
        private set

    @Volatile var isCharging: Boolean = false
        private set

    @Volatile var batteryLevel: Int = -1
        private set

    @Volatile var chargingSource: String = "none"
        private set

    // ── Battery Receiver ─────────────────────────────────────────

    private var batteryReceiver: BroadcastReceiver? = null

    fun start() {
        updateBrightness()
        registerBatteryReceiver()
        refreshBatteryState()
        Log.d(TAG, "DeviceStateTracker started (brightness=$currentBrightness, charging=$isCharging)")
    }

    fun stop() {
        unregisterBatteryReceiver()
    }

    fun snapshot(): DeviceStateSnapshot = DeviceStateSnapshot(
        brightness = currentBrightness,
        autoBrightness = isAutoBrightness,
        charging = isCharging,
        batteryLevel = batteryLevel,
        chargingSource = chargingSource
    )

    // ── Brightness ───────────────────────────────────────────────

    private fun updateBrightness() {
        try {
            currentBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            isAutoBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                0
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            Log.w(TAG, "Could not read brightness", e)
        }
    }

    // ── Battery ──────────────────────────────────────────────────

    private fun registerBatteryReceiver() {
        unregisterBatteryReceiver()
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                                || status == BatteryManager.BATTERY_STATUS_FULL

                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        batteryLevel = if (scale > 0) (level * 100) / scale else -1

                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                        chargingSource = when (plugged) {
                            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                            else -> "none"
                        }
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        isCharging = true
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        isCharging = false
                        chargingSource = "none"
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(batteryReceiver, filter)
        }
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        batteryReceiver = null
    }

    private fun refreshBatteryState() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryLevel = if (scale > 0) (level * 100) / scale else -1

            val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }
        }
    }

    data class DeviceStateSnapshot(
        val brightness: Int,
        val autoBrightness: Boolean,
        val charging: Boolean,
        val batteryLevel: Int,
        val chargingSource: String
    )
}
