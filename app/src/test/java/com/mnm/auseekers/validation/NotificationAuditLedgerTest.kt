package com.mnm.auseekers.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAuditLedgerTest {
    @Test
    fun addKeepsNewestBoundedRecords() {
        val ledger = NotificationAuditLedger(maximumRecords = 2)
        val existing = listOf(record("two", 2), record("one", 1))

        val updated = ledger.add(existing, record("three", 3))

        assertEquals(listOf("three", "two"), updated.map { it.recordId })
    }

    @Test
    fun firstOpenIsRecordedOnceAndNeverPredatesPublication() {
        val ledger = NotificationAuditLedger()
        val records = listOf(record("alert", publishedAt = 2_000))

        val opened = ledger.markOpened(records, "alert", openedAtEpochMillis = 1_000)
        val repeated = ledger.markOpened(
            opened.records,
            "alert",
            openedAtEpochMillis = 4_000,
        )

        assertTrue(opened.changed)
        assertEquals(2_000L, opened.records.single().openedAtEpochMillis)
        assertFalse(repeated.changed)
        assertEquals(2_000L, repeated.records.single().openedAtEpochMillis)
    }

    @Test
    fun legacyRecordWithoutIdIsRetainedButCannotBeMarkedOpened() {
        val ledger = NotificationAuditLedger()
        val legacy = record(recordId = null, publishedAt = 1_000)

        val update = ledger.markOpened(listOf(legacy), "missing", 2_000)

        assertFalse(update.changed)
        assertNull(update.records.single().recordId)
        assertNull(update.records.single().openedAtEpochMillis)
    }

    private fun record(
        recordId: String?,
        publishedAt: Long,
    ) = NotificationAuditRecord(
        kind = NotificationAuditKind.SETUP,
        recordId = recordId,
        symbol = "XAUUSD",
        publishedAtEpochMillis = publishedAt,
        context = "BUY:CONFIRMED setup",
    )
}
