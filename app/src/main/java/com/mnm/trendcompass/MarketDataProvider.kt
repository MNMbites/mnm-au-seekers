package com.mnm.trendcompass

enum class MarketDataMode { LIVE, FALLBACK, DEMO }

data class MarketSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val previousDay: PreviousDayCompass,
    val mode: MarketDataMode,
    val providerName: String,
    val updatedAt: Long,
    val note: String? = null
)

interface MarketDataProvider {
    val name: String
    fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot
}

class DemoMarketDataProvider : MarketDataProvider {
    override val name: String = "Deterministic demo feed"

    override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot {
        val candles = DemoMarketData.candles(timeframe)
        val dailyReference = PreviousDayCompass(high = 3367.4, low = 3321.8)
        return MarketSnapshot(
            symbol = symbol,
            timeframe = timeframe,
            candles = candles,
            previousDay = dailyReference,
            mode = MarketDataMode.DEMO,
            providerName = name,
            updatedAt = candles.last().time,
            note = "Synthetic candles for interface and engine validation"
        )
    }
}

class FallbackMarketDataProvider(
    private val primary: MarketDataProvider,
    private val fallback: MarketDataProvider
) : MarketDataProvider {
    override val name: String = "${primary.name} with ${fallback.name} fallback"

    override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot = try {
        primary.snapshot(symbol, timeframe).also {
            require(it.candles.size >= 90) { "Provider returned insufficient candle history" }
        }
    } catch (error: Exception) {
        fallback.snapshot(symbol, timeframe).copy(
            mode = MarketDataMode.FALLBACK,
            providerName = fallback.name,
            note = "Primary feed unavailable: ${error.message ?: "unknown error"}"
        )
    }
}

/**
 * App-level provider registry. The live Render/MT5 adapter will replace [DemoMarketDataProvider]
 * without changing the analysis engine or Compose dashboard.
 */
object AppMarketData {
    val provider: MarketDataProvider = DemoMarketDataProvider()
}
