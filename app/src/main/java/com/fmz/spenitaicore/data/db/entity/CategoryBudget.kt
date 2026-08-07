package com.fmz.spenitaicore.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A monthly spending limit for a single expense category.
 * The category name is the primary key so each category has at most one budget.
 */
@Entity(tableName = "CategoryBudgets")
data class CategoryBudget(
    @PrimaryKey @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "monthly_limit") val monthlyLimit: Double,
    @ColumnInfo(name = "currency") val currency: String = "$",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
