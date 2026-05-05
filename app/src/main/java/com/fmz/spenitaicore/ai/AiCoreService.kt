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
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
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

    suspend fun classifyFinancialDocument(imagePath: String): FinancialDocumentType {
        Log.d("AiCoreService", "Starting document classification for: $imagePath")
        return try {
            val bitmap = loadBitmapFromPath(imagePath) ?: return FinancialDocumentType.Unknown
            val model = getAvailableModel() ?: return FinancialDocumentType.Unknown

            val prompt = """
                Analyze this financial document image and classify it into exactly one type.
                Valid types are: "income", "expense", or "bankstatement".

                Rules:
                1. "income": payslip, salary slip, wage statement, employer payment advice, income proof. Choose this ONLY when it is primarily a payslip/income document, not a general bank statement.
                2. "expense": receipt, invoice, bill, purchase receipt, tax receipt. Choose this ONLY when it is primarily a merchant receipt/invoice/bill.
                3. "bankstatement": bank account statement or transaction list with multiple credits/debits/balances. Choose this when the document shows account balances, transaction rows, debit/credit columns, or a statement period.

                Return ONLY a JSON object in this exact format with no extra text or markdown:
                {"type":"income"}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = model.generateContent(request)
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
            val model = getAvailableModel() ?: return null

            val prompt = """
                Analyze this receipt image and extract the following information.
                Rules:
                1. merchant: The name of the store or business.
                2. date: Transaction date in YYYY-MM-DD format.
                3. total: The total amount paid as a number.
                4. tax: The tax amount as a number.
                5. category: Best match from this list: [${ExpensesViewModel.SPENDING_CATEGORIES.joinToString(", ")}].
                
                Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
                {"merchant":"...","date":"...","total":0.0,"currency":"$currency","category":"General","taxAmount":0.0}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = model.generateContent(request)
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
            val model = getAvailableModel() ?: return null

            val prompt = """
                Analyze this income document (pay slip or bank statement).
                
                Rules for extraction:
                1. employer: Name of the company, payer, or source.
                2. netPay: For pay slips, use net pay, take-home pay, or amount paid. For bank statements, use the amount from one credit/deposit/money-in transaction row (not the running balance). Must be a positive number. Return 0.0 if not found.
                3. netPayText: The exact text of the netPay amount, including all commas and decimals (e.g. "10,412.51").
                4. date: Date in YYYY-MM-DD format. Use "${DateUtils.today()}" if cannot be read.
                5. category: Best match from: ${IncomeSources.All.joinToString(", ")}.
                6. notes: Any additional reason or description.
                
                Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
                {"employer":"...","netPay":0.0,"netPayText":"...","date":"YYYY-MM-DD","category":"Salary","notes":"..."}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = model.generateContent(request)
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
        return try {
            val model = getAvailableModel()
            if (model == null) return BankStatementResult(errorMessage = "AI model not available. Install Google AICore.")

            val bitmaps: List<Bitmap> = withContext(Dispatchers.IO) {
                if (isPdfInput(imagePath)) loadPdfPages(imagePath)
                else loadImageBitmap(imagePath)?.let { listOf(it) } ?: emptyList()
            }
            if (bitmaps.isEmpty()) {
                Log.w("AiCoreService", "No bitmaps loaded from $imagePath")
                return BankStatementResult(errorMessage = "Could not load image. File may be unsupported or corrupted.")
            }

            var bankName = ""
            var accountLast4 = ""
            var period = ""
            val allTransactions = mutableListOf<BankTransaction>()
            var pageErrors = 0

            for ((index, bitmap) in bitmaps.withIndex()) {
                Log.d("AiCoreService", "Processing page ${index + 1}/${bitmaps.size}")
                val pageResult = extractPageTransactions(model, bitmap)
                if (pageResult == null) {
                    pageErrors++
                    continue
                }
                if (bankName.isEmpty()) bankName = pageResult.bankName
                if (accountLast4.isEmpty()) accountLast4 = pageResult.accountLast4
                if (period.isEmpty()) period = pageResult.period
                allTransactions += pageResult.transactions
            }

            Log.d("AiCoreService", "Total transactions extracted: ${allTransactions.size}")
            if (allTransactions.isEmpty() && pageErrors > 0 && pageErrors == bitmaps.size) {
                BankStatementResult(errorMessage = "AI failed to parse the document. Try a clearer image or different file.")
            } else {
                BankStatementResult(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    period = period,
                    transactions = allTransactions
                )
            }
        } catch (e: Exception) {
            Log.e("AiCoreService", "Bank statement extraction error", e)
            BankStatementResult(errorMessage = "Extraction error: ${e.message ?: "Unknown error"}")
        }
    }

    private suspend fun extractPageTransactions(
        model: GenerativeModel,
        bitmap: Bitmap
    ): BankStatementResult? {
        return try {
            val prompt = """
                Analyze this bank statement page and extract all transaction data.

                EXTRACTION RULES:

                1. BANK METADATA (extract if visible anywhere on the page):
                   - bankName: The bank's name (e.g., "Maybank", "CIMB Bank", "Public Bank", "HSBC", "Standard Chartered", "OCBC", "DBS", "UOB", "RHB", "Hong Leong Bank", "Affin Bank", "Alliance Bank", "AmBank", "Bank Rakyat", "Bank Islam", "BSN", "Citibank")
                   - accountLast4: Last 4 digits of the account number
                   - period: Statement period (e.g., "Jan 2025", "01/01/2025 - 31/01/2025", "January 2025")

                2. TRANSACTION IDENTIFICATION:
                   A transaction row typically has:
                   - A date field
                   - A description/narration field
                   - One or more amount fields

                   IGNORE these non-transaction rows:
                   - Opening balance, closing balance, running balance, available balance
                   - Total debits, total credits, total transactions
                   - Page totals, brought forward, carried forward
                   - Interest earned, fees summary rows
                   - Header rows with column names only

                3. DATE EXTRACTION (convert to YYYY-MM-DD):
                   Handle these common formats:
                   - DD/MM/YYYY or DD-MM-YYYY → convert to YYYY-MM-DD
                   - MM/DD/YYYY or MM-DD-YYYY → convert to YYYY-MM-DD
                   - YYYY/MM/DD or YYYY-MM-DD → keep as is
                   - DD MMM YYYY (e.g., "15 Jan 2025") → convert to YYYY-MM-DD
                   - MMM DD, YYYY (e.g., "Jan 15, 2025") → convert to YYYY-MM-DD
                   - DD/MM/YY → assume 20YY and convert to YYYY-MM-DD
                   If year is missing, use the statement period year or current year.

                4. DESCRIPTION EXTRACTION:
                   Look for fields labeled as: Description, Narration, Particulars, Reference, Transaction Details, Remarks, Payee, Counterparty, Merchant
                   Extract the full description text. If there are multiple description fields, concatenate them with a space.

                5. AMOUNT EXTRACTION - Handle all these patterns:

                   Pattern A - Separate Credit/Debit columns:
                   - Credit column may be labeled: Credit, CR, Money In, Deposit, +, Incoming
                   - Debit column may be labeled: Debit, DR, Money Out, Withdrawal, -, Outgoing
                   - If amount is in Credit column → positive amount, type="credit"
                   - If amount is in Debit column → negative amount, type="debit"

                   Pattern B - Single Amount column with sign:
                   - Amount starting with "+" or no sign in credit context → positive, type="credit"
                   - Amount starting with "-" or in brackets like "(100.00)" → negative, type="debit"

                   Pattern C - CR/DR suffix:
                   - "1,234.56 CR" → positive 1234.56, type="credit"
                   - "1,234.56 DR" → negative 1234.56, type="debit"

                   Pattern D - Running balance column:
                   - Some statements show: Date | Description | Debit | Credit | Balance
                   - The Balance column is NOT the transaction amount - ignore it
                   - Use only the Debit/Credit columns for transaction amounts

                6. AMOUNT FORMAT HANDLING:
                   - Handle commas as thousand separators: "1,234.56" → 1234.56
                   - Handle dots as thousand separators (European): "1.234,56" → 1234.56
                   - Handle bracket notation for negatives: "(100.00)" → -100.00
                   - Preserve the exact text shown in amountText field

                7. TRANSACTION TYPE DETERMINATION:
                   - "credit" for: deposits, incoming transfers, salary, refunds, interest earned, credits
                   - "debit" for: withdrawals, outgoing transfers, purchases, payments, fees, debits
                   - Default to "debit" if unclear (most transactions are expenses)

                OUTPUT FORMAT:
                Return ONLY a valid JSON object in exactly this format, with no markdown formatting and no extra text:
                {"bankName":"Maybank","accountLast4":"1234","period":"Jan 2025","transactions":[{"date":"2025-01-03","description":"SALARY CREDIT ABC COMPANY","amount":3500.00,"amountText":"3,500.00","type":"credit"},{"date":"2025-01-05","description":"GROCERY STORE KL","amount":-62.40,"amountText":"62.40","type":"debit"},{"date":"2025-01-07","description":"ONLINE TRANSFER","amount":-500.00,"amountText":"500.00","type":"debit"},{"date":"2025-01-10","description":"REFUND FROM SHOPEE","amount":45.00,"amountText":"45.00","type":"credit"}]}
            """.trimIndent()

            val request = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(prompt)
            ).build()

            val response = model.generateContent(request)
            val jsonStr = response.candidates.firstOrNull()?.text
                ?.replace("```json", "")?.replace("```", "")?.trim()
                ?: return null

            Log.d("AiCoreService", "Page JSON (first 300): ${jsonStr.take(300)}")
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
            Log.e("AiCoreService", "Page extraction error", e)
            null
        }
    }

    private fun loadPdfPages(pdfPath: String): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        return try {
            val fd = when {
                pdfPath.startsWith("content://") ->
                    context.contentResolver.openFileDescriptor(Uri.parse(pdfPath), "r") ?: return emptyList()
                pdfPath.startsWith("file://") -> {
                    val path = Uri.parse(pdfPath).path ?: return emptyList()
                    ParcelFileDescriptor.open(java.io.File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                }
                else -> ParcelFileDescriptor.open(java.io.File(pdfPath), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val renderer = PdfRenderer(fd)
            val pageCount = minOf(renderer.pageCount, 10)
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val maxDim = maxOf(page.width, page.height).coerceAtLeast(1)
                val scale = (2048f / maxDim).coerceIn(1f, 3f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bm).drawColor(Color.WHITE)
                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pages += bm
            }
            renderer.close()
            fd.close()
            pages
        } catch (e: Exception) {
            Log.e("AiCoreService", "PDF pages load error", e)
            pages
        }
    }

    private suspend fun getAvailableModel(): GenerativeModel? {
        val candidates = listOf(
            "preview_full" to previewFullModel,
            "preview_fast" to previewFastModel,
            "stable_default" to stableModel
        )

        for ((name, model) in candidates) {
            val status = try {
                model.checkStatus()
            } catch (e: Exception) {
                Log.w("AiCoreService", "Model status check failed for $name", e)
                continue
            }

            if (status == FeatureStatus.AVAILABLE) {
                val baseModelName = try {
                    model.getBaseModelName()
                } catch (_: Exception) {
                    name
                }
                Log.d("AiCoreService", "Using AICore model: $name ($baseModelName)")
                return model
            }

            Log.d("AiCoreService", "AICore model $name not available. Status=$status")
        }

        Log.w("AiCoreService", "No AICore model is available")
        return null
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
        val credit = amountTextValue("creditText", "credit text") ?: amountValue("credit", "moneyIn", "money in")
        val debit = amountTextValue("debitText", "debit text") ?: amountValue("debit", "moneyOut", "money out")
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
            signedAmount < 0.0 -> "debit"
            else -> "debit" // default to debit for bank statements — most transactions are expenses
        }

        return BankTransaction(
            date = stringValue("date", "transactionDate", "transaction date", "postingDate", "valueDate"),
            description = stringValue("description", "details", "narration", "reference", "particulars"),
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
