package com.fmz.spenitaicore.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.data.preferences.AppPreferences
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

    suspend fun classifyFinancialDocument(imagePath: String): FinancialDocumentType {
        Log.d("AiCoreService", "Starting document classification for: $imagePath")
        val lowerPath = imagePath.lowercase()
        if (lowerPath.endsWith(".csv")) return FinancialDocumentType.BankStatement

        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return FinancialDocumentType.Unknown
            val status = generativeModel.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w("AiCoreService", "Model not available. Status=$status")
                return FinancialDocumentType.Unknown
            }

            val prompt = """
                Classify this financial document into exactly one type:
                - income: payslip, salary slip, wage statement, employer payment advice, income proof
                - expense: receipt, invoice, bill, purchase receipt, tax receipt
                - bank_statement: bank account statement or transaction list with multiple credits/debits/balances

                Choose bank_statement when the document shows account balances, transaction rows, debit/credit columns, or statement period.
                Choose income only when it is primarily a payslip/income document, not a general bank statement.
                Choose expense only when it is primarily a merchant receipt/invoice/bill.

                Return ONLY JSON:
                {"type":"income"}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = generativeModel.generateContent(request)
            val jsonStr = response.candidates.firstOrNull()?.text?.trim()
                ?: return FinancialDocumentType.Unknown
            val jsonObj = parseFirstJsonObject(jsonStr) ?: return FinancialDocumentType.Unknown
            when (jsonObj.stringValue("type", "documentType", "document_type").normalizedDocumentType()) {
                "income" -> FinancialDocumentType.Income
                "expense" -> FinancialDocumentType.Expense
                "bankstatement" -> FinancialDocumentType.BankStatement
                else -> FinancialDocumentType.Unknown
            }
        } catch (e: Exception) {
            Log.e("AiCoreService", "Document classification error", e)
            FinancialDocumentType.Unknown
        }
    }

    suspend fun extractReceiptData(
        imagePath: String,
        currency: String
    ): OcrResult? {
        Log.d("AiCoreService", "Starting receipt extraction for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return null
            val status = generativeModel.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w("AiCoreService", "Model not available. Status=$status")
                return null
            }

            val prompt = """
                Extract from this receipt: merchant, date (YYYY-MM-DD), total, tax, category from [${ExpensesViewModel.SPENDING_CATEGORIES.joinToString(", ")}].
                Return ONLY: {"merchant":"...","date":"...","total":0.0,"currency":"$currency","category":"General","taxAmount":0.0}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = generativeModel.generateContent(request)
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Receipt JSON: $jsonStr")
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject

            val items = jsonObj["items"]?.jsonArray?.map {
                val i = it.jsonObject
                OcrLineItem(
                    description = i["description"]?.jsonPrimitive?.content ?: "",
                    quantity = i["quantity"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                    unitPrice = i["unitPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    total = i["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            } ?: emptyList()

            OcrResult(
                merchant = jsonObj["merchant"]?.jsonPrimitive?.content ?: "Unknown",
                date = jsonObj["date"]?.jsonPrimitive?.content ?: "",
                total = jsonObj["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                taxAmount = jsonObj["taxAmount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                currency = jsonObj["currency"]?.jsonPrimitive?.content ?: currency,
                category = jsonObj["category"]?.jsonPrimitive?.content ?: "General",
                items = items,
                rawText = "",
                confidence = 0.9
            )
        } catch (e: Exception) {
            Log.e("AiCoreService", "Receipt extraction error", e)
            null
        }
    }

    suspend fun extractIncomeData(
        imagePath: String,
        currency: String
    ): PaySlipResult? {
        Log.d("AiCoreService", "Starting income extraction for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return null
            val status = generativeModel.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w("AiCoreService", "Model not available. Status=$status")
                return null
            }

            val prompt = """
                Read this income document. It can be a pay slip or a bank statement.

                Return exactly one income entry as JSON only:
                {"employer":"...","netPay":0.0,"netPayText":"...","date":"YYYY-MM-DD","category":"Salary","notes":"..."}

                Amount rules:
                - Pay slip: use net pay, take-home pay, amount paid, or paid to employee. If there is no net pay, use gross pay.
                - Bank statement: use the amount from one credit/deposit/money-in transaction row. Use the transaction amount, not the running balance, opening balance, closing balance, available balance, total credits, or total debits.
                - Prefer rows/descriptions containing salary, payroll, wages, commission, bonus, dividend, interest, rental, refund, freelance, or business income.
                - The returned netPay must be positive. Return 0.0 only when no income amount is visible at all.
                - netPayText must copy the full visible amount exactly, including all digits and separators. For example, if the document shows 10,412.51, return netPayText "10,412.51" and netPay 10412.51. Do not shorten it to 10.41.

                Category must be one of: ${IncomeSources.All.joinToString(", ")}.
                Use today's date "${DateUtils.today()}" only if the document date cannot be read.
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = generativeModel.generateContent(request)
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Income JSON: $jsonStr")
            val jsonObj = parseFirstJsonObject(jsonStr) ?: return null

            val employer = jsonObj.stringValue("employer", "source", "payer", "company", "description")
            val netPayFromText = jsonObj.amountTextValue(
                "netPayText",
                "net pay text",
                "amountText",
                "amount text",
                "rawAmount",
                "raw amount"
            )
            val netPayFromNumber = jsonObj.amountValue(
                "netPay",
                "net_pay",
                "net pay",
                "takeHomePay",
                "take home pay",
                "amount paid",
                "paid to employee",
                "transactionAmount",
                "transaction amount",
                "creditAmount",
                "credit amount",
                "depositAmount",
                "deposit amount",
                "amount",
                "credit"
            )?.let { kotlin.math.abs(it) }
            val netPay = netPayFromText ?: netPayFromNumber ?: 0.0
            val date = jsonObj.stringValue("date", "transactionDate", "transaction date", "payDate", "pay date")
                .ifBlank { DateUtils.today() }
            val category = jsonObj.stringValue("category", "incomeCategory", "income category").ifBlank { "Salary" }
            val notes = jsonObj.stringValue("notes", "note", "reason").ifBlank { null }

            Log.d("AiCoreService", "Income result: employer=$employer netPay=$netPay date=$date category=$category")
            PaySlipResult(employer = employer, netPay = netPay, date = date, category = category, notes = notes)
        } catch (e: Exception) {
            Log.e("AiCoreService", "Income extraction error", e)
            null
        }
    }

    suspend fun extractBankStatementData(
        imagePath: String,
        currency: String
    ): BankStatementResult? {
        Log.d("AiCoreService", "Starting bank statement extraction for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return null
            val status = generativeModel.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w("AiCoreService", "Model not available. Status=$status")
                return null
            }

            val prompt = """
                Look at this bank statement image. Extract ALL transactions visible.
                For each transaction, extract: date (YYYY-MM-DD), description, amount (positive for credit/income, negative for debit/expense), amountText (the exact visible amount), and type ("credit" or "debit").
                Also extract: bank name, account number (last 4 digits only), statement period.
                Do not use running balance, opening balance, closing balance, or available balance as the transaction amount.
                Return ONLY JSON: {"bankName":"...","accountLast4":"...","period":"...","transactions":[{"date":"YYYY-MM-DD","description":"...","amount":0.0,"amountText":"...","type":"debit"}]}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = generativeModel.generateContent(request)
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Bank statement JSON: ${jsonStr.take(200)}...")
            val jsonObj = parseFirstJsonObject(jsonStr) ?: return null

            val transactions = jsonObj["transactions"]?.jsonArray?.map {
                val t = it.jsonObject
                val type = t.stringValue("type", "transactionType", "transaction type")
                val amountFromText = t.amountTextValue("amountText", "amount text", "rawAmount", "raw amount")
                val amountFromNumber = t.amountValue(
                    "amount",
                    "transactionAmount",
                    "transaction amount",
                    "credit",
                    "debit",
                    "moneyIn",
                    "money in",
                    "moneyOut",
                    "money out"
                )
                val signedAmount = amountFromText ?: amountFromNumber ?: 0.0
                val normalizedType = when {
                    type.equals("credit", ignoreCase = true) -> "credit"
                    type.equals("debit", ignoreCase = true) -> "debit"
                    signedAmount >= 0.0 -> "credit"
                    else -> "debit"
                }
                BankTransaction(
                    date = t.stringValue("date", "transactionDate", "transaction date"),
                    description = t.stringValue("description", "details", "narration", "reference"),
                    amount = if (normalizedType == "credit") kotlin.math.abs(signedAmount) else -kotlin.math.abs(signedAmount),
                    type = normalizedType
                )
            } ?: emptyList()

            BankStatementResult(
                bankName = jsonObj.stringValue("bankName", "bank name", "bank"),
                accountLast4 = jsonObj.stringValue("accountLast4", "account last 4", "accountNumber", "account number"),
                period = jsonObj.stringValue("period", "statementPeriod", "statement period"),
                transactions = transactions
            )
        } catch (e: Exception) {
            Log.e("AiCoreService", "Bank statement extraction error", e)
            null
        }
    }

    /**
     * Loads a bitmap from an image path. Handles JPEG/PNG images and PDFs
     * (renders first page as bitmap).
     */
    private suspend fun loadBitmapFromPath(imagePath: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (isPdfInput(imagePath)) {
                    loadPdfAsBitmap(imagePath)
                } else {
                    loadImageBitmap(imagePath)
                }
            } catch (e: Exception) {
                Log.e("AiCoreService", "Failed to load bitmap", e)
                null
            }
        }
    }

    private fun loadImageBitmap(imagePath: String): Bitmap? {
        return try {
            if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                val uri = Uri.parse(imagePath)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                BitmapFactory.decodeFile(imagePath)
            }
        } catch (e: Exception) {
            BitmapFactory.decodeFile(imagePath)
        }
    }

    private fun loadPdfAsBitmap(pdfPath: String): Bitmap? {
        return try {
            val fd: ParcelFileDescriptor = if (pdfPath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(pdfPath), "r") ?: return null
            } else if (pdfPath.startsWith("file://")) {
                val filePath = Uri.parse(pdfPath).path ?: return null
                ParcelFileDescriptor.open(java.io.File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                ParcelFileDescriptor.open(java.io.File(pdfPath), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)
            val maxPageDimension = maxOf(page.width, page.height).coerceAtLeast(1)
            val scale = (2400f / maxPageDimension).coerceIn(1f, 3f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            bitmap
        } catch (e: Exception) {
            Log.e("AiCoreService", "PDF render error", e)
            null
        }
    }

    private fun isPdfInput(path: String): Boolean {
        val lowerPath = path.lowercase()
        if (lowerPath.endsWith(".pdf")) return true
        if (!path.startsWith("content://")) return false

        return try {
            context.contentResolver.getType(Uri.parse(path)) == "application/pdf"
        } catch (_: Exception) {
            false
        }
    }

    private fun parseFirstJsonObject(text: String): JsonObject? {
        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val candidates = listOfNotNull(
            cleaned,
            cleaned.substringOrNull('{', '}'),
            cleaned.substringOrNull('[', ']')
        ).distinct()

        for (candidate in candidates) {
            try {
                firstJsonObject(json.parseToJsonElement(candidate))?.let { return it }
            } catch (_: Exception) { }
        }

        Log.w("AiCoreService", "Unable to parse income JSON: $text")
        return null
    }

    private fun String.substringOrNull(startChar: Char, endChar: Char): String? {
        val start = indexOf(startChar)
        val end = lastIndexOf(endChar)
        return if (start >= 0 && end > start) substring(start, end + 1) else null
    }

    private fun firstJsonObject(element: JsonElement): JsonObject? {
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            else -> null
        }
    }

    private fun JsonObject.stringValue(vararg keys: String): String {
        return findValue(*keys)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    private fun JsonObject.amountValue(vararg keys: String): Double? {
        val primitive = findValue(*keys)?.jsonPrimitive ?: return null
        return primitive.doubleOrNull
            ?: primitive.contentOrNull?.parseVisibleAmount()
    }

    private fun JsonObject.amountTextValue(vararg keys: String): Double? {
        return findValue(*keys)?.jsonPrimitive?.contentOrNull?.parseVisibleAmount()
    }

    private fun String.parseVisibleAmount(): Double? {
        val amountText = replace(Regex("[^0-9,\\.\\-]"), "")
            .trim(',', '.')
            .ifBlank { return null }

        val lastComma = amountText.lastIndexOf(',')
        val lastDot = amountText.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 && lastComma > lastDot -> {
                amountText.replace(".", "").replace(",", ".")
            }
            lastComma >= 0 && lastDot >= 0 -> {
                amountText.replace(",", "")
            }
            lastComma >= 0 && amountText.length - lastComma - 1 == 2 -> {
                amountText.replace(",", ".")
            }
            else -> amountText.replace(",", "")
        }

        return normalized.toDoubleOrNull()?.let { kotlin.math.abs(it) }
    }

    private fun JsonObject.findValue(vararg keys: String): JsonElement? {
        for (key in keys) {
            this[key]?.let { return it }
        }

        val normalizedKeys = keys.map { it.normalizedJsonKey() }.toSet()
        return entries.firstOrNull { it.key.normalizedJsonKey() in normalizedKeys }?.value
    }

    private fun String.normalizedJsonKey(): String {
        return lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun String.normalizedDocumentType(): String {
        val normalized = normalizedJsonKey()
        return when (normalized) {
            "bank", "bankstatement", "statement", "banktransaction", "transactionlist" -> "bankstatement"
            "receipt", "expense", "invoice", "bill" -> "expense"
            "income", "payslip", "salaryslip", "salary", "paystub", "wagestatement" -> "income"
            else -> normalized
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

}
