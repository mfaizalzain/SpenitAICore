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
import com.fmz.spenitaicore.util.SalaryCycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class InsightsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo = container.receiptRepository
    private val incomeRepo = container.incomeRepository
    private val preferences = container.preferences
    private val aiCore = container.aiCoreService

    private val _selectedRange = MutableStateFlow("Last30")
    val selectedRange: StateFlow<String> = _selectedRange

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
        quietLoad()
    }

    private suspend fun applyInsights(forceAiRefresh: Boolean) {
        val currency = preferences.getDefaultCurrency()
        val payDay = preferences.getSalaryPayDay()
        val now = LocalDate.now()
        val cycle = SalaryCycle.getCurrentPeriod(payDay, now)

        val from = when (_selectedRange.value) {
            "QuarterToDate" -> DateUtils.daysAgo(90)
            "YearToDate" -> DateUtils.startOfYear(now.year)
            else -> DateUtils.daysAgo(30)
        }

        val receipts = receiptRepo.getReceiptsFromSync(from)
        val thisCycleCost = receipts.filter { r ->
            val d = DateUtils.toLocalDate(r.date)
            SalaryCycle.isInPeriod(d, cycle)
        }.sumOf { it.total }

        val lastReceipts = receiptRepo.getReceiptsFromSync(DateUtils.fromLocalDate(cycle.previousStart.minusMonths(1)))
        val lastCycleCost = lastReceipts.filter { r ->
            val d = DateUtils.toLocalDate(r.date)
            !d.isBefore(cycle.previousStart) && !d.isAfter(cycle.previousEnd)
        }.sumOf { it.total }

        val avgDaily = if (receipts.isNotEmpty()) thisCycleCost / 30.0 else 0.0
        val taxTotal = receipts.filter { it.isTaxDeductible }.sumOf { it.total }

        val incomeEntries = incomeRepo.getIncomeEntriesFromSync(from)

        _totalThisMonth.value = thisCycleCost
        _averageDailySpend.value = avgDaily
        _taxDeductibleTotal.value = taxTotal
        _periodSpendLabel.value = "Spending \u00B7 ${cycle.label}"
        _totalThisMonthText.value = CurrencyFormatter.formatInt(thisCycleCost, currency)
        _averageDailySpendText.value = CurrencyFormatter.format(avgDaily, currency)
        _taxDeductibleTotalText.value = CurrencyFormatter.formatInt(taxTotal, currency)

        val pct = if (lastCycleCost > 0) (thisCycleCost - lastCycleCost) / lastCycleCost * 100 else 0.0
        _isIncrease.value = pct >= 0
        val arrow = if (pct >= 0) "\u2191" else "\u2193"
        _monthOverMonthText.value = "$arrow ${"%.0f".format(kotlin.math.abs(pct))}%"

        val categories = receipts.groupBy { it.category }
            .map { (cat, items) ->
                CategoryBreakdown(
                    category = cat,
                    amount = items.sumOf { it.total },
                    percentage = if (thisCycleCost > 0) (items.sumOf { it.total } / thisCycleCost) * 100 else 0.0
                )
            }
            .sortedByDescending { it.amount }
            .take(5)

        _topCategories.value = categories

        if (forceAiRefresh || _aiSummary.value == null) {
            _aiStatusText.value = "Generating insights..."
            try {
                val result = aiCore.generateInsights(receipts, incomeEntries, cycle.label, currency)
                _aiSummary.value = result.summary
                _keyFindings.value = result.keyFindings
                _savingTips.value = result.savingTips
                _weeklyTrend.value = result.weeklyTrend
                _anomalyAlert.value = result.anomalyAlert
                _aiStatusText.value = "Updated just now"
            } catch (e: Exception) {
                _aiStatusText.value = "AI insight generation failed"
            }
        }
    }

    fun setIsRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }
}
