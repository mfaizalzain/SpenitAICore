package com.fmz.spenitaicore.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "IncomeEntries")
data class IncomeEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "source") val source: String = "Salary",
    @ColumnInfo(name = "category") val category: String = "Other Income",
    @ColumnInfo(name = "amount") val amount: Double = 0.0,
    @ColumnInfo(name = "currency") val currency: String = "$",
    @ColumnInfo(name = "date") val date: String = "", // yyyy-MM-dd
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "is_recurring") val isRecurring: Boolean = false,
    @ColumnInfo(name = "recurrence_interval") val recurrenceInterval: String? = null,
    @ColumnInfo(name = "is_from_bank_import") val isFromBankImport: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    val categoryEmoji: String
        get() = categoryEmoji(category)

    val isPdf: Boolean
        get() = imagePath?.endsWith(".pdf", ignoreCase = true) == true

    val isImage: Boolean
        get() = !imagePath.isNullOrEmpty() && !isPdf

    companion object {
        fun categoryEmoji(category: String): String = when (category) {
            "Salary" -> "\uD83D\uDCBC"
            "Freelance" -> "\uD83D\uDCBB"
            "Business" -> "\uD83C\uDFE2"
            "Investment" -> "\uD83D\uDCC8"
            "Rental" -> "\uD83C\uDFE0"
            "Bonus" -> "\uD83C\uDF81"
            "Gift" -> "\uD83C\uDF80"
            "Refund" -> "\uD83D\uDD04"
            "Commision" -> "\uD83E\uDD1D"
            "Commission" -> "\uD83E\uDD1D"
            "Dividend" -> "\uD83D\uDCC8"
            "Royalty" -> "\uD83D\uDC51"
            "Transfer" -> "\uD83D\uDD04"
            else -> "\uD83D\uDCB0"
        }
    }
}

object IncomeSources {
    val All = listOf(
        "Salary", "Freelance", "Business", "Investment", "Rental",
        "Bonus", "Gift", "Refund", "Commission", "Commision",
        "Dividend", "Royalty", "Transfer", "Other Income"
    )
}

object RecurrenceIntervals {
    val All = listOf("Weekly", "Bi-Weekly", "Monthly", "Quarterly", "Annually")
}
