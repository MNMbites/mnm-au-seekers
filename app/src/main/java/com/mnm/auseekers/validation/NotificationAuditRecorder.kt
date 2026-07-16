package com.mnm.auseekers.validation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NotificationAuditRecorder(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()

    fun record(
        kind: NotificationAuditKind,
        symbol: String,
        expectedAtEpochMillis: Long? = null,
        context: String,
    ) {
        val updated = listOf(
            NotificationAuditRecord(
                kind = kind,
                symbol = symbol.take(MAX_TEXT_LENGTH),
                publishedAtEpochMillis = clock(),
                expectedAtEpochMillis = expectedAtEpochMillis,
                context = context.take(MAX_TEXT_LENGTH),
            ),
        ) + load()
        preferences.edit().putString(
            RECORDS_KEY,
            gson.toJson(updated.take(MAX_RECORDS)),
        ).apply()
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

    private companion object {
        const val PREFERENCES_NAME = "notification_validation_audit"
        const val RECORDS_KEY = "publication_records"
        const val MAX_RECORDS = 20
        const val MAX_TEXT_LENGTH = 80
    }
}
