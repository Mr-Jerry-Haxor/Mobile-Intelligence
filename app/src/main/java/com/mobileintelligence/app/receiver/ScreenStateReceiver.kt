package com.mobileintelligence.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dynamic receiver registered by MonitoringService.
 * Handles screen ON/OFF/USER_PRESENT events.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    var onScreenOn: (() -> Unit)? = null
    var onScreenOff: (() -> Unit)? = null
    var onUserPresent: (() -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> onScreenOn?.invoke()
            Intent.ACTION_SCREEN_OFF -> onScreenOff?.invoke()
            Intent.ACTION_USER_PRESENT -> onUserPresent?.invoke()
        }
    }
}
