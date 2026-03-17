package com.mobileintelligence.app.dns.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Restarts VPN when a package is removed (system may kill VPN on package changes).
 */
class PackageRemovedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageRemoved"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_REMOVED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        if (replacing) return // Update, not removal

        Log.d(TAG, "Package removed: $packageName")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = com.mobileintelligence.app.dns.data.DnsPreferences(context)
                val isEnabled = prefs.vpnEnabled.first()
                val isRunning = LocalDnsVpnService.isRunning.value

                // Restart VPN if it was running but got killed
                if (isEnabled && !isRunning) {
                    Log.d(TAG, "Restarting VPN after package removal")
                    delay(2000) // Small delay for system to settle
                    LocalDnsVpnService.start(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting VPN", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
