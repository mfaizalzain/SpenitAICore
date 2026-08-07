package com.fmz.spenitaicore.util

/**
 * Converts amounts between currencies using an exchange-rate map.
 *
 * Rates are expressed as "1 unit of the base currency = N units of the
 * target currency" (e.g. rates["MYR"] = 4.70 means 1 USD = 4.70 MYR when
 * the base is USD). The `$` shorthand used by the app maps to USD.
 */
object CurrencyConverter {

    /** Normalizes the app's currency codes to ISO codes for rate lookups. */
    fun normalizeCode(code: String): String = when (code.uppercase()) {
        "$" -> "USD"
        else -> code.uppercase()
    }

    /**
     * Converts [amount] from [fromCurrency] to [toCurrency].
     * Returns null when a required rate is missing or the amount is invalid.
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double? {
        if (amount.isNaN() || amount.isInfinite()) return null
        val from = normalizeCode(fromCurrency)
        val to = normalizeCode(toCurrency)
        if (from == to) return amount
        if (rates.isEmpty()) return null

        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate <= 0.0 || toRate <= 0.0) return null

        // Through the base currency: amount → base → target.
        return amount / fromRate * toRate
    }
}
