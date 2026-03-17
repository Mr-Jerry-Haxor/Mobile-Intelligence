package com.mobileintelligence.app.dns.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Monitors network connectivity changes and restarts VPN if needed.
 * Handles WiFi ↔ Mobile transitions silently.
 */
class ConnectivityChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConnectivityChange"
        private var networkCallback: ConnectivityManager.NetworkCallback? = null

        /**
         * Register active network monitoring (preferred over broadcast receiver).
         */
        fun register(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (networkCallback != null) return // Already registered

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    handleNetworkChange(context)
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    // Network capabilities changed (WiFi ↔ Cellular)
                    handleNetworkChange(context)
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, networkCallback!!)
        }

        fun unregister(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let {
                try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
            }
            networkCallback = null
        }

        private fun handleNetworkChange(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = com.mobileintelligence.app.dns.data.DnsPreferences(context)
                    val isEnabled = prefs.vpnEnabled.first()
                    val isRunning = LocalDnsVpnService.isRunning.value

                    if (isEnabled && !isRunning) {
                        Log.d(TAG, "Restarting VPN after network change")
                        LocalDnsVpnService.start(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling network change", e)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Log.d(TAG, "Connectivity changed (broadcast)")
            handleNetworkChange(context)
        }
    }
}
