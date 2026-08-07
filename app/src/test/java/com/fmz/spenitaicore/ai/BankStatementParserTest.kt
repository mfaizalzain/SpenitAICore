package com.fmz.spenitaicore.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStatementParserTest {

    private val sampleStatement = listOf(
        "Maybank Berhad",
        "Statement period: 1 Mar 2026 - 31 Mar 2026",
        "Account ending 1234",
        "Opening balance 1,000.00",
        "01/03/2026 SALARY CR 5,000.00",
        "02/03/2026 GRAB RIDE 25.50 DR",
        "03/03/2026 TESCO STORE 120.30",
        "05/03/2026 REFUND FROM SHOPEE (45.00)",
        "Closing balance 5,809.20"
    )

    @Test
    fun `parses credit and debit transactions from statement lines`() {
        val transactions = BankStatementParser.parseLines(sampleStatement)

        val salary = transactions.firstOrNull { it.description.contains("SALARY", ignoreCase = true) }
        assertTrue(salary != null)
        assertEquals(5000.0, salary!!.amount, 0.001)
        assertEquals("credit", salary.type)
        assertEquals("2026-03-01", salary.date)

        val grab = transactions.firstOrNull { it.description.contains("GRAB", ignoreCase = true) }
        assertTrue(grab != null)
        assertEquals(-25.50, grab!!.amount, 0.001)
        assertEquals("debit", grab.type)

        val tesco = transactions.firstOrNull { it.description.contains("TESCO", ignoreCase = true) }
        assertTrue(tesco != null)
        assertEquals(-120.30, tesco!!.amount, 0.001)
        assertEquals("debit", tesco.type)
    }

    @Test
    fun `parenthetical amounts are treated as debits`() {
        val transactions = BankStatementParser.parseLines(sampleStatement)
        val refund = transactions.firstOrNull { it.description.contains("REFUND", ignoreCase = true) }
        assertTrue(refund != null)
        assertEquals(-45.0, refund!!.amount, 0.001)
        assertEquals("debit", refund.type)
    }

    @Test
    fun `balance and header lines are skipped`() {
        val transactions = BankStatementParser.parseLines(sampleStatement)
        assertEquals(4, transactions.size)
        assertTrue(transactions.none { it.description.contains("balance", ignoreCase = true) })
    }

    @Test
    fun `compact DDMmmYYYY dates are parsed`() {
        val lines = listOf(
            "Statement for March 2026",
            "27Mar2026 INTEREST 12.00 CR",
            "28Mar2026 WITHDRAWAL ATM 200.00"
        )
        val transactions = BankStatementParser.parseLines(lines)
        val interest = transactions.firstOrNull { it.description.contains("INTEREST", ignoreCase = true) }
        assertTrue(interest != null)
        assertEquals("2026-03-27", interest!!.date)
        assertEquals(12.0, interest.amount, 0.001)
    }

    @Test
    fun `empty input produces no transactions`() {
        assertTrue(BankStatementParser.parseLines(emptyList()).isEmpty())
    }
}
