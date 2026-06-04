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
import android.os.PowerManager
import android.util.Log
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.data.preferences.AppPreferences
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AiCoreService(
    private val context: Context,
    private val preferences: AppPreferences,
    private val receiptRepository: ReceiptRepository
) {
    private val remoteAiService = RemoteAiService(context)

    // ── Remote provider helpers ──────────────────────────────────────────

    /** Check if a remote AI API key is configured. */
    private suspend fun isRemoteConfigured(): Boolean {
        return preferences.hasAiApiKey() && preferences.getAiProvider() != "aicore"
    }

    /** Get the remote provider config for API calls. */
    private suspend fun remoteProvider(): String? {
        val p = preferences.getAiProvider()
        return if (p != "aicore") p else null
    }

    /** Try remote AI. Returns null if not configured, offline, or request fails. */
    private suspend fun tryRemoteVision(imageBitmap: Bitmap, prompt: String): String? {
        if (!isRemoteConfigured() || !remoteAiService.isOnline()) return null
        val provider = preferences.getAiProvider()
        val key = preferences.getAiApiKey()
        val model = preferences.getAiModel()
        val customUrl = preferences.getAiCustomUrl()
        if (key.isBlank()) return null
        return remoteAiService.sendVisionRequest(provider, key, model, imageBitmap, prompt, customUrl)
    }

    /** Try remote AI text-only. Returns null if not configured, offline, or fails. */
    private suspend fun tryRemoteText(prompt: String): String? {
        if (!isRemoteConfigured() || !remoteAiService.isOnline()) return null
        val provider = preferences.getAiProvider()
        val key = preferences.getAiApiKey()
        val model = preferences.getAiModel()
        val customUrl = preferences.getAiCustomUrl()
        if (key.isBlank()) return null
        return remoteAiService.sendTextRequest(provider, key, model, prompt, customUrl)
    }


    /**
     * Parse remote AI JSON response into OcrResult.
     */
    private fun parseJsonToOcrResult(jsonStr: String, currency: String): OcrResult? {
        return try {
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
            null
        }
    }


    /**
     * Parse remote AI JSON response into PaySlipResult.
     */
    private fun parseJsonToPaySlipResult(jsonStr: String): PaySlipResult? {
        return try {
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
            val employer = jsonObj.stringValue("employer", "source", "payer", "company", "description")
            val netPayFromText = jsonObj.amountTextValue(
                "netPayText", "net pay text", "amountText", "amount text", "rawAmount", "raw amount"
            )
            val netPayFromNumber = jsonObj.amountValue(
                "netPay", "net_pay", "net pay", "takeHomePay", "take home pay",
                "amount paid", "paid to employee", "transactionAmount", "transaction amount",
                "creditAmount", "credit amount", "depositAmount", "deposit amount",
                "amount", "credit"
            )?.let { kotlin.math.abs(it) }
            val netPay = netPayFromText ?: netPayFromNumber ?: 0.0
            val date = jsonObj.stringValue("date", "transactionDate", "transaction date", "payDate", "pay date")
                .ifBlank { com.fmz.spenitaicore.util.DateUtils.today() }
            val category = jsonObj.stringValue("category", "incomeCategory", "income category").ifBlank { "Salary" }
            val notes = jsonObj.stringValue("notes", "note", "reason").ifBlank { null }
            PaySlipResult(employer = employer, netPay = netPay, date = date, category = category, notes = notes)
        } catch (e: Exception) {
            null
        }
    }

    /** Try remote AI for bank statement extraction across multiple pages. */
    private suspend fun tryRemoteBankStatement(
        imagePath: String,
        isPdf: Boolean,
        renderer: PdfRenderer?,
        pageCount: Int
    ): BankStatementResult? {
        if (!isRemoteConfigured() || !remoteAiService.isOnline()) {
            Log.d(TAG, "Remote AI not configured or offline, skipping bank statement remote path")
            return null
        }
        val provider = preferences.getAiProvider()
        val key = preferences.getAiApiKey()
        val model = preferences.getAiModel()
        val customUrl = preferences.getAiCustomUrl()
        if (key.isBlank()) return null

        val prompt = """You are a bank statement parser. Extract all transaction data from this bank statement page.
Return ONLY valid JSON with no markdown, no explanation, no extra text.

Extract ALL transaction rows. Each transaction should have: date (YYYY-MM-DD), description, amount (positive for credit, negative for debit), type ("credit" or "debit").

IMPORTANT: Look at sign prefixes/suffixes on amounts:
- Amount with "+" before or after (e.g. "+100.00" or "100.00+") → positive credit (income)
- Amount with "-" before or after (e.g. "-50.00" or "50.00-") → negative debit (expense)
- Parenthetical amounts "(50.00)" → negative debit (expense)

Handle these date formats:
- DD/MM/YY or DD/MM/YYYY
- D MMM YYYY or DD MMM YYYY (e.g., "1 Mar 2026")
- DD Mon or D Mon (day + abbreviated month, infer year from statement period)
- DDMmmYYYY (no separators, e.g., "27Mar2026")

Skip: balance rows, summary rows, column headers, daily interest below RM 1.00, internal pocket transfers.

Return format only (strict JSON, no markdown):
{
  "bankName": "Bank Name",
  "accountLast4": "1234",
  "period": "Mar 2026",
  "transactions": [
    {"date":"2026-03-01","description":"Something","amount":-69.00,"type":"debit"}
  ]
}
"""

        val allTransactions = mutableListOf<BankTransaction>()
        var bankName = ""
        var accountLast4 = ""
        var period = ""

        for (i in 0 until pageCount) {
            Log.d(TAG, "Remote AI bank statement page ${i + 1}/$pageCount")
            val bitmap = getPageBitmap(imagePath, isPdf, renderer, i)
            if (bitmap == null) {
                Log.w(TAG, "Could not render or load page ${i + 1}")
                continue
            }
            try {
                val response = remoteAiService.sendVisionRequest(provider, key, model, bitmap, prompt, customUrl)
                if (response == null) {
                    Log.w(TAG, "Remote AI returned null for page ${i + 1}")
                    continue
                }
                Log.d(TAG, "Remote AI page ${i + 1} response: ${response.take(300)}")

                try {
                    val root = json.parseToJsonElement(response).jsonObject
                    val transactions = root.arrayValue("transactions", "transaction", "entries", "rows", "items", "statementLines")
                    if (transactions != null) {
                        allTransactions += transactions.mapNotNull { (it as? JsonObject)?.toBankTransaction() }
                            .filter { it.amount != 0.0 }
                    }
                    if (bankName.isEmpty()) bankName = root.stringValue("bankName", "bank name", "bank")
                    if (accountLast4.isEmpty()) accountLast4 = root.stringValue("accountLast4", "account last 4", "accountNumber", "account number")
                    if (period.isEmpty()) period = root.stringValue("period", "statementPeriod", "statement period")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse remote AI page ${i + 1}", e)
                }
            } finally {
                bitmap.recycle()
            }
        }

        if (allTransactions.isEmpty()) return null

        val deduped = allTransactions.distinctBy { "${it.date}|${it.amount}|${it.description.take(40)}" }
        Log.d(TAG, "Remote AI total: ${deduped.size} transactions (${allTransactions.size} raw)")
        return BankStatementResult(bankName = bankName, accountLast4 = accountLast4, period = period, transactions = deduped)
    }

    companion object {
        private const val AICORE_PACKAGE = "com.google.android.aicore"
        private const val AICORE_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$AICORE_PACKAGE"
        private const val TAG = "AiCoreService"

        private const val CLASSIFY_PROMPT = """Analyze this financial document image and classify it into exactly one type.
Valid types are: "income", "expense", or "bankstatement".

Rules:
1. "income": income document, salary statement, wage statement, employer payment advice, income proof.
2. "expense": expense receipt, invoice, bill, purchase receipt, tax receipt.
3. "bankstatement": bank account statement or transaction list with multiple credits/debits/balances.
4. If the document shows a single primary amount with a leading sign, use the sign: "+" means "income", "-" means "expense".
   If no leading sign, assume "expense" unless clearly a bank statement.

Return ONLY a JSON object: {"type":"income"}
"""

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

    enum class AiCoreSupportStatus {
        AVAILABLE,
        DOWNLOADABLE,
        NOT_SUPPORTED
    }

    suspend fun checkAiCoreSupportStatus(): AiCoreSupportStatus {
        val candidates = listOf(previewFullModel, previewFastModel, stableModel)
        var hasDownloadable = false
        for (model in candidates) {
            try {
                val status = model.checkStatus()
                if (status == FeatureStatus.AVAILABLE) {
                    return AiCoreSupportStatus.AVAILABLE
                }
                if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                    hasDownloadable = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model status check failed: ${e.message}")
            }
        }
        if (hasDownloadable) return AiCoreSupportStatus.DOWNLOADABLE
        if (isAiCoreAvailable(context)) {
            return AiCoreSupportStatus.DOWNLOADABLE
        }
        return AiCoreSupportStatus.NOT_SUPPORTED
    }

    private val previewFullModel = Generation.getClient(
        generationConfig {
            modelConfig = modelConfig {
                releaseStage = ModelReleaseStage.PREVIEW
                preference = ModelPreference.FULL
            }
        }
    )
    private val previewFastModel = Generation.getClient(
        generationConfig {
            modelConfig = modelConfig {
                releaseStage = ModelReleaseStage.PREVIEW
                preference = ModelPreference.FAST
            }
        }
    )
    private val stableModel = Generation.getClient()

    private val json = Json { ignoreUnknownKeys = true }
    private val inferenceMutex = Mutex()

    suspend fun classifyFinancialDocument(imagePath: String): FinancialDocumentType {
        Log.d(TAG, "Starting document classification for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return FinancialDocumentType.Unknown

            // ── Remote provider path (configured + online) ────────────────
            val remoteResult = tryRemoteVision(bitmap, CLASSIFY_PROMPT)
            if (remoteResult != null) {
                Log.d(TAG, "Remote classify result: ${remoteResult.take(200)}")
                val jsonObj = parseFirstJsonObject(remoteResult)
                val type = jsonObj?.stringValue("type", "documentType", "document_type")
                    ?.normalizedDocumentType()
                val result = when (type) {
                    "income" -> FinancialDocumentType.Income
                    "expense" -> FinancialDocumentType.Expense
                    "bankstatement" -> FinancialDocumentType.BankStatement
                    else -> FinancialDocumentType.Unknown
                }
                if (result != FinancialDocumentType.Unknown) {
                    Log.d(TAG, "Remote classify: $result")
                    return result
                }
                // If remote returned unknown, fall through to AICore
            }

            // ── AICore fallback ──────────────────────────────────────────
            val model = getAvailableModel().model ?: return FinancialDocumentType.Unknown

            val prompt = CLASSIFY_PROMPT

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = inferenceMutex.withLock { model.generateContent(request) }
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
            Log.e(TAG, "Document classification error", e)
            FinancialDocumentType.Unknown
        }
    }

    suspend fun extractReceiptData(
        imagePath: String,
        currency: String
    ): OcrResult? {
        Log.d(TAG, "Starting expense extraction for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return null

            // ── Remote provider path ──────────────────────────────────
            val prompt = """Analyze this expense document image and extract the following information.
Rules:
1. merchant: The name of the store or business.
2. date: Transaction date in YYYY-MM-DD format.
   IMPORTANT - Handle date format ambiguity:
   - Expense documents usually show day first (DD/MM/YYYY or DD-MM-YYYY), especially outside US
   - Look at the date: if day value > 12, it's definitely DD/MM format
   - If month value > 12, it's definitely MM/DD format
   - Textual dates like "15 Jan 2025" or "Jan 15, 2025" are unambiguous - use the text
   - When ambiguous (both day and month ≤ 12), prefer DD/MM/YYYY for non-US expense documents
   - Examples: "15/03/2025" → "2025-03-15", "03/15/2025" (US) → "2025-03-15", "01/02/2025" → prefer "2025-02-01"
3. total: The total amount paid as a number.
4. tax: The tax amount as a number.
5. category: Best match from this list: [${ExpensesViewModel.SPENDING_CATEGORIES.joinToString(", ")}].

Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
{"merchant":"...","date":"...","total":0.0,"currency":"$currency","category":"General","taxAmount":0.0}
"""

            val remoteResult = tryRemoteVision(bitmap, prompt)
            if (remoteResult != null) {
                Log.d(TAG, "Remote expense result: ${remoteResult.take(300)}")
            val parsedOcr = parseJsonToOcrResult(remoteResult, currency)
            if (parsedOcr != null) { Log.d(TAG, "Remote expense parse OK"); return parsedOcr }
            }

            // ── AICore fallback ──────────────────────────────────────
            val model = getAvailableModel().model ?: return null

            val aicorePrompt = """Analyze this expense document image and extract the following information.
Rules:
1. merchant: The name of the store or business.
2. date: Transaction date in YYYY-MM-DD format.
                   IMPORTANT - Handle date format ambiguity:
                   - Expense documents usually show day first (DD/MM/YYYY or DD-MM-YYYY), especially outside US
                   - Look at the date: if day value > 12, it's definitely DD/MM format
                   - If month value > 12, it's definitely MM/DD format
                   - Textual dates like "15 Jan 2025" or "Jan 15, 2025" are unambiguous - use the text
                   - When ambiguous (both day and month ≤ 12), prefer DD/MM/YYYY for non-US expense documents
                   - Examples: "15/03/2025" → "2025-03-15", "03/15/2025" (US) → "2025-03-15", "01/02/2025" → prefer "2025-02-01"
                3. total: The total amount paid as a number.
                4. tax: The tax amount as a number.
                5. category: Best match from this list: [${ExpensesViewModel.SPENDING_CATEGORIES.joinToString(", ")}].

                Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
                {"merchant":"...","date":"...","total":0.0,"currency":"$currency","category":"General","taxAmount":0.0}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(aicorePrompt)
            ).build()

            val response = inferenceMutex.withLock { model.generateContent(request) }
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Expense JSON: $jsonStr")
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
            val model = getAvailableModel().model ?: return null

            val prompt = """
                Analyze this income document (pay slip or bank statement).

                Rules for extraction:
                1. employer: Name of the company, payer, or source.
                2. netPay: For pay slips, use net pay, take-home pay, or amount paid. For bank statements, use the amount from one credit/deposit/money-in transaction row (not the running balance). Must be a positive number. Return 0.0 if not found.
                3. netPayText: The exact text of the netPay amount, including all commas and decimals (e.g. "10,412.51").
                4. date: Date in YYYY-MM-DD format. Use "${DateUtils.today()}" if cannot be read.
                   IMPORTANT - Handle date format ambiguity:
                   - Payslips usually show day first (DD/MM/YYYY), especially outside US
                   - Look for context: if pay period shows "01/01/2025 - 31/01/2025", it's DD/MM format
                   - If day value > 12 in any date, it's definitely DD/MM format
                   - Textual dates like "15 Jan 2025" are unambiguous
                   - When ambiguous (both ≤ 12), prefer DD/MM/YYYY for non-US documents
                5. category: Best match from: ${IncomeSources.All.joinToString(", ")}.
                6. notes: Any additional reason or description.

                Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
                {"employer":"...","netPay":0.0,"netPayText":"...","date":"YYYY-MM-DD","category":"Salary","notes":"..."}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = inferenceMutex.withLock { model.generateContent(request) }
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
    ): BankStatementResult {
        Log.d("AiCoreService", "Starting bank statement extraction for: $imagePath")

        val isPdf = isPdfInput(imagePath)
        var pdfRenderer: PdfRenderer? = null
        var fileDescriptor: ParcelFileDescriptor? = null
        var pageCount = 0

        try {
            withContext(Dispatchers.IO) {
                if (isPdf) {
                    try {
                        fileDescriptor = when {
                            imagePath.startsWith("content://") ->
                                context.contentResolver.openFileDescriptor(Uri.parse(imagePath), "r")
                            imagePath.startsWith("file://") -> {
                                val path = Uri.parse(imagePath).path
                                if (path != null) ParcelFileDescriptor.open(java.io.File(path), ParcelFileDescriptor.MODE_READ_ONLY) else null
                            }
                            else -> ParcelFileDescriptor.open(java.io.File(imagePath), ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                        val fd = fileDescriptor
                        if (fd != null) {
                            val renderer = PdfRenderer(fd)
                            pdfRenderer = renderer
                            pageCount = minOf(renderer.pageCount, 20)
                        }
                    } catch (e: Exception) {
                        Log.e("AiCoreService", "Failed to open PDF renderer for $imagePath", e)
                    }
                } else {
                    val testBitmap = loadImageBitmap(imagePath)
                    if (testBitmap != null) {
                        pageCount = 1
                        testBitmap.recycle()
                    }
                }
            }

            if (pageCount == 0) {
                Log.w("AiCoreService", "No pages or valid image loaded from $imagePath")
                return BankStatementResult(errorMessage = "Could not load image/PDF. File may be unsupported or corrupted.")
            }

            // ── Remote AI path (if API key configured + online) ─────────────
            val remoteResult = tryRemoteBankStatement(imagePath, isPdf, pdfRenderer, pageCount)
            if (remoteResult != null) {
                Log.d(TAG, "Remote AI returned ${remoteResult.transactions.size} transactions for bank statement")
                if (remoteResult.transactions.size >= 3) {
                    return remoteResult
                }
                // If remote found too few, fall through to OCR
            }

            // ── If remote AI is configured but offline/failed → try OCR directly ─
            // Gemini Nano AI is skipped for bank statements because its output context
            // is too limited to return complete transaction data, even for one page.
            // ML Kit OCR + regex is less accurate but more reliable for this use case.
            Log.d("AiCoreService", "Remote AI not available — using ML Kit OCR + regex for bank statement")
            val textTransactions = mutableListOf<BankTransaction>()
            for (i in 0 until pageCount) {
                Log.d("AiCoreService", "OCR page ${i + 1}/$pageCount")
                val bitmap = getPageBitmap(imagePath, isPdf, pdfRenderer, i)
                if (bitmap != null) {
                    try {
                        val lines = BankStatementParser.extractLines(bitmap)
                        Log.d("AiCoreService", "OCR page ${i + 1}: ${lines.size} lines extracted")
                        textTransactions += BankStatementParser.parseLines(lines)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            val textDeduped = textTransactions.distinctBy { "${it.date}|${it.amount}|${it.description.take(40)}" }
            Log.d("AiCoreService", "OCR+regex: ${textDeduped.size} transactions found")

            val hintMessage = if (!isRemoteConfigured()) {
                "Tip: Configure an AI API key in Settings for more accurate bank statement extraction."
            } else null

            if (textDeduped.isNotEmpty()) {
                return BankStatementResult(transactions = textDeduped, hintMessage = hintMessage)
            } else {
                return BankStatementResult(errorMessage = "Could not extract transactions. Try a clearer image or different file.")
            }
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    pdfRenderer?.close()
                } catch (e: Exception) {
                    Log.e("AiCoreService", "Error closing PdfRenderer", e)
                }
                try {
                    fileDescriptor?.close()
                } catch (e: Exception) {
                    Log.e("AiCoreService", "Error closing FileDescriptor", e)
                }
            }
        }
    }

    private suspend fun extractPageTransactions(
        model: GenerativeModel,
        bitmap: Bitmap
    ): BankStatementResult? {
        return try {
            val prompt = buildPagePrompt()
            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = inferenceMutex.withLock { model.generateContent(request) }
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Page JSON (first 300): ${jsonStr.take(300)}")

            // Try primary parse
            val parsed = parsePageResponse(jsonStr)
            if (parsed != null) return parsed

            // ── Retry with minimal prompt on parse failure (likely truncation) ─
            Log.d("AiCoreService", "Primary parse failed, retrying with minimal prompt")
            val retryPrompt = """
                Extract all transactions from this bank statement page image.
                Return ONLY JSON: {"bankName":"","accountLast4":"","period":"","transactions":[{"date":"YYYY-MM-DD","description":"","amount":0.0,"type":"credit|debit"}]}
                Date: DD/MM/YY or D MMM YYYY or DDMmmYYYY. Amount: + is credit, - is debit. Skip: balance, summary, headers, tiny interest.
            """.trimIndent()

            val retryRequest = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(retryPrompt)
            ).build()

            val retryResponse = inferenceMutex.withLock { model.generateContent(retryRequest) }
            val retryJson = retryResponse.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Retry JSON (first 300): ${retryJson.take(300)}")
            parsePageResponse(retryJson)
        } catch (e: Exception) {
            Log.e("AiCoreService", "Page extraction error", e)
            null
        }
    }

    /** Build compact extraction prompt to minimize token usage. */
    private fun buildPagePrompt(): String {
        return """
            Extract ALL transactions from this bank statement page.
            Return ONLY valid JSON — no markdown, no explanation.

            METADATA:
            - bankName: bank from logo/header. Known: Maybank, CIMB, Public Bank, HSBC, RHB, UOB, OCBC,
              Hong Leong, Affin, AmBank, Ryt Bank, GX Bank, Bank Islam, BSN, YTL Digital
            - accountLast4: last 4 digits of account (remove dashes/spaces first)
            - period: statement period from header (e.g. "Mar 2026", "01/03/2026 - 31/03/2026")

            DATES → YYYY-MM-DD:
            - DD/MM/YY → 2026-03-01   DDMmmYYYY → 27Mar2026
            - D MMM YYYY → 1 Mar 2026   DD Mon (no year) → infer from statement period
            - MMM DD YYYY → Apr 1 2026
            - If no date on a row, use last seen date
            - Month: Jan=01, Feb=02, Mar=03, Apr=04, May=05, Jun=06, Jul=07, Aug=08, Sep=09, Oct=10, Nov=11, Dec=12

            SKIP: balance rows, summary rows, column headers, page totals, daily interest < RM1.00,
            internal pocket transfers. Do NOT include "Opening balance", "Closing balance", "BALANCE B/F".

            AMOUNTS → just the transaction amount (not the running balance):
            - Separate credit/debit columns → use the correct column
            - Trailing sign: "300.00+" → +300.00 (credit), "69.00-" → -69.00 (debit)
            - Leading sign: "+1,280" → credit, "-30.00" → debit
            - CR/DR suffix: "100.00 CR" → credit, "100.00 DR" → debit
            - Parenthetical "(50.00)" → debit
            - Strip commas and currency symbols (RM, MYR)

            DESCRIPTIONS: merge all sub-lines into one string. Remove Ref. IDs, card numbers,
            reference codes. Keep merchant, "SALE DEBIT", "IBK FUND TFR", "Salary", merchant names.

            TYPE: positive → "credit" (income), negative → "debit" (expense)

            OUTPUT FORMAT:
            {"bankName":"","accountLast4":"","period":"","transactions":[{"date":"2026-03-01","description":"GRABPAY SALE DEBIT","amount":-69.00,"type":"debit"},{"date":"2026-03-10","description":"SALARY","amount":10412.51,"type":"credit"}]}
        """.trimIndent()
    }

    /** Parse the AI response into a BankStatementResult. Returns null if JSON is invalid. */
    private fun parsePageResponse(jsonStr: String): BankStatementResult? {
        return try {
            val jsonElement = parseFirstJsonElement(jsonStr) ?: return null
            val jsonObj = jsonElement as? JsonObject
            val transactionElements = when (jsonElement) {
                is JsonArray -> jsonElement
                is JsonObject -> jsonElement.arrayValue(
                    "transactions", "transaction", "entries", "rows", "items", "statementLines"
                )
                else -> null
            }

            val transactions = transactionElements?.mapNotNull { element ->
                (element as? JsonObject)?.toBankTransaction()
            }?.filter { it.amount != 0.0 } ?: emptyList()

            BankStatementResult(
                bankName = jsonObj?.stringValue("bankName", "bank name", "bank").orEmpty(),
                accountLast4 = jsonObj?.stringValue("accountLast4", "account last 4", "accountNumber", "account number").orEmpty(),
                period = jsonObj?.stringValue("period", "statementPeriod", "statement period").orEmpty(),
                transactions = transactions
            )
        } catch (e: Exception) {
            Log.e("AiCoreService", "Page JSON parse error", e)
            null
        }
    }

    private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int): Bitmap {
        val page = renderer.openPage(pageIndex)
        val maxDim = maxOf(page.width, page.height).coerceAtLeast(1)
        val scale = (2048f / maxDim).coerceIn(1f, 3f)
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bm).drawColor(Color.WHITE)
        page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bm
    }

    private fun getPageBitmap(
        imagePath: String,
        isPdf: Boolean,
        renderer: PdfRenderer?,
        index: Int
    ): Bitmap? {
        return if (isPdf) {
            if (renderer != null) renderPdfPage(renderer, index) else null
        } else {
            loadImageBitmap(imagePath)
        }
    }

    private data class ModelResult(val model: GenerativeModel? = null, val errorMessage: String? = null)

    private suspend fun getAvailableModel(): ModelResult {
        val candidates = listOf(
            "preview_full" to previewFullModel,
            "preview_fast" to previewFastModel,
            "stable_default" to stableModel
        )

        var anyDownloading = false
        var downloadTriggered = false

        for ((name, model) in candidates) {
            val status = try {
                model.checkStatus()
            } catch (e: Exception) {
                Log.w("AiCoreService", "Model status check failed for $name: ${e.message}")
                continue
            }

            Log.e("AiCoreService", "AICore model $name status=$status")

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    val baseModelName = try { model.getBaseModelName() } catch (_: Exception) { name }
                    Log.e("AiCoreService", "Using AICore model: $name ($baseModelName)")
                    return ModelResult(model = model)
                }
                FeatureStatus.DOWNLOADABLE -> {
                    if (!downloadTriggered) {
                        downloadTriggered = true
                        model.download()
                            .onEach { Log.e("AiCoreService", "Model $name download progress: $it") }
                            .catch { Log.w("AiCoreService", "Model $name download error", it) }
                            .launchIn(CoroutineScope(Dispatchers.IO))
                        Log.e("AiCoreService", "Triggered download for model $name")
                    }
                }
                FeatureStatus.DOWNLOADING -> anyDownloading = true
                else -> Log.e("AiCoreService", "Model $name unavailable (status=$status)")
            }
        }

        val msg = when {
            anyDownloading || downloadTriggered ->
                "AI model is downloading in the background. Please try again in a few minutes."
            else ->
                "AI model is not supported on this device."
        }
        Log.w("AiCoreService", "No AICore model ready: $msg")
        return ModelResult(errorMessage = msg)
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
        val raw = try {
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
        } ?: return null
        val maxDim = maxOf(raw.width, raw.height)
        if (maxDim <= 2048) return raw
        val scale = 2048f / maxDim
        val scaled = Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== raw) raw.recycle()
        return scaled
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
        if (path.startsWith("content://")) {
            return try {
                context.contentResolver.getType(Uri.parse(path)) == "application/pdf"
            } catch (_: Exception) {
                false
            }
        }
        return try {
            java.io.FileInputStream(path).use { fis ->
                val header = ByteArray(4)
                fis.read(header) == 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte()
                    && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseFirstJsonObject(text: String): JsonObject? {
        return parseFirstJsonElement(text)?.let { firstJsonObject(it) }
    }

    private fun parseFirstJsonElement(text: String): JsonElement? {
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
                return json.parseToJsonElement(candidate)
            } catch (_: Exception) { }
        }

        Log.w("AiCoreService", "Unable to parse JSON: $text")
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

    private fun JsonObject.arrayValue(vararg keys: String): JsonArray? {
        return findValue(*keys) as? JsonArray
    }

    private fun JsonObject.toBankTransaction(): BankTransaction {
        val type = stringValue("type", "transactionType", "transaction type", "drCr", "debitCredit")
        val credit = amountTextValue("creditText", "credit text") ?: amountValue("credit", "moneyIn", "money in", "deposit", "deposits")
        val debit = amountTextValue("debitText", "debit text") ?: amountValue("debit", "moneyOut", "money out", "withdrawal", "withdrawals")
        val amountFromText = amountTextValue("amountText", "amount text", "rawAmount", "raw amount")
        val amountFromNumber = amountValue("amount", "transactionAmount", "transaction amount", "value")
        val signedAmount = when {
            credit != null && credit > 0.0 -> credit
            debit != null && debit > 0.0 -> -debit
            else -> amountFromText ?: amountFromNumber ?: 0.0
        }
        val normalizedType = when {
            type.contains("credit", ignoreCase = true) || type.equals("cr", ignoreCase = true) -> "credit"
            type.contains("debit", ignoreCase = true) || type.equals("dr", ignoreCase = true) -> "debit"
            signedAmount > 0.0 -> "credit"
            signedAmount < 0.0 -> "debit"
            else -> "debit"
        }

        return BankTransaction(
            date = stringValue("date", "transactionDate", "transaction date", "postingDate", "valueDate"),
            description = stringValue("description", "details", "narration", "reference", "particulars", "transactionDetails", "transaction details"),
            amount = if (normalizedType == "credit") kotlin.math.abs(signedAmount) else -kotlin.math.abs(signedAmount),
            type = normalizedType
        )
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
        val negative = trim().startsWith("-")
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

        val absolute = normalized.replace("-", "").toDoubleOrNull() ?: return null
        return if (negative || amountText.startsWith("-")) -absolute else absolute
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

    suspend fun generateInsights(
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        incomeEntries: List<com.fmz.spenitaicore.data.db.entity.IncomeEntry>,
        periodLabel: String,
        currency: String,
        periodStart: String? = null,
        periodEnd: String? = null
    ): AiInsightResult {
        val fallback = buildRuleBasedInsights(receipts, incomeEntries, periodLabel, currency, periodStart, periodEnd)
        if (receipts.isEmpty()) return fallback

        return try {
            val model = getAvailableModel().model ?: return fallback
            val prompt = buildInsightsPrompt(receipts, incomeEntries, fallback, periodLabel, currency, periodStart, periodEnd)
            val request = GenerateContentRequest.Builder(TextPart(prompt)).build()
            val response = inferenceMutex.withLock { model.generateContent(request) }
            val jsonStr = response.candidates.firstOrNull()?.text?.trim() ?: return fallback
            val jsonObj = parseFirstJsonObject(jsonStr) ?: return fallback
            mergeAiInsightJson(jsonObj, fallback)
        } catch (e: Exception) {
            Log.e("AiCoreService", "Insight generation error", e)
            fallback
        }
    }

    private fun buildRuleBasedInsights(
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        incomeEntries: List<com.fmz.spenitaicore.data.db.entity.IncomeEntry>,
        periodLabel: String,
        currency: String,
        periodStart: String?,
        periodEnd: String?
    ): AiInsightResult {
        val total = receipts.sumOf { it.total }
        val dayCount = insightDayCount(receipts, periodStart, periodEnd)
        val avgDaily = if (dayCount > 0) total / dayCount else 0.0
        val taxDeductible = receipts.filter { it.isTaxDeductible }.sumOf { it.total }
        val totalIncome = incomeEntries.sumOf { it.amount }
        val netCash = totalIncome - total

        val categories = receipts.groupBy { it.category }
            .map { (cat, items) ->
                val amount = items.sumOf { it.total }
                CategoryBreakdown(
                    category = cat,
                    amount = amount,
                    percentage = if (total > 0) (amount / total) * 100 else 0.0
                )
            }
            .sortedByDescending { it.amount }
            .take(5)

        val savingTips = buildRuleBasedSavingTips(categories, avgDaily, currency)

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
            if (receipts.isEmpty()) {
                append("No spending is recorded for $periodLabel yet.")
                return@buildString
            }
            append("You spent ${com.fmz.spenitaicore.util.CurrencyFormatter.formatInt(total, currency)} in $periodLabel. ")
            topCategory?.let {
                append("${it.category} is the largest category at ${"%.0f".format(it.percentage)}% of tracked spending. ")
            }
            if (totalIncome > 0) append("Net cash flow is ${com.fmz.spenitaicore.util.CurrencyFormatter.format(netCash, currency)}. ")
            if (taxDeductible > 0) {
                append("You have ${com.fmz.spenitaicore.util.CurrencyFormatter.formatInt(taxDeductible, currency)} in tax-deductible expenses. ")
            }
        }

        val keyFindings = buildList {
            add("Average daily spending: ${com.fmz.spenitaicore.util.CurrencyFormatter.format(avgDaily, currency)}")
            add("Tracked ${receipts.size} transactions across ${categories.size} categories")
            if (totalIncome > 0) add("Income minus spending: ${com.fmz.spenitaicore.util.CurrencyFormatter.format(netCash, currency)}")
        }

        val anomalyAlert = detectRuleBasedAnomaly(receipts, avgDaily, currency)

        return AiInsightResult(
            summary = summary.trim(),
            keyFindings = keyFindings,
            savingTips = savingTips,
            categoryBreakdown = categories,
            weeklyTrend = normalizedTrend,
            taxDeductibleTotal = taxDeductible,
            averageDailySpend = avgDaily,
            anomalyAlert = anomalyAlert
        )
    }

    private fun buildInsightsPrompt(
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        incomeEntries: List<com.fmz.spenitaicore.data.db.entity.IncomeEntry>,
        fallback: AiInsightResult,
        periodLabel: String,
        currency: String,
        periodStart: String?,
        periodEnd: String?
    ): String {
        val topCategories = fallback.categoryBreakdown.joinToString("\n") {
            "- ${it.category}: ${formatAmountForPrompt(it.amount, currency)} (${ "%.0f".format(it.percentage) }%)"
        }.ifBlank { "- None" }
        val recentTransactions = receipts.sortedByDescending { it.date }.take(30).joinToString("\n") {
            "- ${it.date} | ${it.category} | ${it.merchant.ifBlank { "Unknown merchant" }} | ${formatAmountForPrompt(it.total, currency)}${if (it.isTaxDeductible) " | tax-deductible" else ""}"
        }
        val incomeSummary = incomeEntries.groupBy { it.category }
            .map { (category, items) -> category to items.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .joinToString("\n") { "- ${it.first}: ${formatAmountForPrompt(it.second, currency)}" }
            .ifBlank { "- No income recorded" }

        return """
            You are the on-device financial insights engine for SpenIt AICore.
            Analyze the user's tracked spending and income for the selected period.

            Period:
            - Label: $periodLabel
            - Start: ${periodStart ?: "unknown"}
            - End: ${periodEnd ?: "unknown"}
            - Currency: $currency

            Computed metrics:
            - Total spending: ${formatAmountForPrompt(receipts.sumOf { it.total }, currency)}
            - Average daily spending: ${formatAmountForPrompt(fallback.averageDailySpend, currency)}
            - Tax-deductible spending: ${formatAmountForPrompt(fallback.taxDeductibleTotal, currency)}
            - Transaction count: ${receipts.size}

            Top categories:
            $topCategories

            Income by category:
            $incomeSummary

            Recent transactions:
            $recentTransactions

            Write grounded, specific, user-friendly insights. Do not invent transactions, income, budgets, subscriptions, or goals that are not visible in the data. Prefer concrete category names, amounts, and proportions. If data is thin, say so and give cautious next steps. Keep the tone practical and concise.

            Return ONLY a valid JSON object with no markdown and no extra text:
            {
              "summary": "One concise paragraph, max 45 words.",
              "keyFindings": ["2-3 short findings grounded in the data"],
              "savingTips": [
                {"title":"Action title","description":"Specific action based on this data, max 22 words.","potentialSaving":0.0,"icon":"💡"}
              ],
              "anomalyAlert": "Only include if one merchant/category/day is unusually high; otherwise null"
            }
        """.trimIndent()
    }

    private fun mergeAiInsightJson(jsonObj: JsonObject, fallback: AiInsightResult): AiInsightResult {
        val summary = jsonObj.stringValue("summary", "aiSummary", "analysis").ifBlank { fallback.summary }
        val keyFindings = jsonObj.arrayValue("keyFindings", "key findings", "findings")
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(3)
            ?.ifEmpty { null }
            ?: fallback.keyFindings
        val savingTips = jsonObj.arrayValue("savingTips", "saving tips", "tips")
            ?.mapNotNull { it as? JsonObject }
            ?.map {
                SavingTip(
                    title = it.stringValue("title").ifBlank { "Review spending" },
                    description = it.stringValue("description", "tip").ifBlank { "Review this category for avoidable repeat expenses." },
                    potentialSaving = it.amountValue("potentialSaving", "potential saving"),
                    icon = it.stringValue("icon").normalizedSavingTipIcon()
                )
            }
            ?.take(3)
            ?.ifEmpty { null }
            ?: fallback.savingTips
        val anomalyAlert = jsonObj.findValue("anomalyAlert", "anomaly alert", "alert")
            ?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: fallback.anomalyAlert

        return fallback.copy(
            summary = summary,
            keyFindings = keyFindings,
            savingTips = savingTips,
            anomalyAlert = anomalyAlert
        )
    }

    private fun insightDayCount(
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        periodStart: String?,
        periodEnd: String?
    ): Int {
        val start = periodStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = periodEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start != null && end != null && !end.isBefore(start)) {
            return ChronoUnit.DAYS.between(start, end).toInt() + 1
        }
        val dates = receipts.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        if (dates.isEmpty()) return 0
        return ChronoUnit.DAYS.between(dates.minOrNull(), dates.maxOrNull()).toInt().coerceAtLeast(0) + 1
    }

    private fun buildRuleBasedSavingTips(
        categories: List<CategoryBreakdown>,
        avgDaily: Double,
        currency: String
    ): List<SavingTip> {
        val tips = mutableListOf<SavingTip>()
        val top = categories.firstOrNull()
        if (top != null && top.percentage >= 35.0) {
            tips += SavingTip(
                title = "Trim ${top.category}",
                description = "A 10% reduction would save about ${formatAmountForPrompt(top.amount * 0.10, currency)} this period.",
                potentialSaving = top.amount * 0.10
            )
        }
        if (avgDaily > 0) {
            tips += SavingTip(
                title = "Set a daily guardrail",
                description = "Keep daily spending below ${formatAmountForPrompt(avgDaily * 0.9, currency)} to trend 10% lower.",
                potentialSaving = avgDaily * 0.1 * 30
            )
        }
        tips += SavingTip(
            title = "Check repeat purchases",
            description = "Review recurring merchants and small frequent payments before the next pay cycle."
        )
        return tips.take(3)
    }

    private fun detectRuleBasedAnomaly(
        receipts: List<com.fmz.spenitaicore.data.db.entity.Receipt>,
        avgDaily: Double,
        currency: String
    ): String? {
        if (receipts.size < 3 || avgDaily <= 0.0) return null
        val largest = receipts.maxByOrNull { it.total } ?: return null
        return if (largest.total >= avgDaily * 3) {
            "${largest.merchant.ifBlank { largest.category }} is unusually high at ${formatAmountForPrompt(largest.total, currency)}."
        } else {
            null
        }
    }

    private fun formatAmountForPrompt(amount: Double, currency: String): String {
        return com.fmz.spenitaicore.util.CurrencyFormatter.format(amount, currency)
    }

    private fun String.normalizedSavingTipIcon(): String {
        val value = trim()
        return when (value.lowercase()) {
            "", "lightbulb", "bulb", "idea" -> "\uD83D\uDCA1"
            "food", "meal", "meals", "groceries" -> "\uD83C\uDF7D\uFE0F"
            "transport", "travel" -> "\uD83D\uDE97"
            "shopping" -> "\uD83D\uDED2"
            "subscription", "subscriptions", "recurring" -> "\uD83D\uDD01"
            "tax" -> "\uD83E\uDDFE"
            "save", "saving", "savings", "money" -> "\uD83D\uDCB0"
            else -> value.takeIf { it.codePointCount(0, it.length) <= 2 } ?: "\uD83D\uDCA1"
        }
    }

}
