package com.example.smsforwarder.data.local

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.smsforwarder.data.local.dao.FilterDao
import com.example.smsforwarder.data.local.dao.FilterDao_Impl
import com.example.smsforwarder.data.local.dao.LogDao
import com.example.smsforwarder.data.local.dao.LogDao_Impl

class AppDatabase_Impl : AppDatabase() {

    @Volatile
    private var _filterDao: FilterDao? = null

    @Volatile
    private var _logDao: LogDao? = null

    override fun filterDao(): FilterDao {
        if (_filterDao != null) {
            return _filterDao!!
        } else {
            synchronized(this) {
                if (_filterDao == null) {
                    _filterDao = FilterDao_Impl(this)
                }
                return _filterDao!!
            }
        }
    }

    override fun logDao(): LogDao {
        if (_logDao != null) {
            return _logDao!!
        } else {
            synchronized(this) {
                if (_logDao == null) {
                    _logDao = LogDao_Impl(this)
                }
                return _logDao!!
            }
        }
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val callback = RoomOpenHelper(
            config,
            object : RoomOpenHelper.Delegate(2) {
                override fun createAllTables(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetPackageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `keywords` TEXT NOT NULL, `excludeKeywords` TEXT NOT NULL DEFAULT '', `keywordLogic` TEXT NOT NULL, `recipientPhoneNumber` TEXT NOT NULL, `messageTemplate` TEXT NOT NULL, `isActive` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `forward_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `filterName` TEXT NOT NULL, `appName` TEXT NOT NULL, `packageName` TEXT NOT NULL, `rawTitle` TEXT NOT NULL, `rawBody` TEXT NOT NULL, `parsedMessage` TEXT NOT NULL, `recipientNumber` TEXT NOT NULL, `isSuccess` INTEGER NOT NULL, `errorMessage` TEXT)")
                }

                override fun dropAllTables(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `filters`")
                    db.execSQL("DROP TABLE IF EXISTS `forward_logs`")
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `filters` ADD COLUMN `excludeKeywords` TEXT NOT NULL DEFAULT ''")
                    } catch (e: Exception) {
                        // Column already exists or newly created table
                    }
                    internalInitInvalidationTracker(db)
                }

                override fun onPreMigrate(db: SupportSQLiteDatabase) {
                }

                override fun onPostMigrate(db: SupportSQLiteDatabase) {
                }

                override fun validateMigration(db: SupportSQLiteDatabase) {
                }
            },
            "c6b8408f654b0870954b88a876404771",
            "d41d8cd98f00b204e9800998ecf8427e"
        )
        val builder = SupportSQLiteOpenHelper.Configuration.builder(config.context)
            .name(config.name)
            .callback(callback)
        return config.sqliteOpenHelperFactory.create(builder.build())
    }

    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(this, "filters", "forward_logs")
    }

    override fun clearAllTables() {
        super.assertNotMainThread()
        val db = super.openHelper.writableDatabase
        try {
            super.beginTransaction()
            db.execSQL("DELETE FROM `filters`")
            db.execSQL("DELETE FROM `forward_logs`")
            super.setTransactionSuccessful()
        } finally {
            super.endTransaction()
            db.query("PRAGMA wal_checkpoint(FULL)").close()
            if (!db.inTransaction()) {
                db.execSQL("VACUUM")
            }
        }
    }
}
