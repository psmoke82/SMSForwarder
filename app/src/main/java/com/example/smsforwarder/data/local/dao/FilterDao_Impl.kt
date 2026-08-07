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
        val _sql = "SELECT * FROM filters ORDER BY id DESC"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<FilterEntity>()
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

            while (_cursor.moveToNext()) {
                val excludeKw = if (_excludeKeywords != -1 && !_cursor.isNull(_excludeKeywords)) _cursor.getString(_excludeKeywords) else ""
                val _item = FilterEntity(
                    id = _cursor.getLong(_id),
                    name = _cursor.getString(_name),
                    targetPackageName = _cursor.getString(_targetPackageName),
                    appName = _cursor.getString(_appName),
                    keywords = _cursor.getString(_keywords),
                    excludeKeywords = excludeKw,
                    keywordLogic = _cursor.getString(_keywordLogic),
                    recipientPhoneNumber = _cursor.getString(_recipientPhoneNumber),
                    messageTemplate = _cursor.getString(_messageTemplate),
                    isActive = _cursor.getInt(_isActive) != 0
                )
                _result.add(_item)
            }
            return _result
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override fun getAllFilters(): Flow<List<FilterEntity>> {
        return updateSignal.map {
            queryAllFiltersFromDb()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getActiveFilters(): List<FilterEntity> = withContext(Dispatchers.IO) {
        val _sql = "SELECT * FROM filters WHERE isActive = 1"
        val _statement = RoomSQLiteQuery.acquire(_sql, 0)
        val _cursor: Cursor = __db.query(_statement)
        try {
            val _result = mutableListOf<FilterEntity>()
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

            while (_cursor.moveToNext()) {
                val excludeKw = if (_excludeKeywords != -1 && !_cursor.isNull(_excludeKeywords)) _cursor.getString(_excludeKeywords) else ""
                val _item = FilterEntity(
                    id = _cursor.getLong(_id),
                    name = _cursor.getString(_name),
                    targetPackageName = _cursor.getString(_targetPackageName),
                    appName = _cursor.getString(_appName),
                    keywords = _cursor.getString(_keywords),
                    excludeKeywords = excludeKw,
                    keywordLogic = _cursor.getString(_keywordLogic),
                    recipientPhoneNumber = _cursor.getString(_recipientPhoneNumber),
                    messageTemplate = _cursor.getString(_messageTemplate),
                    isActive = _cursor.getInt(_isActive) != 0
                )
                _result.add(_item)
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

            if (_cursor.moveToFirst()) {
                val excludeKw = if (_excludeKeywords != -1 && !_cursor.isNull(_excludeKeywords)) _cursor.getString(_excludeKeywords) else ""
                FilterEntity(
                    id = _cursor.getLong(_id),
                    name = _cursor.getString(_name),
                    targetPackageName = _cursor.getString(_targetPackageName),
                    appName = _cursor.getString(_appName),
                    keywords = _cursor.getString(_keywords),
                    excludeKeywords = excludeKw,
                    keywordLogic = _cursor.getString(_keywordLogic),
                    recipientPhoneNumber = _cursor.getString(_recipientPhoneNumber),
                    messageTemplate = _cursor.getString(_messageTemplate),
                    isActive = _cursor.getInt(_isActive) != 0
                )
            } else null
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

            if (_cursor.moveToFirst()) {
                val excludeKw = if (_excludeKeywords != -1 && !_cursor.isNull(_excludeKeywords)) _cursor.getString(_excludeKeywords) else ""
                FilterEntity(
                    id = _cursor.getLong(_id),
                    name = _cursor.getString(_name),
                    targetPackageName = _cursor.getString(_targetPackageName),
                    appName = _cursor.getString(_appName),
                    keywords = _cursor.getString(_keywords),
                    excludeKeywords = excludeKw,
                    keywordLogic = _cursor.getString(_keywordLogic),
                    recipientPhoneNumber = _cursor.getString(_recipientPhoneNumber),
                    messageTemplate = _cursor.getString(_messageTemplate),
                    isActive = _cursor.getInt(_isActive) != 0
                )
            } else null
        } finally {
            _cursor.close()
            _statement.release()
        }
    }

    override suspend fun insertFilter(filter: FilterEntity): Long = withContext(Dispatchers.IO) {
        val result: Long
        __db.beginTransaction()
        try {
            val _sql: String
            val _stmt: SupportSQLiteStatement
            if (filter.id > 0) {
                _sql = "INSERT OR REPLACE INTO filters (id, name, targetPackageName, appName, keywords, excludeKeywords, keywordLogic, recipientPhoneNumber, messageTemplate, isActive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
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
            } else {
                _sql = "INSERT INTO filters (name, targetPackageName, appName, keywords, excludeKeywords, keywordLogic, recipientPhoneNumber, messageTemplate, isActive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
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
