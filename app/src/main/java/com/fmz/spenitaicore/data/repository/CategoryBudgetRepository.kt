package com.fmz.spenitaicore.data.repository

import com.fmz.spenitaicore.data.db.dao.CategoryBudgetDao
import com.fmz.spenitaicore.data.db.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

class CategoryBudgetRepository(
    private val budgetDao: CategoryBudgetDao
) {
    fun getAllBudgets(): Flow<List<CategoryBudget>> = budgetDao.getAllBudgets()

    suspend fun getBudget(category: String): CategoryBudget? = budgetDao.getBudget(category)

    suspend fun upsert(budget: CategoryBudget) {
        budgetDao.upsert(budget.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(category: String) = budgetDao.delete(category)

    suspend fun deleteAll() = budgetDao.deleteAll()
}
