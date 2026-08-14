package com.example.smsforwarder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.smsforwarder.data.local.dao.FilterDao
import com.example.smsforwarder.data.local.dao.FilterMonthlySummaryDao
import com.example.smsforwarder.data.local.dao.LogDao
import com.example.smsforwarder.data.local.entity.FilterEntity
import com.example.smsforwarder.data.local.entity.FilterMonthlySummaryEntity
import com.example.smsforwarder.data.local.entity.LogEntity

@Database(
    entities = [FilterEntity::class, LogEntity::class, FilterMonthlySummaryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun filterDao(): FilterDao
    abstract fun logDao(): LogDao
    abstract fun filterMonthlySummaryDao(): FilterMonthlySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE filters ADD COLUMN excludeKeywords TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE filters ADD COLUMN isSummationEnabled INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN startMonthOffset INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN startDayType TEXT NOT NULL DEFAULT 'SPECIFIC_DAY'")
                    db.execSQL("ALTER TABLE filters ADD COLUMN startDayValue INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE filters ADD COLUMN endMonthOffset INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN endDayType TEXT NOT NULL DEFAULT 'SPECIFIC_DAY'")
                    db.execSQL("ALTER TABLE filters ADD COLUMN endDayValue INTEGER NOT NULL DEFAULT 31")
                    db.execSQL("ALTER TABLE filters ADD COLUMN monthlyTotal INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN yearlyTotal INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN lastMonthlyResetTime INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE filters ADD COLUMN lastYearlyResetTime INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE forward_logs ADD COLUMN isSummationEnabled INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE forward_logs ADD COLUMN extractedAmountKRW INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE forward_logs ADD COLUMN originalCurrencyCode TEXT")
                    db.execSQL("ALTER TABLE forward_logs ADD COLUMN originalForeignAmount REAL")
                    db.execSQL("ALTER TABLE forward_logs ADD COLUMN appliedExchangeRate REAL")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `filter_monthly_summaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filterId` INTEGER NOT NULL, `filterName` TEXT NOT NULL, `periodLabel` TEXT NOT NULL, `periodStartTimestamp` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_filter_monthly_summaries_filterId_periodLabel` ON `filter_monthly_summaries` (`filterId`, `periodLabel`)"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE filters ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
