package com.fmz.spenitaicore.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun `format uses symbol and two decimals`() {
        assertEquals("$ 1,234.50", CurrencyFormatter.format(1234.5))
        assertEquals("RM 1,234.50", CurrencyFormatter.format(1234.5, "MYR"))
        assertEquals("$ 0.00", CurrencyFormatter.format(0.0))
    }

    @Test
    fun `formatInt drops decimals`() {
        assertEquals("$ 1,235", CurrencyFormatter.formatInt(1234.56))
        assertEquals("RM 0", CurrencyFormatter.formatInt(0.0, "MYR"))
    }

    @Test
    fun `formatCompact scales large values`() {
        assertEquals("$ 2.5M", CurrencyFormatter.formatCompact(2_500_000.0))
        assertEquals("$ 1M", CurrencyFormatter.formatCompact(1_000_000.0))
        assertEquals("$ 2.5K", CurrencyFormatter.formatCompact(2_500.0))
        assertEquals("$ 999K", CurrencyFormatter.formatCompact(999_000.0))
    }

    @Test
    fun `formatCompact leaves small values untouched`() {
        assertEquals("$ 123.45", CurrencyFormatter.formatCompact(123.45))
        assertEquals("$ 0.00", CurrencyFormatter.formatCompact(0.0))
    }

    @Test
    fun `formatIncome and formatNet prefix signs`() {
        assertEquals("+$ 100.00", CurrencyFormatter.formatIncome(100.0))
        assertEquals("+$ 100.00", CurrencyFormatter.formatNet(100.0))
        assertEquals("-$ 100.00", CurrencyFormatter.formatNet(-100.0))
    }

    @Test
    fun `unknown currency code falls back to the raw code`() {
        assertEquals("XYZ 10.00", CurrencyFormatter.format(10.0, "XYZ"))
    }
}
