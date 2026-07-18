package com.mnm.trendcompass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {
    @Test
    fun previousDayLevelsAreCalculatedCorrectly() {
        val compass = PreviousDayCompass(high = 110.0, low = 90.0)
        assertEquals(100.0, compass.midpoint, 0.0001)
        assertEquals(95.0, compass.q25, 0.0001)
        assertEquals(105.0, compass.q75, 0.0001)
    }

    @Test
    fun demoSeriesProducesTargetsAndStop() {
        val result = AnalysisEngine.analyze(
            Timeframe.H4,
            DemoMarketData.candles(Timeframe.H4),
            PreviousDayCompass(3367.4, 3321.8)
        )
        assertEquals(3, result.targets.size)
        assertTrue(result.score in 0..100)
        assertTrue(result.stopLoss.isFinite())
    }

    @Test
    fun movingAveragesPreserveSeriesLength() {
        val values = (1..100).map(Int::toDouble)
        assertEquals(values.size, AnalysisEngine.sma(values, 21).size)
        assertEquals(values.size, AnalysisEngine.ema(values, 5).size)
    }
}
