package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.entity.CategoryBudget
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.MoneyAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class BudgetsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val budgetRepo = container.categoryBudgetRepository
    private val receiptRepo = container.receiptRepository
    private val preferences = container.preferences
    private val exchangeRates = container.exchangeRateRepository

    private val _budgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    val budgets: StateFlow<List<CategoryBudget>> = _budgets

    /** Category -> total spent this calendar month. */
    private val _spendingByCategory = MutableStateFlow<Map<String, Double>>(emptyMap())
    val spendingByCategory: StateFlow<Map<String, Double>> = _spendingByCategory

    private val _currency = MutableStateFlow("$")
    val currency: StateFlow<String> = _currency

    init {
        viewModelScope.launch {
            budgetRepo.getAllBudgets().collectLatest { _budgets.value = it }
        }
        refreshSpending()
    }

    fun refreshSpending() {
        viewModelScope.launch {
            _currency.value = preferences.getDefaultCurrency()
            val monthStart = DateUtils.fromLocalDate(
                LocalDate.now().withDayOfMonth(1)
            )
            val today = DateUtils.today()
            val receipts = receiptRepo.getReceiptsFromSync(monthStart)
                .filter { it.date <= today }
            _spendingByCategory.value = MoneyAggregator.sumReceiptsByCategory(
                receipts,
                _currency.value,
                exchangeRates.rates.value
            )
        }
    }

    /** Upserts a budget; a non-positive limit removes it. */
    fun setBudget(category: String, monthlyLimit: Double, currency: String) {
        viewModelScope.launch {
            if (monthlyLimit > 0) {
                budgetRepo.upsert(
                    CategoryBudget(
                        category = category,
                        monthlyLimit = monthlyLimit,
                        currency = currency
                    )
                )
            } else {
                budgetRepo.delete(category)
            }
            refreshSpending()
        }
    }

    fun deleteBudget(category: String) {
        viewModelScope.launch {
            budgetRepo.delete(category)
            refreshSpending()
        }
    }
}
