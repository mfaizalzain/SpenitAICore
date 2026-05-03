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
            amount >= 1_000_000 -> "$sym ${"%,.1f".format(Locale.US, amount / 1_000_000)}M"
            amount >= 1_000 -> "$sym ${"%,.0f".format(Locale.US, amount / 1_000)}K"
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
        if (code == "$") return "$"
        return try {
            Currency.getInstance(code).symbol
        } catch (e: Exception) {
            code
        }
    }
}
