package com.example.smsforwarder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smsforwarder.data.local.entity.LogEntity
import kotlinx.coroutines.flow.Flow

import androidx.room.Transaction

@Dao
interface LogDao {
    @Query("SELECT * FROM forward_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

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
