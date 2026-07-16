package com.mnm.auseekers.validation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class NotificationAuditRecorder(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()
    private val ledger = NotificationAuditLedger(MAX_RECORDS)

    fun record(
        recordId: String,
        kind: NotificationAuditKind,
        symbol: String,
        expectedAtEpochMillis: Long? = null,
        context: String,
    ): Boolean {
        val record = NotificationAuditRecord(
            kind = kind,
            recordId = recordId.take(MAX_TEXT_LENGTH),
            symbol = symbol.take(MAX_TEXT_LENGTH),
            publishedAtEpochMillis = clock(),
            expectedAtEpochMillis = expectedAtEpochMillis,
            context = context.take(MAX_TEXT_LENGTH),
        )
        val updated = ledger.add(load(), record)
        return preferences.edit().putString(
            RECORDS_KEY,
            gson.toJson(updated),
        ).commit()
    }

    fun markOpened(recordId: String): Boolean {
        val update = ledger.markOpened(load(), recordId, clock())
        if (!update.changed) return false
        return preferences.edit().putString(RECORDS_KEY, gson.toJson(update.records)).commit()
    }

    fun remove(recordId: String): Boolean {
        val updated = ledger.remove(load(), recordId)
        return preferences.edit().putString(RECORDS_KEY, gson.toJson(updated)).commit()
    }

    fun load(): List<NotificationAuditRecord> {
        val json = preferences.getString(RECORDS_KEY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<NotificationAuditRecord>>() {}.type
            gson.fromJson<List<NotificationAuditRecord>>(json, type)
                .orEmpty()
                .filter { it.publishedAtEpochMillis > 0 }
                .take(MAX_RECORDS)
        }.getOrDefault(emptyList())
    }

    companion object {
        fun newRecordId(): String = UUID.randomUUID().toString()

        private const val PREFERENCES_NAME = "notification_validation_audit"
        private const val RECORDS_KEY = "publication_records"
        private const val MAX_RECORDS = 20
        private const val MAX_TEXT_LENGTH = 80
    }
}
