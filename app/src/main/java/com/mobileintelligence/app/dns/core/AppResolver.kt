package com.mobileintelligence.app.dns.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps network UIDs to package names.
 * Uses PackageManager and /proc/net/udp for accurate UID resolution.
 */
class AppResolver(private val context: Context) {

    companion object {
        private const val TAG = "AppResolver"
    }

    // Cache: UID → PackageName
    private val uidCache = ConcurrentHashMap<Int, String>()
    // Cache: PackageName → App Label
    private val labelCache = ConcurrentHashMap<String, String>()
    // Cache: source port → UID (from /proc/net/udp) — bounded size to prevent memory leak
    private val portUidCache = object : LinkedHashMap<Int, Int>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Int>?): Boolean =
            size > 1024  // Keep max 1024 port mappings
    }

    private val pm: PackageManager = context.packageManager

    init {
        // Pre-populate UID cache from installed apps
        try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in apps) {
                uidCache[app.uid] = app.packageName
                labelCache[app.packageName] = pm.getApplicationLabel(app).toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pre-populate UID cache", e)
        }
    }

    /**
     * Resolve package name from UID.
     */
    fun getPackageName(uid: Int): String {
        if (uid < 0) return "unknown"

        uidCache[uid]?.let { return it }

        // Try PackageManager
        val packages = pm.getPackagesForUid(uid)
        if (!packages.isNullOrEmpty()) {
            val pkg = packages[0]
            uidCache[uid] = pkg
            return pkg
        }

        return "uid:$uid"
    }

    /**
     * Get human-readable app label.
     */
    fun getAppLabel(packageName: String): String {
        if (packageName.startsWith("uid:") || packageName == "unknown") return packageName

        labelCache[packageName]?.let { return it }

        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(info).toString()
            labelCache[packageName] = label
            label
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * Try to resolve UID from source port by reading /proc/net/udp.
     * This is a best-effort approach and may not always work.
     */
    fun resolveUidFromPort(localPort: Int): Int {
        synchronized(portUidCache) {
            portUidCache[localPort]?.let { return it }
        }

        try {
            // Parse /proc/net/udp and /proc/net/udp6
            for (procFile in listOf("/proc/net/udp", "/proc/net/udp6")) {
                BufferedReader(FileReader(procFile)).use { reader ->
                    reader.readLine() // Skip header

                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 8) {
                            val localAddr = parts[1]
                            val port = localAddr.substringAfter(':').toIntOrNull(16) ?: 0
                            val uid = parts[7].toIntOrNull() ?: -1

                            if (port == localPort && uid >= 0) {
                                synchronized(portUidCache) { portUidCache[localPort] = uid }
                                return uid
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail — /proc/net may not be accessible
        }

        return -1
    }

    /**
     * Get list of all installed apps with network access.
     */
    fun getNetworkApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        for (app in installedApps) {
            val hasInternet = try {
                pm.checkPermission(
                    android.Manifest.permission.INTERNET,
                    app.packageName
                ) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            if (hasInternet) {
                apps.add(AppInfo(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    uid = app.uid,
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                ))
            }
        }

        return apps.sortedBy { it.label.lowercase() }
    }

    /**
     * Clear port-UID cache (should be done periodically).
     */
    fun clearPortCache() {
        synchronized(portUidCache) { portUidCache.clear() }
    }

    data class AppInfo(
        val packageName: String,
        val label: String,
        val uid: Int,
        val isSystem: Boolean
    )
}
