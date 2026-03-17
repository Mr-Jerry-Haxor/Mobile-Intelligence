package com.mobileintelligence.app.dns.data.entity

import androidx.room.*

/**
 * Represents a DNS blocklist source — either a pre-configured community list 
 * or a user-added custom list (via URL or local file).
 */
@Entity(
    tableName = "blocklists",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["is_enabled"]),
        Index(value = ["category"]),
        Index(value = ["is_built_in"])
    ]
)
data class BlocklistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Display name for the blocklist */
    @ColumnInfo(name = "name")
    val name: String,

    /** Optional description */
    @ColumnInfo(name = "description")
    val description: String = "",

    /** Remote URL to download the list from. Empty for local-only uploads. */
    @ColumnInfo(name = "url")
    val url: String = "",

    /** Category tag: ads, trackers, malware, gambling, crypto, privacy, custom */
    @ColumnInfo(name = "category")
    val category: String = "custom",

    /** Whether this blocklist is currently active in filtering */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,

    /** True if this is a pre-configured community list, false for user-added */
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,

    /** Number of domains loaded from this list */
    @ColumnInfo(name = "domain_count")
    val domainCount: Int = 0,

    /** Timestamp of last successful download/update */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = 0,

    /** Timestamp when the entry was created */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Local file path where the parsed domain list is cached */
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String = "",

    /** Download status: idle, downloading, success, error */
    @ColumnInfo(name = "status")
    val status: String = "idle",

    /** Error message if last download failed */
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
