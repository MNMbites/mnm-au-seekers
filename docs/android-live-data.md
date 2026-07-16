# Android live market data

The Android app can now read the versioned market-data endpoint introduced by
the MT5 bridge milestone. The backend URL is a build-time setting, not a field
that accepts arbitrary user input.

## Configure a build

Set `MNM_MARKET_DATA_BASE_URL` to the backend origin before running Gradle:

```bash
export MNM_MARKET_DATA_BASE_URL='https://market-data.example.com'
gradle --no-daemon assembleDebug
```

For an Android emulator reaching a backend on the development computer, use:

```bash
export MNM_MARKET_DATA_BASE_URL='http://10.0.2.2:8000'
```

Cleartext traffic is denied by default and permitted only for the emulator and
loopback hosts. Deployed backends must use HTTPS.

## Runtime behaviour

- With no configured URL, the app clearly labels bundled demo data.
- With a configured URL, the app requests
  `GET /api/v1/market-data/XAUUSD` away from the main thread.
- A valid response replaces the demo snapshots and displays bid, ask, age, and
  a `LIVE DATA` or `STALE DATA` connection state.
- A timeout, malformed response, or non-200 status falls back to bundled demo
  data and displays the fallback state when no validated snapshot is available.
- After a validated live snapshot has been shown, a failed refresh retains that
  snapshot as explicitly stale instead of substituting bundled demo analysis.
- `Refresh all` repeats the market request and also reloads the watchlist,
  economic calendar, and bounded history without restarting the app.
- The app reads `GET /api/v1/watchlist` and exposes every configured symbol as
  a selector, falling back to XAU/USD when the list is empty or unavailable.

## Foreground live analysis

The `Live analysis refresh` selector offers Manual, 15-second, 30-second, and
60-second modes. The selection is stored in private app preferences. Automatic
refresh is opt-in and available only when a live-service URL was compiled into
the build.

While the app is in the foreground, each automatic cycle requests only the
latest selected-symbol snapshot. New bid, ask, M15, H1, and H4 values flow
through the existing deterministic signal and market-health evaluators. The
watchlist, calendar, and historical replay are not polled on this short cadence;
use `Refresh all` when those supporting datasets should be reloaded.

Polling pauses when the activity leaves the foreground and restarts with an
immediate market refresh when it returns. This is bounded HTTP polling, not a
tick stream, and Android or network scheduling can delay a cycle. The displayed
snapshot capture time and age remain the authoritative freshness evidence.

If a cycle cannot reach or validate the service, the most recent validated
snapshot remains visible but changes to `STALE DATA`. Readiness and downstream
analysis therefore remain blocked until another validated live snapshot arrives.
Changing symbols never reuses a quote from the previously selected symbol.

The Android client never receives the MT5 bridge token. That secret is only
used between the authorised EA and the backend ingestion endpoint. The Android
transport remains read-only and contains no order route.

See [android-notifications.md](android-notifications.md) for multi-symbol bridge
setup and optional 30/60-minute analysis alerts.
