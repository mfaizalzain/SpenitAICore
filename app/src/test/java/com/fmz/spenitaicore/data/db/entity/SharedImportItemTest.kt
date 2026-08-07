package com.fmz.spenitaicore.data.db.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedImportItemTest {

    private fun item(
        kind: SharedImportKind = SharedImportKind.ExpenseReceipt,
        status: SharedImportStatus = SharedImportStatus.NeedsReview,
        retryCount: Int = 0
    ) = SharedImportItem(
        id = "1",
        filePath = "/tmp/x.pdf",
        displayName = "x.pdf",
        kind = kind,
        status = status,
        retryCount = retryCount
    )

    @Test
    fun `canProcess is false for unknown or terminal states`() {
        assertFalse(item(kind = SharedImportKind.Unknown).canProcess)
        assertFalse(item(status = SharedImportStatus.Processing).canProcess)
        assertFalse(item(status = SharedImportStatus.Completed).canProcess)
        assertFalse(item(status = SharedImportStatus.InQueue).canProcess)
    }

    @Test
    fun `canProcess is true for needs review and retryable failures`() {
        assertTrue(item().canProcess)
        assertTrue(item(status = SharedImportStatus.Failed, retryCount = 2).canProcess)
        assertTrue(item(status = SharedImportStatus.Duplicate, retryCount = 2).canProcess)
    }

    @Test
    fun `retries are capped at three`() {
        assertTrue(item(status = SharedImportStatus.Failed, retryCount = 2).canRetry)
        assertFalse(item(status = SharedImportStatus.Failed, retryCount = 3).canRetry)
        assertFalse(item(status = SharedImportStatus.Completed).canRetry)
    }

    @Test
    fun `isCompleted reflects completed status`() {
        assertTrue(item(status = SharedImportStatus.Completed).isCompleted)
        assertFalse(item().isCompleted)
    }
}
