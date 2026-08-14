package com.example.smsforwarder.data.local.dao

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.smsforwarder.data.local.entity.FilterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FilterDao_Impl(private val __db: RoomDatabase) : FilterDao {

    private val updateSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    private fun queryAllFiltersFromDb(): List<FilterEntity> {
        val _sql = "SELECT * FROM filters ORDER BY displayOrder ASC, id DESC"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<FilterEntity>()
            while (_cursor.moveToNext()) {
                _result.add(readFilterEntity(_cursor))
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    private fun readFilterEntity(_cursor: Cursor): FilterEntity {
        val _id = _cursor.getColumnIndexOrThrow("id")
        val _name = _cursor.getColumnIndexOrThrow("name")
        val _targetPackageName = _cursor.getColumnIndexOrThrow("targetPackageName")
        val _appName = _cursor.getColumnIndexOrThrow("appName")
        val _keywords = _cursor.getColumnIndexOrThrow("keywords")
        val _excludeKeywords = _cursor.getColumnIndex("excludeKeywords")
        val _keywordLogic = _cursor.getColumnIndexOrThrow("keywordLogic")
        val _recipientPhoneNumber = _cursor.getColumnIndexOrThrow("recipientPhoneNumber")
        val _messageTemplate = _cursor.getColumnIndexOrThrow("messageTemplate")
        val _isActive = _cursor.getColumnIndexOrThrow("isActive")
        val _isSummationEnabled = _cursor.getColumnIndex("isSummationEnabled")
        val _startMonthOffset = _cursor.getColumnIndex("startMonthOffset")
        val _startDayType = _cursor.getColumnIndex("startDayType")
        val _startDayValue = _cursor.getColumnIndex("startDayValue")
        val _endMonthOffset = _cursor.getColumnIndex("endMonthOffset")
        val _endDayType = _cursor.getColumnIndex("endDayType")
        val _endDayValue = _cursor.getColumnIndex("endDayValue")
        val _monthlyTotal = _cursor.getColumnIndex("monthlyTotal")
        val _yearlyTotal = _cursor.getColumnIndex("yearlyTotal")
        val _lastMonthlyResetTime = _cursor.getColumnIndex("lastMonthlyResetTime")
        val _lastYearlyResetTime = _cursor.getColumnIndex("lastYearlyResetTime")
        val _displayOrder = _cursor.getColumnIndex("displayOrder")

        val excludeKw = if (_excludeKeywords != -1 && !_cursor.isNull(_excludeKeywords)) _cursor.getString(_excludeKeywords) else ""
        return FilterEntity(
            id = _cursor.getLong(_id),
            name = _cursor.getString(_name),
            targetPackageName = _cursor.getString(_targetPackageName),
            appName = _cursor.getString(_appName),
            keywords = _cursor.getString(_keywords),
            excludeKeywords = excludeKw,
            keywordLogic = _cursor.getString(_keywordLogic),
            recipientPhoneNumber = _cursor.getString(_recipientPhoneNumber),
            messageTemplate = _cursor.getString(_messageTemplate),
            isActive = _cursor.getInt(_isActive) != 0,
            isSummationEnabled = if (_isSummationEnabled != -1) _cursor.getInt(_isSummationEnabled) != 0 else false,
            startMonthOffset = if (_startMonthOffset != -1) _cursor.getInt(_startMonthOffset) else 0,
            startDayType = if (_startDayType != -1 && !_cursor.isNull(_startDayType)) _cursor.getString(_startDayType) else "SPECIFIC_DAY",
            startDayValue = if (_startDayValue != -1) _cursor.getInt(_startDayValue) else 1,
            endMonthOffset = if (_endMonthOffset != -1) _cursor.getInt(_endMonthOffset) else 0,
            endDayType = if (_endDayType != -1 && !_cursor.isNull(_endDayType)) _cursor.getString(_endDayType) else "SPECIFIC_DAY",
            endDayValue = if (_endDayValue != -1) _cursor.getInt(_endDayValue) else 31,
            monthlyTotal = if (_monthlyTotal != -1) _cursor.getLong(_monthlyTotal) else 0L,
            yearlyTotal = if (_yearlyTotal != -1) _cursor.getLong(_yearlyTotal) else 0L,
            lastMonthlyResetTime = if (_lastMonthlyResetTime != -1) _cursor.getLong(_lastMonthlyResetTime) else 0L,
            lastYearlyResetTime = if (_lastYearlyResetTime != -1) _cursor.getLong(_lastYearlyResetTime) else 0L,
            displayOrder = if (_displayOrder != -1) _cursor.getInt(_displayOrder) else 0
        )
    }

    override fun getAllFilters(): Flow<List<FilterEntity>> {
        return updateSignal.map {
            queryAllFiltersFromDb()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getActiveFilters(): List<FilterEntity> = withContext(Dispatchers.IO) {
        val _sql = "SELECT * FROM filters WHERE isActive = 1 ORDER BY displayOrder ASC, id DESC"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<FilterEntity>()
            while (_cursor.moveToNext()) {
                _result.add(readFilterEntity(_cursor))
            }
            _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override suspend fun getFilterById(id: Long): FilterEntity? = withContext(Dispatchers.IO) {
        val _sql = "SELECT * FROM filters WHERE id = ?"
        val _statement = RoomSQLiteQuery.acquire(_sql, 1)
        _statement.bindLong(1, id)
        val _cursor: Cursor = __db.query(_statement)
        try {
            if (_cursor.moveToFirst()) readFilterEntity(_cursor) else null
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override suspend fun getFilterByName(name: String): FilterEntity? = withContext(Dispatchers.IO) {
        val _sql = "SELECT * FROM filters WHERE name = ? LIMIT 1"
        val _statement = RoomSQLiteQuery.acquire(_sql, 1)
        _statement.bindString(1, name)
        val _cursor: Cursor = __db.query(_statement)
        try {
            if (_cursor.moveToFirst()) readFilterEntity(_cursor) else null
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override suspend fun updateFilterOrders(orderList: List<Pair<Long, Int>>): Unit = withContext(Dispatchers.IO) {
        val _sql = "UPDATE filters SET displayOrder = ? WHERE id = ?"
        __db.beginTransaction()
        try {
            val _stmt = __db.compileStatement(_sql)
            for ((id, order) in orderList) {
                _stmt.bindLong(1, order.toLong())
                _stmt.bindLong(2, id)
                _stmt.executeUpdateDelete()
            }
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun insertFilter(filter: FilterEntity): Long = withContext(Dispatchers.IO) {
        val result: Long
        __db.beginTransaction()
        try {
            val _sql: String
            val _stmt: SupportSQLiteStatement
            if (filter.id > 0) {
                _sql = "INSERT OR REPLACE INTO filters (id, name, targetPackageName, appName, keywords, excludeKeywords, keywordLogic, recipientPhoneNumber, messageTemplate, isActive, isSummationEnabled, startMonthOffset, startDayType, startDayValue, endMonthOffset, endDayType, endDayValue, monthlyTotal, yearlyTotal, lastMonthlyResetTime, lastYearlyResetTime, displayOrder) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                _stmt = __db.compileStatement(_sql)
                _stmt.bindLong(1, filter.id)
                _stmt.bindString(2, filter.name)
                _stmt.bindString(3, filter.targetPackageName)
                _stmt.bindString(4, filter.appName)
                _stmt.bindString(5, filter.keywords)
                _stmt.bindString(6, filter.excludeKeywords)
                _stmt.bindString(7, filter.keywordLogic)
                _stmt.bindString(8, filter.recipientPhoneNumber)
                _stmt.bindString(9, filter.messageTemplate)
                _stmt.bindLong(10, if (filter.isActive) 1L else 0L)
                _stmt.bindLong(11, if (filter.isSummationEnabled) 1L else 0L)
                _stmt.bindLong(12, filter.startMonthOffset.toLong())
                _stmt.bindString(13, filter.startDayType)
                _stmt.bindLong(14, filter.startDayValue.toLong())
                _stmt.bindLong(15, filter.endMonthOffset.toLong())
                _stmt.bindString(16, filter.endDayType)
                _stmt.bindLong(17, filter.endDayValue.toLong())
                _stmt.bindLong(18, filter.monthlyTotal)
                _stmt.bindLong(19, filter.yearlyTotal)
                _stmt.bindLong(20, filter.lastMonthlyResetTime)
                _stmt.bindLong(21, filter.lastYearlyResetTime)
                _stmt.bindLong(22, filter.displayOrder.toLong())
            } else {
                _sql = "INSERT INTO filters (name, targetPackageName, appName, keywords, excludeKeywords, keywordLogic, recipientPhoneNumber, messageTemplate, isActive, isSummationEnabled, startMonthOffset, startDayType, startDayValue, endMonthOffset, endDayType, endDayValue, monthlyTotal, yearlyTotal, lastMonthlyResetTime, lastYearlyResetTime, displayOrder) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                _stmt = __db.compileStatement(_sql)
                _stmt.bindString(1, filter.name)
                _stmt.bindString(2, filter.targetPackageName)
                _stmt.bindString(3, filter.appName)
                _stmt.bindString(4, filter.keywords)
                _stmt.bindString(5, filter.excludeKeywords)
                _stmt.bindString(6, filter.keywordLogic)
                _stmt.bindString(7, filter.recipientPhoneNumber)
                _stmt.bindString(8, filter.messageTemplate)
                _stmt.bindLong(9, if (filter.isActive) 1L else 0L)
                _stmt.bindLong(10, if (filter.isSummationEnabled) 1L else 0L)
                _stmt.bindLong(11, filter.startMonthOffset.toLong())
                _stmt.bindString(12, filter.startDayType)
                _stmt.bindLong(13, filter.startDayValue.toLong())
                _stmt.bindLong(14, filter.endMonthOffset.toLong())
                _stmt.bindString(15, filter.endDayType)
                _stmt.bindLong(16, filter.endDayValue.toLong())
                _stmt.bindLong(17, filter.monthlyTotal)
                _stmt.bindLong(18, filter.yearlyTotal)
                _stmt.bindLong(19, filter.lastMonthlyResetTime)
                _stmt.bindLong(20, filter.lastYearlyResetTime)
                _stmt.bindLong(21, filter.displayOrder.toLong())
            }
            result = _stmt.executeInsert()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
        result
    }

    override suspend fun updateFilter(filter: FilterEntity) {
        insertFilter(filter)
    }

    override suspend fun updateActiveStatus(filterId: Long, isActive: Boolean): Unit = withContext(Dispatchers.IO) {
        val _sql = "UPDATE filters SET isActive = ? WHERE id = ?"
        val _stmt = __db.compileStatement(_sql)
        _stmt.bindLong(1, if (isActive) 1L else 0L)
        _stmt.bindLong(2, filterId)
        __db.beginTransaction()
        try {
            _stmt.executeUpdateDelete()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun applySummationDelta(
        filterId: Long,
        amountDelta: Long,
        currentMonthlyCycleStart: Long,
        currentYearlyCycleStart: Long
    ): Unit = withContext(Dispatchers.IO) {
        // Single atomic UPDATE: SQLite serializes this statement, so the period-reset
        // check and the total increment happen as one indivisible read-modify-write —
        // two concurrent callers can never both read the pre-increment value.
        val _sql = """
            UPDATE filters SET
                monthlyTotal = CASE WHEN lastMonthlyResetTime < ? THEN ? ELSE monthlyTotal + ? END,
                yearlyTotal = CASE WHEN lastYearlyResetTime < ? THEN ? ELSE yearlyTotal + ? END,
                lastMonthlyResetTime = CASE WHEN lastMonthlyResetTime < ? THEN ? ELSE lastMonthlyResetTime END,
                lastYearlyResetTime = CASE WHEN lastYearlyResetTime < ? THEN ? ELSE lastYearlyResetTime END
            WHERE id = ?
        """.trimIndent()
        val _stmt = __db.compileStatement(_sql)
        _stmt.bindLong(1, currentMonthlyCycleStart)
        _stmt.bindLong(2, amountDelta)
        _stmt.bindLong(3, amountDelta)
        _stmt.bindLong(4, currentYearlyCycleStart)
        _stmt.bindLong(5, amountDelta)
        _stmt.bindLong(6, amountDelta)
        _stmt.bindLong(7, currentMonthlyCycleStart)
        _stmt.bindLong(8, currentMonthlyCycleStart)
        _stmt.bindLong(9, currentYearlyCycleStart)
        _stmt.bindLong(10, currentYearlyCycleStart)
        _stmt.bindLong(11, filterId)
        __db.beginTransaction()
        try {
            _stmt.executeUpdateDelete()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun deleteFilter(filter: FilterEntity): Unit = withContext(Dispatchers.IO) {
        val _sql = if (filter.id > 0) {
            "DELETE FROM filters WHERE id = ?"
        } else {
            "DELETE FROM filters WHERE name = ?"
        }
        val _stmt = __db.compileStatement(_sql)
        if (filter.id > 0) {
            _stmt.bindLong(1, filter.id)
        } else {
            _stmt.bindString(1, filter.name)
        }
        __db.beginTransaction()
        try {
            _stmt.executeUpdateDelete()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }
}
