package com.fmz.spenitaicore.util

import com.fmz.spenitaicore.data.db.entity.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringExpenseAnalyzerTest {

    private fun receipt(merchant: String, total: Double, category: String = "General") =
        Receipt(merchant = merchant, total = total, category = category, date = "2026-03-01")

    @Test
    fun `merchants appearing at least three times are flagged`() {
        val receipts = listOf(
            receipt("Netflix", 55.0, "Subscriptions"),
            receipt("Netflix", 55.0, "Subscriptions"),
            receipt("Netflix", 55.0, "Subscriptions"),
            receipt("Kedai Kopi", 8.0),
            receipt("Kedai Kopi", 9.0)
        )
        val result = RecurringExpenseAnalyzer.analyze(receipts)

        assertEquals(1, result.size)
        assertEquals("Netflix", result[0].merchant)
        assertEquals(3, result[0].occurrences)
        assertEquals(165.0, result[0].totalAmount, 0.001)
        assertEquals("Subscriptions", result[0].category)
    }

    @Test
    fun `matching is case-insensitive and trims whitespace`() {
        val receipts = listOf(
            receipt("Spotify", 14.9),
            receipt("  spotify ", 14.9),
            receipt("SPOTIFY", 14.9)
        )
        val result = RecurringExpenseAnalyzer.analyze(receipts)
        assertEquals(1, result.size)
        assertEquals("Spotify", result[0].merchant)
    }

    @Test
    fun `results are sorted by total amount descending`() {
        val receipts = listOf(
            receipt("Small", 1.0),
            receipt("Small", 1.0),
            receipt("Small", 1.0),
            receipt("Big", 50.0),
            receipt("Big", 50.0),
            receipt("Big", 50.0)
        )
        val result = RecurringExpenseAnalyzer.analyze(receipts)
        assertEquals("Big", result[0].merchant)
        assertEquals("Small", result[1].merchant)
    }

    @Test
    fun `rare merchants and blank names are excluded`() {
        val receipts = listOf(
            receipt("Once Only", 10.0),
            receipt("", 10.0)
        )
        assertTrue(RecurringExpenseAnalyzer.analyze(receipts).isEmpty())
    }
}
