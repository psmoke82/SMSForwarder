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
    @Query("SELECT * FROM filters ORDER BY displayOrder ASC, id DESC")
    fun getAllFilters(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE isActive = 1 ORDER BY displayOrder ASC, id DESC")
    suspend fun getActiveFilters(): List<FilterEntity>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun getFilterById(id: Long): FilterEntity?

    @Query("SELECT * FROM filters WHERE name = :name LIMIT 1")
    suspend fun getFilterByName(name: String): FilterEntity?

    suspend fun updateFilterOrders(orderList: List<Pair<Long, Int>>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: FilterEntity): Long

    @Update
    suspend fun updateFilter(filter: FilterEntity)

    @Delete
    suspend fun deleteFilter(filter: FilterEntity)

    /**
     * Updates only the isActive column, leaving monthlyTotal/yearlyTotal untouched —
     * used by the filter-list toggle switch so it can never clobber a total that a
     * background message just accumulated via applySummationDelta.
     */
    suspend fun updateActiveStatus(filterId: Long, isActive: Boolean)

    /**
     * Atomically applies a period-reset check and an amount delta to a filter's
     * monthly/yearly totals in a single SQL statement, avoiding the read-modify-write
     * race that two near-simultaneous messages (e.g. SMS + notification for the same
     * payment) could otherwise hit.
     */
    suspend fun applySummationDelta(
        filterId: Long,
        amountDelta: Long,
        currentMonthlyCycleStart: Long,
        currentYearlyCycleStart: Long
    )
}
