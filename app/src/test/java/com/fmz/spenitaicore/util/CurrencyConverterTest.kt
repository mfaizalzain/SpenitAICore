package com.fmz.spenitaicore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConverterTest {

    // 1 USD = 4.70 MYR, 1 USD = 1.35 SGD
    private val rates = mapOf("USD" to 1.0, "MYR" to 4.70, "SGD" to 1.35)

    @Test
    fun `converts through the base currency`() {
        // 100 SGD → MYR: (100 / 1.35) * 4.70
        val result = CurrencyConverter.convert(100.0, "SGD", "MYR", rates)
        assertEquals(348.148, result!!, 0.01)
    }

    @Test
    fun `same currency returns the amount unchanged`() {
        assertEquals(42.0, CurrencyConverter.convert(42.0, "USD", "USD", rates)!!, 0.001)
    }

    @Test
    fun `dollar shorthand maps to USD`() {
        // "$" amount of 10 → MYR
        val result = CurrencyConverter.convert(10.0, "$", "MYR", rates)
        assertEquals(47.0, result!!, 0.001)
    }

    @Test
    fun `missing rates return null`() {
        assertNull(CurrencyConverter.convert(10.0, "USD", "EUR", rates))
        assertNull(CurrencyConverter.convert(10.0, "EUR", "USD", rates))
    }

    @Test
    fun `empty rates and invalid amounts return null`() {
        assertNull(CurrencyConverter.convert(10.0, "USD", "MYR", emptyMap()))
        assertNull(CurrencyConverter.convert(Double.NaN, "USD", "MYR", rates))
        assertNull(CurrencyConverter.convert(Double.POSITIVE_INFINITY, "USD", "MYR", rates))
    }

    @Test
    fun `normalizeCode uppercases and maps dollar`() {
        assertEquals("USD", CurrencyConverter.normalizeCode("$"))
        assertEquals("MYR", CurrencyConverter.normalizeCode("myr"))
        assertEquals("EUR", CurrencyConverter.normalizeCode("EUR"))
    }
}
