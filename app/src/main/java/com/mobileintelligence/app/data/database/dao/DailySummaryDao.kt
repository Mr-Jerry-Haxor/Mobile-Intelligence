package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.DailySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(summary: DailySummary)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getByDate(date: String): DailySummary?

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    fun getByDateFlow(date: String): Flow<DailySummary?>

    @Query("SELECT * FROM daily_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<DailySummary>>

    @Query("SELECT * FROM daily_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRangeSync(startDate: String, endDate: String): List<DailySummary>

    @Query("SELECT AVG(total_screen_time_ms) FROM daily_summary WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getAverageScreenTime(startDate: String, endDate: String): Long?

    @Query("SELECT AVG(total_unlocks) FROM daily_summary WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getAverageUnlocks(startDate: String, endDate: String): Float?

    @Query("SELECT MAX(total_screen_time_ms) FROM daily_summary WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getMaxScreenTime(startDate: String, endDate: String): Long?

    @Query("SELECT * FROM daily_summary ORDER BY total_screen_time_ms DESC LIMIT 1")
    suspend fun getHighestUsageDay(): DailySummary?

    @Query("SELECT AVG(addiction_score) FROM daily_summary WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getAverageAddictionScore(startDate: String, endDate: String): Float?

    @Query("SELECT AVG(focus_score) FROM daily_summary WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getAverageFocusScore(startDate: String, endDate: String): Float?

    @Query("SELECT * FROM daily_summary ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDays(limit: Int): List<DailySummary>

    @Query("DELETE FROM daily_summary WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}
