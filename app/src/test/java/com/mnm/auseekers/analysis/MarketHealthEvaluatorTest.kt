package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.MarketSnapshot
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.Timeframe
import com.mnm.auseekers.domain.TradingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHealthEvaluatorTest {
    private val evaluator = MarketHealthEvaluator()
    private val engine = SignalEngine()

    @Test
    fun ratesFreshTightConfirmedSetupReady() {
        val snapshots = Timeframe.entries.map(::bullish)
        val assessment = evaluator.evaluate(
            feed = feed(snapshots),
            analysis = engine.analyse(snapshots, TradingMode.PRIMARY),
        )

        assertEquals(MarketHealthLevel.READY, assessment.level)
        assertEquals(97, assessment.score)
        assertEquals("Strong agreement", assessment.confidenceLabel)
        assertTrue(assessment.confidenceExplanation.contains("M15, H1, H4"))
    }

    @Test
    fun explainsEarlySetupWhenEntryTimeframeIsPending() {
        val snapshots = listOf(
            neutral(Timeframe.M15),
            bullish(Timeframe.H1),
            bullish(Timeframe.H4),
        )
        val analysis = engine.analyse(snapshots, TradingMode.PRIMARY)
        val assessment = evaluator.evaluate(feed(snapshots), analysis)

        assertEquals(SetupStage.EARLY, analysis.stage)
        assertEquals("Developing agreement", assessment.confidenceLabel)
        assertTrue(assessment.confidenceExplanation.contains("M15 has not confirmed"))
    }

    @Test
    fun staleFeedIsNotReadyDespiteStrongAgreement() {
        val snapshots = Timeframe.entries.map(::bullish)
        val assessment = evaluator.evaluate(
            feed = feed(snapshots).copy(state = FeedState.STALE, ageSeconds = 180),
            analysis = engine.analyse(snapshots, TradingMode.PRIMARY),
        )

        assertEquals(MarketHealthLevel.NOT_READY, assessment.level)
        assertEquals("Unavailable", assessment.confidenceLabel)
        assertTrue(assessment.factors.any { it.state == HealthFactorState.BLOCKING })
    }

    @Test
    fun unusuallyWideRelativeSpreadBlocksReadiness() {
        val snapshots = Timeframe.entries.map(::bullish)
        val assessment = evaluator.evaluate(
            feed = feed(snapshots).copy(bid = 100.0, ask = 100.2),
            analysis = engine.analyse(snapshots, TradingMode.PRIMARY),
        )

        assertEquals(MarketHealthLevel.NOT_READY, assessment.level)
        assertTrue(
            assessment.factors.any {
                it.title == "Relative spread" && it.state == HealthFactorState.BLOCKING
            },
        )
    }

    private fun feed(snapshots: List<MarketSnapshot>) = MarketDataFeed(
        symbol = "XAUUSD",
        capturedAt = null,
        state = FeedState.LIVE,
        snapshots = snapshots,
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
        previousEma5 = 106.0,
        previousMa9 = 105.0,
        previousMa21 = 104.0,
        previousMa63 = 103.0,
        previousMa84 = 102.0,
        previousBbUpper = 109.0,
        previousBbLower = 99.0,
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
        previousEma5 = 105.0,
        previousMa9 = 105.0,
        previousMa21 = 105.0,
        previousMa63 = 105.0,
        previousMa84 = 105.0,
        previousBbUpper = 110.0,
        previousBbLower = 100.0,
    )
}
