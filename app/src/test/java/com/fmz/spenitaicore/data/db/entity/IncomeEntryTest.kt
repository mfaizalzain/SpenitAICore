package com.fmz.spenitaicore.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeEntryTest {

    @Test
    fun `category emoji maps known and unknown categories`() {
        assertEquals("\uD83D\uDCBC", IncomeEntry.categoryEmoji("Salary"))
        assertEquals("\uD83D\uDCBB", IncomeEntry.categoryEmoji("Freelance"))
        assertEquals("\uD83D\uDCB0", IncomeEntry.categoryEmoji("Totally Unknown"))
    }

    @Test
    fun `isPdf and isImage reflect the attachment type`() {
        val pdf = IncomeEntry(imagePath = "/data/foo.PDF")
        assertTrue(pdf.isPdf)
        assertFalse(pdf.isImage)

        val image = IncomeEntry(imagePath = "/data/foo.jpg")
        assertFalse(image.isPdf)
        assertTrue(image.isImage)

        val none = IncomeEntry(imagePath = null)
        assertFalse(none.isPdf)
        assertFalse(none.isImage)
    }
}
