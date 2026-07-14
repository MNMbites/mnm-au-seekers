package com.mnm.auseekers.notifications

import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketSnapshot
import com.mnm.auseekers.domain.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNotificationEvaluatorTest {
    private val evaluator = SetupNotificationEvaluator()

    @Test
    fun createsAnalysisOnlyAlertForLiveConfirmedSetup() {
        val notification = evaluator.evaluate(feed(FeedState.LIVE, ::bullish))

        requireNotNull(notification)
        assertEquals("XAUUSD", notification.symbol)
        assertTrue(notification.title.contains(Direction.BUY.label))
        assertTrue(notification.body.contains("Analysis only"))
    }

    @Test
    fun skipsStaleDataEvenWhenSignalsAlign() {
        assertNull(evaluator.evaluate(feed(FeedState.STALE, ::bullish)))
    }

    @Test
    fun skipsSetupWhenMarketHealthIsNotReady() {
        val feed = feed(FeedState.LIVE, ::bullish).copy(bid = 100.0, ask = 100.2)

        assertNull(evaluator.evaluate(feed))
    }

    @Test
    fun skipsLiveDataWithoutHigherTimeframeAgreement() {
        val snapshots = listOf(
            bullish(Timeframe.M15),
            bullish(Timeframe.H1),
            bearish(Timeframe.H4),
        )
        val feed = feed(FeedState.LIVE) { timeframe ->
            snapshots.first { it.timeframe == timeframe }
        }

        assertNull(evaluator.evaluate(feed))
    }

    private fun feed(
        state: FeedState,
        snapshot: (Timeframe) -> MarketSnapshot,
    ) = MarketDataFeed(
        symbol = "XAUUSD",
        capturedAt = null,
        state = state,
        snapshots = Timeframe.entries.map(snapshot),
        bid = 2420.10,
        ask = 2420.30,
        ageSeconds = 30,
        statusMessage = "test",
    )

    private fun bullish(timeframe: Timeframe) = MarketSnapshot(
        timeframe = timeframe,
        close = 108.0,
        ema5 = 107.0,
        ma9 = 106.0,
        ma21 = 105.0,
        ma63 = 104.0,
        ma84 = 103.0,
        bbUpper = 110.0,
        bbLower = 100.0,
        rsi = 62.0,
        macdHistogram = 1.0,
    )

    private fun bearish(timeframe: Timeframe) = MarketSnapshot(
        timeframe = timeframe,
        close = 102.0,
        ema5 = 103.0,
        ma9 = 104.0,
        ma21 = 105.0,
        ma63 = 106.0,
        ma84 = 107.0,
        bbUpper = 110.0,
        bbLower = 100.0,
        rsi = 38.0,
        macdHistogram = -1.0,
    )
}
