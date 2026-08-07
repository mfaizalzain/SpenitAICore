package com.fmz.spenitaicore.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.util.CurrencyConverter
import com.fmz.spenitaicore.util.CurrencyFormatter

/**
 * Renders a "≈ <amount> in your default currency" hint when [amount] is
 * stored in a currency that differs from the user's default currency.
 * Shows nothing when the currencies match or rates are unavailable.
 */
@Composable
fun ConvertedAmountLabel(
    amount: Double,
    fromCurrency: String,
    modifier: Modifier = Modifier
) {
    val container = remember { SpenItApp.instance.container }
    val repository = container.exchangeRateRepository
    val rates by repository.rates.collectAsState()
    val defaultCurrency by container.preferences.defaultCurrency.collectAsState(initial = "$")

    LaunchedEffect(Unit) {
        repository.ensureLoaded()
    }

    val from = CurrencyConverter.normalizeCode(fromCurrency)
    val to = CurrencyConverter.normalizeCode(defaultCurrency)
    if (from == to || amount <= 0.0) return

    val converted = CurrencyConverter.convert(amount, fromCurrency, defaultCurrency, rates)
    if (converted == null || converted <= 0.0) return

    Text(
        text = "\u2248 ${CurrencyFormatter.format(converted, defaultCurrency)} in your default currency",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
