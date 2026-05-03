package com.fmz.spenitaicore.ai

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.fmz.spenitaicore.data.preferences.AppPreferences
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray

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

    private val generativeModel = Generation.getClient()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extractReceiptData(
        imagePath: String,
        currency: String
    ): OcrResult? {
        Log.d("AiCoreService", "Starting receipt extraction for: $imagePath")
        return try {
            val ocrText = runMlKitOcr(imagePath) ?: run {
                Log.e("AiCoreService", "OCR failed to extract text from image")
                return null
            }
            Log.d("AiCoreService", "OCR Text extracted: ${ocrText.take(100)}...")
            
            val prompt = """
                Extract receipt information from the following OCR text.
                It is CRITICAL that you identify the:
                1. Merchant Name (the business name)
                2. Date (in YYYY-MM-DD format)
                3. Total Amount (as a decimal number)
                4. Category (MUST be one of: ${ExpensesViewModel.SPENDING_CATEGORIES.joinToString(", ")})
                
                Return ONLY a JSON object with these keys:
                - "merchant": name of the store or provider
                - "date": date in YYYY-MM-DD format
                - "total": total amount as a number
                - "currency": currency code or symbol (use "$currency" as default)
                - "category": the chosen category from the list above
                - "taxAmount": total tax or VAT amount as a number
                - "items": an array of objects with "description", "quantity", "unitPrice", "total"
                
                OCR TEXT:
                $ocrText
            """.trimIndent()

            Log.d("AiCoreService", "Checking model status...")
            val status = generativeModel.checkStatus()
            val statusName = when (status) {
                FeatureStatus.AVAILABLE -> "AVAILABLE"
                FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
                FeatureStatus.DOWNLOADING -> "DOWNLOADING"
                else -> "UNAVAILABLE"
            }
            Log.d("AiCoreService", "Model status: $statusName ($status)")

            if (status != FeatureStatus.AVAILABLE) {
                Log.w("AiCoreService", "Model not available. Current status: $status")
                if (status == FeatureStatus.DOWNLOADABLE) {
                    Log.i("AiCoreService", "Model is downloadable. Starting download...")
                    generativeModel.download().collect { downloadProgress ->
                        Log.d("AiCoreService", "Download progress: $downloadProgress")
                    }
                    Log.i("AiCoreService", "Download process finished. Please try again once status is AVAILABLE.")
                } else if (status == FeatureStatus.DOWNLOADING) {
                    Log.i("AiCoreService", "Model is currently downloading...")
                } else {
                    Log.e("AiCoreService", "Model is UNAVAILABLE on this device.")
                }
                return null
            }
            
            val request = GenerateContentRequest.Builder(TextPart(prompt))
                .build()

            Log.d("AiCoreService", "Generating content with Gemini Nano...")
            val response = generativeModel.generateContent(request)
            Log.d("AiCoreService", "Response received")

            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim() ?: run {
                    Log.e("AiCoreService", "No text found in AI response")
                    return null
                }
            
            Log.d("AiCoreService", "Raw JSON from AI: $jsonStr")
            
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
            
            val items = jsonObj["items"]?.jsonArray?.map { 
                val itemObj = it.jsonObject
                OcrLineItem(
                    description = itemObj["description"]?.jsonPrimitive?.content ?: "",
                    quantity = itemObj["quantity"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                    unitPrice = itemObj["unitPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    total = itemObj["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            } ?: emptyList()

            Log.d("AiCoreService", "Successfully parsed result for: ${jsonObj["merchant"]?.jsonPrimitive?.content}")

            OcrResult(
                merchant = jsonObj["merchant"]?.jsonPrimitive?.content ?: "Unknown",
                date = jsonObj["date"]?.jsonPrimitive?.content ?: "",
                total = jsonObj["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                taxAmount = jsonObj["taxAmount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                currency = jsonObj["currency"]?.jsonPrimitive?.content ?: currency,
                category = jsonObj["category"]?.jsonPrimitive?.content ?: "General",
                items = items,
                rawText = ocrText,
                confidence = 0.9
            )
        } catch (e: Exception) {
            Log.e("AiCoreService", "Error during extraction", e)
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
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        incomeEntries: List<com.fmz.spenitaicore.data.db.entity.IncomeEntry>,
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
            append("Your spending for $periodLabel totals ${com.fmz.spenitaicore.util.CurrencyFormatter.formatInt(total, currency)}. ")
            topCategory?.let {
                append("Your highest category is ${it.category} at ${"%.0f".format(it.percentage)}% of total spending. ")
            }
            if (taxDeductible > 0) {
                append("You have ${com.fmz.spenitaicore.util.CurrencyFormatter.formatInt(taxDeductible, currency)} in tax-deductible expenses. ")
            }
        }

        val keyFindings = listOf(
            "Average daily spending: ${com.fmz.spenitaicore.util.CurrencyFormatter.format(avgDaily, currency)}",
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
