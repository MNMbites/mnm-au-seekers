package com.mnm.auseekers.data

import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.domain.MarketSnapshot
import java.time.Instant
import java.util.concurrent.CancellationException

enum class FeedState {
    CONNECTING,
    DEMO,
    LIVE,
    STALE,
}

data class MarketDataFeed(
    val symbol: String,
    val capturedAt: Instant?,
    val state: FeedState,
    val snapshots: List<MarketSnapshot>,
    val bid: Double? = null,
    val ask: Double? = null,
    val ageSeconds: Int? = null,
    val statusMessage: String,
)

/** Boundary shared by bundled demo data and the versioned backend transport. */
interface MarketDataProvider {
    suspend fun latest(symbol: String): MarketDataFeed
}

class DemoMarketDataProvider(
    private val statusMessage: String = "Bundled demo snapshot; no live service is configured.",
) : MarketDataProvider {
    override suspend fun latest(symbol: String): MarketDataFeed = feed(statusMessage)

    companion object {
        fun feed(statusMessage: String): MarketDataFeed = MarketDataFeed(
            symbol = DemoMarket.symbol,
            capturedAt = null,
            state = FeedState.DEMO,
            snapshots = DemoMarket.snapshots,
            statusMessage = statusMessage,
        )
    }
}

class FallbackMarketDataProvider(
    private val primary: MarketDataProvider,
    private val fallback: MarketDataProvider = DemoMarketDataProvider(),
) : MarketDataProvider {
    override suspend fun latest(symbol: String): MarketDataFeed = try {
        primary.latest(symbol)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        fallback.latest(symbol).copy(
            statusMessage = "Live service unavailable; using bundled demo data.",
        )
    }
}
