package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.export.ExportService
import com.fmz.spenitaicore.util.SalaryCycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val preferences = container.preferences
    private val receiptRepo = container.receiptRepository
    private val exportService = container.exportService

    private val _selectedCurrencyCode = MutableStateFlow("$")
    val selectedCurrencyCode: StateFlow<String> = _selectedCurrencyCode

    private val _selectedLanguageCode = MutableStateFlow("en")
    val selectedLanguageCode: StateFlow<String> = _selectedLanguageCode

    private val _salaryPayDay = MutableStateFlow(SalaryCycle.DEFAULT_PAY_DAY)
    val salaryPayDay: StateFlow<Int> = _salaryPayDay

    private val _isAppLockEnabled = MutableStateFlow(false)
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    // ── Tax Export ────────────────────────────────────────────────

    private val _availableTaxYears = MutableStateFlow<List<String>>(emptyList())
    val availableTaxYears: StateFlow<List<String>> = _availableTaxYears

    private val _selectedTaxYear = MutableStateFlow("")
    val selectedTaxYear: StateFlow<String> = _selectedTaxYear

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _exportResult = MutableStateFlow<ExportService.ExportResult?>(null)
    val exportResult: StateFlow<ExportService.ExportResult?> = _exportResult

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError

    val availableCurrencies = listOf(
        "$", "USD", "MYR", "SGD", "IDR", "THB", "PHP", "VND",
        "EUR", "GBP", "AUD", "JPY", "CNY", "INR"
    )

    val availableLanguages = listOf(
        "en" to "English",
        "ms" to "Bahasa Melayu"
    )

    val availablePayDays = (1..31).toList()

    init {
        loadSettings()
        loadTaxYears()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                _selectedCurrencyCode.value = preferences.getDefaultCurrency()
                _selectedLanguageCode.value = preferences.getAppLanguage()
                _salaryPayDay.value = preferences.getSalaryPayDay()
                _isAppLockEnabled.value = preferences.getAppLockEnabled()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch {
            if (code.isNotBlank()) {
                _selectedCurrencyCode.value = code
                preferences.setDefaultCurrency(code)
            }
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            if (code.isNotBlank()) {
                _selectedLanguageCode.value = code
                preferences.setAppLanguage(code)
            }
        }
    }

    fun setPayDay(day: Int) {
        viewModelScope.launch {
            _salaryPayDay.value = day.coerceIn(1, 31)
            preferences.setSalaryPayDay(day)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _isAppLockEnabled.value = enabled
            preferences.setAppLockEnabled(enabled)
        }
    }

    // ── Tax Export ────────────────────────────────────────────────

    private fun loadTaxYears() {
        viewModelScope.launch {
            try {
                val years = receiptRepo.getDistinctTaxYears()
                _availableTaxYears.value = years
                if (years.isNotEmpty() && _selectedTaxYear.value.isEmpty()) {
                    _selectedTaxYear.value = years.first()
                }
            } catch (_: Exception) { }
        }
    }

    fun setTaxYear(year: String) {
        _selectedTaxYear.value = year
    }

    fun exportTaxRelief() {
        val year = _selectedTaxYear.value
        if (year.isEmpty()) return

        viewModelScope.launch {
            _isExporting.value = true
            _exportError.value = null
            try {
                val result = exportService.exportTaxRelief(year)
                if (result == null) {
                    _exportError.value = "No tax-deductible expenses found for $year"
                } else {
                    _exportResult.value = result
                }
            } catch (e: Exception) {
                _exportError.value = "Export failed: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun clearExportError() {
        _exportError.value = null
    }
}
