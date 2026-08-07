package com.fmz.spenitaicore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SalaryCycleTest {

    @Test
    fun `pay day date falls on the requested day of the current month`() {
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(LocalDate.of(2026, 3, 1), SalaryCycle.getPayDayDate(1, today))
        assertEquals(LocalDate.of(2026, 3, 15), SalaryCycle.getPayDayDate(15, today))
    }

    @Test
    fun `pay day is coerced to the last day of a short month`() {
        val today = LocalDate.of(2026, 2, 10)
        assertEquals(LocalDate.of(2026, 2, 28), SalaryCycle.getPayDayDate(31, today))
    }

    @Test
    fun `current period starts on the most recent pay day`() {
        // Today is before the 15th → the current cycle started last month on the 15th.
        val today = LocalDate.of(2026, 3, 10)
        val period = SalaryCycle.getCurrentPeriod(15, today)

        assertEquals(LocalDate.of(2026, 2, 15), period.start)
        assertEquals(LocalDate.of(2026, 3, 14), period.end)
        assertEquals(LocalDate.of(2026, 1, 15), period.previousStart)
        assertEquals(LocalDate.of(2026, 2, 14), period.previousEnd)
    }

    @Test
    fun `current period starts today when today is the pay day`() {
        val today = LocalDate.of(2026, 3, 15)
        val period = SalaryCycle.getCurrentPeriod(15, today)

        assertEquals(LocalDate.of(2026, 3, 15), period.start)
        assertEquals(LocalDate.of(2026, 4, 14), period.end)
    }

    @Test
    fun `isInPeriod includes boundaries`() {
        val today = LocalDate.of(2026, 3, 20)
        val period = SalaryCycle.getCurrentPeriod(15, today)

        assertTrue(SalaryCycle.isInPeriod(period.start, period))
        assertTrue(SalaryCycle.isInPeriod(period.end, period))
        assertFalse(SalaryCycle.isInPeriod(period.end.plusDays(1), period))
        assertFalse(SalaryCycle.isInPeriod(period.start.minusDays(1), period))
    }
}
