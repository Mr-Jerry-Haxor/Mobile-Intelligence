package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.ScreenSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScreenSession): Long

    @Update
    suspend fun update(session: ScreenSession)

    @Query("SELECT * FROM screen_sessions WHERE id = :id")
    suspend fun getById(id: Long): ScreenSession?

    @Query("SELECT * FROM screen_sessions WHERE date = :date ORDER BY screen_on_time DESC")
    fun getByDate(date: String): Flow<List<ScreenSession>>

    @Query("SELECT * FROM screen_sessions WHERE date = :date ORDER BY screen_on_time DESC")
    suspend fun getByDateSync(date: String): List<ScreenSession>

    @Query("SELECT * FROM screen_sessions WHERE screen_off_time IS NULL ORDER BY screen_on_time DESC LIMIT 1")
    suspend fun getActiveSession(): ScreenSession?

    @Query("SELECT * FROM screen_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY screen_on_time DESC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<ScreenSession>>

    @Query("SELECT * FROM screen_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY screen_on_time DESC")
    suspend fun getByDateRangeSync(startDate: String, endDate: String): List<ScreenSession>

    @Query("SELECT SUM(duration_ms) FROM screen_sessions WHERE date = :date")
    suspend fun getTotalScreenTimeForDate(date: String): Long?

    @Query("SELECT COUNT(*) FROM screen_sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: String): Int

    @Query("SELECT MAX(duration_ms) FROM screen_sessions WHERE date = :date")
    suspend fun getLongestSessionForDate(date: String): Long?

    @Query("SELECT AVG(duration_ms) FROM screen_sessions WHERE date = :date AND duration_ms > 0")
    suspend fun getAverageSessionForDate(date: String): Long?

    @Query("SELECT SUM(duration_ms) FROM screen_sessions WHERE date = :date AND is_night_usage = 1")
    suspend fun getNightUsageForDate(date: String): Long?

    @Query("SELECT COUNT(*) FROM screen_sessions WHERE date = :date AND is_night_usage = 1")
    suspend fun getNightSessionCountForDate(date: String): Int

    @Query("SELECT * FROM screen_sessions WHERE date = :date ORDER BY screen_on_time ASC LIMIT 1")
    suspend fun getFirstSessionOfDay(date: String): ScreenSession?

    @Query("SELECT * FROM screen_sessions WHERE date = :date ORDER BY screen_on_time DESC LIMIT 1")
    suspend fun getLastSessionOfDay(date: String): ScreenSession?

    @Query("SELECT SUM(duration_ms) FROM screen_sessions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalScreenTimeForRange(startDate: String, endDate: String): Long?

    @Query("SELECT * FROM screen_sessions WHERE hour_of_day = :hour AND date BETWEEN :startDate AND :endDate")
    suspend fun getSessionsByHour(hour: Int, startDate: String, endDate: String): List<ScreenSession>

    @Query("DELETE FROM screen_sessions WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int

    @Query("SELECT COUNT(*) FROM screen_sessions")
    suspend fun getTotalCount(): Int

    @Query("SELECT MIN(date) FROM screen_sessions")
    suspend fun getOldestDate(): String?
}
