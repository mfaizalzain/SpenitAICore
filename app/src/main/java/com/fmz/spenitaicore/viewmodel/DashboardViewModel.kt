package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.ai.AiInsightResult
import com.fmz.spenitaicore.ai.SpendingInsights
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.SalaryCycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DashboardViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo = container.receiptRepository
    private val incomeRepo = container.incomeRepository
    private val preferences = container.preferences
    private val aiCore = container.aiCoreService

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting

    private val _totalToday = MutableStateFlow(0.0)
    val totalToday: StateFlow<Double> = _totalToday

    private val _totalThisWeek = MutableStateFlow(0.0)
    val totalThisWeek: StateFlow<Double> = _totalThisWeek

    private val _totalThisMonth = MutableStateFlow(0.0)
    val totalThisMonth: StateFlow<Double> = _totalThisMonth

    private val _totalLastMonth = MutableStateFlow(0.0)
    val totalLastMonth: StateFlow<Double> = _totalLastMonth

    private val _taxDeductibleTotal = MutableStateFlow(0.0)
    val taxDeductibleTotal: StateFlow<Double> = _taxDeductibleTotal

    private val _averageDailySpend = MutableStateFlow(0.0)
    val averageDailySpend: StateFlow<Double> = _averageDailySpend

    private val _totalIncomeThisMonth = MutableStateFlow(0.0)
    val totalIncomeThisMonth: StateFlow<Double> = _totalIncomeThisMonth

    private val _totalTodayText = MutableStateFlow("$ 0.00")
    val totalTodayText: StateFlow<String> = _totalTodayText

    private val _totalThisWeekText = MutableStateFlow("$ 0.00")
    val totalThisWeekText: StateFlow<String> = _totalThisWeekText

    private val _totalThisMonthText = MutableStateFlow("$ 0.00")
    val totalThisMonthText: StateFlow<String> = _totalThisMonthText

    private val _taxDeductibleTotalText = MutableStateFlow("$ 0")
    val taxDeductibleTotalText: StateFlow<String> = _taxDeductibleTotalText

    private val _averageDailySpendText = MutableStateFlow("$ 0.00")
    val averageDailySpendText: StateFlow<String> = _averageDailySpendText

    private val _totalIncomeThisMonthText = MutableStateFlow("$0.00")
    val totalIncomeThisMonthText: StateFlow<String> = _totalIncomeThisMonthText

    private val _totalLastMonthText = MutableStateFlow("$ 0.00")
    val totalLastMonthText: StateFlow<String> = _totalLastMonthText

    private val _monthOverMonthText = MutableStateFlow("")
    val monthOverMonthText: StateFlow<String> = _monthOverMonthText

    private val _safeToSpendText = MutableStateFlow("$ 0.00")
    val safeToSpendText: StateFlow<String> = _safeToSpendText

    private val _financialStatusText = MutableStateFlow("")
    val financialStatusText: StateFlow<String> = _financialStatusText

    private val _isSpendingUp = MutableStateFlow(false)
    val isSpendingUp: StateFlow<Boolean> = _isSpendingUp

    private val _dashboardStoryText = MutableStateFlow("")
    val dashboardStoryText: StateFlow<String> = _dashboardStoryText

    private val _latestInsightSummary = MutableStateFlow("")
    val latestInsightSummary: StateFlow<String> = _latestInsightSummary

    private val _latestInsightFinding = MutableStateFlow("")
    val latestInsightFinding: StateFlow<String> = _latestInsightFinding

    private val _recentReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val recentReceipts: StateFlow<List<Receipt>> = _recentReceipts

    private val _selectedReceipt = MutableStateFlow<Receipt?>(null)
    val selectedReceipt: StateFlow<Receipt?> = _selectedReceipt

    private val _selectedItems = MutableStateFlow<List<ReceiptItem>>(emptyList())
    val selectedItems: StateFlow<List<ReceiptItem>> = _selectedItems

    private val _selectedTagsDisplay = MutableStateFlow("")
    val selectedTagsDisplay: StateFlow<String> = _selectedTagsDisplay

    private val _isDetailVisible = MutableStateFlow(false)
    val isDetailVisible: StateFlow<Boolean> = _isDetailVisible

    private val _isEditVisible = MutableStateFlow(false)
    val isEditVisible: StateFlow<Boolean> = _isEditVisible

    private val _editingReceipt = MutableStateFlow<Receipt?>(null)
    val editingReceipt: StateFlow<Receipt?> = _editingReceipt

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        observeGreeting()
        loadData()
    }

    private fun observeGreeting() {
        viewModelScope.launch {
            preferences.userName.collect { name ->
                _greeting.value = formatGreeting(name)
            }
        }
    }

    private fun formatGreeting(name: String): String {
        val hour = java.time.LocalTime.now().hour
        val timeGreeting = when (hour) {
            in 5..11 -> "Morning"
            in 12..17 -> "Afternoon"
            else -> "Evening"
        }
        val firstName = name
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()

        return if (firstName.isBlank()) {
            timeGreeting
        } else {
            "$timeGreeting, $firstName"
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetchData()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun quietLoad() {
        viewModelScope.launch {
            try {
                fetchData()
            } catch (_: Exception) { }
        }
    }

    private suspend fun fetchData() {
        val currency = preferences.getDefaultCurrency()
        val payDay = preferences.getSalaryPayDay()
        val now = LocalDate.now()
        val cycle = SalaryCycle.getCurrentPeriod(payDay, now)

        val cutoff = now.minusMonths(6)
        val cutoffStr = DateUtils.fromLocalDate(cutoff)
        val allReceipts = receiptRepo.getReceiptsFromSync(cutoffStr)

        val thisCycleCost = allReceipts.filter { r ->
            val d = DateUtils.toLocalDate(r.date)
            SalaryCycle.isInPeriod(d, cycle)
        }.sumOf { it.total }
        val lastCycleCost = allReceipts.filter { r ->
            val d = DateUtils.toLocalDate(r.date)
            !d.isBefore(cycle.previousStart) && !d.isAfter(cycle.previousEnd)
        }.sumOf { it.total }

        val todayDate = DateUtils.fromLocalDate(now)
        val todayTotal = allReceipts.filter { it.date == todayDate }.sumOf { it.total }
        val weekStart = DateUtils.startOfWeek()
        val weekTotal = allReceipts.filter { it.date >= weekStart && it.date <= todayDate }.sumOf { it.total }

        val thisYear = now.year.toString()
        val taxTotal = allReceipts.filter { it.isTaxDeductible && (it.taxYear == thisYear || it.date.startsWith(thisYear)) }.sumOf { it.total }

        val cycleDaysElapsed = maxOf(1, ChronoUnit.DAYS.between(cycle.start, now).toInt() + 1)
        val avgDaily = thisCycleCost / cycleDaysElapsed

        val allIncome = incomeRepo.getIncomeEntriesFromSync(cutoffStr)
        val thisCycleIncome = allIncome.filter { e ->
            val d = DateUtils.toLocalDate(e.date)
            SalaryCycle.isInPeriod(d, cycle)
        }.sumOf { it.amount }

        _totalToday.value = todayTotal
        _totalThisWeek.value = weekTotal
        _totalThisMonth.value = thisCycleCost
        _totalLastMonth.value = lastCycleCost
        _taxDeductibleTotal.value = taxTotal
        _averageDailySpend.value = avgDaily
        _totalIncomeThisMonth.value = thisCycleIncome

        _totalTodayText.value = CurrencyFormatter.format(todayTotal, currency)
        _totalThisWeekText.value = CurrencyFormatter.format(weekTotal, currency)
        _totalThisMonthText.value = CurrencyFormatter.format(thisCycleCost, currency)
        _totalLastMonthText.value = CurrencyFormatter.format(lastCycleCost, currency)
        _taxDeductibleTotalText.value = CurrencyFormatter.formatInt(taxTotal, currency)
        _averageDailySpendText.value = CurrencyFormatter.format(avgDaily, currency)
        _totalIncomeThisMonthText.value = CurrencyFormatter.format(thisCycleIncome, currency)

        _isSpendingUp.value = lastCycleCost > 0 && thisCycleCost >= lastCycleCost
        _monthOverMonthText.value = if (lastCycleCost == 0.0) {
            "New cycle"
        } else {
            val arrow = if (thisCycleCost >= lastCycleCost) "\u2191" else "\u2193"
            val pct = kotlin.math.abs((thisCycleCost - lastCycleCost) / lastCycleCost * 100)
            "$arrow ${"%.0f".format(pct)}%"
        }

        val safeToSpend = thisCycleIncome - thisCycleCost
        _safeToSpendText.value = CurrencyFormatter.format(safeToSpend, currency)
        updateFinancialStatus(safeToSpend, thisCycleIncome)
        _dashboardStoryText.value = if (thisCycleIncome > 0) {
            "Income ${CurrencyFormatter.format(thisCycleIncome, currency)} \u00B7 Daily avg ${CurrencyFormatter.format(avgDaily, currency)}"
        } else {
            "Daily avg ${CurrencyFormatter.format(avgDaily, currency)} \u00B7 Tax ${CurrencyFormatter.formatInt(taxTotal, currency)}"
        }

        _recentReceipts.value = allReceipts
            .sortedWith(compareByDescending<com.fmz.spenitaicore.data.db.entity.Receipt> { it.date }
                .thenByDescending { it.createdAt })
            .take(5)

        // Generate a quick AI insight if possible
        try {
            val insightResult = aiCore.generateInsights(
                receipts = allReceipts.filter {
                    val d = DateUtils.toLocalDate(it.date)
                    SalaryCycle.isInPeriod(d, cycle)
                },
                incomeEntries = allIncome.filter { e ->
                    val d = DateUtils.toLocalDate(e.date)
                    SalaryCycle.isInPeriod(d, cycle)
                },
                periodLabel = "this cycle",
                currency = currency,
                periodStart = DateUtils.fromLocalDate(cycle.start),
                periodEnd = DateUtils.fromLocalDate(cycle.end)
            )
            _latestInsightSummary.value = insightResult.summary ?: ""
            _latestInsightFinding.value = insightResult.keyFindings.firstOrNull() ?: ""
        } catch (_: Exception) { }
    }

    private fun updateFinancialStatus(safeToSpend: Double, totalIncome: Double) {
        when {
            safeToSpend < 0 -> {
                _financialStatusText.value = "Overspending"
            }
            totalIncome > 0 && safeToSpend <= totalIncome * 0.2 -> {
                _financialStatusText.value = "Watch spending"
            }
            else -> {
                _financialStatusText.value = "On track"
            }
        }
    }

    fun viewReceipt(receipt: Receipt) {
        viewModelScope.launch {
            _selectedReceipt.value = receipt
            _selectedItems.value = receiptRepo.getReceiptItems(receipt.id)
            val tags = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(receipt.tagsJson)
            } catch (e: Exception) { emptyList() }
            _selectedTagsDisplay.value = tags.joinToString(" \u00B7 ")
            _isDetailVisible.value = true
        }
    }

    fun dismissDetail() {
        _isDetailVisible.value = false
        _selectedReceipt.value = null
    }

    fun startEdit(receipt: Receipt) {
        _editingReceipt.value = receipt
        _isEditVisible.value = true
        _isDetailVisible.value = false
    }

    fun dismissEdit() {
        _isEditVisible.value = false
        _editingReceipt.value = null
    }

    fun saveEdit(
        merchant: String,
        amountText: String,
        category: String,
        notes: String,
        date: String,
        isTaxDeductible: Boolean = false,
        taxCategory: String = ""
    ) {
        viewModelScope.launch {
            val receipt = _editingReceipt.value ?: return@launch
            val amount = amountText.toDoubleOrNull()
            if (merchant.isBlank() || amount == null || amount <= 0) return@launch
            val updated = receipt.copy(
                merchant = merchant.trim(),
                total = amount,
                category = category.ifBlank { "General" },
                notes = notes.trim().ifBlank { null },
                date = date,
                isTaxDeductible = isTaxDeductible,
                taxYear = if (isTaxDeductible) date.substring(0, 4) else null,
                taxCategory = if (isTaxDeductible) taxCategory.ifBlank { null } else null
            )
            receiptRepo.saveReceipt(updated)
            _isEditVisible.value = false
            _editingReceipt.value = null
            loadData()
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            _isBusy.value = true
            receiptRepo.deleteReceipt(receipt)
            fetchData()
            _isBusy.value = false
        }
    }

    fun convertToIncome(receipt: Receipt) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                receiptRepo.deleteReceipt(receipt)
                val entry = IncomeEntry(
                    source = receipt.merchant,
                    amount = receipt.total,
                    currency = receipt.currency,
                    date = receipt.date,
                    notes = receipt.notes,
                    category = "Other Income"
                )
                incomeRepo.saveIncomeEntry(entry)
                dismissDetail()
                loadData()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setIsRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }
}
