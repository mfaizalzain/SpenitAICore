package com.fmz.spenitaicore.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object DateUtils {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val displayFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    fun today(): String = LocalDate.now().format(isoFormatter)

    fun todayLocalDate(): LocalDate = LocalDate.now()

    fun format(date: String): String {
        return try {
            LocalDate.parse(date, isoFormatter).format(displayFormatter)
        } catch (e: Exception) {
            date
        }
    }

    fun toLocalDate(date: String): LocalDate = LocalDate.parse(date, isoFormatter)

    fun fromLocalDate(date: LocalDate): String = date.format(isoFormatter)

    fun daysAgo(days: Long): String = LocalDate.now().minusDays(days).format(isoFormatter)

    fun startOfYear(year: Int): String = LocalDate.of(year, 1, 1).format(isoFormatter)

    fun startOfWeek(): String {
        val today = LocalDate.now()
        val locale = Locale.getDefault()
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val daysSinceWeekStart = (today.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return today.minusDays(daysSinceWeekStart.toLong()).format(isoFormatter)
    }

    fun daysAgoMillis(days: Long): Long =
        LocalDate.now().minusDays(days).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    fun startOfYearMillis(year: Int): Long =
        LocalDate.of(year, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    fun getGreetingKey(hour: Int = java.time.LocalTime.now().hour): String = when (hour) {
        in 5..11 -> "GreetingMorning"
        in 12..17 -> "GreetingAfternoon"
        else -> "GreetingEvening"
    }

    fun getGreeting(): String = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
