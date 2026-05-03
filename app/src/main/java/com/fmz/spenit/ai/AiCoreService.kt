package com.fmz.spenit.ai

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.fmz.spenit.data.preferences.AppPreferences
import com.fmz.spenit.data.repository.ReceiptRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AiCoreService(
    private val context: Context,
    private val preferences: AppPreferences,
    private val receiptRepository: ReceiptRepository
) {
    companion object {
        private const val AICORE_PACKAGE = "com.google.android.aicore"
        private const val AICORE_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$AICORE_PACKAGE"

        fun isAiCoreAvailable(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun getInstallIntent(): Intent {
            return Intent(Intent.ACTION_VIEW, Uri.parse(AICORE_PLAY_STORE_URL))
        }
    }

    suspend fun extractReceiptData(
        imagePath: String,
        currency: String
    ): OcrResult? {
        return try {
            val ocrText = runMlKitOcr(imagePath)
            if (ocrText != null) {
                parseReceiptText(ocrText, currency)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractPaySlipData(
        imagePath: String,
        currency: String
    ): PaySlipResult? {
        return try {
            val ocrText = runMlKitOcr(imagePath)
            if (ocrText != null) {
                parsePaySlipText(ocrText, currency)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun runMlKitOcr(imagePath: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                val bitmap = try {
                    if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                        val uri = Uri.parse(imagePath)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    } else {
                        BitmapFactory.decodeFile(imagePath)
                    }
                } catch (e: Exception) {
                    BitmapFactory.decodeFile(imagePath)
                }

                if (bitmap == null) return@withContext null

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).await()
                visionText.text
            } catch (e: Exception) {
                null
            }
        }
    }

    fun generateInsights(
        receipts: List<com.fmz.spenit.data.db.entity.Receipt>,
        incomeEntries: List<com.fmz.spenit.data.db.entity.IncomeEntry>,
        periodLabel: String,
        currency: String
    ): AiInsightResult {
        val total = receipts.sumOf { it.total }
        val avgDaily = if (receipts.isNotEmpty()) total / 30.0 else 0.0
        val taxDeductible = receipts.filter { it.isTaxDeductible }.sumOf { it.total }

        val categories = receipts.groupBy { it.category }
            .map { (cat, items) ->
                CategoryBreakdown(
                    category = cat,
                    amount = items.sumOf { it.total },
                    percentage = if (total > 0) (items.sumOf { it.total } / total) * 100 else 0.0
                )
            }
            .sortedByDescending { it.amount }
            .take(5)

        val savingTips = listOf(
            SavingTip(
                title = "Review Subscriptions",
                description = "Check if you're using all active subscriptions. The average person wastes 30% on unused subscriptions."
            ),
            SavingTip(
                title = "Meal Planning",
                description = "Planning weekly meals can reduce food expenses by 20-30% and minimize impulse purchases."
            ),
            SavingTip(
                title = "Track Small Purchases",
                description = "Daily small purchases (coffee, snacks) can add up to significant amounts. Try setting a weekly allowance."
            )
        )

        // Weekly trend based on actual dates in the receipt list
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weeklyTrend = dayNames.map { _ -> 0.0 }.toMutableList()
        for (r in receipts) {
            try {
                val localDate = java.time.LocalDate.parse(r.date)
                val dayIdx = localDate.dayOfWeek.value - 1 // 0=Mon
                weeklyTrend[dayIdx] = weeklyTrend[dayIdx] + r.total
            } catch (_: Exception) { }
        }
        val maxWeekSpend = weeklyTrend.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val normalizedTrend = dayNames.mapIndexed { idx, name ->
            SpendingTrend(
                label = name,
                amount = weeklyTrend[idx],
                normalizedHeight = weeklyTrend[idx] / maxWeekSpend
            )
        }

        val topCategory = categories.firstOrNull()
        val summary = buildString {
            append("Your spending for $periodLabel totals ${com.fmz.spenit.util.CurrencyFormatter.formatInt(total, currency)}. ")
            topCategory?.let {
                append("Your highest category is ${it.category} at ${"%.0f".format(it.percentage)}% of total spending. ")
            }
            if (taxDeductible > 0) {
                append("You have ${com.fmz.spenit.util.CurrencyFormatter.formatInt(taxDeductible, currency)} in tax-deductible expenses. ")
            }
        }

        val keyFindings = listOf(
            "Average daily spending: ${com.fmz.spenit.util.CurrencyFormatter.format(avgDaily, currency)}",
            "Total tracked: ${receipts.size} transactions across ${categories.size} categories"
        )

        return AiInsightResult(
            summary = summary.trim(),
            keyFindings = keyFindings,
            savingTips = savingTips,
            categoryBreakdown = categories,
            weeklyTrend = normalizedTrend,
            taxDeductibleTotal = taxDeductible,
            averageDailySpend = avgDaily
        )
    }

    private fun parseReceiptText(text: String, currency: String): OcrResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val merchant = lines.firstOrNull() ?: "Unknown"

        val amountPattern = Regex("""(?:RM|MYR|USD|SGD|\$|Rp|€|£|¥)?\s*(\d+[\.,]?\d{0,2})\s*$""")
        var total = 0.0
        var taxAmount = 0.0
        val items = mutableListOf<OcrLineItem>()

        for (line in lines.drop(1)) {
            val match = amountPattern.find(line)
            if (match != null) {
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                if (amount > total) {
                    total = amount
                }
                val desc = line.replace(match.value, "").trim()
                if (desc.isNotEmpty()) {
                    items.add(OcrLineItem(description = desc, total = amount, unitPrice = amount))
                }
            }
        }

        taxAmount = total * 0.06

        // Try to find date
        val datePattern = Regex("""(\d{2})[/-](\d{2})[/-](\d{2,4})""")
        var dateStr = ""
        for (line in lines) {
            val dm = datePattern.find(line)
            if (dm != null) {
                val (d, m, y) = dm.destructured
                val year = if (y.length == 2) "20$y" else y
                dateStr = "$year-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
                break
            }
        }

        return OcrResult(
            merchant = merchant,
            date = dateStr,
            total = total,
            taxAmount = taxAmount,
            currency = currency,
            items = items,
            rawText = text,
            confidence = 0.75
        )
    }

    private fun parsePaySlipText(text: String, currency: String): PaySlipResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val employer = lines.firstOrNull() ?: "Employer"

        val amountPattern = Regex("""(?:RM|MYR|USD|SGD|\$|Rp|€|£|¥)?\s*(\d+[.,]?\d{0,2})\s*$""")
        var netPay = 0.0

        for (line in lines) {
            val match = amountPattern.find(line)
            if (match != null) {
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                if (amount > netPay) {
                    netPay = amount
                }
            }
        }

        val datePattern = Regex("""(\d{2})[/-](\d{2})[/-](\d{2,4})""")
        var dateStr = ""
        for (line in lines) {
            val dm = datePattern.find(line)
            if (dm != null) {
                val (d, m, y) = dm.destructured
                val year = if (y.length == 2) "20$y" else y
                dateStr = "$year-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
                break
            }
        }

        return PaySlipResult(
            employer = employer,
            netPay = netPay,
            date = dateStr
        )
    }
}
