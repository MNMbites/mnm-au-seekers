package com.mnm.trendcompass

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AnalysisEngineTest {
    @Test
    fun previousDayLevelsAreCalculatedCorrectly() {
        val compass = PreviousDayCompass(high = 110.0, low = 90.0)
        assertEquals(100.0, compass.midpoint, 0.0001)
        assertEquals(95.0, compass.q25, 0.0001)
        assertEquals(105.0, compass.q75, 0.0001)
    }

    @Test
    fun demoSeriesProducesTargetsStopAndFanSummary() {
        val result = AnalysisEngine.analyze(
            Timeframe.H4,
            DemoMarketData.candles(Timeframe.H4),
            PreviousDayCompass(3367.4, 3321.8)
        )
        assertEquals(3, result.targets.size)
        assertTrue(result.score in 0..100)
        assertTrue(result.stopLoss.isFinite())
        assertTrue(result.fanSummary.anchorQuality in 0..100)
        assertTrue(result.fanSummary.retracementDepth >= 0)
        assertTrue(result.fanSummary.followThroughScore in 0..100)
        assertTrue(result.fanSummary.breakdownRisk in 0..100)
    }

    @Test
    fun executiveSummaryContainsBackToBackFacts() {
        val result = AnalysisEngine.analyze(
            Timeframe.H1,
            DemoMarketData.candles(Timeframe.H1),
            PreviousDayCompass(3367.4, 3321.8)
        )
        assertTrue(result.executiveSummary.headline.isNotBlank())
        assertTrue(result.executiveSummary.conclusion.isNotBlank())
        assertTrue(result.executiveSummary.facts.size >= 5)
        assertTrue(result.executiveSummary.facts.any { it.contains("Bollinger") })
        assertTrue(result.executiveSummary.facts.any { it.contains("Fibonacci") })
    }

    @Test
    fun demoProviderReturnsCompleteSnapshot() {
        val snapshot = DemoMarketDataProvider().snapshot("XAUUSD", Timeframe.H4)
        assertEquals("XAUUSD", snapshot.symbol)
        assertEquals(Timeframe.H4, snapshot.timeframe)
        assertEquals(MarketDataMode.DEMO, snapshot.mode)
        assertTrue(snapshot.candles.size >= 90)
    }

    @Test
    fun fallbackProviderMarksFallbackModeWhenPrimaryFails() {
        val failing = object : MarketDataProvider {
            override val name = "Broken live feed"
            override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot = error("offline")
        }
        val provider = FallbackMarketDataProvider(failing, DemoMarketDataProvider())
        val snapshot = provider.snapshot("XAUUSD", Timeframe.H1)
        assertEquals(MarketDataMode.FALLBACK, snapshot.mode)
        assertTrue(snapshot.note.orEmpty().contains("offline"))
        assertTrue(snapshot.candles.size >= 90)
        assertEquals(FeedFailureType.UNKNOWN, snapshot.diagnostics?.failureType)
    }

    @Test
    fun feedFailuresAreClassifiedForFieldDiagnostics() {
        val provider = FallbackMarketDataProvider(DemoMarketDataProvider(), DemoMarketDataProvider())
        assertEquals(FeedFailureType.DNS, provider.classifyFailure(UnknownHostException("host")))
        assertEquals(FeedFailureType.TIMEOUT, provider.classifyFailure(SocketTimeoutException("slow")))
        assertEquals(FeedFailureType.SCHEMA, provider.classifyFailure(JSONException("bad json")))
        assertEquals(FeedFailureType.HTTP, provider.classifyFailure(IllegalArgumentException("HTTP 503")))
        assertEquals(FeedFailureType.DATA, provider.classifyFailure(IllegalArgumentException("invalid OHLC candle")))
    }

    @Test
    fun movingAveragesPreserveSeriesLength() {
        val values = (1..100).map(Int::toDouble)
        assertEquals(values.size, AnalysisEngine.sma(values, 21).size)
        assertEquals(values.size, AnalysisEngine.ema(values, 5).size)
    }
}
