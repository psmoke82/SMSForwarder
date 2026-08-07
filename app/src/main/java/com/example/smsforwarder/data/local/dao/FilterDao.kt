package com.example.smsforwarder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.smsforwarder.data.local.entity.FilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters ORDER BY id DESC")
    fun getAllFilters(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE isActive = 1")
    suspend fun getActiveFilters(): List<FilterEntity>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun getFilterById(id: Long): FilterEntity?

    @Query("SELECT * FROM filters WHERE name = :name LIMIT 1")
    suspend fun getFilterByName(name: String): FilterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: FilterEntity): Long

    @Update
    suspend fun updateFilter(filter: FilterEntity)

    @Delete
    suspend fun deleteFilter(filter: FilterEntity)
}
