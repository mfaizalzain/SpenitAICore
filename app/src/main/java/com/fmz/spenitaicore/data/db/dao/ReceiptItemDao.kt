package com.fmz.spenitaicore.data.db.dao

import androidx.room.*
import com.fmz.spenitaicore.data.db.entity.ReceiptItem

@Dao
interface ReceiptItemDao {

    @Query("SELECT * FROM ReceiptItems WHERE receipt_id = :receiptId")
    suspend fun getItemsForReceipt(receiptId: Int): List<ReceiptItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReceiptItem): Long

    @Update
    suspend fun update(item: ReceiptItem)

    @Query("DELETE FROM ReceiptItems WHERE receipt_id = :receiptId")
    suspend fun deleteByReceiptId(receiptId: Int)

    @Delete
    suspend fun delete(item: ReceiptItem)

    @Query("DELETE FROM ReceiptItems")
    suspend fun deleteAll()
}
