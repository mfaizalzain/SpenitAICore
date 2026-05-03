package com.fmz.spenit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenit.SpenItApp
import com.fmz.spenit.data.db.entity.Receipt
import com.fmz.spenit.data.db.entity.ReceiptItem
import com.fmz.spenit.util.CurrencyFormatter
import com.fmz.spenit.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExpensesViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo = container.receiptRepository
    private val preferences = container.preferences

    private var allReceipts = emptyList<Receipt>()

    private val _filteredReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val filteredReceipts: StateFlow<List<Receipt>> = _filteredReceipts

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _showTaxOnly = MutableStateFlow(false)
    val showTaxOnly: StateFlow<Boolean> = _showTaxOnly

    private val _selectedPeriod = MutableStateFlow("Last30")
    val selectedPeriod: StateFlow<String> = _selectedPeriod

    private val _selectedTaxYear = MutableStateFlow(DateUtils.todayLocalDate().year.toString())
    val selectedTaxYear: StateFlow<String> = _selectedTaxYear

    private val _availableTaxYears = MutableStateFlow(listOf(DateUtils.todayLocalDate().year.toString()))
    val availableTaxYears: StateFlow<List<String>> = _availableTaxYears

    private val _totalText = MutableStateFlow("$0.00")
    val totalText: StateFlow<String> = _totalText

    private val _editingReceipt = MutableStateFlow<Receipt?>(null)
    val editingReceipt: StateFlow<Receipt?> = _editingReceipt

    private val _isEditVisible = MutableStateFlow(false)
    val isEditVisible: StateFlow<Boolean> = _isEditVisible

    private val _isDetailVisible = MutableStateFlow(false)
    val isDetailVisible: StateFlow<Boolean> = _isDetailVisible

    private val _selectedReceipt = MutableStateFlow<Receipt?>(null)
    val selectedReceipt: StateFlow<Receipt?> = _selectedReceipt

    private val _selectedItems = MutableStateFlow<List<ReceiptItem>>(emptyList())
    val selectedItems: StateFlow<List<ReceiptItem>> = _selectedItems

    private val _selectedTagsDisplay = MutableStateFlow("")
    val selectedTagsDisplay: StateFlow<String> = _selectedTagsDisplay

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var currency = "$"

    companion object {
        val SPENDING_CATEGORIES = listOf(
            "Food & Drinks", "Groceries", "Transport", "Shopping", "Gadget",
            "Entertainment", "Health & Medical", "Utilities", "Travel", "Education",
            "Business", "Home & Garden", "Personal Care", "Subscriptions", "Insurance",
            "Investment", "Services", "Fuel/EV Charging", "Credit Card", "Rental",
            "Toll", "Parking", "Loan", "Mortgage", "General"
        )

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
        allReceipts = receiptRepo.getReceiptsFromSync(from)
        refreshTaxYears()
        applyFilters()
    }

    private fun refreshTaxYears() {
        val years = allReceipts
            .filter { it.isTaxDeductible }
            .map { it.taxYear ?: it.date.substring(0, 4) }
            .distinct()
            .sortedDescending()
        _availableTaxYears.value = years.ifEmpty { listOf(DateUtils.todayLocalDate().year.toString()) }
        if (_selectedTaxYear.value !in _availableTaxYears.value) {
            _selectedTaxYear.value = _availableTaxYears.value.first()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            applyFilters()
        }
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
        // "tax 2024"
        Regex("""^tax[:\s]*(\d{4})$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val year = it.groupValues[1]
            if (year in _availableTaxYears.value) {
                _searchQuery.value = ""
                _filteredReceipts.value = allReceipts.filter { r ->
                    r.isTaxDeductible && (r.taxYear == year || r.date.startsWith(year))
                }
                updateTotal()
                return true
            }
        }

        // ">500" or "above:500"
        Regex("""^(?:>\s*|above[:\s]*)(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val min = it.groupValues[1].toDoubleOrNull() ?: return@let
            _searchQuery.value = ""
            _filteredReceipts.value = allReceipts.filter { r -> r.total >= min }
            updateTotal()
            return true
        }

        // "<100"
        Regex("""^(?:<\s*|below[:\s]*)(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val max = it.groupValues[1].toDoubleOrNull() ?: return@let
            _searchQuery.value = ""
            _filteredReceipts.value = allReceipts.filter { r -> r.total <= max }
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
            _filteredReceipts.value = allReceipts.filter { r ->
                val d = DateUtils.toLocalDate(r.date)
                !d.isBefore(from) && !d.isAfter(to)
            }
            updateTotal()
            return true
        }

        // "#groceries" or "category:groceries"
        Regex("""^(?:#|category[:\s]*)(.+)$""", RegexOption.IGNORE_CASE).find(query)?.let {
            val cat = it.groupValues[1].trim()
            val match = SPENDING_CATEGORIES.firstOrNull { c ->
                c.startsWith(cat, ignoreCase = true)
            }
            if (match != null) {
                _searchQuery.value = ""
                _filteredReceipts.value = allReceipts.filter { r -> r.category == match }
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

    fun setShowTaxOnly(show: Boolean) {
        _showTaxOnly.value = show
        applyFilters()
    }

    fun setTaxYear(year: String) {
        _selectedTaxYear.value = year
    }

    private fun applyFilters() {
        var filtered = allReceipts.asSequence()

        val q = _searchQuery.value.trim().lowercase()
        if (q.isNotEmpty()) {
            filtered = filtered.filter { r ->
                r.merchant.lowercase().contains(q) ||
                r.category.lowercase().contains(q) ||
                (r.notes?.lowercase()?.contains(q) ?: false)
            }
        }

        if (_selectedCategory.value != "All") {
            filtered = filtered.filter { it.category == _selectedCategory.value }
        }

        if (_showTaxOnly.value) {
            filtered = filtered.filter { it.isTaxDeductible }
        }

        _filteredReceipts.value = filtered.toList()
        updateTotal()
    }

    private fun updateTotal() {
        val total = _filteredReceipts.value.sumOf { it.total }
        _totalText.value = CurrencyFormatter.format(total, currency)
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
    }

    fun dismissEdit() {
        _isEditVisible.value = false
        _editingReceipt.value = null
    }

    fun saveEdit(merchant: String, amountText: String, category: String, notes: String) {
        viewModelScope.launch {
            val receipt = _editingReceipt.value ?: return@launch
            val amount = amountText.toDoubleOrNull()
            if (merchant.isBlank() || amount == null || amount <= 0) return@launch

            val updated = receipt.copy(
                merchant = merchant.trim(),
                total = amount,
                category = category.ifBlank { "General" },
                notes = notes.trim().ifBlank { null }
            )
            receiptRepo.saveReceipt(updated)
            _isEditVisible.value = false
            _editingReceipt.value = null
            loadData()
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            receiptRepo.deleteReceipt(receipt)
            loadData()
        }
    }

    fun setIsRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }
}
