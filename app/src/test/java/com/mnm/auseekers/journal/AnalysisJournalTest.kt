package com.mnm.auseekers.journal

import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.domain.TradingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisJournalTest {
    private var now = 1_000L
    private var nextId = 0
    private val ledger = AnalysisJournalLedger(
        clock = { now },
        idFactory = { "journal-${++nextId}" },
    )

    @Test
    fun storesNormalizedManualSnapshot() {
        val journal = ledger.add(
            AnalysisJournal(),
            request(note = "  Wait for M15 close.  ").copy(symbol = " xauusd "),
        )

        val entry = journal.entries.single()
        assertEquals("journal-1", entry.id)
        assertEquals("XAUUSD", entry.symbol)
        assertEquals("Wait for M15 close.", entry.note)
        assertEquals(1_000L, entry.recordedAtEpochMillis)
        assertEquals(FeedState.LIVE, entry.feedState)
    }

    @Test
    fun rejectsOversizedNotesAndInvalidScores() {
        assertTrue(
            runCatching {
                ledger.add(AnalysisJournal(), request(note = "x".repeat(281)))
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                ledger.add(AnalysisJournal(), request(note = "").copy(marketHealthScore = 101))
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun keepsNewestTwoHundredEntries() {
        var journal = AnalysisJournal()
        repeat(205) { index ->
            now = index + 1L
            journal = ledger.add(journal, request(note = index.toString()))
        }

        assertEquals(200, journal.entries.size)
        assertEquals("204", journal.entries.first().note)
        assertEquals("5", journal.entries.last().note)
    }

    @Test
    fun calculatesDescriptiveSnapshotStatistics() {
        val entries = listOf(
            entry(Direction.BUY, SetupStage.CONFIRMED, MarketHealthLevel.READY, 80),
            entry(Direction.SELL, SetupStage.EARLY, MarketHealthLevel.CAUTION, 60),
            entry(Direction.WAIT, SetupStage.WATCH, MarketHealthLevel.NOT_READY, 40),
        )

        val stats = AnalysisJournalStatisticsCalculator().calculate(AnalysisJournal(entries))

        assertEquals(3, stats.entryCount)
        assertEquals(1, stats.buyCount)
        assertEquals(1, stats.sellCount)
        assertEquals(1, stats.waitCount)
        assertEquals(1, stats.readyCount)
        assertEquals(1, stats.confirmedCount)
        assertEquals(60.0, stats.averageHealthScore!!, 0.0001)
    }

    @Test
    fun emptyJournalHasNoAverage() {
        val stats = AnalysisJournalStatisticsCalculator().calculate(AnalysisJournal())

        assertEquals(0, stats.entryCount)
        assertNull(stats.averageHealthScore)
    }

    private fun request(note: String) = AddAnalysisJournalEntry(
        symbol = "XAUUSD",
        mode = TradingMode.PRIMARY,
        direction = Direction.BUY,
        stage = SetupStage.CONFIRMED,
        signalStrength = 82,
        rationale = "H1 and H4 align while M15 confirms.",
        marketHealthLevel = MarketHealthLevel.READY,
        marketHealthScore = 80,
        confidenceLabel = "Strong agreement",
        feedState = FeedState.LIVE,
        bid = 2_400.1,
        ask = 2_400.3,
        feedCapturedAtEpochMillis = 900,
        note = note,
    )

    private fun entry(
        direction: Direction,
        stage: SetupStage,
        level: MarketHealthLevel,
        score: Int,
    ) = AnalysisJournalEntry(
        id = "$direction-$score",
        symbol = "XAUUSD",
        mode = TradingMode.PRIMARY,
        direction = direction,
        stage = stage,
        signalStrength = score,
        rationale = "Recorded rationale",
        marketHealthLevel = level,
        marketHealthScore = score,
        confidenceLabel = "Recorded agreement",
        feedState = FeedState.LIVE,
        bid = 2_400.1,
        ask = 2_400.3,
        feedCapturedAtEpochMillis = 1,
        note = "",
        recordedAtEpochMillis = score.toLong(),
    )
}
