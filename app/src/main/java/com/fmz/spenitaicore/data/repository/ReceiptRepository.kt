package com.fmz.spenitaicore.data.repository

import com.fmz.spenitaicore.data.db.dao.ReceiptDao
import com.fmz.spenitaicore.data.db.dao.ReceiptItemDao
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import kotlinx.coroutines.flow.Flow

class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val receiptItemDao: ReceiptItemDao
) {

    fun getAllReceipts(): Flow<List<Receipt>> = receiptDao.getAllReceipts()

    fun getReceiptsFrom(from: String): Flow<List<Receipt>> = receiptDao.getReceiptsFrom(from)

    suspend fun getReceiptsFromSync(from: String?): List<Receipt> =
        if (from == null) {
            receiptDao.getAllReceiptsSync()
        } else {
            receiptDao.getReceiptsFromSync(from)
        }

    suspend fun getReceiptsByCreatedAtFromSync(fromMillis: Long?): List<Receipt> =
        if (fromMillis == null) {
            receiptDao.getAllReceiptsSync()
        } else {
            receiptDao.getReceiptsByCreatedAtFromSync(fromMillis)
        }

    suspend fun getReceiptById(id: Int): Receipt? = receiptDao.getReceiptById(id)

    suspend fun searchReceipts(query: String): List<Receipt> = receiptDao.searchReceipts(query)

    suspend fun getTaxReceipts(taxYear: String): List<Receipt> {
        var list = emptyList<Receipt>()
        receiptDao.getTaxReceipts(taxYear).collect { list = it; return@collect }
        return list
    }

    suspend fun getTotalSpend(from: String, to: String): Double = receiptDao.getTotalSpend(from, to)

    suspend fun getDistinctTaxYears(): List<String> = receiptDao.getDistinctTaxYears()

    suspend fun findPotentialDuplicate(candidate: Receipt): Receipt? =
        receiptDao.findPotentialDuplicate(
            date = candidate.date,
            currency = candidate.currency,
            total = candidate.total,
            merchant = candidate.merchant,
            excludeId = candidate.id
        )

    suspend fun saveReceipt(receipt: Receipt): Long {
        val now = System.currentTimeMillis()
        val saved = receipt.copy(updatedAt = now)
        return if (receipt.id == 0) {
            receiptDao.insert(saved.copy(createdAt = now))
        } else {
            receiptDao.update(saved)
            receipt.id.toLong()
        }
    }

    suspend fun deleteReceipt(receipt: Receipt) {
        receiptItemDao.deleteByReceiptId(receipt.id)
        receiptDao.delete(receipt)
    }

    suspend fun deleteAll() {
        receiptItemDao.deleteAll()
        receiptDao.deleteAll()
    }

    suspend fun getReceiptItems(receiptId: Int): List<ReceiptItem> =
        receiptItemDao.getItemsForReceipt(receiptId)

    suspend fun saveReceiptItem(item: ReceiptItem): Long =
        if (item.id == 0) receiptItemDao.insert(item) else {
            receiptItemDao.update(item)
            item.id.toLong()
        }

    suspend fun deleteReceiptItems(receiptId: Int) = receiptItemDao.deleteByReceiptId(receiptId)
}
