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
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.PendingSharedFiles
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class SharedImportsViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val aiCore = container.aiCoreService
    private val receiptRepo = container.receiptRepository
    private val incomeRepo = container.incomeRepository
    private val preferences = container.preferences
    private val appContext = SpenItApp.instance.applicationContext

    private val _imports = MutableStateFlow<List<SharedImportItem>>(emptyList())
    val imports: StateFlow<List<SharedImportItem>> = _imports

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    init {
        drainPendingFiles()
    }

    fun drainPendingFiles() {
        val pending = PendingSharedFiles.drain()
        if (pending.isNotEmpty()) {
            val newItems = pending.map { entry ->
                SharedImportItem(
                    id = UUID.randomUUID().toString(),
                    filePath = entry.filePath,
                    displayName = entry.displayName,
                    kind = SharedImportKind.Unknown,
                    status = SharedImportStatus.NeedsReview
                )
            }
            _imports.value = _imports.value + newItems
            refreshSummary()
            classifyImports(newItems)
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
        _imports.value = _imports.value + newItems
        refreshSummary()
        classifyImports(newItems)
    }

    fun setImportKind(item: SharedImportItem, kind: SharedImportKind) {
        _imports.value = _imports.value.map {
            if (it.id == item.id) it.copy(kind = kind, statusMessage = null) else it
        }
        refreshSummary()
    }

    private fun classifyImports(items: List<SharedImportItem>) {
        items.forEach { item ->
            viewModelScope.launch {
                setStatusMessage(item.id, "Classifying...")
                val kind = aiCore.classifyFinancialDocument(item.filePath).toSharedImportKind()
                _imports.value = _imports.value.map {
                    if (it.id == item.id) {
                        if (kind == SharedImportKind.Unknown) {
                            it.copy(statusMessage = "Choose a type to import")
                        } else {
                            it.copy(kind = kind, statusMessage = "Detected ${kind.displayName()}")
                        }
                    } else {
                        it
                    }
                }
                refreshSummary()
            }
        }
    }

    fun processImport(item: SharedImportItem) {
        viewModelScope.launch {
            val currentItem = _imports.value.firstOrNull { it.id == item.id } ?: item
            _imports.value = _imports.value.map {
                if (it.id == currentItem.id) {
                    it.copy(status = SharedImportStatus.Processing, statusMessage = "Importing...")
                } else {
                    it
                }
            }
            refreshSummary()

            try {
                val currency = preferences.getDefaultCurrency()
                val result = when (currentItem.kind) {
                    SharedImportKind.ExpenseReceipt -> importExpenseReceipt(currentItem, currency)
                    SharedImportKind.Income -> importIncome(currentItem, currency)
                    SharedImportKind.BankStatement -> importBankStatement(currentItem, currency)
                    SharedImportKind.Unknown -> ImportResult.failure("Choose a file type before importing")
                }

                _imports.value = _imports.value.map {
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
                }
            } catch (e: Exception) {
                _imports.value = _imports.value.map {
                    if (it.id == currentItem.id) it.copy(
                        status = SharedImportStatus.Failed,
                        statusMessage = e.message ?: "Failed"
                    ) else it
                }
            }
            refreshSummary()
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
        if (item.filePath.endsWith(".csv", ignoreCase = true) || item.displayName.endsWith(".csv", ignoreCase = true)) {
            return importBankStatementCsv(item, currency)
        }

        val result = aiCore.extractBankStatementData(item.filePath, currency)
            ?: return ImportResult.failure("Could not extract bank statement data")

        if (result.transactions.isEmpty()) {
            return ImportResult.failure("No transactions found")
        }

        val incomeIds = mutableListOf<Int>()
        val receiptIds = mutableListOf<Int>()
        var duplicateCount = 0
        result.transactions.forEach { transaction ->
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

    private suspend fun importBankStatementCsv(item: SharedImportItem, currency: String): ImportResult {
        val rows = readCsvRows(item.filePath)
        if (rows.size < 2) {
            return ImportResult.failure("CSV bank statement has no transaction rows")
        }

        val headers = rows.first().map { it.normalizedHeader() }
        val dataRows = rows.drop(1)
        val incomeIds = mutableListOf<Int>()
        val receiptIds = mutableListOf<Int>()
        var duplicateCount = 0

        dataRows.forEach { row ->
            if (row.all { it.isBlank() }) return@forEach

            val date = valueFor(row, headers, "date", "transactiondate", "postingdate", "valuedate")
                .normalizeCsvDate()
            val description = valueFor(
                row,
                headers,
                "description",
                "details",
                "narration",
                "particulars",
                "reference",
                "transactiondescription"
            ).ifBlank { item.displayName }

            val credit = amountFor(row, headers, "credit", "deposit", "moneyin", "paidin", "inflow")
            val debit = amountFor(row, headers, "debit", "withdrawal", "moneyout", "paidout", "outflow")
            val amount = signedAmountFor(row, headers, "amount", "transactionamount")

            when {
                credit != null && credit > 0.0 -> {
                    val entry = IncomeEntry(
                            source = description,
                            category = "Other Income",
                            amount = credit,
                            currency = currency,
                            date = date,
                            notes = "Imported from ${item.displayName}",
                            isFromBankImport = true
                        )
                    val savedId = saveIncomeIfNotDuplicate(entry)
                    if (savedId == null) duplicateCount++ else incomeIds += savedId
                }
                debit != null && debit > 0.0 -> {
                    val receipt = Receipt(
                            merchant = description,
                            date = date,
                            total = debit,
                            currency = currency,
                            category = "General",
                            imagePath = item.filePath,
                            notes = "Imported from ${item.displayName}"
                        )
                    val savedId = saveReceiptIfNotDuplicate(receipt)
                    if (savedId == null) duplicateCount++ else receiptIds += savedId
                }
                amount != null && amount != 0.0 -> {
                    if (amount > 0.0) {
                        val entry = IncomeEntry(
                                source = description,
                                category = "Other Income",
                                amount = amount,
                                currency = currency,
                                date = date,
                                notes = "Imported from ${item.displayName}",
                                isFromBankImport = true
                            )
                        val savedId = saveIncomeIfNotDuplicate(entry)
                        if (savedId == null) duplicateCount++ else incomeIds += savedId
                    } else {
                        val receipt = Receipt(
                                merchant = description,
                                date = date,
                                total = kotlin.math.abs(amount),
                                currency = currency,
                                category = "General",
                                imagePath = item.filePath,
                                notes = "Imported from ${item.displayName}"
                            )
                        val savedId = saveReceiptIfNotDuplicate(receipt)
                        if (savedId == null) duplicateCount++ else receiptIds += savedId
                    }
                }
            }
        }

        val importedCount = incomeIds.size + receiptIds.size
        return if (importedCount > 0) {
            ImportResult.success(
                buildImportMessage(importedCount, duplicateCount, "CSV transaction(s)"),
                receiptIds = receiptIds,
                incomeEntryIds = incomeIds
            )
        } else if (duplicateCount > 0) {
            ImportResult.duplicate("All $duplicateCount CSV transaction(s) already exist")
        } else {
            ImportResult.failure("No importable CSV transactions found")
        }
    }

    fun retryImport(item: SharedImportItem) {
        viewModelScope.launch {
            _imports.value = _imports.value.map {
                if (it.id == item.id) it.copy(
                    status = SharedImportStatus.NeedsReview,
                    statusMessage = null,
                    retryCount = it.retryCount + 1
                ) else it
            }
            processImport(item)
        }
    }

    fun removeImport(item: SharedImportItem) {
        _imports.value = _imports.value.filter { it.id != item.id }
        refreshSummary()
    }

    fun clearCompleted() {
        _imports.value = _imports.value.filter { it.status != SharedImportStatus.Completed }
        refreshSummary()
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
        _imports.value = _imports.value.map {
            if (it.id == itemId) it.copy(statusMessage = message) else it
        }
    }

    private fun FinancialDocumentType.toSharedImportKind(): SharedImportKind {
        return when (this) {
            FinancialDocumentType.Income -> SharedImportKind.Income
            FinancialDocumentType.Expense -> SharedImportKind.ExpenseReceipt
            FinancialDocumentType.BankStatement -> SharedImportKind.BankStatement
            FinancialDocumentType.Unknown -> SharedImportKind.Unknown
        }
    }

    private fun SharedImportKind.displayName(): String {
        return when (this) {
            SharedImportKind.ExpenseReceipt -> "Expense"
            SharedImportKind.Income -> "Income"
            SharedImportKind.BankStatement -> "Bank Statement"
            SharedImportKind.Unknown -> "Unknown"
        }
    }

    private fun readCsvRows(path: String): List<List<String>> {
        return readTextFromPath(path).lineSequence()
            .filter { it.isNotBlank() }
            .map { it.parseCsvLine() }
            .toList()
    }

    private fun readTextFromPath(path: String): String {
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            appContext.contentResolver.openInputStream(Uri.parse(path))?.bufferedReader()?.use { it.readText() }
                ?: ""
        } else {
            File(path).readText()
        }
    }

    private fun String.parseCsvLine(): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < length) {
            val char = this[index]
            when {
                char == '"' && inQuotes && getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells += current.toString().trim()
        return cells
    }

    private fun valueFor(row: List<String>, headers: List<String>, vararg names: String): String {
        val index = headers.indexOfFirst { it in names }
        return if (index >= 0) row.getOrNull(index).orEmpty().trim() else ""
    }

    private fun amountFor(row: List<String>, headers: List<String>, vararg names: String): Double? {
        return valueFor(row, headers, *names).parseCsvAmount()?.let { kotlin.math.abs(it) }
    }

    private fun signedAmountFor(row: List<String>, headers: List<String>, vararg names: String): Double? {
        return valueFor(row, headers, *names).parseCsvAmount()
    }

    private fun String.normalizedHeader(): String {
        return lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun String.parseCsvAmount(): Double? {
        val cleaned = replace(Regex("[^0-9,\\.\\-()]"), "")
            .replace("(", "-")
            .replace(")", "")
            .trim(',', '.')
            .ifBlank { return null }
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 && lastComma > lastDot -> cleaned.replace(".", "").replace(",", ".")
            lastComma >= 0 && lastDot >= 0 -> cleaned.replace(",", "")
            lastComma >= 0 && cleaned.length - lastComma - 1 == 2 -> cleaned.replace(",", ".")
            else -> cleaned.replace(",", "")
        }
        return normalized.toDoubleOrNull()
    }

    private fun String.normalizeCsvDate(): String {
        val raw = trim()
        if (raw.isBlank()) return DateUtils.today()
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("MMM dd yyyy")
        )
        for (formatter in formatters) {
            try {
                return LocalDate.parse(raw, formatter).toString()
            } catch (_: Exception) { }
        }
        return raw
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
