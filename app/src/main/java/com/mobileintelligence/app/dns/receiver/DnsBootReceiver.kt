package com.mobileintelligence.app.dns.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.mobileintelligence.app.dns.data.DnsPreferences
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts DNS VPN on device boot if it was enabled.
 */
class DnsBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DnsBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action !in validActions) return

        Log.d(TAG, "Boot event: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = DnsPreferences(context)
                val autoStart = prefs.vpnAutoStart.first()
                val wasEnabled = prefs.vpnEnabled.first()

                if (autoStart && wasEnabled) {
                    // Check VPN permission before starting
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent == null) {
                        // Permission already granted
                        LocalDnsVpnService.start(context)
                        Log.d(TAG, "DNS VPN auto-started on boot")
                    } else {
                        Log.w(TAG, "VPN permission not granted, cannot auto-start")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in boot receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
