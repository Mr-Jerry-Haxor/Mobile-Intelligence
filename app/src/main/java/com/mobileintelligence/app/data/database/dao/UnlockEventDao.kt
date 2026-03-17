package com.mobileintelligence.app.data.database.dao

import androidx.room.*
import com.mobileintelligence.app.data.database.entity.UnlockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: UnlockEvent): Long

    @Query("SELECT * FROM unlock_events WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<UnlockEvent>>

    @Query("SELECT * FROM unlock_events WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getByDateSync(date: String): List<UnlockEvent>

    @Query("SELECT COUNT(*) FROM unlock_events WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT * FROM unlock_events WHERE date = :date ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstUnlockOfDay(date: String): UnlockEvent?

    @Query("SELECT * FROM unlock_events WHERE date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastUnlockOfDay(date: String): UnlockEvent?

    @Query("SELECT * FROM unlock_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastUnlock(): UnlockEvent?

    @Query("""
        SELECT hour_of_day, COUNT(*) as count 
        FROM unlock_events 
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY hour_of_day 
        ORDER BY count DESC
    """)
    suspend fun getUnlocksByHour(startDate: String, endDate: String): List<UnlockHourCount>

    @Query("SELECT AVG(cnt) FROM (SELECT COUNT(*) as cnt FROM unlock_events WHERE date BETWEEN :startDate AND :endDate GROUP BY date)")
    suspend fun getAverageDailyUnlocks(startDate: String, endDate: String): Float?

    @Query("DELETE FROM unlock_events WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}

data class UnlockHourCount(
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "count") val count: Int
)
