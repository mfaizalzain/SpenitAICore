package com.fmz.spenitaicore.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Universal bank statement parser — no bank-specific hardcoding.
 *
 * Strategy: scan every OCR line for ANY date-like token + ANY amount-like token.
 * If a line has both, it's a transaction row. Multi-line descriptions are grouped
 * by vertical proximity. This works for ANY bank format without per-bank rules.
 *
 * Ported from SpenIt/SpenIt/Services/Income/BankStatementService.cs with
 * significant structural improvements for universal format support.
 */
object BankStatementParser {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // ── Universal date patterns (broad match — catch everything) ──────────────

    /** Matches any string that looks like a date in ANY common format. */
    private val anyDateRx = Regex(
        """\b""" +
        // DD/MM/YY, DD/MM/YYYY, MM/DD/YYYY (numeric with separators)
        """(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4})""" + "|" +
        // D Mmm YYYY, DD Mmm YYYY (with 4-digit year, avoids matching times like "5 Jan 02")
        """(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})""" + "|" +
        // D Mmm or DD Mmm (no year, standalone) — avoid matching times by requiring end-of-word
        """(\d{1,2}\s+[A-Za-z]{3,9})(?:\s|$)""" + "|" +
        // Mmm DD YYYY, Mmm D YYYY
        """([A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4})""" + "|" +
        // DDMmmYYYY (no separators) — 27Mar2026, 01Apr2026
        """(\d{1,2}[A-Za-z]{3,9}\d{4})""" + "|" +
        // Mmm YYYY standalone (period headers)
        """([A-Za-z]{3,9}\s+\d{4})""" +
        """\b"""
    )

    /** Matches any monetary amount — the core detection pattern. */
    private val anyAmountRx = Regex(
        """\b""" +
        // Signed amount: +1,234.56 or -1,234.56 or 1,234.56-
        """([+\-−]?\s*[\d,]+\.\d{2})""" + "|" +
        // Parenthetical: (1,234.56) — typically debit
        """\([\d,]+\.\d{2}\)""" + "|" +
        // CR/DR suffix: 1,234.56 CR or 1,234.56 DR
        """([\d,]+\.\d{2}\s+(?:C\.?R\.?|D\.?R\.?))""" + "|" +
        // Amount with currency prefix: RM1,234.56, MYR1,234.56
        """(RM|MYR|\$)\s*[\d,]+\.\d{2}""" +
        """\b"""
    )

    /** Matches ONLY amount tokens (for filtering out non-transaction numbers). */
    private val pureAmountRx = Regex("""[+\-−]?\s*[\d,]+\.\d{2}""")

    /** Checks if a number is too small to be a transaction (daily interest, fees). */
    private val tinyAmountRx = Regex("""[+\-−]?0\.\d{2}""")

    /** Ref ID / long reference numbers to skip in descriptions. */
    private val refIdRx = Regex(
        """\b(?:Ref\.?\s*ID|Transaction\s*date|Card\s*••?\d{4}|""" +
        """MYR\d{10,}|REF\s+\w+\-\d+|HIB\-|LP\s+\-|DW\s+\-|""" +
        """EREF|REMI|REF\s+ZAFA)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Lines that are definitely NOT transaction rows. */
    private val skipLineRx = Regex(
        """(?i)\b(opening\s*balance|closing\s*balance|beginning\s*balance|""" +
        """ending\s*balance|balance\s*(brought|bring|carried)\s*forward|""" +
        """balance\s*b/?f|balance\s*c/?f|total\s*(credit|debit)|""" +
        """transaction\s*(turnover|count)|page\s+\d+\s+of\s+\d+|""" +
        """protected\s+by\s+pidm|end\s+of\s+statement|""" +
        """urusan?iaga\s*akaun|account\s+transactions|""" +
        """tarikh|butir|deskripsi|description|amaun|amount|""" +
        """baki|balance|deposit|withdrawal|pengeluaran)\b"""
    )

    /** Balance marker patterns — skip these entirely. */
    private val balanceMarkerRx = Regex(
        """(?i)\b(opening\s+balance|closing\s+balance|beginning\s+balance|""" +
        """ending\s+balance|balance\s+(brought|bring|carried)\s+forward|""" +
        """balance\s+b/?f|balance\s+c/?f|baki\s+(awal|akhir|bawa)\b|""" +
        """baki\s+bawa\s+ke\s+muka|baki\s+harian)\b"""
    )

    private val moneyMovementRx = Regex(
        """(?i)(from\s+\w+\s+pocket|to\s+\w+\s+pocket|money\s+movement)"""
    )

    /** Summary/total lines — never a transaction. */
    private val totalLineRx = Regex(
        """(?i)^(total|jumlah|grand total|subtotal|closing|opening|beginning|ending)"""
    )

    // ── Date parsing ──────────────────────────────────────────────────────────

    private val dateFormats = listOf(
        "d/M/yyyy", "d-M-yyyy", "d.M.yyyy",
        "yyyy-MM-dd", "yyyy-M-d",
        "dd/MM/yy", "dd/MM/yyyy",
        "d MMM yyyy", "dd MMM yyyy", "d MMMM yyyy", "dd MMMM yyyy",
        "MMM d yyyy", "MMM dd yyyy", "MMM d, yyyy", "MMM dd, yyyy",
        "dd MMM yyyy", "MMM dd yyyy"
    )

    /** US-style formats — tried only when US date format is detected. */
    private val usDateFormats = listOf(
        "M/d/yyyy", "M-d-yyyy", "M.d.yyyy",
        "MM/dd/yy", "MM/dd/yyyy"
    )

    private val shortFormats = listOf(
        "d MMM", "dd MMM", "d MMMM", "dd MMMM",
        "d/M", "dd/MM"
    )

    private val compactRx = Regex("""(\d{1,2})([A-Za-z]{3,9})(\d{4})""")

    private fun parseAnyDate(token: String, statementYear: Int? = null, isDDMM: Boolean = true): String? {
        val today = LocalDate.now()

        // Try unambiguous formats first (with month names — always correct)
        val nameFormats = listOf(
            "d MMM yyyy", "dd MMM yyyy", "d MMMM yyyy", "dd MMMM yyyy",
            "MMM d yyyy", "MMM dd yyyy", "MMM d, yyyy", "MMM dd, yyyy",
            "dd MMM yyyy", "MMM dd yyyy"
        )
        for (fmt in nameFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                val date = LocalDate.parse(token.trim(), formatter)
                val result = if (date.isAfter(today) && date.year == today.year + 1)
                    today else date
                return result.toString()
            } catch (_: DateTimeParseException) { }
        }

        // Try format-appropriate numeric formats
        val primaryFormats = if (isDDMM) {
            listOf("d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "dd/MM/yy", "dd/MM/yyyy")
        } else {
            listOf("M/d/yyyy", "M-d-yyyy", "M.d.yyyy", "MM/dd/yy", "MM/dd/yyyy")
        }
        for (fmt in primaryFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                val date = LocalDate.parse(token.trim(), formatter)
                val result = if (date.isAfter(today) && date.year == today.year + 1)
                    today else date
                return result.toString()
            } catch (_: DateTimeParseException) { }
        }

        // Try ISO format
        try {
            val date = LocalDate.parse(token.trim())
            return                    if (date.isAfter(today)) today.toString() else date.toString()
        } catch (_: DateTimeParseException) { }

        // Try DDMmmYYYY compact format (e.g., "27Mar2026")
        compactRx.find(token)?.let { m ->
            val day = m.groupValues[1].padStart(2, '0')
            val month = m.groupValues[2]
            val year = m.groupValues[3]
            val rebuilt = "$day $month $year"
            for (fmt in listOf("dd MMM yyyy", "d MMM yyyy")) {
                try {
                    val date = LocalDate.parse(rebuilt, DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
                    return                    if (date.isAfter(today)) today.toString() else date.toString()
                } catch (_: DateTimeParseException) { }
            }
        }

        // Try short formats with statement year inference
        if (statementYear != null) {
            for (fmt in shortFormats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                    val parsed = LocalDate.parse(token.trim(), formatter)
                    val date = parsed.withYear(statementYear)
                    return if (date.isAfter(today) && statementYear >= today.year)
                        date.withYear(today.year).toString()
                    else date.toString()
                } catch (_: DateTimeParseException) { }
            }
        }

        return null
    }

    /**
     * Estimate the statement year from the first few lines.
     * Looks for "January 2026", "Jan 2026", "01/03/2026 - 31/03/2026" etc.
     */
    private fun detectStatementYear(lines: List<String>): Int? {
        val yearRx = Regex("""\b(20\d{2})\b""")
        val monthYearRx = Regex(
            """(?i)\b(?:january|february|march|april|may|june|july|august|""" +
            """september|october|november|december|""" +
            """jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\s+(20\d{2})\b"""
        )
        for (line in lines.take(15)) {
            monthYearRx.find(line)?.let { return it.groupValues[1].toInt() }
        }
        // Fallback: any 20xx in the first lines
        for (line in lines.take(10)) {
            yearRx.find(line)?.let { return it.groupValues[1].toInt() }
        }
        return LocalDate.now().year
    }

    /**
     * Detect whether dates use DD/MM or MM/DD format by analyzing
     * date tokens in the first few lines.
     *
     * Rules:
     * 1. If ANY date token has day > 12 → must be DD/MM (day can't be month)
     * 2. If ANY date token with month names → unambiguous (month is text)
     * 3. If bilingual Malay/English → Malaysian bank → DD/MM
     * 4. Default → DD/MM/YYYY (user is in Malaysia)
     */
    private fun detectDateFormat(lines: List<String>): Boolean {
        // true = DD/MM/YYYY (rest of world), false = MM/DD/YYYY (US)
        val numericDateRx = Regex("""\b(\d{1,2})[/\-\.](\d{1,2})[/\-\.]\d{2,4}\b""")

        var seenDates = 0
        var ddmCount = 0   // count of dates where first number > 12 (must be day)
        var mddCount = 0   // count of dates where second number > 12 (must be day)

        for (line in lines.take(20)) {
            // Check for month names → unambiguous
            if (Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\b""").containsMatchIn(line)) {
                // Month names present → format is clear (either "D Mmm" or "Mmm D")
                return true // DD/MM is safe when month names are text
            }

            numericDateRx.findAll(line).forEach { m ->
                val first = m.groupValues[1].toIntOrNull() ?: 0
                val second = m.groupValues[2].toIntOrNull() ?: 0
                seenDates++

                if (first > 12 && first <= 31) ddmCount++   // MUST be DD
                if (second > 12 && second <= 31) mddCount++ // MUST be DD in MM/DD → first is month
            }

            // Check for Malay language indicators → Malaysian bank
            if (Regex("""(?i)\b(penyata|akaun|baki|amaun|tarikh|butir|urusniaga|pengeluaran|deposit)\b""")
                    .containsMatchIn(line)) {
                return true // Malaysian bank → DD/MM
            }
        }

        // If we saw dates, use majority vote
        if (seenDates > 0) {
            if (ddmCount > mddCount) return true  // DD/MM
            if (mddCount > ddmCount) return false // MM/DD
        }

        // Default: DD/MM (rest of world)
        return true
    }

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
        val elements = mutableListOf<Pair<Int, String>>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val y = line.boundingBox?.centerY() ?: continue
                val t = line.text.trim()
                if (t.isNotBlank()) elements.add(y to t)
            }
        }
        if (elements.isEmpty()) return emptyList()

        val bucketSize = 16
        return elements
            .sortedBy { it.first }
            .groupBy { (it.first / bucketSize.toFloat()).roundToInt() * bucketSize }
            .values
            .map { group -> group.sortedBy { it.first }.joinToString(" ") { it.second }.trim() }
            .filter { it.isNotBlank() }
    }

    // ── Universal entry point ─────────────────────────────────────────────────

    fun parseLines(lines: List<String>): List<BankTransaction> {
        if (lines.isEmpty()) return emptyList()

        val statementYear = detectStatementYear(lines)
        val isDDMM = detectDateFormat(lines)  // true = DD/MM, false = MM/DD
        Log.d("BankStatementParser", "Detected statement year: $statementYear, date format: ${if (isDDMM) "DD/MM" else "MM/DD"}")

        // Phase 1: Scan every line for date + amount → identify transaction rows
        val candidates = lines.mapIndexed { index, line ->
            scanLine(line, index, statementYear, isDDMM)
        }.filterNotNull()

        Log.d("BankStatementParser", "Phase 1: ${candidates.size} candidate rows from ${lines.size} lines")

        if (candidates.isEmpty()) return emptyList()

        // Phase 2: Group multi-line descriptions (lines that follow a transaction
        // but have no amount or no date)
        return groupTransactions(candidates, lines)
    }

    private data class LineCandidate(
        val lineIndex: Int,
        val date: String,
        val amount: Double,
        val isCredit: Boolean,
        val rawLine: String,
        /** How confident we are this is a transaction (0.0–1.0) */
        val confidence: Float
    )

    private fun scanLine(line: String, lineIndex: Int, statementYear: Int?, isDDMM: Boolean): LineCandidate? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        if (skipLineRx.matches(trimmed)) return null
        if (totalLineRx.matches(trimmed.split(" ").firstOrNull() ?: "")) return null
        if (balanceMarkerRx.containsMatchIn(trimmed)) return null
        if (moneyMovementRx.containsMatchIn(trimmed)) return null

        // Extract ALL date tokens from the line
        val dateMatches = anyDateRx.findAll(trimmed).toList()
        if (dateMatches.isEmpty()) return null

        // Try each date token
        for (dateMatch in dateMatches) {
            val dateStr = dateMatch.value.trim()
            val parsedDate = parseAnyDate(dateStr, statementYear, isDDMM) ?: continue
            if (parsedDate.isEmpty()) continue

            // Extract ALL amount tokens from the line
            val amountMatches = pureAmountRx.findAll(trimmed).toList()
            if (amountMatches.isEmpty()) continue

            // Pick the best amount: prefer the rightmost amount (amounts are on the right)
            // Also check for CR/DR markers on the line
            val isCrLine = Regex("""\bCR\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
            val isDrLine = Regex("""\bDR\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
            val isParenthetical = Regex("""\([\d,]+\.[\d]{2}\)""").containsMatchIn(trimmed)
            val trailingMinus = Regex("""[\d,]+\.[\d]{2}\s*-""").containsMatchIn(trimmed)
            val trailingPlus = Regex("""[\d,]+\.[\d]{2}\s*\+""").containsMatchIn(trimmed)

            // Pick amount: prefer rightmost or the one with sign context
            val amountStr = if (amountMatches.size == 1) {
                amountMatches[0].value
            } else {
                // Rightmost amount is usually the transaction amount
                // (leftmost amounts might be balance, rightmost is the transaction value)
                amountMatches.last().value
            }

            val rawAmount = amountStr.replace(",", "").replace("−", "-").toDoubleOrNull() ?: continue

            // Determine sign and credit/debit
            val hasLeadingMinus = amountStr.trim().startsWith("-") || amountStr.trim().startsWith("−")
            val hasLeadingPlus = amountStr.trim().startsWith("+")

            val isCredit = when {
                isCrLine -> true
                isDrLine -> false
                isParenthetical -> false
                hasLeadingMinus || trailingMinus -> false
                hasLeadingPlus || trailingPlus -> true
                else -> {
                    // Infer from description keywords
                    val desc = trimmed.replace(pureAmountRx, "").trim()
                    inferCreditFromDescription(desc)
                }
            }

            val amount = if (isCredit) abs(rawAmount) else -abs(rawAmount)

            // Skip tiny amounts (daily interest < RM1)
            if (abs(amount) < 1.0 && tinyAmountRx.matches(amountStr)) continue

            // Confidence heuristic
            val confidence = calculateConfidence(trimmed, dateStr, amountStr)

            return LineCandidate(
                lineIndex = lineIndex,
                date = parsedDate,
                amount = amount,
                isCredit = isCredit,
                rawLine = trimmed,
                confidence = confidence
            )
        }

        return null
    }

    private fun calculateConfidence(line: String, dateStr: String, amountStr: String): Float {
        var score = 0.5f
        // Has a clear signed amount
        if (amountStr.contains("+") || amountStr.contains("-") || amountStr.contains("−")) score += 0.2f
        if (Regex("""\bCR\b|\bDR\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) score += 0.2f
        // Description has transaction keywords
        if (Regex("""(?i)(sale|debit|credit|payment|transfer|deposit|withdrawal|refund|salary|interest|fee)""").containsMatchIn(line)) score += 0.15f
        // Line has a standard date format with separators
        if (dateStr.contains("/") || dateStr.contains("-") || dateStr.contains(" ")) score += 0.15f
        return minOf(score, 1.0f)
    }

    private fun inferCreditFromDescription(desc: String): Boolean {
        val l = desc.lowercase()
        if (l.contains("credit card") || l.contains("payment to") || l.contains("to ") ||
            l.contains("paid to") || l.contains("deduction") || l.contains("fee") ||
            l.contains("cash-out") || l.contains("cash out") || l.contains("withdrawal") ||
            l.contains("debit") || l.contains("sale") || l.contains("purchase"))
            return false
        if (l.contains("salary") || l.contains("deposit") || l.contains("interest") ||
            l.contains("refund") || l.contains("received") || l.contains("credit") ||
            l.contains("transfer from") || l.contains("from ") && !l.contains("from account") ||
            l.contains("incoming") || l.contains("credit card payment") && l.contains("refund") ||
            l.contains("dividend") || l.contains("bonus") || l.contains("cashback"))
            return true
        // Default: negative sign → debit, no sign → try position heuristic
        return false
    }

    private fun groupTransactions(
        candidates: List<LineCandidate>,
        allLines: List<String>
    ): List<BankTransaction> {
        if (candidates.isEmpty()) return emptyList()

        val result = mutableListOf<BankTransaction>()
        val sorted = candidates.sortedBy { it.lineIndex }

        for (i in sorted.indices) {
            val current = sorted[i]

            // Build description: get text from this line's index to next transaction's line
            val nextIndex = if (i + 1 < sorted.size) sorted[i + 1].lineIndex else allLines.size
            val descLines = allLines.subList(current.lineIndex, minOf(nextIndex, allLines.size))
                .map { cleanDescription(it) }
                .filter { it.isNotBlank() }
                .distinct()

            val description = descLines.joinToString(" ").trim()
            if (description.isBlank()) continue

            result.add(
                BankTransaction(
                    date = current.date,
                    description = description,
                    amount = current.amount,
                    type = if (current.isCredit) "credit" else "debit"
                )
            )
        }

        // Deduplicate similar transactions
        return result.distinctBy {
            "${it.date}|${it.amount}|${it.description.take(40)}"
        }
    }

    private fun cleanDescription(line: String): String {
        var cleaned = line
        // Remove Ref. IDs, transaction dates, card numbers
        cleaned = refIdRx.replace(cleaned, "")
        // Remove standalone amount tokens
        cleaned = pureAmountRx.replace(cleaned, "")
        // Remove balance markers
        cleaned = balanceMarkerRx.replace(cleaned, "")
        // Remove anyDate tokens (to keep only description text)
        // But don't remove the actual description parts
        cleaned = cleaned.trim(' ', '-', '.', ',', ':', '|', '•')
        cleaned = Regex("""\s+""").replace(cleaned, " ").trim()
        return cleaned
    }
}
