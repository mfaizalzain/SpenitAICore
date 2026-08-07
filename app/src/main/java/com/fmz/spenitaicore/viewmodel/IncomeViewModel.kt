package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.SalaryCycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class IncomeViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val incomeRepo = container.incomeRepository
    private val receiptRepo = container.receiptRepository
    private val preferences = container.preferences

    private var allEntries = emptyList<IncomeEntry>()

    private val _incomeEntries = MutableStateFlow<List<IncomeEntry>>(emptyList())
    val incomeEntries: StateFlow<List<IncomeEntry>> = _incomeEntries

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _totalText = MutableStateFlow("$0.00")
    val totalText: StateFlow<String> = _totalText

    private val _totalThisMonth = MutableStateFlow(0.0)
    val totalThisMonth: StateFlow<Double> = _totalThisMonth

    private val _netThisMonth = MutableStateFlow(0.0)
    val netThisMonth: StateFlow<Double> = _netThisMonth

    private val _totalThisMonthText = MutableStateFlow("$0.00")
    val totalThisMonthText: StateFlow<String> = _totalThisMonthText

    private val _netText = MutableStateFlow("$0.00")
    val netText: StateFlow<String> = _netText

    private val _selectedPeriod = MutableStateFlow("Last30")
    val selectedPeriod: StateFlow<String> = _selectedPeriod

    private val _editingEntry = MutableStateFlow<IncomeEntry?>(null)
    val editingEntry: StateFlow<IncomeEntry?> = _editingEntry

    private val _selectedEntry = MutableStateFlow<IncomeEntry?>(null)
    val selectedEntry: StateFlow<IncomeEntry?> = _selectedEntry

    private val _isEditVisible = MutableStateFlow(false)
    val isEditVisible: StateFlow<Boolean> = _isEditVisible

    private val _isDetailVisible = MutableStateFlow(false)
    val isDetailVisible: StateFlow<Boolean> = _isDetailVisible

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var currency = "$"

    companion object {
        val INCOME_CATEGORIES = IncomeSources.All

        private val MONTH_ABBR = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "june" to 6, "july" to 7, "august" to 8, "september" to 9,
            "october" to 10, "november" to 11, "december" to 12
        )
    }

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetchAsync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun quietLoad() {
        viewModelScope.launch {
            try { fetchAsync() } catch (_: Exception) { }
        }
    }

    private suspend fun fetchAsync() {
        currency = preferences.getDefaultCurrency()
        val from = when (_selectedPeriod.value) {
            "Last90" -> DateUtils.daysAgo(90)
            "ThisYear" -> DateUtils.startOfYear(DateUtils.todayLocalDate().year)
            "All" -> null
            else -> DateUtils.daysAgo(30)
        }
        allEntries = incomeRepo.getIncomeEntriesFromSync(from)
        applyFilters()

        val now = LocalDate.now()
        val payDay = preferences.getSalaryPayDay()
        val cycle = SalaryCycle.getCurrentPeriod(payDay, now)

        val thisCycleIncome = allEntries.filter { e ->
            val d = DateUtils.toLocalDate(e.date)
            SalaryCycle.isInPeriod(d, cycle)
        }.sumOf { it.amount }

        val cycleSpend = receiptRepo.getTotalSpend(DateUtils.fromLocalDate(cycle.start), DateUtils.fromLocalDate(cycle.end))
        val net = thisCycleIncome - cycleSpend

        _totalThisMonth.value = thisCycleIncome
        _netThisMonth.value = net
        _totalThisMonthText.value = CurrencyFormatter.format(thisCycleIncome, currency)
        _netText.value = CurrencyFormatter.formatNet(net, currency)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun applySearchFilters() {
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) {
            applyFilters()
            return
        }
        if (tryApplySmartSearch(q)) {
            _searchQuery.value = ""
            return
        }
        applyFilters()
    }

    private fun tryApplySmartSearch(query: String): Boolean {
        // ">500" or "above:500"
        Regex("""^(?:>\s*|above[:\s]*)(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val min = it.groupValues[1].toDoubleOrNull() ?: return@let
            _searchQuery.value = ""
            _incomeEntries.value = allEntries.filter { e -> e.amount >= min }
            updateTotal()
            return true
        }

        // "<100"
        Regex("""^(?:<\s*|below[:\s]*)(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val max = it.groupValues[1].toDoubleOrNull() ?: return@let
            _searchQuery.value = ""
            _incomeEntries.value = allEntries.filter { e -> e.amount <= max }
            updateTotal()
            return true
        }

        // "jan-mar" or "jan to mar 2024"
        Regex("""^(\w{3,9})\s*(?:-|to)\s*(\w{3,9})(?:\s*(\d{4}))?$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val fromMonth = MONTH_ABBR[it.groupValues[1].lowercase()] ?: return@let
            val toMonth = MONTH_ABBR[it.groupValues[2].lowercase()] ?: return@let
            val year = it.groupValues[3].toIntOrNull() ?: java.time.LocalDate.now().year
            val from = java.time.LocalDate.of(year, fromMonth, 1)
            val to = java.time.LocalDate.of(year, toMonth, 1).plusMonths(1).minusDays(1)
            _searchQuery.value = ""
            _incomeEntries.value = allEntries.filter { e ->
                val d = DateUtils.toLocalDate(e.date)
                !d.isBefore(from) && !d.isAfter(to)
            }
            updateTotal()
            return true
        }

        // "#salary" or "category:salary"
        Regex("""^(?:#|category[:\\s]*)(.+)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val cat = it.groupValues[1].trim()
            val match = INCOME_CATEGORIES.find { c ->
                c.startsWith(cat, ignoreCase = true)
            }
            if (match != null) {
                _searchQuery.value = ""
                _incomeEntries.value = allEntries.filter { e -> e.category == match }
                updateTotal()
                return true
            }
        }

        return false
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
        applyFilters()
    }

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
        quietLoad()
    }

    private fun applyFilters() {
        var filtered = allEntries.asSequence()

        val q = _searchQuery.value.trim().lowercase()
        if (q.isNotEmpty()) {
            filtered = filtered.filter { e ->
                e.source.lowercase().contains(q) ||
                e.category.lowercase().contains(q) ||
                (e.notes?.lowercase()?.contains(q) ?: false)
            }
        }

        if (_selectedCategory.value != "All") {
            filtered = filtered.filter { it.category == _selectedCategory.value }
        }

        _incomeEntries.value = filtered.toList()
        updateTotal()
    }

    private fun updateTotal() {
        val total = _incomeEntries.value.sumOf { it.amount }
        _totalText.value = CurrencyFormatter.format(total, currency)
    }

    fun viewIncome(entry: IncomeEntry) {
        _selectedEntry.value = entry
        _isDetailVisible.value = true
    }

    fun dismissDetail() {
        _isDetailVisible.value = false
        _selectedEntry.value = null
    }

    fun startEdit(entry: IncomeEntry) {
        _isDetailVisible.value = false
        _editingEntry.value = entry
        _isEditVisible.value = true
    }

    fun dismissEdit() {
        _isEditVisible.value = false
        _editingEntry.value = null
    }

    fun saveEdit(source: String, category: String, amountText: String, date: String, notes: String) {
        viewModelScope.launch {
            val entry = _editingEntry.value ?: return@launch
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) return@launch

            val updated = entry.copy(
                source = source.ifBlank { "Income" },
                category = category.ifBlank { "Other Income" },
                amount = amount,
                date = date.ifBlank { DateUtils.today() },
                notes = notes.trim().ifBlank { null }
            )
            incomeRepo.saveIncomeEntry(updated)
            _isEditVisible.value = false
            _editingEntry.value = null
            loadData()
        }
    }

    fun deleteIncome(entry: IncomeEntry) {
        viewModelScope.launch {
            incomeRepo.deleteIncomeEntry(entry)
            loadData()
        }
    }

    fun convertToExpense(entry: IncomeEntry) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                // Delete the income entry
                incomeRepo.deleteIncomeEntry(entry)
                // Create a receipt (expense) from the same data
                val receipt = Receipt(
                    merchant = entry.source,
                    date = entry.date,
                    total = entry.amount,
                    currency = entry.currency,
                    category = "General",
                    notes = entry.notes
                )
                receiptRepo.saveReceipt(receipt)
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
