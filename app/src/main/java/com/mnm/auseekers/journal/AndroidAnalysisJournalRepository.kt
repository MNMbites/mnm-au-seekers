package com.mnm.auseekers.journal

import android.content.Context
import com.google.gson.Gson

class AndroidAnalysisJournalRepository(
    context: Context,
    private val ledger: AnalysisJournalLedger = AnalysisJournalLedger(),
    private val gson: Gson = Gson(),
) {
    private val preferences = context.getSharedPreferences(
        "analysis_journal",
        Context.MODE_PRIVATE,
    )

    fun load(): AnalysisJournal {
        val serialized = preferences.getString(JOURNAL_KEY, null) ?: return AnalysisJournal()
        return runCatching { gson.fromJson(serialized, AnalysisJournal::class.java) }
            .getOrNull()
            ?: AnalysisJournal()
    }

    fun add(request: AddAnalysisJournalEntry): AnalysisJournal =
        save(ledger.add(load(), request))

    private fun save(journal: AnalysisJournal): AnalysisJournal {
        preferences.edit().putString(JOURNAL_KEY, gson.toJson(journal)).apply()
        return journal
    }

    private companion object {
        const val JOURNAL_KEY = "journal"
    }
}
