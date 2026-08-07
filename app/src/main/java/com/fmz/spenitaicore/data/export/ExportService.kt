package com.fmz.spenitaicore.data.export

import android.content.Context
import android.util.Log
import com.fmz.spenitaicore.data.db.entity.IncomeEntry
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.data.repository.IncomeRepository
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportService(
    private val context: Context,
    private val receiptRepository: ReceiptRepository,
    private val incomeRepository: IncomeRepository
) {
    companion object {
        private const val TAG = "ExportService"
    }

    data class ExportResult(
        val zipPath: String,
        val receiptCount: Int,
        val totalTaxRelief: Double,
        val currency: String
    )

    data class FullExportResult(
        val zipPath: String,
        val receiptCount: Int,
        val incomeCount: Int,
        val currency: String
    )

    /**
     * Export all tax-deductible expenses for a given year as a ZIP file.
     * The ZIP contains:
     *   - summary.csv — tabular breakdown of all tax-relief expenses
     *   - images/ — copies of all original documents (receipts/invoices)
     *
     * @param taxYear  e.g. "2025"
     * @return ExportResult with path to the ZIP and summary stats, or null on failure
     */
    suspend fun exportTaxRelief(taxYear: String): ExportResult? {
        val receipts = receiptRepository.getTaxReceipts(taxYear)
        if (receipts.isEmpty()) return null

        val currency = receipts.first().currency

        // Create temp export directory
        val exportDir = File(context.cacheDir, "tax_export_$taxYear")
        exportDir.deleteRecursively()
        exportDir.mkdirs()

        try {
            // 1. Generate summary CSV
            val csvFile = File(exportDir, "summary.csv")
            writeCsv(csvFile, receipts)

            // 2. Copy original documents
            val imagesDir = File(exportDir, "images")
            imagesDir.mkdirs()
            var copiedCount = 0

            receipts.forEach { receipt ->
                receipt.imagePath?.let { path ->
                    val source = File(path)
                    if (source.exists()) {
                        val ext = source.extension.ifEmpty { "jpg" }
                        val destName = "${receipt.id}_${sanitizeFilename(receipt.merchant)}.$ext"
                        try {
                            source.copyTo(File(imagesDir, destName), overwrite = true)
                            copiedCount++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to copy attachment for receipt ${receipt.id}", e)
                        }
                    }
                }
            }

            // 3. Create ZIP
            val zipFile = File(context.cacheDir, "TaxRelief_${taxYear}.zip")
            createZip(exportDir, zipFile)

            // Clean up temp directory
            exportDir.deleteRecursively()

            val totalTaxRelief = receipts.sumOf { it.taxAmount }

            Log.i(TAG, "Exported $taxYear: ${receipts.size} receipts, " +
                    "$copiedCount attachments, total tax relief: $totalTaxRelief")

            return ExportResult(
                zipPath = zipFile.absolutePath,
                receiptCount = receipts.size,
                totalTaxRelief = totalTaxRelief,
                currency = currency
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for year $taxYear", e)
            exportDir.deleteRecursively()
            return null
        }
    }

    private fun writeCsv(file: File, receipts: List<Receipt>) {
        FileOutputStream(file).bufferedWriter().use { writer ->
            // Header
            writer.write("Date,Merchant,Category,Amount,Tax Amount,Tax Category,Notes,Has Attachment")
            writer.newLine()

            receipts.forEach { r ->
                val hasAttachment = r.imagePath?.let { File(it).exists() } == true
                val row = listOf(
                    r.date,
                    escapeCsv(r.merchant),
                    escapeCsv(r.category),
                    "%.2f".format(r.total),
                    "%.2f".format(r.taxAmount),
                    escapeCsv(r.taxCategory ?: ""),
                    escapeCsv(r.notes ?: ""),
                    if (hasAttachment) "Yes" else "No"
                )
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
    }

    /**
     * Export every receipt and income entry as CSV files inside a ZIP.
     */
    suspend fun exportAllData(): FullExportResult? {
        val receipts = receiptRepository.getReceiptsFromSync(null)
        val incomes = incomeRepository.getIncomeEntriesFromSync(null)
        if (receipts.isEmpty() && incomes.isEmpty()) return null

        val currency = receipts.firstOrNull()?.currency
            ?: incomes.firstOrNull()?.currency
            ?: "$"

        val exportDir = File(context.cacheDir, "full_export")
        exportDir.deleteRecursively()
        exportDir.mkdirs()

        try {
            writeReceiptsCsv(File(exportDir, "receipts.csv"), receipts)
            writeIncomeCsv(File(exportDir, "income.csv"), incomes)

            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val zipFile = File(context.cacheDir, "SpenIt_Export_$stamp.zip")
            createZip(exportDir, zipFile)
            exportDir.deleteRecursively()

            Log.i(TAG, "Full export: ${receipts.size} receipts, ${incomes.size} income entries")
            return FullExportResult(
                zipPath = zipFile.absolutePath,
                receiptCount = receipts.size,
                incomeCount = incomes.size,
                currency = currency
            )
        } catch (e: Exception) {
            Log.e(TAG, "Full export failed", e)
            exportDir.deleteRecursively()
            return null
        }
    }

    private fun writeReceiptsCsv(file: File, receipts: List<Receipt>) {
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write(
                "Date,Merchant,Category,Total,Currency,Tax Amount,Tax Year,Tax Category,Notes,Tags"
            )
            writer.newLine()
            receipts.forEach { r ->
                val row = listOf(
                    r.date,
                    escapeCsv(r.merchant),
                    escapeCsv(r.category),
                    "%.2f".format(r.total),
                    r.currency,
                    "%.2f".format(r.taxAmount),
                    r.taxYear ?: "",
                    escapeCsv(r.taxCategory ?: ""),
                    escapeCsv(r.notes ?: ""),
                    escapeCsv(r.tagsJson)
                )
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
    }

    private fun writeIncomeCsv(file: File, incomes: List<IncomeEntry>) {
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("Date,Source,Category,Amount,Currency,Notes,Recurring,From Bank Import")
            writer.newLine()
            incomes.forEach { e ->
                val row = listOf(
                    e.date,
                    escapeCsv(e.source),
                    escapeCsv(e.category),
                    "%.2f".format(e.amount),
                    e.currency,
                    escapeCsv(e.notes ?: ""),
                    if (e.isRecurring) "Yes" else "No",
                    if (e.isFromBankImport) "Yes" else "No"
                )
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
    }

    private fun createZip(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceDir).path
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(" ", "_")
            .take(50)
    }
}
