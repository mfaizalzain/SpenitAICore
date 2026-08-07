package com.fmz.spenitaicore.util

import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyAggregatorTest {

    // 1 USD = 4.70 MYR
    private val rates = mapOf("USD" to 1.0, "MYR" to 4.70)

    @Test
    fun `receipt totals are converted to the default currency`() {
        val receipts = listOf(
            Receipt(merchant = "A", total = 10.0, currency = "USD"),
            Receipt(merchant = "B", total = 47.0, currency = "MYR")
        )
        // 10 USD → 47 MYR + 47 MYR = 94 MYR
        assertEquals(94.0, MoneyAggregator.sumReceipts(receipts, "MYR", rates), 0.001)
    }

    @Test
    fun `income totals are converted to the default currency`() {
        val entries = listOf(
            IncomeEntry(source = "A", amount = 100.0, currency = "USD"),
            IncomeEntry(source = "B", amount = 100.0, currency = "MYR")
        )
        assertEquals(570.0, MoneyAggregator.sumIncomes(entries, "MYR", rates), 0.001)
    }

    @Test
    fun `missing rates fall back to the raw amount`() {
        val receipts = listOf(
            Receipt(merchant = "A", total = 10.0, currency = "USD"),
            Receipt(merchant = "B", total = 5.0, currency = "XXX")
        )
        assertEquals(52.0, MoneyAggregator.sumReceipts(receipts, "MYR", rates), 0.001)
    }

    @Test
    fun `same-currency sums are unchanged`() {
        val receipts = listOf(
            Receipt(merchant = "A", total = 10.0, currency = "MYR"),
            Receipt(merchant = "B", total = 5.0, currency = "MYR")
        )
        assertEquals(15.0, MoneyAggregator.sumReceipts(receipts, "MYR", emptyMap()), 0.001)
    }

    @Test
    fun `category grouping converts each category`() {
        val receipts = listOf(
            Receipt(merchant = "A", total = 10.0, currency = "USD", category = "Food & Drinks"),
            Receipt(merchant = "B", total = 10.0, currency = "MYR", category = "Food & Drinks"),
            Receipt(merchant = "C", total = 5.0, currency = "MYR", category = "Transport")
        )
        val byCategory = MoneyAggregator.sumReceiptsByCategory(receipts, "MYR", rates)
        assertEquals(57.0, byCategory["Food & Drinks"]!!, 0.001)
        assertEquals(5.0, byCategory["Transport"]!!, 0.001)
    }
}
