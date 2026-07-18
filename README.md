# MNM Trend Compass

A focused Android analysis module for MNM AU Seekers. It aligns Bollinger Bands, moving averages, EMA direction, previous-day range position, and an adaptive Fibonacci fan across M15, H1, H4, and D1.

## Prototype scope

- Kotlin + Jetpack Compose Android app
- Dark-green and gold mobile interface
- Candlestick chart with EMA5, MA21/63/84 and BB21/2 overlays
- Previous-day high, midpoint and low compass
- Adaptive latest-trend Fibonacci fan
- Four-timeframe weighted consensus
- Tiered TP1/TP2/TP3 travel estimates
- Structural stop-loss estimate
- Unit tests and GitHub Actions debug APK build

The first build intentionally uses deterministic demo candles. Live market connectivity is the next integration layer and should implement a replaceable `MarketDataProvider` rather than coupling the analysis engine to one broker.

## Build

```bash
gradle testDebugUnitTest assembleDebug
```

The generated APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Analytical warning

Target and stop zones are probabilistic analytical estimates. They are not guaranteed prices or automated trade instructions.
