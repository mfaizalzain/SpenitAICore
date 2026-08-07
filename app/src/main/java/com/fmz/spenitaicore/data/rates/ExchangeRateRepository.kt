package com.fmz.spenitaicore.data.rates

import android.content.Context
import com.fmz.spenitaicore.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches and caches exchange rates (USD base) from a free public API,
 * falling back to cached values and finally to embedded approximate rates
 * when offline. Exposes the latest known rates as a [StateFlow].
 */
class ExchangeRateRepository(
    context: Context,
    private val preferences: AppPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _rates = MutableStateFlow<Map<String, Double>>(FALLBACK_RATES)
    val rates: StateFlow<Map<String, Double>> = _rates

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated

    private var refreshJob: Job? = null

    /** Loads cached rates immediately, then refreshes from the network if stale. */
    fun ensureLoaded() {
        if (refreshJob?.isActive == true) return
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            val cachedJson = preferences.getCachedRatesJson()
            val cachedAt = preferences.getCachedRatesUpdatedAt()
            if (cachedJson != null) {
                parseRates(cachedJson)?.let { parsed ->
                    if (parsed.isNotEmpty()) {
                        _rates.value = parsed
                        _lastUpdated.value = cachedAt
                    }
                }
            }
            if (System.currentTimeMillis() - cachedAt > RATES_TTL_MS) {
                refresh()
            }
        }
    }

    suspend fun refresh() {
        try {
            val json = withContext(Dispatchers.IO) { fetchRates() } ?: return
            val parsed = parseRates(json) ?: return
            if (parsed.isEmpty()) return
            _rates.value = parsed
            _lastUpdated.value = System.currentTimeMillis()
            preferences.setCachedExchangeRates(json, _lastUpdated.value)
        } catch (_: Exception) {
            // Keep cached or fallback rates.
        }
    }

    private fun fetchRates(): String? {
        val request = Request.Builder()
            .url("https://open.er-api.com/v6/latest/USD")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRates(json: String): Map<String, Double>? {
        return try {
            val root = jsonParser.parseToJsonElement(json).jsonObject
            val ratesObj = root["rates"]?.jsonObject ?: return null
            ratesObj.mapNotNull { (code, value) ->
                value.jsonPrimitive.doubleOrNull?.let { code to it }
            }.toMap()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val RATES_TTL_MS = 24 * 60 * 60 * 1000L
        private val jsonParser = Json { ignoreUnknownKeys = true }

        /** Approximate USD-based rates used when the network is unavailable. */
        val FALLBACK_RATES = mapOf(
            "USD" to 1.0,
            "MYR" to 4.70,
            "SGD" to 1.35,
            "IDR" to 16_000.0,
            "THB" to 36.0,
            "PHP" to 56.0,
            "VND" to 25_000.0,
            "EUR" to 0.92,
            "GBP" to 0.78,
            "AUD" to 1.52,
            "JPY" to 150.0,
            "CNY" to 7.2,
            "INR" to 83.0
        )
    }
}
