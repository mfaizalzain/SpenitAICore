package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.ai.FinancialDocumentType
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.data.db.entity.SharedImportItem
import com.fmz.spenitaicore.data.db.entity.SharedImportKind
import com.fmz.spenitaicore.data.db.entity.SharedImportStatus
import com.fmz.spenitaicore.data.notification.ImportNotificationHelper
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.PendingSharedFiles
import com.fmz.spenitaicore.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SharedImportsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val aiCore = container.aiCoreService
    private val receiptRepo = container.receiptRepository
    private val incomeRepo = container.incomeRepository
    private val preferences = container.preferences
    private val sharedImportStore = container.sharedImportStore
    private val appContext = SpenItApp.instance.applicationContext

    private val _imports = MutableStateFlow<List<SharedImportItem>>(emptyList())
    val imports: StateFlow<List<SharedImportItem>> = _imports

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    init {
        _imports.value = sharedImportStore.load()
        refreshSummary()
        drainPendingFiles()
    }

    fun drainPendingFiles() {
        val existingIds = _imports.value.map { it.id }.toSet()
        val persisted = sharedImportStore.load()
        val pending = PendingSharedFiles.drain()
        val pendingItems = pending.map { entry ->
            SharedImportItem(
                id = UUID.randomUUID().toString(),
                filePath = entry.filePath,
                displayName = entry.displayName,
                kind = SharedImportKind.Unknown,
                status = SharedImportStatus.NeedsReview
            )
        }
        val merged = (persisted + pendingItems).distinctBy { it.id }
        if (merged != _imports.value) {
            setImports(merged)
        }
        val newItems = merged.filter { it.id !in existingIds }
        val itemsToClassify = newItems.filter {
            it.kind == SharedImportKind.Unknown && it.status == SharedImportStatus.NeedsReview
        }
        if (itemsToClassify.isNotEmpty()) {
            classifyImports(itemsToClassify)
        }
    }

    fun addFiles(filePaths: List<String>, displayNames: List<String>) {
        val newItems = filePaths.zip(displayNames).map { (path, name) ->
                SharedImportItem(
                    id = UUID.randomUUID().toString(),
                    filePath = path,
                    displayName = name,
                    kind = SharedImportKind.Unknown,
                    status = SharedImportStatus.NeedsReview
                )
            }
        setImports(_imports.value + newItems)
        classifyImports(newItems)
    }

    fun addFileUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = uris.mapNotNull { uri ->
                val localFile = FileUtils.copySharedFileToImports(appContext, uri) ?: return@mapNotNull null
                val displayName = FileUtils.getDisplayName(appContext, uri) ?: localFile.name
                SharedImportItem(
                    id = UUID.randomUUID().toString(),
                    filePath = localFile.absolutePath,
                    displayName = displayName,
                    kind = SharedImportKind.Unknown,
                    status = SharedImportStatus.NeedsReview
                )
            }
            if (newItems.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    setImports(_imports.value + newItems)
                    classifyImports(newItems)
                }
            }
        }
    }

    fun setImportKind(item: SharedImportItem, kind: SharedImportKind) {
        setImports(_imports.value.map {
            if (it.id == item.id) it.copy(kind = kind, statusMessage = null) else it
        })
    }

    private fun classifyImports(items: List<SharedImportItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                setStatusMessage(item.id, "Classifying...")
                val kind = aiCore.classifyFinancialDocument(item.filePath).toSharedImportKind()
                val updatedItem = if (kind == SharedImportKind.Unknown) {
                    item.copy(statusMessage = "Choose a type to import")
                } else {
                    item.copy(kind = kind, statusMessage = "Detected ${kind.displayName()}")
                }
                setImports(_imports.value.map {
                    if (it.id == item.id) updatedItem else it
                })

                if (kind != SharedImportKind.Unknown) {
                    setImports(_imports.value.map {
                        if (it.id == item.id) it.copy(statusMessage = "Auto-processing ${kind.displayName()}...") else it
                    })
                    doProcessImport(updatedItem)
                }
            }
        }
    }

    fun processImport(item: SharedImportItem) {
        viewModelScope.launch { doProcessImport(item) }
    }

    private suspend fun doProcessImport(item: SharedImportItem) {
        val currentItem = _imports.value.firstOrNull { it.id == item.id } ?: item
        setImports(_imports.value.map {
            if (it.id == currentItem.id) {
                it.copy(status = SharedImportStatus.Processing, statusMessage = "Importing...")
            } else {
                it
            }
        })

        try {
            val currency = preferences.getDefaultCurrency()
            val result = when (currentItem.kind) {
                SharedImportKind.ExpenseReceipt -> importExpenseReceipt(currentItem, currency)
                SharedImportKind.Income -> importIncome(currentItem, currency)
                SharedImportKind.BankStatement -> importBankStatement(currentItem, currency)
                SharedImportKind.Unknown -> ImportResult.failure("Choose a file type before importing")
            }

            setImports(_imports.value.map {
                if (it.id == currentItem.id) {
                    if (result.isSuccess) {
                        it.copy(
                            status = SharedImportStatus.Completed,
                            statusMessage = result.message,
                            linkedReceiptId = result.receiptIds.firstOrNull() ?: 0,
                            linkedIncomeEntryId = result.incomeEntryIds.firstOrNull() ?: 0,
                            linkedReceiptIds = result.receiptIds,
                            linkedIncomeEntryIds = result.incomeEntryIds
                        )
                    } else {
                        it.copy(
                            status = if (result.isDuplicate) {
                                SharedImportStatus.Duplicate
                            } else {
                                SharedImportStatus.Failed
                            },
                            statusMessage = result.message
                        )
                    }
                } else {
                    it
                }
            })
        } catch (e: Exception) {
            setImports(_imports.value.map {
                if (it.id == currentItem.id) it.copy(
                    status = SharedImportStatus.Failed,
                    statusMessage = e.message ?: "Failed"
                ) else it
            })
        }
        notifyImportResult(currentItem.id, currentItem.displayName)
    }

    private fun notifyImportResult(itemId: String, displayName: String) {
        val item = _imports.value.firstOrNull { it.id == itemId } ?: return
        when (item.status) {
            SharedImportStatus.Completed ->
                ImportNotificationHelper.showSuccess(appContext, displayName, item.statusMessage ?: "")
            SharedImportStatus.Failed ->
                ImportNotificationHelper.showFailed(appContext, displayName, item.statusMessage ?: "")
            SharedImportStatus.Duplicate ->
                ImportNotificationHelper.showDuplicate(appContext, displayName)
            else -> {}
        }
    }

    private suspend fun importExpenseReceipt(item: SharedImportItem, currency: String): ImportResult {
        val result = aiCore.extractReceiptData(item.filePath, currency)
            ?: return ImportResult.failure("Could not extract receipt data")

        if (result.total <= 0.0) {
            return ImportResult.failure("No receipt total found")
        }

        val receipt = Receipt(
            merchant = result.merchant.ifBlank { item.displayName },
            date = result.date.ifBlank { DateUtils.today() },
            total = result.total,
            taxAmount = result.taxAmount,
            currency = result.currency.ifBlank { currency },
            category = result.category.ifBlank { "General" },
            imagePath = item.filePath,
            notes = result.notes
        )
        val receiptId = saveReceiptIfNotDuplicate(receipt)
            ?: return ImportResult.duplicate("Duplicate receipt: ${receipt.merchant}")
        result.items.forEach { extractedItem ->
            receiptRepo.saveReceiptItem(
                ReceiptItem(
                    receiptId = receiptId,
                    description = extractedItem.description,
                    quantity = extractedItem.quantity,
                    unitPrice = extractedItem.unitPrice,
                    total = extractedItem.total
                )
            )
        }

        return ImportResult.success("Imported receipt: ${receipt.merchant}", receiptIds = listOf(receiptId))
    }

    private suspend fun importIncome(item: SharedImportItem, currency: String): ImportResult {
        val result = aiCore.extractIncomeData(item.filePath, currency)
            ?: return ImportResult.failure("Could not extract income data")

        if (result.netPay <= 0.0) {
            return ImportResult.failure("No income amount found")
        }

        val entry = IncomeEntry(
            source = result.employer.ifBlank { item.displayName },
            category = result.category.ifBlank { "Other Income" },
            amount = result.netPay,
            currency = currency,
            date = result.date.ifBlank { DateUtils.today() },
            notes = result.notes
        )
        val incomeId = saveIncomeIfNotDuplicate(entry)
            ?: return ImportResult.duplicate("Duplicate income: ${entry.source}")

        return ImportResult.success("Imported income: ${entry.source}", incomeEntryIds = listOf(incomeId))
    }

    private suspend fun importBankStatement(item: SharedImportItem, currency: String): ImportResult {
        val result = aiCore.extractBankStatementData(item.filePath, currency)
        result.errorMessage?.let {
            return ImportResult.failure(it)
        }

        if (result.transactions.isEmpty()) {
            return ImportResult.failure("No transactions found")
        }

        val incomeIds = mutableListOf<Int>()
        val receiptIds = mutableListOf<Int>()
        var duplicateCount = 0
        result.transactions.forEach { transaction ->
            if (transaction.amount == 0.0) return@forEach
            val date = transaction.date.ifBlank { DateUtils.today() }
            if (transaction.type.equals("credit", ignoreCase = true) || transaction.amount > 0) {
                val entry = IncomeEntry(
                    source = transaction.description.ifBlank { result.bankName.ifBlank { "Bank Credit" } },
                    category = "Other Income",
                    amount = kotlin.math.abs(transaction.amount),
                    currency = currency,
                    date = date,
                    notes = "Imported from ${result.bankName.ifBlank { item.displayName }}",
                    isFromBankImport = true
                )
                val savedId = saveIncomeIfNotDuplicate(entry)
                if (savedId == null) duplicateCount++ else incomeIds += savedId
            } else {
                val receipt = Receipt(
                    merchant = transaction.description.ifBlank { result.bankName.ifBlank { "Bank Debit" } },
                    date = date,
                    total = kotlin.math.abs(transaction.amount),
                    currency = currency,
                    category = "General",
                    imagePath = item.filePath,
                    notes = "Imported from ${result.bankName.ifBlank { item.displayName }}"
                )
                val savedId = saveReceiptIfNotDuplicate(receipt)
                if (savedId == null) duplicateCount++ else receiptIds += savedId
            }
        }

        val importedCount = incomeIds.size + receiptIds.size
        return if (importedCount > 0) {
            ImportResult.success(
                buildImportMessage(importedCount, duplicateCount, "transaction(s)"),
                receiptIds = receiptIds,
                incomeEntryIds = incomeIds
            )
        } else if (duplicateCount > 0) {
            ImportResult.duplicate("All $duplicateCount transaction(s) already exist")
        } else {
            ImportResult.failure("No importable transactions found")
        }
    }

    fun retryImport(item: SharedImportItem) {
        viewModelScope.launch {
            setImports(_imports.value.map {
                if (it.id == item.id) it.copy(
                    status = SharedImportStatus.NeedsReview,
                    statusMessage = null,
                    retryCount = it.retryCount + 1
                ) else it
            })
            doProcessImport(item)
        }
    }

    fun removeImport(item: SharedImportItem) {
        setImports(_imports.value.filter { it.id != item.id })
    }

    fun clearCompleted() {
        setImports(_imports.value.filter { it.status != SharedImportStatus.Completed })
    }

    fun clearAll() {
        setImports(emptyList())
    }

    private fun setImports(items: List<SharedImportItem>) {
        _imports.value = items
        refreshSummary()
        sharedImportStore.save(items)
    }

    private suspend fun saveReceiptIfNotDuplicate(receipt: Receipt): Int? {
        val duplicate = receiptRepo.findPotentialDuplicate(receipt)
        if (duplicate != null) return null
        return receiptRepo.saveReceipt(receipt).toInt()
    }

    private suspend fun saveIncomeIfNotDuplicate(entry: IncomeEntry): Int? {
        val duplicate = incomeRepo.findPotentialDuplicate(entry)
        if (duplicate != null) return null
        return incomeRepo.saveIncomeEntry(entry).toInt()
    }

    private fun buildImportMessage(importedCount: Int, duplicateCount: Int, label: String): String {
        return if (duplicateCount > 0) {
            "Imported $importedCount $label, skipped $duplicateCount duplicate(s)"
        } else {
            "Imported $importedCount $label"
        }
    }

    private fun refreshSummary() {
        _pendingCount.value = _imports.value.size
    }

    private fun setStatusMessage(itemId: String, message: String?) {
        setImports(_imports.value.map {
            if (it.id == itemId) it.copy(statusMessage = message) else it
        })
    }

    private fun FinancialDocumentType.toSharedImportKind(): SharedImportKind {
        return when (this) {
            FinancialDocumentType.Income -> SharedImportKind.Income
            FinancialDocumentType.Expense -> SharedImportKind.ExpenseReceipt
            FinancialDocumentType.BankStatement -> SharedImportKind.BankStatement
            FinancialDocumentType.Unknown -> SharedImportKind.Unknown
        }
    }

    private val _navigateToEdit = MutableStateFlow<EditNavigation?>(null)
    val navigateToEdit: StateFlow<EditNavigation?> = _navigateToEdit

    fun onCompletedItemClick(item: SharedImportItem) {
        when (item.kind) {
            SharedImportKind.ExpenseReceipt -> {
                if (item.linkedReceiptId > 0) {
                    _navigateToEdit.value = EditNavigation.Receipt(item.linkedReceiptId)
                }
            }
            SharedImportKind.Income -> {
                if (item.linkedIncomeEntryId > 0) {
                    _navigateToEdit.value = EditNavigation.Income(item.linkedIncomeEntryId)
                }
            }
            SharedImportKind.BankStatement -> {
                // For bank statements, show a dialog to choose which entry to edit
                val receiptIds = item.linkedReceiptIds
                val incomeIds = item.linkedIncomeEntryIds
                if (receiptIds.isNotEmpty() || incomeIds.isNotEmpty()) {
                    _navigateToEdit.value = EditNavigation.BankStatement(
                        receiptIds = receiptIds,
                        incomeEntryIds = incomeIds
                    )
                }
            }
            SharedImportKind.Unknown -> { /* Do nothing */ }
        }
    }

    fun onEditNavigationHandled() {
        _navigateToEdit.value = null
    }

    sealed class EditNavigation {
        data class Receipt(val receiptId: Int) : EditNavigation()
        data class Income(val incomeEntryId: Int) : EditNavigation()
        data class BankStatement(
            val receiptIds: List<Int> = emptyList(),
            val incomeEntryIds: List<Int> = emptyList()
        ) : EditNavigation()
    }

    private fun SharedImportKind.displayName(): String {
        return when (this) {
            SharedImportKind.ExpenseReceipt -> "Expense"
            SharedImportKind.Income -> "Income"
            SharedImportKind.BankStatement -> "Bank Statement"
            SharedImportKind.Unknown -> "Unknown"
        }
    }

    private data class ImportResult(
        val isSuccess: Boolean,
        val message: String,
        val isDuplicate: Boolean = false,
        val receiptIds: List<Int> = emptyList(),
        val incomeEntryIds: List<Int> = emptyList()
    ) {
        companion object {
            fun success(
                message: String,
                receiptIds: List<Int> = emptyList(),
                incomeEntryIds: List<Int> = emptyList()
            ) = ImportResult(
                isSuccess = true,
                message = message,
                isDuplicate = false,
                receiptIds = receiptIds,
                incomeEntryIds = incomeEntryIds
            )

            fun failure(message: String) = ImportResult(isSuccess = false, message = message)

            fun duplicate(message: String) = ImportResult(
                isSuccess = false,
                message = message,
                isDuplicate = true
            )
        }
    }
}
