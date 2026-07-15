package com.mnm.auseekers.journal

import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.domain.TradingMode
import java.util.Locale
import java.util.UUID

data class AnalysisJournalEntry(
    val id: String,
    val symbol: String,
    val mode: TradingMode,
    val direction: Direction,
    val stage: SetupStage,
    val signalStrength: Int,
    val rationale: String,
    val marketHealthLevel: MarketHealthLevel,
    val marketHealthScore: Int,
    val confidenceLabel: String,
    val feedState: FeedState,
    val bid: Double?,
    val ask: Double?,
    val feedCapturedAtEpochMillis: Long?,
    val note: String,
    val recordedAtEpochMillis: Long,
)

data class AnalysisJournal(
    val entries: List<AnalysisJournalEntry> = emptyList(),
)

data class AddAnalysisJournalEntry(
    val symbol: String,
    val mode: TradingMode,
    val direction: Direction,
    val stage: SetupStage,
    val signalStrength: Int,
    val rationale: String,
    val marketHealthLevel: MarketHealthLevel,
    val marketHealthScore: Int,
    val confidenceLabel: String,
    val feedState: FeedState,
    val bid: Double?,
    val ask: Double?,
    val feedCapturedAtEpochMillis: Long?,
    val note: String,
)

data class AnalysisJournalStatistics(
    val entryCount: Int,
    val buyCount: Int,
    val sellCount: Int,
    val waitCount: Int,
    val readyCount: Int,
    val confirmedCount: Int,
    val averageHealthScore: Double?,
)

class AnalysisJournalLedger(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun add(
        journal: AnalysisJournal,
        request: AddAnalysisJournalEntry,
    ): AnalysisJournal {
        val symbol = request.symbol.trim().uppercase(Locale.US)
        val confidenceLabel = request.confidenceLabel.trim()
        val rationale = request.rationale.trim()
        val note = request.note.trim()
        require(symbol.length in 2..32 && symbol.none(Char::isWhitespace)) {
            "Journal symbol is invalid"
        }
        require(request.marketHealthScore in 0..100) {
            "Journal market-health score must be from 0 to 100"
        }
        require(request.signalStrength in 0..100) {
            "Journal signal strength must be from 0 to 100"
        }
        require(rationale.isNotEmpty() && rationale.length <= RATIONALE_LIMIT) {
            "Journal rationale is invalid"
        }
        require(confidenceLabel.isNotEmpty() && confidenceLabel.length <= 80) {
            "Journal confidence label is invalid"
        }
        require(note.length <= NOTE_LIMIT) {
            "Journal note must be $NOTE_LIMIT characters or fewer"
        }
        require(
            (request.bid == null && request.ask == null) ||
                (
                    request.bid != null && request.ask != null &&
                        request.bid.isFinite() && request.ask.isFinite() &&
                        request.bid > 0 && request.ask >= request.bid
                ),
        ) { "Journal quote is invalid" }
        require(
            request.feedCapturedAtEpochMillis == null ||
                request.feedCapturedAtEpochMillis > 0,
        ) { "Journal feed timestamp is invalid" }
        val recordedAt = clock()
        require(recordedAt > 0) { "Journal timestamp is invalid" }

        val entry = AnalysisJournalEntry(
            id = idFactory(),
            symbol = symbol,
            mode = request.mode,
            direction = request.direction,
            stage = request.stage,
            signalStrength = request.signalStrength,
            rationale = rationale,
            marketHealthLevel = request.marketHealthLevel,
            marketHealthScore = request.marketHealthScore,
            confidenceLabel = confidenceLabel,
            feedState = request.feedState,
            bid = request.bid,
            ask = request.ask,
            feedCapturedAtEpochMillis = request.feedCapturedAtEpochMillis,
            note = note,
            recordedAtEpochMillis = recordedAt,
        )
        return journal.copy(entries = (listOf(entry) + journal.entries).take(HISTORY_LIMIT))
    }

    private companion object {
        const val HISTORY_LIMIT = 200
        const val NOTE_LIMIT = 280
        const val RATIONALE_LIMIT = 500
    }
}

class AnalysisJournalStatisticsCalculator {
    fun calculate(journal: AnalysisJournal): AnalysisJournalStatistics {
        val entries = journal.entries
        return AnalysisJournalStatistics(
            entryCount = entries.size,
            buyCount = entries.count { it.direction == Direction.BUY },
            sellCount = entries.count { it.direction == Direction.SELL },
            waitCount = entries.count { it.direction == Direction.WAIT },
            readyCount = entries.count { it.marketHealthLevel == MarketHealthLevel.READY },
            confirmedCount = entries.count { it.stage == SetupStage.CONFIRMED },
            averageHealthScore = entries.takeIf { it.isNotEmpty() }
                ?.map { it.marketHealthScore }
                ?.average(),
        )
    }
}
