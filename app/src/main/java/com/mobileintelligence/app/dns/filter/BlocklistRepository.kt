package com.mobileintelligence.app.dns.filter

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.dns.data.dao.BlocklistDao
import com.mobileintelligence.app.dns.data.entity.BlocklistEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for managing custom and pre-configured DNS blocklists.
 * Handles CRUD operations, downloading, parsing, and integration with DomainFilter.
 */
class BlocklistRepository(private val context: Context) {

    companion object {
        private const val TAG = "BlocklistRepo"
        private const val BLOCKLIST_DIR = "custom_blocklists"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 60_000
    }

    private val database = DnsDatabase.getInstance(context)
    private val dao: BlocklistDao = database.blocklistDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Observable streams ─────────────────────────────────────

    val allBlocklists: Flow<List<BlocklistEntity>> = dao.getAllBlocklists()
    val enabledBlocklists: Flow<List<BlocklistEntity>> = dao.getEnabledBlocklists()
    val enabledCount: Flow<Int> = dao.getEnabledCount()
    val totalEnabledDomains: Flow<Int?> = dao.getTotalEnabledDomainCount()

    // ─── Initialization ─────────────────────────────────────────

    /**
     * Seed pre-configured blocklists into the database if not already present.
     * Called once at app startup.
     */
    suspend fun seedPreConfiguredLists() = withContext(Dispatchers.IO) {
        val count = dao.count()
        if (count == 0) {
            Log.d(TAG, "Seeding ${PreConfiguredBlocklists.SOURCES.size} pre-configured blocklists")
            dao.insertAll(PreConfiguredBlocklists.toEntities())
        } else {
            // Ensure any new pre-configured lists are added on update
            val existing = dao.getEnabledBlocklistsSnapshot() + getAllBlocklistsSnapshot()
            val existingUrls = existing.map { it.url }.toSet()
            val newLists = PreConfiguredBlocklists.toEntities().filter { it.url !in existingUrls }
            if (newLists.isNotEmpty()) {
                Log.d(TAG, "Adding ${newLists.size} new pre-configured blocklists")
                dao.insertAll(newLists)
            }
        }
    }

    private suspend fun getAllBlocklistsSnapshot(): List<BlocklistEntity> {
        return dao.getAllBlocklists().first()
    }

    // ─── CRUD Operations ────────────────────────────────────────

    /**
     * Add a custom blocklist by URL.
     */
    suspend fun addCustomBlocklist(
        name: String,
        url: String,
        description: String = "",
        category: String = "custom"
    ): Long = withContext(Dispatchers.IO) {
        val entity = BlocklistEntity(
            name = name,
            url = url,
            description = description,
            category = category,
            isEnabled = false,
            isBuiltIn = false
        )
        val id = dao.insert(entity)
        Log.d(TAG, "Added custom blocklist: $name (id=$id)")
        id
    }

    /**
     * Add a blocklist from a local file (user uploaded txt file).
     */
    suspend fun addFromLocalFile(
        name: String,
        uri: Uri,
        description: String = "",
        category: String = "custom"
    ): Long = withContext(Dispatchers.IO) {
        // Copy and parse the file
        val domains = parseFromUri(uri)
        val fileName = "local_${System.currentTimeMillis()}.txt"
        val localFile = saveDomainsToFile(fileName, domains)

        val entity = BlocklistEntity(
            name = name,
            url = "local://$fileName",
            description = description,
            category = category,
            isEnabled = false,
            isBuiltIn = false,
            domainCount = domains.size,
            lastUpdated = System.currentTimeMillis(),
            localFilePath = localFile.absolutePath,
            status = "success"
        )
        val id = dao.insert(entity)
        Log.d(TAG, "Added local blocklist: $name with ${domains.size} domains (id=$id)")
        id
    }

    /**
     * Update a custom blocklist's metadata.
     */
    suspend fun updateBlocklist(
        id: Long,
        name: String,
        url: String,
        description: String,
        category: String
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: return@withContext
        dao.update(existing.copy(
            name = name,
            url = url,
            description = description,
            category = category
        ))
        Log.d(TAG, "Updated blocklist: $name (id=$id)")
    }

    /**
     * Delete a blocklist and its cached file.
     */
    suspend fun deleteBlocklist(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id)
        if (entity != null) {
            // Delete cached file
            if (entity.localFilePath.isNotEmpty()) {
                File(entity.localFilePath).delete()
            }
            dao.deleteById(id)
            Log.d(TAG, "Deleted blocklist: ${entity.name} (id=$id)")
        }
    }

    /**
     * Toggle a blocklist on/off.
     */
    suspend fun toggleBlocklist(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
        val entity = dao.getById(id)
        Log.d(TAG, "Toggled blocklist ${entity?.name}: enabled=$enabled")

        // If enabling and not yet downloaded, trigger download
        if (enabled && entity != null && entity.domainCount == 0 && entity.url.isNotEmpty() && !entity.url.startsWith("local://")) {
            downloadBlocklist(id)
        }
    }

    // ─── Download & Parse ───────────────────────────────────────

    /**
     * Download and parse a blocklist from its URL.
     */
    suspend fun downloadBlocklist(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        if (entity.url.isEmpty() || entity.url.startsWith("local://")) return@withContext

        try {
            dao.setStatus(id, "downloading")
            Log.d(TAG, "Downloading blocklist: ${entity.name} from ${entity.url}")

            val domains = downloadAndParse(entity.url)
            val fileName = "blocklist_${id}.txt"
            val localFile = saveDomainsToFile(fileName, domains)

            dao.setDownloadSuccess(id, domains.size, System.currentTimeMillis(), localFile.absolutePath)
            Log.d(TAG, "Downloaded ${entity.name}: ${domains.size} domains")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ${entity.name}", e)
            dao.setDownloadError(id, e.message ?: "Download failed")
        }
    }

    /**
     * Download and update all enabled blocklists.
     */
    suspend fun refreshAllEnabled() = withContext(Dispatchers.IO) {
        val enabled = dao.getEnabledBlocklistsSnapshot()
        Log.d(TAG, "Refreshing ${enabled.size} enabled blocklists")

        enabled.forEach { entity ->
            if (entity.url.isNotEmpty() && !entity.url.startsWith("local://")) {
                try {
                    downloadBlocklist(entity.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh ${entity.name}", e)
                }
            }
        }
    }

    /**
     * Load all domains from enabled blocklists into a single set.
     * Used by DomainFilter for integration.
     */
    suspend fun loadEnabledDomains(): Set<String> = withContext(Dispatchers.IO) {
        val enabled = dao.getEnabledBlocklistsSnapshot()
        val allDomains = mutableSetOf<String>()

        for (entity in enabled) {
            if (entity.localFilePath.isNotEmpty()) {
                val file = File(entity.localFilePath)
                if (file.exists()) {
                    try {
                        file.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
                                    allDomains.add(trimmed)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load ${entity.name} from ${entity.localFilePath}", e)
                    }
                }
            }
        }

        Log.d(TAG, "Loaded ${allDomains.size} domains from ${enabled.size} enabled blocklists")
        allDomains
    }

    // ─── Internal parsing helpers ───────────────────────────────

    /**
     * Download a remote list and parse all domain entries.
     * Supports hosts file format, adblock format, and plain domain lists.
     */
    private fun downloadAndParse(urlString: String): Set<String> {
        val domains = mutableSetOf<String>()
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AwakeMonitor/1.0 DNS-Blocklist-Downloader")

        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                throw RuntimeException("HTTP $responseCode from $urlString")
            }

            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    parseLine(line)?.let { domains.add(it) }
                }
            }
        } finally {
            conn.disconnect()
        }

        return domains
    }

    /**
     * Parse domains from a content URI (local file upload).
     */
    private fun parseFromUri(uri: Uri): Set<String> {
        val domains = mutableSetOf<String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { line ->
                    parseLine(line)?.let { domains.add(it) }
                }
            }
        }
        return domains
    }

    /**
     * Parse a single line from a blocklist file.
     * Supports:
     *  - Hosts format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
     *  - Adblock format: "||domain.com^"
     *  - Plain domain: "domain.com"
     *  - Comment lines: lines starting with # or !
     */
    private fun parseLine(rawLine: String): String? {
        val line = rawLine.trim()

        // Skip empty lines and comments
        if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) return null

        // Adblock format: ||domain.com^ or ||domain.com^$important
        if (line.startsWith("||")) {
            val end = line.indexOfAny(charArrayOf('^', '$', '/', ' '), startIndex = 2)
            val domain = if (end > 2) line.substring(2, end) else line.substring(2)
            return validateAndNormalize(domain)
        }

        // Hosts file format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"  
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
            val domain = parts[1]
            // Skip localhost entries
            if (domain == "localhost" || domain == "local" || domain == "localhost.localdomain" ||
                domain == "broadcasthost" || domain == "ip6-localhost" || domain == "ip6-loopback") {
                return null
            }
            return validateAndNormalize(domain)
        }

        // Plain domain format (single entry per line)
        if (parts.size == 1 && line.contains('.') && !line.contains('/') &&
            !line.contains(':') && !line.startsWith('[')) {
            return validateAndNormalize(line)
        }

        return null
    }

    /**
     * Validate and normalize a domain string.
     */
    private fun validateAndNormalize(domain: String): String? {
        val normalized = domain.lowercase().trimEnd('.').trim()
        if (normalized.isEmpty()) return null
        if (!normalized.contains('.')) return null
        if (normalized.length > 253) return null
        // Basic domain character validation
        if (!normalized.matches(Regex("^[a-z0-9][a-z0-9._-]*[a-z0-9]$"))) return null
        return normalized
    }

    /**
     * Save parsed domains to a local cache file.
     */
    private fun saveDomainsToFile(fileName: String, domains: Set<String>): File {
        val dir = File(context.filesDir, BLOCKLIST_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(domains.joinToString("\n"))
        return file
    }
}
