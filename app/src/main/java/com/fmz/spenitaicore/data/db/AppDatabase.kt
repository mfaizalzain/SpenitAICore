package com.fmz.spenitaicore.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fmz.spenitaicore.data.db.dao.CategoryBudgetDao
import com.fmz.spenitaicore.data.db.dao.IncomeEntryDao
import com.fmz.spenitaicore.data.db.dao.ReceiptDao
import com.fmz.spenitaicore.data.db.dao.ReceiptItemDao
import com.fmz.spenitaicore.data.db.dao.UserProfileDao
import com.fmz.spenitaicore.data.db.entity.CategoryBudget
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.data.db.entity.UserProfile

@Database(
    entities = [
        Receipt::class,
        ReceiptItem::class,
        IncomeEntry::class,
        UserProfile::class,
        CategoryBudget::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun receiptItemDao(): ReceiptItemDao
    abstract fun incomeEntryDao(): IncomeEntryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `CategoryBudgets` (
                        `category` TEXT NOT NULL,
                        `monthly_limit` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`category`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun closeAndReset(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "spenit.db"
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
