# Android watchlists and setup notifications

The Android app reads the backend watchlist and lets the user switch analysis
between its symbols. Optional background alerts can re-check that watchlist at
an approximate 30- or 60-minute interval. Alerts remain analysis-only and never
place or prepare an order.

## Prerequisites

Build the app with a deployed HTTPS backend URL as described in
[android-live-data.md](android-live-data.md). Add symbols through the protected
backend watchlist API described in [postgresql.md](postgresql.md). An empty
watchlist falls back to `XAUUSD`.

Each selected symbol also needs fresh bridge data. Attach the read-only EA to a
chart for every MT5 symbol the backend should receive. Broker-specific names
such as `XAUUSD.a` must match the watchlist exactly apart from letter case.

## Foreground selection

On refresh and startup, Android requests:

```text
GET /api/v1/watchlist
```

The returned symbols appear as horizontally scrollable chips. Selecting one
requests `GET /api/v1/market-data/{symbol}` and runs the existing M15/H1/H4
analysis for that symbol. If the watchlist cannot be loaded, the safe XAU/USD
demo fallback remains available and clearly labelled.

## Opt-in alerts

The Setup notifications control offers Off, 30 min, and 60 min. Choosing an
interval requests Android notification permission when the operating system
requires it. Denying permission leaves alerts off.

Android WorkManager schedules network-constrained, persistent, approximate
checks. The worker:

1. reads the public backend watchlist;
2. fetches the latest snapshot for every symbol;
3. ignores demo, stale, unavailable, and `WAIT` results;
4. applies the Primary-mode signal engine and market-health evaluator; and
5. posts an analysis-only alert for a Ready/Caution early or confirmed Buy/Sell
   setup.

The same symbol/direction/stage is notified once. It can alert again only after
the setup clears or changes, preventing a notification every polling interval.
Turning the control Off cancels the unique periodic work. Android may defer a
check for battery and system scheduling reasons, so these intervals are not an
exact market timer.

Notification evaluation uses only the public read endpoints. The Android app
does not receive the bridge token, watchlist admin token, MT5 credentials,
account data, or any execution capability.

Pre-market briefings use a separate opt-in schedule described in
[pre-market-briefings.md](pre-market-briefings.md). They share Android's
notification permission but have their own WorkManager task, channel, settings,
and once-per-session deduplication state.

For physical-device timing checks, successful notification publication requests
are recorded locally in a bounded, redacted audit described in
[device-validation.md](device-validation.md). This evidence does not prove that
Android displayed the notification or that the user saw it.

See [market-health.md](market-health.md) for the readiness factors and confidence
language included in each alert.
