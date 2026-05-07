package com.fmz.spenitaicore.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Regex-based bank statement parser. Primary extraction strategy — no AI inference needed.
 * Ported from SpenIt/SpenIt/Services/Income/BankStatementService.cs.
 *
 * Uses ML Kit Text Recognition to extract structured lines from rendered PDF page bitmaps,
 * then matches lines against date+amount regex patterns to reconstruct transactions.
 */
object BankStatementParser {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // ── Date format patterns (tried in priority order) ───────────────────────
    private val genericFormats = listOf(
        // "12 Jan 2026" / "12 January 2026"
        Pair(Regex("""^(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})\s+(.+)$"""), listOf("d MMM yyyy", "d MMMM yyyy")),
        // "12/01/2026" — d/M/yyyy first, then M/d
        Pair(Regex("""^\s*(\d{1,2}/\d{1,2}/\d{4})\s+(.+)$"""), listOf("d/M/yyyy", "M/d/yyyy")),
        // "2026-01-12" ISO
        Pair(Regex("""^(\d{4}-\d{2}-\d{2})\s+(.+)$"""), listOf("yyyy-MM-dd")),
        // "12-01-2026" dash-separated
        Pair(Regex("""^\s*(\d{1,2}-\d{1,2}-\d{4})\s+(.+)$"""), listOf("d-M-yyyy", "M-d-yyyy")),
        // "12.01.2026" dot-separated
        Pair(Regex("""^\s*(\d{1,2}\.\d{1,2}\.\d{4})\s+(.+)$"""), listOf("d.M.yyyy", "M.d.yyyy")),
    )

    // ── Amount patterns (tried in priority order) ────────────────────────────
    // 1. +/-amount  balance
    private val signedAmountBalanceRx = Regex("""([+-][\d,]+\.\d{2})\s+[\d,]+\.\d{2}\s*$""")
    // 2. +/-amount standalone
    private val signedStandaloneRx = Regex("""^[+-][\d,]+\.\d{2}$""")
    // 3. amount  CR/DR  balance
    private val amountCrDrBalanceRx = Regex("""([\d,]+\.?\d+)\s+(C\.?R\.?|D\.?R\.?)\s+[\d,]+\.?\d+\s*$""", RegexOption.IGNORE_CASE)
    // 4. amount  CR/DR
    private val amountCrDrRx = Regex("""([\d,]+\.?\d+)\s+(C\.?R\.?|D\.?R\.?)\s*$""", RegexOption.IGNORE_CASE)
    // 5. (amount)  — parenthetical = debit
    private val parentheticalRx = Regex("""\(([\d,]+\.?\d+)\)\s*$""")
    // 6. amount  balance  — last resort (two unsigned decimals at end of line)
    private val unsignedPairRx = Regex("""([\d,]+\.\d{2})\s+[\d,]+\.\d{2}\s*$""")
    // Standalone balance (for filtering continuation lines)
    private val standaloneBalanceRx = Regex("""^[\d,]+\.\d{2}$""")

    // ── TNG eWallet ───────────────────────────────────────────────────────────
    private val tngBlockStartRx = Regex("""^(?:\d{1,4}\s+)?(\d{1,2}/\d{1,2}/\d{4})\b""")
    private val tngRmValueRx = Regex("""RM([\d,]+\.?\d+)""")
    private val tngEndMarkerRx = Regex("""\bCredit\b|\bDebit\b|RM[\d,]""", RegexOption.IGNORE_CASE)
    private val tngLongNumericRx = Regex("""\b\d{8,}\b""")

    // ── ML Kit text extraction ────────────────────────────────────────────────

    suspend fun extractLines(bitmap: Bitmap): List<String> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()
            groupIntoLines(result)
        } catch (e: Exception) {
            Log.w("BankStatementParser", "ML Kit text extraction failed", e)
            emptyList()
        }
    }

    private fun groupIntoLines(text: Text): List<String> {
        // Collect all lines with their vertical centre position
        val elements = mutableListOf<Pair<Int, String>>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val y = line.boundingBox?.centerY() ?: continue
                val t = line.text.trim()
                if (t.isNotBlank()) elements.add(y to t)
            }
        }
        if (elements.isEmpty()) return emptyList()

        // Group by rounded y (±8px tolerance = same row)
        val bucketSize = 16
        return elements
            .sortedBy { it.first }
            .groupBy { (it.first / bucketSize.toFloat()).roundToInt() * bucketSize }
            .values
            .map { group -> group.sortedBy { it.first }.joinToString(" ") { it.second }.trim() }
            .filter { it.isNotBlank() }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    fun parseLines(lines: List<String>): List<BankTransaction> {
        if (lines.isEmpty()) return emptyList()
        return if (isTngFormat(lines)) parseTngStatement(lines)
        else tryParseGenericBankStatement(lines)
    }

    // ── Generic multi-format parser ───────────────────────────────────────────

    private fun tryParseGenericBankStatement(lines: List<String>): List<BankTransaction> {
        var best = emptyList<BankTransaction>()
        for ((lineRegex, dateFormats) in genericFormats) {
            val blocks = splitBlocksWithPattern(lines, lineRegex)
            if (blocks.isEmpty()) continue
            val rows = blocks.mapNotNull { tryParseBlock(it, lineRegex, dateFormats) }
            if (rows.size > best.size) best = rows
        }
        return best
    }

    private fun splitBlocksWithPattern(lines: List<String>, pattern: Regex): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            if (pattern.containsMatchIn(line)) {
                current?.let { blocks.add(it) }
                current = mutableListOf(line)
            } else {
                current?.add(line)
            }
        }
        current?.let { blocks.add(it) }
        return blocks
    }

    private fun tryParseBlock(block: List<String>, dateRegex: Regex, dateFormats: List<String>): BankTransaction? {
        if (block.isEmpty()) return null

        val headerMatch = dateRegex.find(block[0]) ?: return null
        val dateStr = headerMatch.groupValues[1]
        var firstDesc = headerMatch.groupValues[2].trim()

        if (isBalanceMarker(firstDesc)) return null

        val date = parseDate(dateStr, dateFormats) ?: return null

        // Find amount and direction
        var amountStr = ""
        var amountLineIdx = -1
        var isIncomeFromAmt: Boolean? = null

        val (a0, inc0, cleaned0) = extractAmountFromLine(block[0], firstDesc)
        if (a0.isNotEmpty()) {
            amountStr = a0; isIncomeFromAmt = inc0; firstDesc = cleaned0; amountLineIdx = 0
        }

        if (amountLineIdx < 0) {
            for (i in 1 until block.size) {
                val (a, inc, _) = extractAmountFromLine(block[i], block[i])
                if (a.isNotEmpty()) { amountStr = a; isIncomeFromAmt = inc; amountLineIdx = i; break }
            }
        }

        // Last resort: unsigned pair at end of any line
        if (amountLineIdx < 0) {
            for (i in block.indices) {
                val m = unsignedPairRx.find(block[i]) ?: continue
                amountStr = m.groupValues[1]; amountLineIdx = i
                if (i == 0) firstDesc = unsignedPairRx.replace(firstDesc, "").trim()
                break
            }
        }

        if (amountLineIdx < 0 || amountStr.isBlank()) return null

        val amount = amountStr.trimStart('+', '-').replace(",", "").toDoubleOrNull() ?: return null
        if (amount == 0.0) return null

        val details = block.drop(1).take(maxOf(0, amountLineIdx - 1)).filter { isUsefulDescLine(it) }
        val description = (listOf(firstDesc) + details).joinToString(" ").trim()
        if (description.isBlank() || isBalanceMarker(description)) return null

        val isIncome = isIncomeFromAmt ?: isGenericIncomeByDesc(description)
        val type = if (isIncome) "credit" else "debit"
        val signedAmount = if (isIncome) abs(amount) else -abs(amount)

        return BankTransaction(date = date, description = description, amount = signedAmount, type = type)
    }

    private data class AmountResult(val amount: String, val isIncome: Boolean?, val cleanedLine: String)

    private fun extractAmountFromLine(line: String, sub: String): AmountResult {
        signedAmountBalanceRx.find(line)?.let { m ->
            val a = m.groupValues[1]
            return AmountResult(a, a[0] == '+', signedAmountBalanceRx.replace(sub, "").trim())
        }
        if (signedStandaloneRx.matches(line.trim()))
            return AmountResult(line.trim(), line.trim()[0] == '+', "")

        amountCrDrBalanceRx.find(line)?.let { m ->
            return AmountResult(m.groupValues[1], m.groupValues[2].startsWith("C", true), amountCrDrBalanceRx.replace(sub, "").trim())
        }
        amountCrDrRx.find(line)?.let { m ->
            return AmountResult(m.groupValues[1], m.groupValues[2].startsWith("C", true), amountCrDrRx.replace(sub, "").trim())
        }
        parentheticalRx.find(line)?.let { m ->
            return AmountResult(m.groupValues[1], false, parentheticalRx.replace(sub, "").trim())
        }
        return AmountResult("", null, sub)
    }

    // ── Date parsing ──────────────────────────────────────────────────────────

    private fun parseDate(dateStr: String, formats: List<String>): String? {
        val today = LocalDate.now()
        for (fmt in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                val date = LocalDate.parse(dateStr, formatter)
                return (if (date.isAfter(today)) today else date).toString()
            } catch (_: DateTimeParseException) { }
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isBalanceMarker(s: String): Boolean {
        val l = s.lowercase()
        return l.contains("opening balance") || l.contains("closing balance") ||
               l.contains("balance b/f") || l.contains("balance c/f") ||
               l.contains("brought forward") || l.contains("carried forward")
    }

    private fun isUsefulDescLine(line: String): Boolean {
        if (line.isBlank()) return false
        if (line.startsWith("Ref. ID:", ignoreCase = true)) return false
        if (line.startsWith("Card ", ignoreCase = true)) return false
        if (line.contains("Transaction date:", ignoreCase = true)) return false
        if (line in setOf("Transfer", "QR", "JomPAY", "Money movement")) return false
        if (signedStandaloneRx.matches(line)) return false
        if (standaloneBalanceRx.matches(line)) return false
        return true
    }

    private fun isGenericIncomeByDesc(description: String): Boolean {
        val l = description.lowercase()
        if (l.contains("credit card")) return false
        return l.contains("salary") || l.contains("wages") || l.contains("payroll") ||
               l.contains("deposit") || l.contains("interest") || l.contains("dividend") ||
               l.contains("refund") || l.contains("receive") || l.contains("transfer in") ||
               l.contains("cashback") || l.contains("cash back") || l.contains("income") ||
               l.contains("payment received") || l.contains("bonus")
    }

    // ── TNG eWallet ───────────────────────────────────────────────────────────

    private fun isTngFormat(lines: List<String>) = lines.any { l ->
        l.contains("TNG WALLET", ignoreCase = true) ||
        l.contains("GO+ TRANSACTION", ignoreCase = true) ||
        l.contains("Touch 'n Go", ignoreCase = true) ||
        l.contains("eWallet Transaction History", ignoreCase = true)
    }

    private fun parseTngStatement(lines: List<String>): List<BankTransaction> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            if (tngBlockStartRx.containsMatchIn(line)) { current?.let { blocks.add(it) }; current = mutableListOf(line) }
            else current?.add(line)
        }
        current?.let { blocks.add(it) }
        return blocks.mapNotNull { parseTngBlock(it) }
    }

    private fun parseTngBlock(block: List<String>): BankTransaction? {
        if (block.isEmpty()) return null
        val dateMatch = tngBlockStartRx.find(block[0]) ?: return null
        val date = parseDate(dateMatch.groupValues[1], listOf("d/M/yyyy")) ?: return null
        val fullText = block.joinToString(" ")

        val rmMatches = tngRmValueRx.findAll(fullText).toList()
        val amount = if (rmMatches.size >= 2) {
            rmMatches[rmMatches.size - 2].groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        } else {
            var found: Double? = null
            for (line in block) {
                val m = unsignedPairRx.find(line) ?: continue
                val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
                if (v != null && v > 0) { found = v; break }
            }
            found ?: return null
        }
        if (amount == 0.0) return null

        val hasCredit = Regex("""\bCredit\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullText)
        val hasDebit = Regex("""\bDebit\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullText)
        val isIncome = (hasCredit && !hasDebit) || (!hasDebit && isTngIncomeByDesc(fullText))

        val afterPrefix = block[0].substring(dateMatch.range.last + 1).trimStart()
        val endIdx = tngEndMarkerRx.find(afterPrefix)?.range?.first ?: afterPrefix.length
        var desc = tngLongNumericRx.replace(afterPrefix.substring(0, endIdx), "")
        desc = desc.trim(' ', '-', '.', ',', '|')
        desc = Regex("""\s+""").replace(desc, " ").trim()
        if (desc.isBlank()) return null

        val type = if (isIncome) "credit" else "debit"
        val signedAmount = if (isIncome) abs(amount) else -abs(amount)
        return BankTransaction(date = date, description = desc, amount = signedAmount, type = type)
    }

    private fun isTngIncomeByDesc(text: String): Boolean {
        val l = text.lowercase()
        if (l.contains("go+ cash out")) return true
        if (l.contains("cash out")) return false
        return l.contains("reload") || l.contains("top up") || l.contains("topup") ||
               l.contains("receive") || l.contains("earning") || l.contains("refund") ||
               l.contains("cashback") || l.contains("cash back") || l.contains("balance top")
    }
}
