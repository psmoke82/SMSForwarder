package com.example.smsforwarder.data.local.dao

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.smsforwarder.data.local.entity.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LogDao_Impl(private val __db: RoomDatabase) : LogDao {

    private val updateSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    private fun queryAllLogsFromDb(): List<LogEntity> {
        val _sql = "SELECT * FROM forward_logs ORDER BY timestamp DESC"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<LogEntity>()
            val _id = _cursor.getColumnIndexOrThrow("id")
            val _timestamp = _cursor.getColumnIndexOrThrow("timestamp")
            val _filterName = _cursor.getColumnIndexOrThrow("filterName")
            val _appName = _cursor.getColumnIndexOrThrow("appName")
            val _packageName = _cursor.getColumnIndexOrThrow("packageName")
            val _rawTitle = _cursor.getColumnIndexOrThrow("rawTitle")
            val _rawBody = _cursor.getColumnIndexOrThrow("rawBody")
            val _parsedMessage = _cursor.getColumnIndexOrThrow("parsedMessage")
            val _recipientNumber = _cursor.getColumnIndexOrThrow("recipientNumber")
            val _isSuccess = _cursor.getColumnIndexOrThrow("isSuccess")
            val _errorMessage = _cursor.getColumnIndexOrThrow("errorMessage")

            while (_cursor.moveToNext()) {
                val _item = LogEntity(
                    id = _cursor.getLong(_id),
                    timestamp = _cursor.getLong(_timestamp),
                    filterName = _cursor.getString(_filterName),
                    appName = _cursor.getString(_appName),
                    packageName = _cursor.getString(_packageName),
                    rawTitle = _cursor.getString(_rawTitle),
                    rawBody = _cursor.getString(_rawBody),
                    parsedMessage = _cursor.getString(_parsedMessage),
                    recipientNumber = _cursor.getString(_recipientNumber),
                    isSuccess = _cursor.getInt(_isSuccess) != 0,
                    errorMessage = if (_cursor.isNull(_errorMessage)) null else _cursor.getString(_errorMessage)
                )
                _result.add(_item)
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override fun getAllLogs(): Flow<List<LogEntity>> {
        return updateSignal.map {
            queryAllLogsFromDb()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun insertRawLog(log: LogEntity): Long = withContext(Dispatchers.IO) {
        val _sql = "INSERT INTO forward_logs (timestamp, filterName, appName, packageName, rawTitle, rawBody, parsedMessage, recipientNumber, isSuccess, errorMessage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val _stmt: SupportSQLiteStatement = __db.compileStatement(_sql)
        _stmt.bindLong(1, log.timestamp)
        _stmt.bindString(2, log.filterName)
        _stmt.bindString(3, log.appName)
        _stmt.bindString(4, log.packageName)
        _stmt.bindString(5, log.rawTitle)
        _stmt.bindString(6, log.rawBody)
        _stmt.bindString(7, log.parsedMessage)
        _stmt.bindString(8, log.recipientNumber)
        _stmt.bindLong(9, if (log.isSuccess) 1L else 0L)
        if (log.errorMessage == null) {
            _stmt.bindNull(10)
        } else {
            _stmt.bindString(10, log.errorMessage)
        }

        val result: Long
        __db.beginTransaction()
        try {
            result = _stmt.executeInsert()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
        result
    }

    override suspend fun pruneOldLogs(): Unit = withContext(Dispatchers.IO) {
        val _sql = "DELETE FROM forward_logs WHERE id NOT IN (SELECT id FROM forward_logs ORDER BY timestamp DESC LIMIT 200)"
        val _stmt = __db.compileStatement(_sql)
        __db.beginTransaction()
        try {
            _stmt.executeUpdateDelete()
            __db.setTransactionSuccessful()
        } finally {
            __db.endTransaction()
        }
        updateSignal.tryEmit(Unit)
    }

    override suspend fun clearLogs(): Unit = withContext(Dispatchers.IO) {
        val _sql = "DELETE FROM forward_logs"
        val _stmt = __db.compileStatement(_sql)
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
