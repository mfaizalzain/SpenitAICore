package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.ai.CategoryBreakdown
import com.fmz.spenitaicore.ai.SavingTip
import com.fmz.spenitaicore.ai.SpendingInsights
import com.fmz.spenitaicore.ai.SpendingTrend
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.RecurringExpense
import com.fmz.spenitaicore.util.RecurringExpenseAnalyzer
import com.fmz.spenitaicore.util.MoneyAggregator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class InsightsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo = container.receiptRepository
    private val incomeRepo = container.incomeRepository
    private val preferences = container.preferences
    private val aiCore = container.aiCoreService
    private val exchangeRates = container.exchangeRateRepository

    private val _selectedRange = MutableStateFlow("Last30")
    val selectedRange: StateFlow<String> = _selectedRange

    private val _currency = MutableStateFlow("$")
    val currency: StateFlow<String> = _currency

    /** Per-period cache for AI-generated insights — avoids regenerating when switching ranges */
    private data class InsightsCache(
        val summary: String?,
        val keyFindings: List<String>,
        val savingTips: List<SavingTip>,
        val weeklyTrend: List<SpendingTrend>,
        val anomalyAlert: String?,
        val lastUpdateTime: Long
    )

    private val insightsCache = mutableMapOf<String, InsightsCache>()

    private val _totalThisMonth = MutableStateFlow(0.0)
    val totalThisMonth: StateFlow<Double> = _totalThisMonth

    private val _totalThisMonthText = MutableStateFlow("$ 0")
    val totalThisMonthText: StateFlow<String> = _totalThisMonthText

    private val _averageDailySpend = MutableStateFlow(0.0)
    val averageDailySpend: StateFlow<Double> = _averageDailySpend

    private val _averageDailySpendText = MutableStateFlow("$ 0.00")
    val averageDailySpendText: StateFlow<String> = _averageDailySpendText

    private val _taxDeductibleTotal = MutableStateFlow(0.0)
    val taxDeductibleTotal: StateFlow<Double> = _taxDeductibleTotal

    private val _taxDeductibleTotalText = MutableStateFlow("$ 0")
    val taxDeductibleTotalText: StateFlow<String> = _taxDeductibleTotalText

    private val _monthOverMonthText = MutableStateFlow("")
    val monthOverMonthText: StateFlow<String> = _monthOverMonthText

    private val _isIncrease = MutableStateFlow(false)
    val isIncrease: StateFlow<Boolean> = _isIncrease

    private val _periodSpendLabel = MutableStateFlow("")
    val periodSpendLabel: StateFlow<String> = _periodSpendLabel

    private val _topCategories = MutableStateFlow<List<CategoryBreakdown>>(emptyList())
    val topCategories: StateFlow<List<CategoryBreakdown>> = _topCategories

    private val _recurringExpenses = MutableStateFlow<List<RecurringExpense>>(emptyList())
    val recurringExpenses: StateFlow<List<RecurringExpense>> = _recurringExpenses

    private val _savingTips = MutableStateFlow<List<SavingTip>>(emptyList())
    val savingTips: StateFlow<List<SavingTip>> = _savingTips

    private val _weeklyTrend = MutableStateFlow<List<SpendingTrend>>(emptyList())
    val weeklyTrend: StateFlow<List<SpendingTrend>> = _weeklyTrend

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary

    private val _keyFindings = MutableStateFlow<List<String>>(emptyList())
    val keyFindings: StateFlow<List<String>> = _keyFindings

    private val _anomalyAlert = MutableStateFlow<String?>(null)
    val anomalyAlert: StateFlow<String?> = _anomalyAlert

    private val _aiStatusText = MutableStateFlow("")
    val aiStatusText: StateFlow<String> = _aiStatusText

    private val _lastInsightUpdateTime = MutableStateFlow(0L)
    val lastInsightUpdateTime: StateFlow<Long> = _lastInsightUpdateTime

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        loadInsights()
    }

    fun loadInsights() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                applyInsights(forceAiRefresh = false)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun quietLoad() {
        viewModelScope.launch {
            try { applyInsights(forceAiRefresh = false) } catch (_: Exception) { }
        }
    }

    fun refreshInsights() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _aiStatusText.value = "Refreshing insights..."
                applyInsights(forceAiRefresh = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectRange(range: String) {
        if (_selectedRange.value == range) return
        _selectedRange.value = range

        // Check per-period cache — if available, restore without re-running AI
        val cached = insightsCache[range]
        if (cached != null) {
            _aiSummary.value = cached.summary
            _keyFindings.value = cached.keyFindings
            _savingTips.value = cached.savingTips
            _weeklyTrend.value = cached.weeklyTrend
            _anomalyAlert.value = cached.anomalyAlert
            _lastInsightUpdateTime.value = cached.lastUpdateTime
        } else {
            _aiSummary.value = null
            _keyFindings.value = emptyList()
            _savingTips.value = emptyList()
            _weeklyTrend.value = emptyList()
            _anomalyAlert.value = null
        }
        quietLoad()
    }

    private suspend fun applyInsights(forceAiRefresh: Boolean) {
        val currency = preferences.getDefaultCurrency()
        _currency.value = currency
        val rates = exchangeRates.rates.value
        val now = LocalDate.now()

        val range = selectedRangePeriod(_selectedRange.value, now)
        val from = DateUtils.fromLocalDate(range.start)
        val to = DateUtils.fromLocalDate(range.end)

        val receipts = receiptRepo.getReceiptsFromSync(from)
            .filter { it.date <= to }
        val periodSpend = MoneyAggregator.sumReceipts(receipts, currency, rates)

        val previousReceipts = receiptRepo.getReceiptsFromSync(DateUtils.fromLocalDate(range.previousStart))
            .filter { it.date <= DateUtils.fromLocalDate(range.previousEnd) }
        val previousPeriodSpend = MoneyAggregator.sumReceipts(previousReceipts, currency, rates)

        val daysElapsed = maxOf(1, ChronoUnit.DAYS.between(range.start, range.end).toInt() + 1)
        val avgDaily = periodSpend / daysElapsed
        val taxTotal = MoneyAggregator.sumReceipts(receipts.filter { it.isTaxDeductible }, currency, rates)

        val incomeEntries = incomeRepo.getIncomeEntriesFromSync(from)
            .filter { it.date <= to }

        _totalThisMonth.value = periodSpend
        _averageDailySpend.value = avgDaily
        _taxDeductibleTotal.value = taxTotal
        _periodSpendLabel.value = "Spending \u00B7 ${range.label}"
        _totalThisMonthText.value = CurrencyFormatter.formatInt(periodSpend, currency)
        _averageDailySpendText.value = CurrencyFormatter.format(avgDaily, currency)
        _taxDeductibleTotalText.value = CurrencyFormatter.formatInt(taxTotal, currency)

        _monthOverMonthText.value = if (previousPeriodSpend > 0) {
            val pct = (periodSpend - previousPeriodSpend) / previousPeriodSpend * 100
            _isIncrease.value = pct >= 0
            val arrow = if (pct >= 0) "\u2191" else "\u2193"
            "$arrow ${"%.0f".format(kotlin.math.abs(pct))}%"
        } else {
            _isIncrease.value = false
            "New period"
        }

        val categories = receipts.groupBy { it.category }
            .map { (cat, items) ->
                CategoryBreakdown(
                    category = cat,
                    amount = MoneyAggregator.sumReceipts(items, currency, rates),
                    percentage = if (periodSpend > 0) {
                        (MoneyAggregator.sumReceipts(items, currency, rates) / periodSpend) * 100
                    } else {
                        0.0
                    }
                )
            }
            .sortedByDescending { it.amount }
            .take(5)

        _topCategories.value = categories
        _recurringExpenses.value = RecurringExpenseAnalyzer.analyze(receipts)

        if (forceAiRefresh || insightsCache[_selectedRange.value] == null) {
            _aiStatusText.value = "Generating insights..."
            try {
                val result = aiCore.generateInsights(
                    receipts = receipts,
                    incomeEntries = incomeEntries,
                    periodLabel = range.label,
                    currency = currency,
                    periodStart = from,
                    periodEnd = to,
                    rates = rates
                )
                _aiSummary.value = result.summary
                _keyFindings.value = result.keyFindings
                _savingTips.value = result.savingTips
                _weeklyTrend.value = result.weeklyTrend
                _anomalyAlert.value = result.anomalyAlert
                _lastInsightUpdateTime.value = System.currentTimeMillis()
                insightsCache[_selectedRange.value] = InsightsCache(
                    summary = result.summary,
                    keyFindings = result.keyFindings,
                    savingTips = result.savingTips,
                    weeklyTrend = result.weeklyTrend,
                    anomalyAlert = result.anomalyAlert,
                    lastUpdateTime = _lastInsightUpdateTime.value
                )
                _aiStatusText.value = "Updated just now"
            } catch (e: Exception) {
                _aiStatusText.value = "AI insight generation failed"
            }
        }
    }

    fun setIsRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }

    private fun selectedRangePeriod(
        selectedRange: String,
        now: LocalDate
    ): InsightRange {
        return when (selectedRange) {
            "QuarterToDate" -> {
                val quarterStartMonth = ((now.monthValue - 1) / 3) * 3 + 1
                val start = LocalDate.of(now.year, quarterStartMonth, 1)
                val previousStart = start.minusMonths(3)
                val previousQuarterEnd = start.minusDays(1)
                InsightRange(
                    start = start,
                    end = now,
                    previousStart = previousStart,
                    previousEnd = previousEndForElapsed(previousStart, previousQuarterEnd, start, now),
                    label = "QTD"
                )
            }
            "YearToDate" -> {
                val start = LocalDate.of(now.year, 1, 1)
                val previousStart = LocalDate.of(now.year - 1, 1, 1)
                val previousYearEnd = LocalDate.of(now.year - 1, 12, 31)
                InsightRange(
                    start = start,
                    end = now,
                    previousStart = previousStart,
                    previousEnd = previousEndForElapsed(previousStart, previousYearEnd, start, now),
                    label = "YTD"
                )
            }
            else -> {
                val start = now.minusDays(29)
                InsightRange(
                    start = start,
                    end = now,
                    previousStart = start.minusDays(30),
                    previousEnd = start.minusDays(1),
                    label = "Last 30 days"
                )
            }
        }
    }

    private fun previousEndForElapsed(
        previousStart: LocalDate,
        previousHardEnd: LocalDate,
        currentStart: LocalDate,
        currentEnd: LocalDate
    ): LocalDate {
        val elapsedDays = ChronoUnit.DAYS.between(currentStart, currentEnd)
        val candidate = previousStart.plusDays(elapsedDays)
        return if (candidate.isAfter(previousHardEnd)) previousHardEnd else candidate
    }

    private data class InsightRange(
        val start: LocalDate,
        val end: LocalDate,
        val previousStart: LocalDate,
        val previousEnd: LocalDate,
        val label: String
    )
}
