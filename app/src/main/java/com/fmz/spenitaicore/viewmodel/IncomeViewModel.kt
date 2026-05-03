package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
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
        _incomeEntries.value = allEntries

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

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
        quietLoad()
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

    fun setIsRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }
}
