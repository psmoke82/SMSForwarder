package com.example.smsforwarder.data.local.dao

import com.example.smsforwarder.data.local.entity.FilterMonthlySummaryEntity
import kotlinx.coroutines.flow.Flow

interface FilterMonthlySummaryDao {
    fun getAllSummaries(): Flow<List<FilterMonthlySummaryEntity>>

    /**
     * Atomically upserts: increments totalAmount if a row already exists for
     * (filterId, periodLabel), otherwise inserts a new row with amountDelta as the
     * starting total. Also keeps filterName in sync with the filter's current name.
     */
    suspend fun incrementAmount(
        filterId: Long,
        filterName: String,
        periodLabel: String,
        periodStartTimestamp: Long,
        amountDelta: Long
    )

    /**
     * Restores summary rows from a backup, replacing any existing row for the same
     * (filterId, periodLabel) — matches the "overwrite with the backup snapshot" semantics
     * already used for restoring filters, rather than merging/adding into local data.
     */
    suspend fun restoreSummaries(entries: List<FilterMonthlySummaryEntity>)

    suspend fun deleteSummaryById(id: Long)

    suspend fun deleteSummaryByFilterAndPeriod(filterId: Long, periodLabel: String)

    suspend fun deleteSummariesByFilterId(filterId: Long)
}

