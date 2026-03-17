package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.AppSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AppSession): Long

    @Update
    suspend fun update(session: AppSession)

    @Query("SELECT * FROM app_sessions WHERE date = :date ORDER BY start_time DESC")
    fun getByDate(date: String): Flow<List<AppSession>>

    @Query("SELECT * FROM app_sessions WHERE date = :date ORDER BY start_time DESC")
    suspend fun getByDateSync(date: String): List<AppSession>

    @Query("SELECT * FROM app_sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveSession(): AppSession?

    @Query("SELECT * FROM app_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY start_time DESC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<AppSession>>

    @Query("SELECT * FROM app_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY start_time DESC")
    suspend fun getByDateRangeSync(startDate: String, endDate: String): List<AppSession>

    @Query("SELECT * FROM app_sessions WHERE package_name = :packageName AND date BETWEEN :startDate AND :endDate ORDER BY start_time DESC")
    suspend fun getByAppAndDateRange(packageName: String, startDate: String, endDate: String): List<AppSession>

    @Query("""
        SELECT package_name, app_name, 
               SUM(duration_ms) as total_time_ms, 
               COUNT(*) as session_count
        FROM app_sessions 
        WHERE date = :date AND is_foreground = 1
        GROUP BY package_name 
        ORDER BY total_time_ms DESC
    """)
    suspend fun getAppRankingForDate(date: String): List<AppRanking>

    @Query("""
        SELECT package_name, app_name, 
               SUM(duration_ms) as total_time_ms, 
               COUNT(*) as session_count
        FROM app_sessions 
        WHERE date BETWEEN :startDate AND :endDate AND is_foreground = 1
        GROUP BY package_name 
        ORDER BY total_time_ms DESC
    """)
    suspend fun getAppRankingForRange(startDate: String, endDate: String): List<AppRanking>

    @Query("SELECT DISTINCT package_name FROM app_sessions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getDistinctApps(startDate: String, endDate: String): List<String>

    @Query("SELECT COUNT(DISTINCT package_name) FROM app_sessions WHERE date = :date")
    suspend fun getUniqueAppCountForDate(date: String): Int

    @Query("DELETE FROM app_sessions WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}

data class AppRanking(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "total_time_ms") val totalTimeMs: Long,
    @ColumnInfo(name = "session_count") val sessionCount: Int
)
