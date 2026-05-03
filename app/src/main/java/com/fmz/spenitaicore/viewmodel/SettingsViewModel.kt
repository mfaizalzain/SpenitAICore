package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.util.SalaryCycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val preferences = container.preferences

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
}
