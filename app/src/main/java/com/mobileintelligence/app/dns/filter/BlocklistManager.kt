package com.mobileintelligence.app.dns.filter

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Manages blocklist sources: bundled, user, and remote.
 * Handles weekly updates via WorkManager with diff merge and atomic replace.
 */
class BlocklistManager(private val context: Context) {

    companion object {
        private const val TAG = "BlocklistManager"
        private const val WORK_TAG = "blocklist_update"
        private const val UPDATE_INTERVAL_DAYS = 7L

        /**
         * Default remote blocklist sources.
         * These are popular community-maintained lists.
         */
        val DEFAULT_SOURCES = mapOf(
            "ads" to listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                "https://adaway.org/hosts.txt"
            ),
            "trackers" to listOf(
                "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling/hosts"
            ),
            "malware" to listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/extensions/malware/malwaredomainlist.com/hosts"
            )
        )
    }

    data class BlocklistInfo(
        val category: String,
        val domainCount: Int,
        val lastUpdated: Long,
        val sources: List<String>
    )

    /**
     * Schedule weekly blocklist update via WorkManager.
     */
    fun schedulePeriodicUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val work = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(
            UPDATE_INTERVAL_DAYS, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )

        Log.d(TAG, "Scheduled weekly blocklist update")
    }

    /**
     * Trigger immediate blocklist update.
     */
    fun triggerImmediateUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>()
            .setConstraints(constraints)
            .addTag("${WORK_TAG}_immediate")
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }

    /**
     * Get info about loaded blocklists.
     */
    fun getBlocklistInfo(): List<BlocklistInfo> {
        val result = mutableListOf<BlocklistInfo>()
        val dir = File(context.filesDir, "blocklists")

        for (category in listOf("ads", "trackers", "malware")) {
            val file = File(dir, "${category}_updated.txt")
            val count = if (file.exists()) file.readLines().count { it.isNotBlank() && !it.startsWith('#') } else 0
            val lastModified = if (file.exists()) file.lastModified() else 0L

            result.add(BlocklistInfo(
                category = category,
                domainCount = count,
                lastUpdated = lastModified,
                sources = DEFAULT_SOURCES[category] ?: emptyList()
            ))
        }

        return result
    }

    /**
     * WorkManager worker for background blocklist updates.
     */
    class BlocklistUpdateWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting blocklist update...")

            var anySuccess = false

            for ((category, urls) in DEFAULT_SOURCES) {
                try {
                    val domains = mutableSetOf<String>()

                    for (url in urls) {
                        try {
                            val downloaded = downloadList(url)
                            domains.addAll(downloaded)
                            Log.d(TAG, "Downloaded ${downloaded.size} domains from $url")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to download from $url", e)
                        }
                    }

                    if (domains.isNotEmpty()) {
                        // Atomic replace: write to temp, then rename
                        val dir = File(applicationContext.filesDir, "blocklists")
                        if (!dir.exists()) dir.mkdirs()

                        val tempFile = File(dir, "${category}_updated.tmp")
                        val targetFile = File(dir, "${category}_updated.txt")

                        tempFile.writeText(domains.joinToString("\n"))
                        tempFile.renameTo(targetFile)

                        anySuccess = true
                        Log.d(TAG, "Updated $category with ${domains.size} domains")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating $category list", e)
                }
            }

            if (anySuccess) Result.success() else Result.retry()
        }

        private fun downloadList(urlString: String): Set<String> {
            val domains = mutableSetOf<String>()
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"

            try {
                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith('#') && !trimmed.startsWith('!')) {
                                val domain = trimmed.split("\\s+".toRegex()).last()
                                if (domain.contains('.') && domain != "0.0.0.0" && domain != "127.0.0.1" && domain != "localhost") {
                                    domains.add(domain.lowercase().trimEnd('.'))
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            return domains
        }
    }
}
