package com.fmz.spenitaicore.util

import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt

/**
 * Sums monetary values across records that may be stored in different
 * currencies, converting each amount into the user's default currency.
 *
 * When a rate is missing for a currency, the raw amount is used as a
 * fallback so that no data is ever dropped from the totals.
 */
object MoneyAggregator {

    fun convert(
        amount: Double,
        fromCurrency: String,
        defaultCurrency: String,
        rates: Map<String, Double>
    ): Double =
        CurrencyConverter.convert(amount, fromCurrency, defaultCurrency, rates) ?: amount

    fun sumReceipts(
        receipts: List<Receipt>,
        defaultCurrency: String,
        rates: Map<String, Double>
    ): Double =
        receipts.sumOf { convert(it.total, it.currency, defaultCurrency, rates) }

    fun sumIncomes(
        entries: List<IncomeEntry>,
        defaultCurrency: String,
        rates: Map<String, Double>
    ): Double =
        entries.sumOf { convert(it.amount, it.currency, defaultCurrency, rates) }

    fun sumTaxAmounts(
        receipts: List<Receipt>,
        defaultCurrency: String,
        rates: Map<String, Double>
    ): Double =
        receipts.sumOf { convert(it.taxAmount, it.currency, defaultCurrency, rates) }

    /** Total spend per category, with amounts converted to [defaultCurrency]. */
    fun sumReceiptsByCategory(
        receipts: List<Receipt>,
        defaultCurrency: String,
        rates: Map<String, Double>
    ): Map<String, Double> =
        receipts
            .groupBy { it.category }
            .mapValues { (_, items) ->
                items.sumOf { convert(it.total, it.currency, defaultCurrency, rates) }
            }
}
