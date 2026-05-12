package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Year

data class TaxCategorySummary(
    val category: String,
    val receipts: List<Receipt>,
    val totalTaxAmount: Double
)

data class TaxReliefUiState(
    val availableYears: List<String> = emptyList(),
    val selectedYear: String = "",
    val categories: List<TaxCategorySummary> = emptyList(),
    val totalRelief: Double = 0.0,
    val isLoading: Boolean = true
)

class TaxReliefViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo: ReceiptRepository = container.receiptRepository

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
                _state.value = _state.value.copy(availableYears = years)
                selectYear(defaultYear)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    availableYears = listOf(Year.now().toString())
                )
                selectYear(Year.now().toString())
            }
        }
    }

    fun selectYear(year: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedYear = year, isLoading = true)
            try {
                val receipts = receiptRepo.getTaxReceipts(year)
                val grouped = receipts.groupBy { it.taxCategory ?: "Uncategorised" }
                val categories = grouped.map { (cat, list) ->
                    TaxCategorySummary(
                        category = cat,
                        receipts = list,
                        totalTaxAmount = list.sumOf { it.taxAmount }
                    )
                }.sortedByDescending { it.totalTaxAmount }
                val total = categories.sumOf { it.totalTaxAmount }
                _state.value = _state.value.copy(
                    categories = categories,
                    totalRelief = total,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
