package com.mnm.auseekers.domain

object DemoMarket {
    const val symbol = "XAU/USD"

    val snapshots = listOf(
        MarketSnapshot(
            timeframe = Timeframe.M15,
            close = 2378.40,
            ema5 = 2377.90,
            ma9 = 2377.50,
            ma21 = 2376.80,
            ma63 = 2373.20,
            ma84 = 2370.40,
            bbUpper = 2381.00,
            bbLower = 2369.10,
            rsi = 58.0,
            macdHistogram = 0.42,
        ),
        MarketSnapshot(
            timeframe = Timeframe.H1,
            close = 2378.40,
            ema5 = 2376.90,
            ma9 = 2375.60,
            ma21 = 2372.10,
            ma63 = 2361.80,
            ma84 = 2354.30,
            bbUpper = 2386.20,
            bbLower = 2358.00,
            rsi = 61.0,
            macdHistogram = 1.18,
        ),
        MarketSnapshot(
            timeframe = Timeframe.H4,
            close = 2378.40,
            ema5 = 2370.80,
            ma9 = 2365.50,
            ma21 = 2356.30,
            ma63 = 2328.40,
            ma84 = 2309.20,
            bbUpper = 2395.00,
            bbLower = 2317.60,
            rsi = 64.0,
            macdHistogram = 3.25,
        ),
    )
}
