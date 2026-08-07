package com.fmz.spenitaicore.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fmz.spenitaicore.data.db.dao.IncomeEntryDao
import com.fmz.spenitaicore.data.db.dao.ReceiptDao
import com.fmz.spenitaicore.data.db.dao.ReceiptItemDao
import com.fmz.spenitaicore.data.db.dao.UserProfileDao
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.data.db.entity.UserProfile

@Database(
    entities = [
        Receipt::class,
        ReceiptItem::class,
        IncomeEntry::class,
        UserProfile::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun receiptItemDao(): ReceiptItemDao
    abstract fun incomeEntryDao(): IncomeEntryDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
