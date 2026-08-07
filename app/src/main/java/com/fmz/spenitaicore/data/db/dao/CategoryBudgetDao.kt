package com.fmz.spenitaicore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fmz.spenitaicore.data.db.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Query("SELECT * FROM CategoryBudgets ORDER BY category")
    fun getAllBudgets(): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM CategoryBudgets ORDER BY category")
    suspend fun getAllBudgetsSync(): List<CategoryBudget>

    @Query("SELECT * FROM CategoryBudgets WHERE category = :category")
    suspend fun getBudget(category: String): CategoryBudget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: CategoryBudget)

    @Query("DELETE FROM CategoryBudgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("DELETE FROM CategoryBudgets")
    suspend fun deleteAll()
}
