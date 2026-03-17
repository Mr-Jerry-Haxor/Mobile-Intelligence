package com.mobileintelligence.app.dns.data.dao

import androidx.room.*
import com.mobileintelligence.app.dns.data.entity.BlocklistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {

    // ─── Queries ────────────────────────────────────────────────

    @Query("SELECT * FROM blocklists ORDER BY is_built_in DESC, name ASC")
    fun getAllBlocklists(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklists WHERE is_enabled = 1 ORDER BY name ASC")
    fun getEnabledBlocklists(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklists WHERE is_enabled = 1")
    suspend fun getEnabledBlocklistsSnapshot(): List<BlocklistEntity>

    @Query("SELECT * FROM blocklists WHERE is_built_in = 1 ORDER BY name ASC")
    fun getBuiltInBlocklists(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklists WHERE is_built_in = 0 ORDER BY name ASC")
    fun getCustomBlocklists(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklists WHERE id = :id")
    suspend fun getById(id: Long): BlocklistEntity?

    @Query("SELECT * FROM blocklists WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): BlocklistEntity?

    @Query("SELECT COUNT(*) FROM blocklists WHERE is_enabled = 1")
    fun getEnabledCount(): Flow<Int>

    @Query("SELECT SUM(domain_count) FROM blocklists WHERE is_enabled = 1")
    fun getTotalEnabledDomainCount(): Flow<Int?>

    // ─── Insert / Update / Delete ───────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(blocklist: BlocklistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(blocklists: List<BlocklistEntity>)

    @Update
    suspend fun update(blocklist: BlocklistEntity)

    @Delete
    suspend fun delete(blocklist: BlocklistEntity)

    @Query("DELETE FROM blocklists WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ─── Targeted updates ───────────────────────────────────────

    @Query("UPDATE blocklists SET is_enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE blocklists SET status = :status, error_message = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE blocklists SET domain_count = :count, last_updated = :timestamp, local_file_path = :filePath, status = 'success', error_message = NULL WHERE id = :id")
    suspend fun setDownloadSuccess(id: Long, count: Int, timestamp: Long, filePath: String)

    @Query("UPDATE blocklists SET status = 'error', error_message = :error WHERE id = :id")
    suspend fun setDownloadError(id: Long, error: String)

    // ─── Bulk operations ────────────────────────────────────────

    @Query("DELETE FROM blocklists WHERE is_built_in = 0")
    suspend fun deleteAllCustom()

    @Query("SELECT COUNT(*) FROM blocklists")
    suspend fun count(): Int
}
