package com.fmz.spenit.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SalaryCyclePeriod(
    val start: LocalDate,
    val end: LocalDate,
    val previousStart: LocalDate,
    val previousEnd: LocalDate,
    val label: String
)

object SalaryCycle {
    const val STORAGE_KEY = "salary_pay_day"
    const val DEFAULT_PAY_DAY = 1

    fun getPayDayDate(payDay: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val maxDay = today.lengthOfMonth()
        val day = payDay.coerceIn(1, maxDay)
        return LocalDate.of(today.year, today.month, day)
    }

    fun getCurrentPeriod(payDay: Int, today: LocalDate = LocalDate.now()): SalaryCyclePeriod {
        val cycleStart = if (today.dayOfMonth >= payDay) {
            getPayDayDate(payDay, today)
        } else {
            getPayDayDate(payDay, today.minusMonths(1))
        }

        val cycleEnd = cycleStart.plusMonths(1).minusDays(1)
        val previousStart = cycleStart.minusMonths(1)
        val previousEnd = cycleStart.minusDays(1)

        val formatter = DateTimeFormatter.ofPattern("MMM dd")
        val label = "${cycleStart.format(formatter)} - ${cycleEnd.format(formatter)}"
        return SalaryCyclePeriod(cycleStart, cycleEnd, previousStart, previousEnd, label)
    }

    fun isInPeriod(date: LocalDate, period: SalaryCyclePeriod): Boolean =
        !date.isBefore(period.start) && !date.isAfter(period.end)
}
