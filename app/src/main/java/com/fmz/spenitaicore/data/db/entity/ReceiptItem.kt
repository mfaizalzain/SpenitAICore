package com.fmz.spenitaicore.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ReceiptItems",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receipt_id")]
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "receipt_id") val receiptId: Int = 0,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "quantity") val quantity: Double = 1.0,
    @ColumnInfo(name = "unit_price") val unitPrice: Double = 0.0,
    @ColumnInfo(name = "total") val total: Double = 0.0,
    @ColumnInfo(name = "category") val category: String? = null
)
