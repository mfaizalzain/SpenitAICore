package com.fmz.spenitaicore.data.db.dao

import androidx.room.*
import com.fmz.spenitaicore.data.db.entity.Receipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM Receipts ORDER BY date DESC, created_at DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM Receipts WHERE date >= :from ORDER BY date DESC, created_at DESC")
    fun getReceiptsFrom(from: String): Flow<List<Receipt>>

    @Query("SELECT * FROM Receipts WHERE date >= :from")
    suspend fun getReceiptsFromSync(from: String?): List<Receipt>

    @Query("SELECT * FROM Receipts WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getReceiptsBetween(start: String, end: String): Flow<List<Receipt>>

    @Query("SELECT * FROM Receipts WHERE is_tax_deductible = 1 AND tax_year = :taxYear ORDER BY date DESC")
    fun getTaxReceipts(taxYear: String): Flow<List<Receipt>>

    @Query("SELECT * FROM Receipts WHERE id = :id")
    suspend fun getReceiptById(id: Int): Receipt?

    @Query("""
        SELECT * FROM Receipts 
        WHERE (merchant LIKE '%' || :query || '%' 
            OR category LIKE '%' || :query || '%' 
            OR notes LIKE '%' || :query || '%')
        ORDER BY date DESC
    """)
    suspend fun searchReceipts(query: String): List<Receipt>

    @Query("SELECT * FROM Receipts WHERE is_synced = 0")
    suspend fun getUnsyncedReceipts(): List<Receipt>

    @Query("UPDATE Receipts SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Int>)

    @Query("SELECT COALESCE(SUM(total), 0) FROM Receipts WHERE date >= :from AND date <= :to")
    suspend fun getTotalSpend(from: String, to: String): Double

    @Query("SELECT * FROM Receipts WHERE date = :date ORDER BY date DESC")
    suspend fun getReceiptsByDate(date: String): List<Receipt>

    @Query("SELECT * FROM Receipts WHERE date = :date AND currency = :currency AND total = :total AND id != :excludeId AND merchant = :merchant")
    suspend fun findPotentialDuplicate(
        date: String,
        currency: String,
        total: Double,
        merchant: String,
        excludeId: Int
    ): Receipt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("DELETE FROM Receipts")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT tax_year FROM Receipts WHERE is_tax_deductible = 1 AND tax_year IS NOT NULL ORDER BY tax_year DESC")
    suspend fun getDistinctTaxYears(): List<String>
}
