package com.fmz.spenitaicore.data.db.dao

import androidx.room.*
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeEntryDao {

    @Query("SELECT * FROM IncomeEntries ORDER BY date DESC, created_at DESC")
    fun getAllIncomeEntries(): Flow<List<IncomeEntry>>

    @Query("SELECT * FROM IncomeEntries WHERE date >= :from ORDER BY date DESC, created_at DESC")
    fun getIncomeEntriesFrom(from: String): Flow<List<IncomeEntry>>

    @Query("SELECT * FROM IncomeEntries WHERE date >= :from")
    suspend fun getIncomeEntriesFromSync(from: String?): List<IncomeEntry>

    @Query("SELECT * FROM IncomeEntries WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getIncomeEntriesBetween(start: String, end: String): Flow<List<IncomeEntry>>

    @Query("SELECT * FROM IncomeEntries WHERE id = :id")
    suspend fun getIncomeEntryById(id: Int): IncomeEntry?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM IncomeEntries WHERE date >= :from AND date <= :to")
    suspend fun getTotalIncome(from: String, to: String): Double

    @Query("SELECT * FROM IncomeEntries WHERE date = :date AND currency = :currency AND amount = :amount AND id != :excludeId AND source = :source")
    suspend fun findPotentialDuplicate(
        date: String,
        currency: String,
        amount: Double,
        source: String,
        excludeId: Int
    ): IncomeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: IncomeEntry): Long

    @Update
    suspend fun update(entry: IncomeEntry)

    @Delete
    suspend fun delete(entry: IncomeEntry)

    @Query("DELETE FROM IncomeEntries")
    suspend fun deleteAll()
}
