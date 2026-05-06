package com.fmz.spenitaicore.viewmodel

import android.accounts.Account
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.export.ExportService
import com.fmz.spenitaicore.data.backup.BackupWorker
import com.fmz.spenitaicore.data.backup.DriveBackupService
import com.fmz.spenitaicore.util.SalaryCycle
import com.google.android.gms.auth.UserRecoverableAuthException
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

    fun enableBackup() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupError.value = null
            try {
                val account = authService.findGoogleAccount(preferences.getUserEmail())
                    ?: run {
                        _backupError.value = "No Google account found. Please sign in first."
                        _isBackingUp.value = false
                        return@launch
                    }

                // Request Drive authorization — may throw consent required
                authService.authorizeDrive(account)

                // Store backup settings
                preferences.setBackupEnabled(true, account.name)
                _isBackupEnabled.value = true
                _backupAccountName.value = account.name

                // Schedule nightly backup
                val context = SpenItApp.instance
                BackupWorker.schedule(context)

                // Run first backup immediately
                runManualBackup(account)
            } catch (e: UserRecoverableAuthException) {
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
        if (granted) {
            enableBackup() // Retry after consent
        } else {
            _isBackingUp.value = false
            _backupError.value = "Drive access was denied"
        }
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
                val account = authService.findGoogleAccount(accountName) ?: run {
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
        val context = SpenItApp.instance
        val dbPath = context.getDatabasePath("spenit.db")
        if (!dbPath.exists()) {
            _backupError.value = "Database not found"
            _isBackingUp.value = false
            return
        }
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val fileName = "SpenIt_Backup_$dateStr.db"
        val result = driveService.uploadBackup(account, dbPath, fileName)

        _isBackingUp.value = false
        if (result.success) {
            preferences.setLastBackupTime(System.currentTimeMillis())
            _lastBackupTime.value = System.currentTimeMillis()
        } else {
            _backupError.value = result.message
        }
    }

    fun disableBackup() {
        viewModelScope.launch {
            preferences.setBackupEnabled(false, null)
            _isBackupEnabled.value = false
            BackupWorker.cancel(SpenItApp.instance)
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
                val account = authService.findGoogleAccount(accountName) ?: run {
                    _restoreError.value = "Google account not found"
                    _isLoadingBackups.value = false
                    return@launch
                }
                val backups = driveService.listBackups(account)
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
                val account = authService.findGoogleAccount(accountName) ?: run {
                    _restoreError.value = "Google account not found"
                    _isRestoring.value = false
                    return@launch
                }

                // Download the backup
                val downloadedFile = driveService.downloadBackup(account, backup.id)
                if (downloadedFile == null || !downloadedFile.exists()) {
                    _restoreError.value = "Download failed"
                    _isRestoring.value = false
                    return@launch
                }

                // Close the database
                try {
                    container.database.close()
                } catch (_: Exception) { }

                // Replace database files
                val context = SpenItApp.instance
                context.getDatabasePath("spenit.db").delete()
                context.getDatabasePath("spenit.db-wal").delete()
                context.getDatabasePath("spenit.db-shm").delete()

                downloadedFile.copyTo(
                    context.getDatabasePath("spenit.db"),
                    overwrite = true
                )

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

    fun restartApp() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
