package com.fmz.spenitaicore.ai

data class OcrResult(
    val merchant: String = "",
    val date: String = "", // yyyy-MM-dd
    val total: Double = 0.0,
    val taxAmount: Double = 0.0,
    val currency: String = "$",
    val category: String = "General",
    val notes: String? = null,
    val transactionType: String = "Unknown",
    val items: List<OcrLineItem> = emptyList(),
    val rawText: String = "",
    val confidence: Double = 0.0
)

data class OcrLineItem(
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val total: Double = 0.0
)

data class PaySlipResult(
    val netPay: Double = 0.0,
    val employer: String = "",
    val date: String = "",
    val category: String = "Salary",
    val notes: String? = null
)

data class BankStatementResult(
    val bankName: String = "",
    val accountLast4: String = "",
    val period: String = "",
    val transactions: List<BankTransaction> = emptyList()
)

data class BankTransaction(
    val date: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val type: String = "debit" // "credit" or "debit"
)

enum class FinancialDocumentType {
    Income,
    Expense,
    BankStatement,
    Unknown
}

data class AiInsightResult(
    val summary: String = "",
    val keyFindings: List<String> = emptyList(),
    val savingTips: List<SavingTip> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val weeklyTrend: List<SpendingTrend> = emptyList(),
    val taxDeductibleTotal: Double = 0.0,
    val averageDailySpend: Double = 0.0,
    val anomalyAlert: String? = null
)

data class CategoryBreakdown(
    val category: String = "",
    val amount: Double = 0.0,
    val percentage: Double = 0.0,
    val colorHex: String = "#F5C518"
)

data class SavingTip(
    val title: String = "",
    val description: String = "",
    val potentialSaving: Double? = null,
    val icon: String = "\uD83D\uDCA1"
)

data class SpendingTrend(
    val label: String = "",
    val amount: Double = 0.0,
    val normalizedHeight: Double = 0.0
)

data class SpendingInsights(
    val periodLabel: String = "",
    val totalThisMonth: Double = 0.0,
    val totalLastMonth: Double = 0.0,
    val averageDailySpend: Double = 0.0,
    val taxDeductibleTotal: Double = 0.0,
    val topCategories: List<CategoryBreakdown> = emptyList(),
    val savingTips: List<SavingTip> = emptyList(),
    val weeklyTrend: List<SpendingTrend> = emptyList(),
    val aiSummary: String? = null,
    val keyFindings: List<String> = emptyList(),
    val anomalyAlert: String? = null,
    val monthOverMonthChange: Double = 0.0
)
