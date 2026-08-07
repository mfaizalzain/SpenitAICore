package com.fmz.spenitaicore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class DateUtilsTest {

    @Test
    fun `format renders a readable date and falls back on bad input`() {
        assertEquals("Mar 05, 2026", DateUtils.format("2026-03-05"))
        assertEquals("not-a-date", DateUtils.format("not-a-date"))
    }

    @Test
    fun `toLocalDate and fromLocalDate round trip`() {
        val date = LocalDate.of(2026, 12, 31)
        assertEquals(date, DateUtils.toLocalDate(DateUtils.fromLocalDate(date)))
    }

    @Test
    fun `daysAgo returns yesterday`() {
        val expected = LocalDate.now().minusDays(1).toString()
        assertEquals(expected, DateUtils.daysAgo(1))
    }

    @Test
    fun `startOfWeek is the first day of the locale week`() {
        val start = LocalDate.parse(DateUtils.startOfWeek())
        val expectedFirstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        assertTrue(start <= LocalDate.now())
        assertEquals(expectedFirstDay, start.dayOfWeek)
        assertTrue(LocalDate.now().minusDays(7) < start)
    }

    @Test
    fun `startOfYear is January first`() {
        assertEquals("2026-01-01", DateUtils.startOfYear(2026))
    }

    @Test
    fun `greeting keys follow the expected hour bands`() {
        assertEquals("GreetingMorning", DateUtils.getGreetingKey(5))
        assertEquals("GreetingMorning", DateUtils.getGreetingKey(11))
        assertEquals("GreetingAfternoon", DateUtils.getGreetingKey(12))
        assertEquals("GreetingAfternoon", DateUtils.getGreetingKey(17))
        assertEquals("GreetingEvening", DateUtils.getGreetingKey(18))
        assertEquals("GreetingEvening", DateUtils.getGreetingKey(4))
    }
}
