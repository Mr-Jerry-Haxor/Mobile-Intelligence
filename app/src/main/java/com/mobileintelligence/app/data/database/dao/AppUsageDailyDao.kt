package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.AppUsageDaily
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDailyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(usage: AppUsageDaily)

    @Query("SELECT * FROM app_usage_daily WHERE date = :date ORDER BY total_time_ms DESC")
    fun getByDate(date: String): Flow<List<AppUsageDaily>>

    @Query("SELECT * FROM app_usage_daily WHERE date = :date ORDER BY total_time_ms DESC")
    suspend fun getByDateSync(date: String): List<AppUsageDaily>

    @Query("""
        SELECT 0 as id, MAX(date) as date,
               package_name, app_name,
               SUM(total_time_ms) as total_time_ms,
               SUM(session_count) as session_count,
               SUM(foreground_time_ms) as foreground_time_ms,
               MAX(last_used_time) as last_used_time,
               SUM(open_count) as open_count
        FROM app_usage_daily 
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY package_name
        ORDER BY total_time_ms DESC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<AppUsageDaily>

    @Query("SELECT * FROM app_usage_daily WHERE package_name = :packageName AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByAppAndDateRange(packageName: String, startDate: String, endDate: String): List<AppUsageDaily>

    @Query("SELECT * FROM app_usage_daily WHERE date = :date ORDER BY total_time_ms DESC LIMIT 1")
    suspend fun getMostUsedAppForDate(date: String): AppUsageDaily?

    @Query("DELETE FROM app_usage_daily WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}
