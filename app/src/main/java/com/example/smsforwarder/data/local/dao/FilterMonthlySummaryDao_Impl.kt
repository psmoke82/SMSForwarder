package com.example.smsforwarder.data.local.dao

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import com.example.smsforwarder.data.local.entity.FilterMonthlySummaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FilterMonthlySummaryDao_Impl(private val __db: RoomDatabase) : FilterMonthlySummaryDao {

    private val updateSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    private fun queryAllFromDb(): List<FilterMonthlySummaryEntity> {
        val _sql = "SELECT * FROM filter_monthly_summaries ORDER BY periodStartTimestamp DESC"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<FilterMonthlySummaryEntity>()
            val _id = _cursor.getColumnIndexOrThrow("id")
            val _filterId = _cursor.getColumnIndexOrThrow("filterId")
            val _filterName = _cursor.getColumnIndexOrThrow("filterName")
            val _periodLabel = _cursor.getColumnIndexOrThrow("periodLabel")
            val _periodStartTimestamp = _cursor.getColumnIndexOrThrow("periodStartTimestamp")
            val _totalAmount = _cursor.getColumnIndexOrThrow("totalAmount")

            while (_cursor.moveToNext()) {
                _result.add(
                    FilterMonthlySummaryEntity(
                        id = _cursor.getLong(_id),
                        filterId = _cursor.getLong(_filterId),
                        filterName = _cursor.getString(_filterName),
                        periodLabel = _cursor.getString(_periodLabel),
                        periodStartTimestamp = _cursor.getLong(_periodStartTimestamp),
                        totalAmount = _cursor.getLong(_totalAmount)
                    )
                )
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override fun getAllSummaries(): Flow<List<FilterMonthlySummaryEntity>> {
        return updateSignal.map { queryAllFromDb() }.flowOn(Dispatchers.IO)
    }

    override suspend fun incrementAmount(
        filterId: Long,
        filterName: String,
        periodLabel: String,
        periodStartTimestamp: Long,
        amountDelta: Long
    ): Unit = withContext(Dispatchers.IO) {
        __db.beginTransaction()
        try {
            val updateStmt = __db.compileStatement(
                "UPDATE filter_monthly_summaries SET totalAmount = totalAmount + ?, filterName = ? WHERE filterId = ? AND periodLabel = ?"
            )
            updateStmt.bindLong(1, amountDelta)
            updateStmt.bindString(2, filterName)
            updateStmt.bindLong(3, filterId)
            updateStmt.bindString(4, periodLabel)
            val rowsUpdated = updateStmt.executeUpdateDelete()

            if (rowsUpdated == 0) {
                val insertStmt = __db.compileStatement(
                    "INSERT INTO filter_monthly_summaries (filterId, filterName, periodLabel, periodStartTimestamp, totalAmount) VALUES (?, ?, ?, ?, ?)"
                )
                insertStmt.bindLong(1, filterId)
                insertStmt.bindString(2, filterName)
                insertStmt.bindString(3, periodLabel)
                insertStmt.bindLong(4, periodStartTimestamp)
                insertStmt.bindLong(5, amountDelta)
                insertStmt.executeInsert()
            }
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun restoreSummaries(entries: List<FilterMonthlySummaryEntity>): Unit = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        __db.beginTransaction()
        try {
            val stmt = __db.compileStatement(
                "INSERT OR REPLACE INTO filter_monthly_summaries (filterId, filterName, periodLabel, periodStartTimestamp, totalAmount) VALUES (?, ?, ?, ?, ?)"
            )
            for (entry in entries) {
                stmt.bindLong(1, entry.filterId)
                stmt.bindString(2, entry.filterName)
                stmt.bindString(3, entry.periodLabel)
                stmt.bindLong(4, entry.periodStartTimestamp)
                stmt.bindLong(5, entry.totalAmount)
                stmt.executeInsert()
            }
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun deleteSummaryById(id: Long): Unit = withContext(Dispatchers.IO) {
        val stmt = __db.compileStatement("DELETE FROM filter_monthly_summaries WHERE id = ?")
        stmt.bindLong(1, id)
        stmt.executeUpdateDelete()
        updateSignal.tryEmit(Unit)
    }

    override suspend fun deleteSummaryByFilterAndPeriod(filterId: Long, periodLabel: String): Unit = withContext(Dispatchers.IO) {
        val stmt = __db.compileStatement("DELETE FROM filter_monthly_summaries WHERE filterId = ? AND periodLabel = ?")
        stmt.bindLong(1, filterId)
        stmt.bindString(2, periodLabel)
        stmt.executeUpdateDelete()
        updateSignal.tryEmit(Unit)
    }

    override suspend fun deleteSummariesByFilterId(filterId: Long): Unit = withContext(Dispatchers.IO) {
        val stmt = __db.compileStatement("DELETE FROM filter_monthly_summaries WHERE filterId = ?")
        stmt.bindLong(1, filterId)
        stmt.executeUpdateDelete()
        updateSignal.tryEmit(Unit)
    }
}

