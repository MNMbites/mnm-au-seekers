package com.mnm.auseekers.notifications

import com.mnm.auseekers.analysis.EconomicCalendarAssessment
import com.mnm.auseekers.analysis.EconomicCalendarRiskLevel
import com.mnm.auseekers.data.EconomicCalendarEvent
import com.mnm.auseekers.data.EconomicCalendarPolicy
import com.mnm.auseekers.data.EconomicImpact
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketSnapshot
import com.mnm.auseekers.domain.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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

    @Test
    fun highImpactPolicyCanWarnOrSuppressAlert() {
        val event = EconomicCalendarEvent(
            eventId = "us-cpi",
            source = "test",
            title = "US CPI",
            currency = "USD",
            impact = EconomicImpact.HIGH,
            scheduledAt = Instant.parse("2026-07-14T12:20:00Z"),
        )
        val assessment = EconomicCalendarAssessment(
            EconomicCalendarRiskLevel.HIGH_IMPACT,
            "US CPI in 20 min",
            event,
        )

        val warned = evaluator.evaluate(
            feed(FeedState.LIVE, ::bullish),
            assessment,
            EconomicCalendarPolicy.WARN_ONLY,
        )
        val blocked = evaluator.evaluate(
            feed(FeedState.LIVE, ::bullish),
            assessment,
            EconomicCalendarPolicy.BLOCK_HIGH_IMPACT,
        )

        requireNotNull(warned)
        assertTrue(warned.body.contains("Calendar warning"))
        assertNull(blocked)
    }

    @Test
    fun confirmedOnlyPolicySuppressesAnEarlySetup() {
        val feed = MarketDataFeed(
            symbol = "XAUUSD",
            capturedAt = null,
            state = FeedState.LIVE,
            snapshots = listOf(
                neutral(Timeframe.M15),
                bullish(Timeframe.H1),
                bullish(Timeframe.H4),
            ),
            bid = 2420.10,
            ask = 2420.30,
            ageSeconds = 30,
            statusMessage = "test",
        )
        val defaultAlert = evaluator.evaluate(feed)
        val filtered = evaluator.evaluate(
            feed = feed,
            notificationPolicy = NotificationPolicy(
                setupStageFilter = SetupStageFilter.CONFIRMED_ONLY,
            ),
        )

        requireNotNull(defaultAlert)
        assertNull(filtered)
    }

    @Test
    fun readyOnlyPolicySuppressesACautionSetup() {
        val feed = MarketDataFeed(
            symbol = "XAUUSD",
            capturedAt = null,
            state = FeedState.LIVE,
            snapshots = listOf(
                neutral(Timeframe.M15),
                weakBullish(Timeframe.H1),
                weakBullish(Timeframe.H4),
            ),
            bid = 2420.10,
            ask = 2420.30,
            ageSeconds = 90,
            statusMessage = "test",
        )
        val defaultAlert = evaluator.evaluate(feed)
        val filtered = evaluator.evaluate(
            feed = feed,
            notificationPolicy = NotificationPolicy(
                confidenceFilter = NotificationConfidenceFilter.READY_ONLY,
            ),
        )

        requireNotNull(defaultAlert)
        assertNull(filtered)
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

    private fun neutral(timeframe: Timeframe) = MarketSnapshot(
        timeframe = timeframe,
        close = 105.0,
        ema5 = 105.0,
        ma9 = 105.0,
        ma21 = 105.0,
        ma63 = 105.0,
        ma84 = 105.0,
        bbUpper = 110.0,
        bbLower = 100.0,
        rsi = 50.0,
        macdHistogram = 0.0,
    )

    private fun weakBullish(timeframe: Timeframe) = MarketSnapshot(
        timeframe = timeframe,
        close = 106.0,
        ema5 = 105.0,
        ma9 = 104.0,
        ma21 = 103.0,
        ma63 = 103.0,
        ma84 = 103.0,
        bbUpper = 110.0,
        bbLower = 100.0,
        rsi = 50.0,
        macdHistogram = 0.0,
    )
}
