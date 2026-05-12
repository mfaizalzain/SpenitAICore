package com.fmz.spenitaicore.data.repository

import com.fmz.spenitaicore.data.db.dao.IncomeEntryDao
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import kotlinx.coroutines.flow.Flow

class IncomeRepository(
    private val incomeEntryDao: IncomeEntryDao
) {

    fun getAllIncomeEntries(): Flow<List<IncomeEntry>> = incomeEntryDao.getAllIncomeEntries()

    fun getIncomeEntriesFrom(from: String): Flow<List<IncomeEntry>> = incomeEntryDao.getIncomeEntriesFrom(from)

    suspend fun getIncomeEntriesFromSync(from: String?): List<IncomeEntry> =
        if (from == null) {
            incomeEntryDao.getAllIncomeEntriesSync()
        } else {
            incomeEntryDao.getIncomeEntriesFromSync(from)
        }

    suspend fun getIncomeEntryById(id: Int): IncomeEntry? = incomeEntryDao.getIncomeEntryById(id)

    suspend fun getTotalIncome(from: String, to: String): Double = incomeEntryDao.getTotalIncome(from, to)

    suspend fun findPotentialDuplicate(candidate: IncomeEntry): IncomeEntry? =
        incomeEntryDao.findPotentialDuplicate(
            date = candidate.date,
            currency = candidate.currency,
            amount = candidate.amount,
            source = candidate.source,
            excludeId = candidate.id
        )

    suspend fun saveIncomeEntry(entry: IncomeEntry): Long {
        val now = System.currentTimeMillis()
        val saved = entry.copy(updatedAt = now)
        return if (entry.id == 0) {
            incomeEntryDao.insert(saved.copy(createdAt = now))
        } else {
            incomeEntryDao.update(saved)
            entry.id.toLong()
        }
    }

    suspend fun deleteIncomeEntry(entry: IncomeEntry) = incomeEntryDao.delete(entry)

    suspend fun deleteAll() = incomeEntryDao.deleteAll()
}
