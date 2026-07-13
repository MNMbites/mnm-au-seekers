package com.mnm.auseekers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {
    private val engine = SignalEngine()

    @Test
    fun primaryModeBuysWhenAllTimeframesAlign() {
        val analysis = engine.analyse(
            snapshots = listOf(
                bullish(Timeframe.M15),
                bullish(Timeframe.H1),
                bullish(Timeframe.H4),
            ),
            mode = TradingMode.PRIMARY,
        )

        assertEquals(Direction.BUY, analysis.direction)
        assertEquals(SetupStage.CONFIRMED, analysis.stage)
        assertTrue(analysis.strength >= 65)
    }

    @Test
    fun primaryModeWaitsWhenHigherTimeframesDisagree() {
        val analysis = engine.analyse(
            snapshots = listOf(
                bullish(Timeframe.M15),
                bullish(Timeframe.H1),
                bearish(Timeframe.H4),
            ),
            mode = TradingMode.PRIMARY,
        )

        assertEquals(Direction.WAIT, analysis.direction)
        assertEquals(SetupStage.WATCH, analysis.stage)
    }

    @Test
    fun scalpingGuardBlocksM15SignalThatOpposesH1() {
        val analysis = engine.analyse(
            snapshots = listOf(
                bullish(Timeframe.M15),
                bearish(Timeframe.H1),
                bearish(Timeframe.H4),
            ),
            mode = TradingMode.SCALPING,
        )

        assertEquals(Direction.WAIT, analysis.direction)
        assertTrue(analysis.rationale.contains("guard"))
    }

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
