package com.mnm.auseekers.data

import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.domain.MarketSnapshot
import java.time.Instant

enum class FeedState {
    DEMO,
    LIVE,
    STALE,
}

data class MarketDataFeed(
    val symbol: String,
    val capturedAt: Instant?,
    val state: FeedState,
    val snapshots: List<MarketSnapshot>,
)

/** Boundary for demo data now and the versioned backend transport next. */
interface MarketDataProvider {
    suspend fun latest(symbol: String): MarketDataFeed
}

class DemoMarketDataProvider : MarketDataProvider {
    override suspend fun latest(symbol: String): MarketDataFeed = MarketDataFeed(
        symbol = DemoMarket.symbol,
        capturedAt = null,
        state = FeedState.DEMO,
        snapshots = DemoMarket.snapshots,
    )
}
