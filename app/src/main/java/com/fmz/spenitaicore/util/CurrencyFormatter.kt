package com.fmz.spenitaicore.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: Double, currencyCode: String = "$"): String {
        val sym = currencySymbol(currencyCode)
        return "$sym ${"%,.2f".format(Locale.US, amount)}"
    }

    fun formatInt(amount: Double, currencyCode: String = "$"): String {
        val sym = currencySymbol(currencyCode)
        return "$sym ${"%,.0f".format(Locale.US, amount)}"
    }

    fun formatCompact(amount: Double, currencyCode: String = "$"): String {
        val sym = currencySymbol(currencyCode)
        return when {
            amount >= 1_000_000 -> "$sym ${trimTrailingZero("%,.1f".format(Locale.US, amount / 1_000_000))}M"
            amount >= 1_000 -> "$sym ${trimTrailingZero("%,.1f".format(Locale.US, amount / 1_000))}K"
            else -> "$sym ${"%,.2f".format(Locale.US, amount)}"
        }
    }

    fun formatIncome(amount: Double, currencyCode: String = "$"): String {
        val sym = currencySymbol(currencyCode)
        return "+$sym ${"%,.2f".format(Locale.US, amount)}"
    }

    fun formatNet(amount: Double, currencyCode: String = "$"): String {
        val sym = currencySymbol(currencyCode)
        return if (amount >= 0) "+$sym ${"%,.2f".format(Locale.US, amount)}"
        else "-$sym ${"%,.2f".format(Locale.US, kotlin.math.abs(amount))}"
    }

    private fun currencySymbol(code: String): String {
        // Curated symbol map so output is stable regardless of the device
        // locale (Currency.getSymbol() varies by locale — e.g. MYR renders
        // as "MYR" instead of "RM" under an en-US locale).
        return when (code.uppercase()) {
            "$", "USD" -> "$"
            "MYR" -> "RM"
            "SGD" -> "S$"
            "IDR" -> "Rp"
            "THB" -> "\u0E3F"
            "PHP" -> "\u20B1"
            "VND" -> "\u20AB"
            "EUR" -> "\u20AC"
            "GBP" -> "\u00A3"
            "JPY", "CNY" -> "\u00A5"
            "INR" -> "\u20B9"
            else -> code
        }
    }

    private fun trimTrailingZero(value: String): String =
        value.removeSuffix(".0")
}
