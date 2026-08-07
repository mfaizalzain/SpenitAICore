package com.fmz.spenitaicore.util

import com.fmz.spenitaicore.data.db.entity.Receipt

data class RecurringExpense(
    val merchant: String,
    val occurrences: Int,
    val totalAmount: Double,
    val category: String
)

/**
 * Detects merchants that appear repeatedly in a set of receipts — a strong
 * signal of a recurring expense (subscriptions, weekly groceries, etc.).
 */
object RecurringExpenseAnalyzer {

    fun analyze(
        receipts: List<Receipt>,
        minOccurrences: Int = 3
    ): List<RecurringExpense> {
        return receipts
            .asSequence()
            .filter { it.merchant.isNotBlank() }
            .groupBy { it.merchant.trim().lowercase() }
            .mapNotNull { (_, items) ->
                if (items.size < minOccurrences) return@mapNotNull null
                val representative = items.first()
                RecurringExpense(
                    merchant = representative.merchant.trim(),
                    occurrences = items.size,
                    totalAmount = items.sumOf { it.total },
                    category = representative.category
                )
            }
            .sortedByDescending { it.totalAmount }
            .toList()
    }
}
