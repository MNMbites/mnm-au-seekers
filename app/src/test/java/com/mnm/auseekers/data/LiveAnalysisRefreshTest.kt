package com.mnm.auseekers.data

import com.mnm.auseekers.domain.DemoMarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveAnalysisRefreshTest {
    private val reducer = LiveAnalysisFeedReducer()

    @Test
    fun failedRefreshRetainsLastValidatedSnapshotAsStale() {
        val previous = feed(
            state = FeedState.LIVE,
            capturedAt = Instant.parse("2026-07-16T04:00:00Z"),
            bid = 2_420.10,
            ask = 2_420.30,
            ageSeconds = 30,
        )

        val merged = reducer.merge(
            previous = previous,
            refreshed = demo(),
            checkedAt = Instant.parse("2026-07-16T04:03:00Z"),
        )

        assertEquals(FeedState.STALE, merged.state)
        assertEquals(previous.snapshots, merged.snapshots)
        assertEquals(2_420.10, merged.bid!!, 0.0001)
        assertEquals(180, merged.ageSeconds)
        assertTrue(merged.statusMessage.contains("last validated"))
    }

    @Test
    fun retainedAgeNeverMovesBackwardsWhenDeviceClockIsBehind() {
        val previous = feed(
            state = FeedState.STALE,
            capturedAt = Instant.parse("2026-07-16T04:00:00Z"),
            bid = 2_420.10,
            ask = 2_420.30,
            ageSeconds = 240,
        )

        val merged = reducer.merge(
            previous = previous,
            refreshed = demo(),
            checkedAt = Instant.parse("2026-07-16T04:01:00Z"),
        )

        assertEquals(240, merged.ageSeconds)
    }

    @Test
    fun successfulRefreshReplacesPreviousAnalysis() {
        val previous = feed(
            state = FeedState.LIVE,
            capturedAt = Instant.parse("2026-07-16T04:00:00Z"),
            bid = 2_420.10,
            ask = 2_420.30,
            ageSeconds = 30,
        )
        val refreshed = feed(
            state = FeedState.LIVE,
            capturedAt = Instant.parse("2026-07-16T04:01:00Z"),
            bid = 2_421.10,
            ask = 2_421.30,
            ageSeconds = 5,
        )

        assertEquals(
            refreshed,
            reducer.merge(previous, refreshed, Instant.parse("2026-07-16T04:01:05Z")),
        )
    }

    @Test
    fun failureForDifferentSymbolDoesNotReuseOldQuote() {
        val previous = feed(
            state = FeedState.LIVE,
            capturedAt = Instant.parse("2026-07-16T04:00:00Z"),
            bid = 2_420.10,
            ask = 2_420.30,
            ageSeconds = 30,
        )
        val differentSymbol = demo().copy(symbol = "EURUSD")

        assertEquals(
            differentSymbol,
            reducer.merge(previous, differentSymbol, Instant.parse("2026-07-16T04:01:00Z")),
        )
    }

    private fun feed(
        state: FeedState,
        capturedAt: Instant,
        bid: Double,
        ask: Double,
        ageSeconds: Int,
    ) = MarketDataFeed(
        symbol = DEFAULT_SYMBOL,
        capturedAt = capturedAt,
        state = state,
        snapshots = DemoMarket.snapshots,
        bid = bid,
        ask = ask,
        ageSeconds = ageSeconds,
        statusMessage = state.name,
    )

    private fun demo() = DemoMarketDataProvider.feed(
        statusMessage = "Live service unavailable; using bundled demo data.",
    )
}
