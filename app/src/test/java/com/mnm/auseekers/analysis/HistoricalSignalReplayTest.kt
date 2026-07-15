package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.HistoricalMarketDataPoint
import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.domain.TradingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HistoricalSignalReplayTest {
    private val replay = HistoricalSignalReplay()

    @Test
    fun replaysCurrentSignalAgainstNextStoredMidpoint() {
        val report = replay.replay(
            listOf(
                point("2026-07-14T10:00:00Z", 2_400.0),
                point("2026-07-14T10:15:00Z", 2_401.2),
                point("2026-07-14T10:30:00Z", 2_400.6),
            ),
            TradingMode.PRIMARY,
        )

        assertEquals(HistoricalReplayStatus.VERIFIED, report.status)
        assertEquals(3, report.observationCount)
        assertEquals(2, report.transitionCount)
        assertEquals(2, report.actionableSignalCount)
        assertEquals(2, report.buySignalCount)
        assertEquals(1, report.favorableMoveCount)
        assertEquals(1, report.adverseMoveCount)
        assertEquals(0, report.flatMoveCount)
        assertTrue(report.maximumAdverseMoveBps > 0)
    }

    @Test
    fun emptyAndSinglePointSamplesDoNotClaimResults() {
        val empty = replay.replay(emptyList(), TradingMode.PRIMARY)
        val single = replay.replay(
            listOf(point("2026-07-14T10:00:00Z", 2_400.0)),
            TradingMode.PRIMARY,
        )

        assertEquals(HistoricalReplayStatus.EMPTY, empty.status)
        assertEquals(HistoricalReplayStatus.INSUFFICIENT, single.status)
        assertNull(single.averageSignedMoveBps)
    }

    @Test
    fun rejectsMixedOrNonChronologicalHistory() {
        val mixed = listOf(
            point("2026-07-14T10:00:00Z", 2_400.0),
            point("2026-07-14T10:15:00Z", 2_401.0).copy(symbol = "EURUSD"),
        )
        val reversed = listOf(
            point("2026-07-14T10:15:00Z", 2_401.0),
            point("2026-07-14T10:00:00Z", 2_400.0),
        )

        assertEquals(
            HistoricalReplayStatus.NEEDS_REVIEW,
            replay.replay(mixed, TradingMode.PRIMARY).status,
        )
        assertEquals(
            HistoricalReplayStatus.NEEDS_REVIEW,
            replay.replay(reversed, TradingMode.PRIMARY).status,
        )
    }

    private fun point(capturedAt: String, midpoint: Double) = HistoricalMarketDataPoint(
        symbol = "XAUUSD",
        capturedAt = Instant.parse(capturedAt),
        bid = midpoint - 0.1,
        ask = midpoint + 0.1,
        snapshots = DemoMarket.snapshots,
    )
}
