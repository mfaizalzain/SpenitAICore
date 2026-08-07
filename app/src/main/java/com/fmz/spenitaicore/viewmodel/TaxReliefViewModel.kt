package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.export.ExportService
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import com.fmz.spenitaicore.util.MoneyAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Year

data class TaxCategorySummary(
    val category: String,
    val receipts: List<Receipt>,
    val totalTaxAmount: Double,
    val totalExpense: Double
)

data class TaxReliefUiState(
    val availableYears: List<String> = emptyList(),
    val selectedYear: String = "",
    val receipts: List<Receipt> = emptyList(),
    val categories: List<TaxCategorySummary> = emptyList(),
    val totalRelief: Double = 0.0,
    val totalExpense: Double = 0.0,
    val currency: String = "$",
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportResult: ExportService.ExportResult? = null,
    val exportError: String? = null
)

class TaxReliefViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo: ReceiptRepository = container.receiptRepository
    private val exportService = container.exportService
    private val preferences = container.preferences

    private val _state = MutableStateFlow(TaxReliefUiState())
    val state: StateFlow<TaxReliefUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val years = receiptRepo.getDistinctTaxYears()
                val defaultYear = years.firstOrNull() ?: Year.now().toString()
                loadYear(defaultYear, years.ifEmpty { listOf(defaultYear) })
            } catch (e: Exception) {
                loadYear(Year.now().toString(), listOf(Year.now().toString()))
            }
        }
    }

    fun selectYear(year: String) {
        viewModelScope.launch {
            loadYear(year, _state.value.availableYears)
        }
    }

    fun exportSelectedYear() {
        val year = _state.value.selectedYear
        if (year.isBlank() || _state.value.isExporting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, exportError = null, exportResult = null)
            try {
                val result = exportService.exportTaxRelief(year)
                _state.value = _state.value.copy(
                    isExporting = false,
                    exportResult = result,
                    exportError = if (result == null) "No tax-deductible expenses found for $year" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    exportError = e.message ?: "Export failed. Please try again."
                )
            }
        }
    }

    fun clearExportResult() {
        _state.value = _state.value.copy(exportResult = null)
    }

    fun clearExportError() {
        _state.value = _state.value.copy(exportError = null)
    }

    private suspend fun loadYear(year: String, availableYears: List<String>) {
        _state.value = _state.value.copy(
            selectedYear = year,
            availableYears = availableYears,
            isLoading = true,
            exportError = null
        )
        val defaultCurrency = preferences.getDefaultCurrency()
        val rates = container.exchangeRateRepository.rates.value
        try {
            val receipts = receiptRepo.getTaxReceipts(year)
            val grouped = receipts.groupBy { it.taxCategory?.takeIf { cat -> cat.isNotBlank() } ?: "Uncategorised" }
            val categories = grouped.map { (cat, list) ->
                TaxCategorySummary(
                    category = cat,
                    receipts = list,
                    totalTaxAmount = MoneyAggregator.sumTaxAmounts(list, defaultCurrency, rates),
                    totalExpense = MoneyAggregator.sumReceipts(list, defaultCurrency, rates)
                )
            }.sortedByDescending { it.totalExpense }
            val totalTax = categories.sumOf { it.totalTaxAmount }
            val totalExp = categories.sumOf { it.totalExpense }
            _state.value = _state.value.copy(
                receipts = receipts,
                categories = categories,
                totalRelief = totalTax,
                totalExpense = totalExp,
                currency = receipts.firstOrNull()?.currency ?: defaultCurrency,
                isLoading = false
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                receipts = emptyList(),
                categories = emptyList(),
                totalRelief = 0.0,
                currency = defaultCurrency,
                isLoading = false,
                exportError = e.message ?: "Unable to load tax relief expenses."
            )
        }
    }
}
