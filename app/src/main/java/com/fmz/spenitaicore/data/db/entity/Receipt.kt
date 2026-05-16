package com.fmz.spenitaicore.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "merchant") val merchant: String = "",
    @ColumnInfo(name = "date") val date: String = "", // yyyy-MM-dd
    @ColumnInfo(name = "total") val total: Double = 0.0,
    @ColumnInfo(name = "tax_amount") val taxAmount: Double = 0.0,
    @ColumnInfo(name = "currency") val currency: String = "$",
    @ColumnInfo(name = "category") val category: String = "General",
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_tax_deductible") val isTaxDeductible: Boolean = false,
    @ColumnInfo(name = "tax_year") val taxYear: String? = null,
    @ColumnInfo(name = "tax_category") val taxCategory: String? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "tags_json") val tagsJson: String = "[]"
) {
    val categoryIcon: String
        get() = getCategoryIcon(category)

    companion object {
        fun getCategoryIcon(category: String): String = when (category) {
            "Food & Drinks", "Food & Dining" -> "\uD83C\uDF7D\uFE0F"
            "Groceries" -> "\uD83D\uDECD\uFE0F"
            "Transport" -> "\uD83D\uDE97"
            "Shopping" -> "\uD83D\uDED2"
            "Gadget" -> "\uD83D\uDCF1"
            "Entertainment" -> "\uD83C\uDFAC"
            "Health & Medical" -> "\uD83E\uDE7A"
            "Utilities" -> "\uD83D\uDCA1"
            "Travel" -> "\u2708\uFE0F"
            "Education" -> "\uD83C\uDF93"
            "Business" -> "\uD83D\uDCBC"
            "Home & Garden" -> "\uD83C\uDFE1"
            "Personal Care" -> "\uD83E\uDDF4"
            "Subscriptions" -> "\uD83D\uDD01"
            "Insurance" -> "\uD83D\uDEE1\uFE0F"
            "Investment" -> "\uD83D\uDCC8"
            "Services" -> "\uD83D\uDEE0\uFE0F"
            "Fuel/EV Charging" -> "\u26FD"
            "Credit Card" -> "\uD83D\uDCB3"
            "Rental" -> "\uD83C\uDFE2"
            "Toll" -> "\uD83D\uDEE3\uFE0F"
            "Parking" -> "\uD83D\uDE97"
            "Loan" -> "\uD83C\uDFE6"
            "Mortgage" -> "\uD83C\uDFD8\uFE0F"
            else -> "\uD83E\uDDFE"
        }
    }

    val isPdf: Boolean
        get() = imagePath?.endsWith(".pdf", ignoreCase = true) == true

    val isImage: Boolean
        get() = !imagePath.isNullOrEmpty() && !isPdf
}
