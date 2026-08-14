package com.example.smsforwarder.data.local

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.smsforwarder.data.local.dao.FilterDao
import com.example.smsforwarder.data.local.dao.FilterDao_Impl
import com.example.smsforwarder.data.local.dao.FilterMonthlySummaryDao
import com.example.smsforwarder.data.local.dao.FilterMonthlySummaryDao_Impl
import com.example.smsforwarder.data.local.dao.LogDao
import com.example.smsforwarder.data.local.dao.LogDao_Impl

class AppDatabase_Impl : AppDatabase() {

    @Volatile
    private var _filterDao: FilterDao? = null

    @Volatile
    private var _logDao: LogDao? = null

    @Volatile
    private var _filterMonthlySummaryDao: FilterMonthlySummaryDao? = null

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

    override fun filterMonthlySummaryDao(): FilterMonthlySummaryDao {
        if (_filterMonthlySummaryDao != null) {
            return _filterMonthlySummaryDao!!
        } else {
            synchronized(this) {
                if (_filterMonthlySummaryDao == null) {
                    _filterMonthlySummaryDao = FilterMonthlySummaryDao_Impl(this)
                }
                return _filterMonthlySummaryDao!!
            }
        }
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val callback = RoomOpenHelper(
            config,
            object : RoomOpenHelper.Delegate(6) {
                override fun createAllTables(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetPackageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `keywords` TEXT NOT NULL, `excludeKeywords` TEXT NOT NULL DEFAULT '', `keywordLogic` TEXT NOT NULL, `recipientPhoneNumber` TEXT NOT NULL, `messageTemplate` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isSummationEnabled` INTEGER NOT NULL DEFAULT 0, `startMonthOffset` INTEGER NOT NULL DEFAULT 0, `startDayType` TEXT NOT NULL DEFAULT 'SPECIFIC_DAY', `startDayValue` INTEGER NOT NULL DEFAULT 1, `endMonthOffset` INTEGER NOT NULL DEFAULT 0, `endDayType` TEXT NOT NULL DEFAULT 'SPECIFIC_DAY', `endDayValue` INTEGER NOT NULL DEFAULT 31, `monthlyTotal` INTEGER NOT NULL DEFAULT 0, `yearlyTotal` INTEGER NOT NULL DEFAULT 0, `lastMonthlyResetTime` INTEGER NOT NULL DEFAULT 0, `lastYearlyResetTime` INTEGER NOT NULL DEFAULT 0, `displayOrder` INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `forward_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `filterName` TEXT NOT NULL, `appName` TEXT NOT NULL, `packageName` TEXT NOT NULL, `rawTitle` TEXT NOT NULL, `rawBody` TEXT NOT NULL, `parsedMessage` TEXT NOT NULL, `recipientNumber` TEXT NOT NULL, `isSuccess` INTEGER NOT NULL, `errorMessage` TEXT, `isSummationEnabled` INTEGER NOT NULL DEFAULT 0, `extractedAmountKRW` INTEGER NOT NULL DEFAULT 0, `originalCurrencyCode` TEXT, `originalForeignAmount` REAL, `appliedExchangeRate` REAL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `filter_monthly_summaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filterId` INTEGER NOT NULL, `filterName` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `periodStartTimestamp` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_filter_monthly_summaries_filterId_periodLabel` ON `filter_monthly_summaries` (`filterId`, `periodLabel`)")
                }

                override fun dropAllTables(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `filters`")
                    db.execSQL("DROP TABLE IF EXISTS `forward_logs`")
                    db.execSQL("DROP TABLE IF EXISTS `filter_monthly_summaries`")
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    val columnFixups = listOf(
                        "ALTER TABLE `filters` ADD COLUMN `excludeKeywords` TEXT NOT NULL DEFAULT ''",
                        "ALTER TABLE `filters` ADD COLUMN `isSummationEnabled` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `startMonthOffset` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `startDayType` TEXT NOT NULL DEFAULT 'SPECIFIC_DAY'",
                        "ALTER TABLE `filters` ADD COLUMN `startDayValue` INTEGER NOT NULL DEFAULT 1",
                        "ALTER TABLE `filters` ADD COLUMN `endMonthOffset` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `endDayType` TEXT NOT NULL DEFAULT 'SPECIFIC_DAY'",
                        "ALTER TABLE `filters` ADD COLUMN `endDayValue` INTEGER NOT NULL DEFAULT 31",
                        "ALTER TABLE `filters` ADD COLUMN `monthlyTotal` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `yearlyTotal` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `lastMonthlyResetTime` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `lastYearlyResetTime` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `filters` ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `forward_logs` ADD COLUMN `isSummationEnabled` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `forward_logs` ADD COLUMN `extractedAmountKRW` INTEGER NOT NULL DEFAULT 0",
                        "ALTER TABLE `forward_logs` ADD COLUMN `originalCurrencyCode` TEXT",
                        "ALTER TABLE `forward_logs` ADD COLUMN `originalForeignAmount` REAL",
                        "ALTER TABLE `forward_logs` ADD COLUMN `appliedExchangeRate` REAL"
                    )
                    for (sql in columnFixups) {
                        try {
                            db.execSQL(sql)
                        } catch (e: Exception) {
                            // Column already exists or newly created table
                        }
                    }
                    try {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `filter_monthly_summaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filterId` INTEGER NOT NULL, `filterName` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `periodStartTimestamp` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_filter_monthly_summaries_filterId_periodLabel` ON `filter_monthly_summaries` (`filterId`, `periodLabel`)")
                    } catch (e: Exception) {
                        // Table/index already exists
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
        return InvalidationTracker(this, "filters", "forward_logs", "filter_monthly_summaries")
    }

    override fun clearAllTables() {
        super.assertNotMainThread()
        val db = super.openHelper.writableDatabase
        try {
            super.beginTransaction()
            db.execSQL("DELETE FROM `filters`")
            db.execSQL("DELETE FROM `forward_logs`")
            db.execSQL("DELETE FROM `filter_monthly_summaries`")
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
