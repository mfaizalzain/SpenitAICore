package com.fmz.spenitaicore.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
enum class SharedImportKind {
    Unknown,
    ExpenseReceipt,
    Income,
    BankStatement
}

@Serializable
enum class SharedImportStatus {
    NeedsReview,
    Processing,
    Completed,
    Failed,
    Skipped,
    InQueue,
    Duplicate
}

@Serializable
data class SharedImportItem(
    val id: String,
    val filePath: String,
    val displayName: String,
    val kind: SharedImportKind = SharedImportKind.Unknown,
    val status: SharedImportStatus = SharedImportStatus.NeedsReview,
    val statusMessage: String? = null,
    val retryCount: Int = 0,
    val linkedReceiptId: Int = 0,
    val linkedIncomeEntryId: Int = 0,
    val linkedReceiptIds: List<Int> = emptyList(),
    val linkedIncomeEntryIds: List<Int> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val canProcess: Boolean
        get() = kind != SharedImportKind.Unknown &&
                status != SharedImportStatus.Processing &&
                status != SharedImportStatus.Completed &&
                status != SharedImportStatus.InQueue &&
                (status != SharedImportStatus.Failed || retryCount < 3) &&
                (status != SharedImportStatus.Duplicate || retryCount < 3)

    val canRetry: Boolean
        get() = (status == SharedImportStatus.Failed || status == SharedImportStatus.Duplicate) && retryCount < 3

    val isCompleted: Boolean
        get() = status == SharedImportStatus.Completed
}
