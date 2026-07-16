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
        assertTrue(analysis.timeframeSignals.all { it.maTrend == TrendDirection.RISING })
        assertTrue(analysis.timeframeSignals.all { it.bollingerTrend == TrendDirection.RISING })
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

    @Test
    fun fallingMaAndBollingerTrendsBlockOtherwiseBullishStructure() {
        val analysis = engine.analyse(
            snapshots = listOf(
                bullish(Timeframe.M15).withFallingTrends(),
                bullish(Timeframe.H1).withFallingTrends(),
                bullish(Timeframe.H4).withFallingTrends(),
            ),
            mode = TradingMode.PRIMARY,
        )

        assertEquals(Direction.WAIT, analysis.direction)
        assertTrue(analysis.timeframeSignals.all { it.maTrend == TrendDirection.FALLING })
        assertTrue(analysis.timeframeSignals.all {
            it.bollingerTrend == TrendDirection.FALLING
        })
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
        previousEma5 = 106.0,
        previousMa9 = 105.0,
        previousMa21 = 104.0,
        previousMa63 = 103.0,
        previousMa84 = 102.0,
        previousBbUpper = 109.0,
        previousBbLower = 99.0,
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
        previousEma5 = 104.0,
        previousMa9 = 105.0,
        previousMa21 = 106.0,
        previousMa63 = 107.0,
        previousMa84 = 108.0,
        previousBbUpper = 111.0,
        previousBbLower = 101.0,
    )

    private fun MarketSnapshot.withFallingTrends() = copy(
        previousEma5 = ema5 + 1.0,
        previousMa9 = ma9 + 1.0,
        previousMa21 = ma21 + 1.0,
        previousMa63 = ma63 + 1.0,
        previousMa84 = ma84 + 1.0,
        previousBbUpper = bbUpper + 1.0,
        previousBbLower = bbLower + 1.0,
    )
}
