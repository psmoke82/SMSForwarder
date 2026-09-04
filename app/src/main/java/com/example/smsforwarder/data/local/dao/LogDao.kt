package com.example.smsforwarder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smsforwarder.data.local.entity.LogEntity
import kotlinx.coroutines.flow.Flow

import androidx.room.Transaction

data class LogCountResult(val total: Int, val successCount: Int)

@Dao
interface LogDao {
    @Query("SELECT * FROM forward_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    /**
     * Aggregate count for the count widget: [filterName] null means "전체" (all filters),
     * [sinceTimestamp] 0L means "전체 기간" — both handled by the caller, not branched in SQL.
     */
    @Query("""
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN isSuccess = 1 THEN 1 ELSE 0 END), 0) AS successCount
        FROM forward_logs
        WHERE timestamp >= :sinceTimestamp
          AND (:filterName IS NULL OR filterName = :filterName)
    """)
    suspend fun getLogCounts(filterName: String?, sinceTimestamp: Long): LogCountResult

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawLog(log: LogEntity): Long

    @Query("""
        DELETE FROM forward_logs 
        WHERE id NOT IN (
            SELECT id FROM forward_logs 
            ORDER BY timestamp DESC 
            LIMIT 20000
        )
    """)
    suspend fun pruneOldLogs()

    @Transaction
    suspend fun insertLog(log: LogEntity): Long {
        val id = insertRawLog(log)
        pruneOldLogs()
        return id
    }

    @Query("DELETE FROM forward_logs")
    suspend fun clearLogs()
}
