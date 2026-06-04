package com.fmz.spenitaicore.viewmodel

import android.accounts.Account
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.db.AppDatabase
import com.fmz.spenitaicore.data.export.ExportService
import com.fmz.spenitaicore.data.backup.BackupWorker
import com.fmz.spenitaicore.data.backup.DriveBackupService
import com.fmz.spenitaicore.data.preferences.THEME_MODE_DAY
import com.fmz.spenitaicore.data.preferences.THEME_MODE_NIGHT
import com.fmz.spenitaicore.data.preferences.THEME_MODE_SYSTEM
import com.fmz.spenitaicore.util.SalaryCycle
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val preferences = container.preferences
    private val receiptRepo = container.receiptRepository
    private val exportService = container.exportService

    private val _selectedCurrencyCode = MutableStateFlow("$")
    val selectedCurrencyCode: StateFlow<String> = _selectedCurrencyCode

    private val _selectedLanguageCode = MutableStateFlow("en")
    val selectedLanguageCode: StateFlow<String> = _selectedLanguageCode

    private val _themeMode = MutableStateFlow(THEME_MODE_DAY)
    val themeMode: StateFlow<String> = _themeMode

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

    val availableThemeModes = listOf(
        THEME_MODE_DAY to "Day",
        THEME_MODE_NIGHT to "Night",
        THEME_MODE_SYSTEM to "Follow system"
    )

    // ── AI Provider ───────────────────────────────────────────────────

    private val _aiProvider = MutableStateFlow("aicore")
    val aiProvider: StateFlow<String> = _aiProvider

    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey

    private val _aiModel = MutableStateFlow("")
    val aiModel: StateFlow<String> = _aiModel

    private val _aiCustomUrl = MutableStateFlow("")
    val aiCustomUrl: StateFlow<String> = _aiCustomUrl

    private val _aiProviderError = MutableStateFlow<String?>(null)
    val aiProviderError: StateFlow<String?> = _aiProviderError

    private val _aiCoreSupported = MutableStateFlow(true)
    val aiCoreSupported: StateFlow<Boolean> = _aiCoreSupported

    val aiProviderNames = mapOf(
        "aicore" to "On-device (AICore)",
        "gemini" to "Google Gemini",
        "openai" to "OpenAI",
        "custom" to "Custom (OpenAI-compatible)"
    )

    val aiProviderKeys = listOf("aicore", "gemini", "openai", "custom")

    val aiProviderModels = mapOf(
        "gemini" to listOf("gemini-flash-lite-latest", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro"),
        "openai" to listOf("gpt-4o-mini", "gpt-4o", "gpt-4o-2024-08-06"),
        "custom" to emptyList()
    )

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
                _themeMode.value = preferences.getThemeMode()
                _salaryPayDay.value = preferences.getSalaryPayDay()
                _isAppLockEnabled.value = preferences.getAppLockEnabled()
                _aiProvider.value = preferences.getAiProvider()
                _aiApiKey.value = preferences.getAiApiKey()
                _aiModel.value = preferences.getAiModel()
                _aiCustomUrl.value = preferences.getAiCustomUrl()
                _aiCoreSupported.value = (container.aiCoreService.checkAiCoreSupportStatus() != com.fmz.spenitaicore.ai.AiCoreService.AiCoreSupportStatus.NOT_SUPPORTED)
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

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            if (availableThemeModes.any { it.first == mode }) {
                _themeMode.value = mode
                preferences.setThemeMode(mode)
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

    // ── AI Provider ───────────────────────────────────────────────────

    fun setAiProvider(provider: String) {
        viewModelScope.launch {
            _aiProvider.value = provider
            preferences.setAiProvider(provider)
            _aiProviderError.value = null
            if (provider == "aicore") {
                // Clear key when switching to on-device
                _aiApiKey.value = ""
                _aiModel.value = ""
                _aiCustomUrl.value = ""
                preferences.clearAiProvider()
            }
        }
    }

    fun saveAiApiKey(provider: String, apiKey: String, model: String, customUrl: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                _aiProviderError.value = "API key cannot be empty"
                return@launch
            }
            // Basic validation
            if (apiKey.length < 8) {
                _aiProviderError.value = "API key seems too short"
                return@launch
            }
            _aiApiKey.value = apiKey
            _aiModel.value = model
            _aiCustomUrl.value = customUrl
            _aiProvider.value = provider
            preferences.setAiProvider(provider)
            preferences.setAiApiKey(apiKey)
            preferences.setAiModel(model)
            preferences.setAiCustomUrl(customUrl)
            _aiProviderError.value = null
        }
    }

    fun removeAiApiKey() {
        viewModelScope.launch {
            _aiApiKey.value = ""
            _aiModel.value = ""
            _aiCustomUrl.value = ""
            _aiProvider.value = "aicore" // fall back to on-device
            preferences.clearAiProvider()
            _aiProviderError.value = null
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

    // ── Backup ────────────────────────────────────────────────────

    private val driveService = container.driveBackupService
    private val authService = container.googleAuthService

    private val _isBackupEnabled = MutableStateFlow(false)
    val isBackupEnabled: StateFlow<Boolean> = _isBackupEnabled

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp

    private val _backupAccountName = MutableStateFlow<String?>(null)
    val backupAccountName: StateFlow<String?> = _backupAccountName

    private val _lastBackupTime = MutableStateFlow(0L)
    val lastBackupTime: StateFlow<Long> = _lastBackupTime

    private val _backupError = MutableStateFlow<String?>(null)
    val backupError: StateFlow<String?> = _backupError

    private val _driveConsentIntent = MutableStateFlow<android.content.Intent?>(null)
    val driveConsentIntent: StateFlow<android.content.Intent?> = _driveConsentIntent

    private var _pendingToggleEnabled = false

    // ── Restore ───────────────────────────────────────────────────

    private val _availableBackups = MutableStateFlow<List<DriveBackupService.BackupFile>>(emptyList())
    val availableBackups: StateFlow<List<DriveBackupService.BackupFile>> = _availableBackups

    private val _isLoadingBackups = MutableStateFlow(false)
    val isLoadingBackups: StateFlow<Boolean> = _isLoadingBackups

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring

    private val _restoreSuccess = MutableStateFlow(false)
    val restoreSuccess: StateFlow<Boolean> = _restoreSuccess

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError

    private val _selectedBackupForRestore = MutableStateFlow<DriveBackupService.BackupFile?>(null)
    val selectedBackupForRestore: StateFlow<DriveBackupService.BackupFile?> = _selectedBackupForRestore

    init {
        loadBackupState()
    }

    private fun loadBackupState() {
        viewModelScope.launch {
            _isBackupEnabled.value = preferences.isBackupEnabled()
            _backupAccountName.value = preferences.getBackupAccountName()
            _lastBackupTime.value = preferences.getLastBackupTime()
        }
    }

    fun toggleBackup(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                // Just disable — no auth needed
                preferences.setBackupEnabled(false, null)
                _isBackupEnabled.value = false
                _backupAccountName.value = null
                BackupWorker.cancel(SpenItApp.instance)
                return@launch
            }

            // Enabling — need Drive auth first
            _isBackingUp.value = true
            _backupError.value = null
            try {
                val account = withContext(Dispatchers.IO) {
                    authService.findGoogleAccount(preferences.getUserEmail())
                } ?: run {
                    _backupError.value = "No Google account found. Please sign in first."
                    _isBackingUp.value = false
                    return@launch
                }

                // Request Drive authorization — may throw consent required
                withContext(Dispatchers.IO) {
                    authService.authorizeDrive(account)
                }

                // Store backup preferences and schedule — but DON'T run backup
                preferences.setBackupEnabled(true, account.name)
                _isBackupEnabled.value = true
                _backupAccountName.value = account.name
                BackupWorker.schedule(SpenItApp.instance)
                _isBackingUp.value = false
            } catch (e: UserRecoverableAuthException) {
                // Save the pending intent so consent retry re-enables
                _pendingToggleEnabled = true
                _driveConsentIntent.value = e.intent
                _isBackingUp.value = false
            } catch (e: Exception) {
                Log.e("SettingsVM", "Failed to enable backup", e)
                _backupError.value = "Failed: ${e.message}"
                _isBackingUp.value = false
            }
        }
    }

    fun handleDriveConsentResult(granted: Boolean) {
        _driveConsentIntent.value = null
        if (granted && _pendingToggleEnabled) {
            _pendingToggleEnabled = false
            toggleBackup(true) // Retry after consent
        } else {
            _isBackingUp.value = false
            _backupError.value = if (!granted) "Drive access was denied" else null
        }
    }

    fun disableBackup() {
        toggleBackup(false)
    }

    fun runManualBackup() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupError.value = null
            try {
                val accountName = preferences.getBackupAccountName() ?: run {
                    _backupError.value = "Backup not set up"
                    _isBackingUp.value = false
                    return@launch
                }
                val account = withContext(Dispatchers.IO) {
                    authService.findGoogleAccount(accountName)
                } ?: run {
                    _backupError.value = "Google account not found"
                    _isBackingUp.value = false
                    return@launch
                }
                runManualBackup(account)
            } catch (e: Exception) {
                _backupError.value = "Backup failed: ${e.message}"
                _isBackingUp.value = false
            }
        }
    }

    private suspend fun runManualBackup(account: Account) {
        withContext(Dispatchers.IO) {
            val context = SpenItApp.instance
            val zipFile = driveService.createBackupPackage()
            if (zipFile == null) {
                _backupError.value = "Failed to create backup package"
                _isBackingUp.value = false
                return@withContext
            }
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US)
                .format(java.util.Date())
            val fileName = "SpenIt_Backup_$dateStr.zip"
            val result = driveService.uploadBackup(account, zipFile, fileName)

            zipFile.delete()

            _isBackingUp.value = false
            if (result.success) {
                preferences.setLastBackupTime(System.currentTimeMillis())
                _lastBackupTime.value = System.currentTimeMillis()
            } else {
                _backupError.value = result.message
            }
        }
    }

    fun clearBackupError() {
        _backupError.value = null
    }

    // ── Restore ───────────────────────────────────────────────────

    fun loadBackups() {
        viewModelScope.launch {
            _isLoadingBackups.value = true
            _restoreError.value = null
            try {
                val accountName = preferences.getBackupAccountName() ?: run {
                    _restoreError.value = "Backup not set up"
                    _isLoadingBackups.value = false
                    return@launch
                }
                val account = withContext(Dispatchers.IO) {
                    authService.findGoogleAccount(accountName)
                } ?: run {
                    _restoreError.value = "Google account not found"
                    _isLoadingBackups.value = false
                    return@launch
                }
                val backups = withContext(Dispatchers.IO) {
                    driveService.listBackups(account)
                }
                _availableBackups.value = backups
                if (backups.isEmpty()) {
                    _restoreError.value = "No backups found on Google Drive"
                }
            } catch (e: Exception) {
                _restoreError.value = "Failed to load backups: ${e.message}"
            } finally {
                _isLoadingBackups.value = false
            }
        }
    }

    fun selectBackupForRestore(backup: DriveBackupService.BackupFile) {
        _selectedBackupForRestore.value = backup
    }

    fun clearSelectedBackup() {
        _selectedBackupForRestore.value = null
    }

    fun clearBackupList() {
        _availableBackups.value = emptyList()
        _restoreError.value = null
        _selectedBackupForRestore.value = null
        _isLoadingBackups.value = false
    }

    fun confirmRestore() {
        val backup = _selectedBackupForRestore.value ?: return
        viewModelScope.launch {
            _isRestoring.value = true
            _restoreError.value = null
            try {
                val accountName = preferences.getBackupAccountName() ?: run {
                    _restoreError.value = "Backup not set up"
                    _isRestoring.value = false
                    return@launch
                }
                val account = withContext(Dispatchers.IO) {
                    authService.findGoogleAccount(accountName)
                } ?: run {
                    _restoreError.value = "Google account not found"
                    _isRestoring.value = false
                    return@launch
                }

                // Download the backup
                val downloadedFile = withContext(Dispatchers.IO) {
                    driveService.downloadBackup(account, backup.id)
                }
                if (downloadedFile == null || !downloadedFile.exists()) {
                    _restoreError.value = "Download failed"
                    _isRestoring.value = false
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // Close and reset database so Room re-opens fresh
                    AppDatabase.closeAndReset(SpenItApp.instance)

                    // Delete old database files
                    val context = SpenItApp.instance
                    context.getDatabasePath("spenit.db").delete()
                    context.getDatabasePath("spenit.db-wal").delete()
                    context.getDatabasePath("spenit.db-shm").delete()

                    // Extract ZIP (db + media files)
                    val success = driveService.extractBackupPackage(downloadedFile)
                    downloadedFile.delete()
                    if (!success) {
                        throw Exception("Failed to extract backup package")
                    }
                }

                _selectedBackupForRestore.value = null
                _restoreSuccess.value = true
            } catch (e: Exception) {
                _restoreError.value = "Restore failed: ${e.message}"
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun clearRestoreResult() {
        _restoreSuccess.value = false
    }

    fun clearRestoreError() {
        _restoreError.value = null
    }

    // ── Delete Account ─────────────────────────────────────────────

    private val _deleteInProgress = MutableStateFlow(false)
    val deleteInProgress: StateFlow<Boolean> = _deleteInProgress

    private val _deleteCompleted = MutableStateFlow(false)
    val deleteCompleted: StateFlow<Boolean> = _deleteCompleted

    fun deleteAccount() {
        viewModelScope.launch {
            _deleteInProgress.value = true
            try {
                withContext(Dispatchers.IO) {
                    receiptRepo.deleteAll()
                    container.incomeRepository.deleteAll()
                    container.database.userProfileDao().deleteAll()
                    container.sharedImportStore.clear()
                    preferences.clearAll()
                    BackupWorker.cancel(SpenItApp.instance)
                }
                _deleteCompleted.value = true
            } catch (e: Exception) {
                // Even on error, try to restart fresh
                _deleteCompleted.value = true
            } finally {
                _deleteInProgress.value = false
                restartApp()
            }
        }
    }

    fun restartApp() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
