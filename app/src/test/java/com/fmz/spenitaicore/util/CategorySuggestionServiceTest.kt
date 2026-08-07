package com.fmz.spenitaicore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySuggestionServiceTest {

    @Test
    fun `expense suggestions match merchants case-insensitively`() {
        assertEquals("Fuel/EV Charging", CategorySuggestionService.suggestExpenseCategory("PETRONAS"))
        assertEquals("Groceries", CategorySuggestionService.suggestExpenseCategory("Tesco Extra"))
        assertEquals("Food & Drinks", CategorySuggestionService.suggestExpenseCategory("Grabfood Delivery"))
        assertEquals("Subscriptions", CategorySuggestionService.suggestExpenseCategory("Netflix.com"))
        assertEquals("Toll", CategorySuggestionService.suggestExpenseCategory("Plus Highway Toll"))
        assertEquals("Transport", CategorySuggestionService.suggestExpenseCategory("Grab Ride"))
    }

    @Test
    fun `unknown expense falls back to General`() {
        assertEquals("General", CategorySuggestionService.suggestExpenseCategory(""))
        assertEquals("General", CategorySuggestionService.suggestExpenseCategory(null))
        assertEquals("General", CategorySuggestionService.suggestExpenseCategory("Some Random Shop 123"))
    }

    @Test
    fun `income suggestions cover common sources`() {
        assertEquals("Salary", CategorySuggestionService.suggestIncomeCategory("ACME SDN BHD", "monthly payslip"))
        assertEquals("Freelance", CategorySuggestionService.suggestIncomeCategory("Client A", "freelance project fee"))
        assertEquals("Business", CategorySuggestionService.suggestIncomeCategory("My Shop", "customer payment"))
        assertEquals("Rental", CategorySuggestionService.suggestIncomeCategory("Airbnb"))
        assertEquals("Bonus", CategorySuggestionService.suggestIncomeCategory("Company", "year-end bonus"))
        assertEquals("Refund", CategorySuggestionService.suggestIncomeCategory("Tax", "cashback refund"))
        assertEquals("Other Income", CategorySuggestionService.suggestIncomeCategory("Unknown Source"))
    }

    @Test
    fun `isGenericCategory detects fallback defaults`() {
        assertTrue(CategorySuggestionService.isGenericCategory("General", isIncome = false))
        assertTrue(CategorySuggestionService.isGenericCategory(null, isIncome = false))
        assertTrue(CategorySuggestionService.isGenericCategory("Other Income", isIncome = true))
        assertTrue(CategorySuggestionService.isGenericCategory("Salary", isIncome = true))

        assertFalse(CategorySuggestionService.isGenericCategory("Food & Drinks", isIncome = false))
        assertFalse(CategorySuggestionService.isGenericCategory("Freelance", isIncome = true))
    }
}
