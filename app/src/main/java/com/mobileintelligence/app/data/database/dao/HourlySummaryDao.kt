package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.HourlySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(summary: HourlySummary)

    @Query("SELECT * FROM hourly_summary WHERE date = :date ORDER BY hour ASC")
    fun getByDate(date: String): Flow<List<HourlySummary>>

    @Query("SELECT * FROM hourly_summary WHERE date = :date ORDER BY hour ASC")
    suspend fun getByDateSync(date: String): List<HourlySummary>

    @Query("""
        SELECT hour, AVG(screen_time_ms) as screen_time_ms, 
               AVG(session_count) as session_count, AVG(unlock_count) as unlock_count
        FROM hourly_summary 
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY hour ORDER BY hour ASC
    """)
    suspend fun getAverageHourlyPattern(startDate: String, endDate: String): List<HourlyAverage>

    @Query("DELETE FROM hourly_summary WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}

data class HourlyAverage(
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "screen_time_ms") val screenTimeMs: Long,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int
)
