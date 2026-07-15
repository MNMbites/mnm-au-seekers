# MNM AU Seekers

MNM AU Seekers is an Android-first market analysis companion for XAU/USD and
other MT5 symbols. The current milestones cover explainable analysis, capital
protection, and a read-only MT5 market-data path; the project does **not** place
trades. PostgreSQL persistence and shared watchlists are available for deployed
backends.

## What is included

- Primary mode for M15/H1/H4 trend alignment
- Optional Scalping mode with an H1 trend guard
- EMA 5, MA 9/21/63/84, Bollinger Bands 21/2, RSI, and MACD inputs
- Safe, Semi-aggressive, and Aggressive risk profiles
- A small-account guard that refuses a trade when the broker's minimum lot
  would exceed the selected risk, including balances around USD 15
- Demo market snapshots so the UI can be reviewed before a data provider is
  connected
- A versioned Android market-data provider boundary
- A read-only Android HTTP provider with live/stale health, manual refresh, and
  labelled demo fallback
- Backend-driven multi-symbol selection and opt-in 30/60-minute setup alerts
- Explainable market readiness from source, freshness, relative spread, and
  timeframe agreement
- An isolated on-device paper ledger with spread-aware simulated entry/exit,
  mark-to-market P&L, and closed-trade history
- Deterministic paper-P&L replay, drawdown statistics, and user-initiated CSV
  sharing
- A private, manual analysis journal with optional review notes and descriptive
  snapshot statistics
- Bounded historical snapshot retrieval and deterministic next-snapshot signal
  replay without synthetic fills or P&L claims
- A read-only MT5 EA that publishes quotes and indicator snapshots
- A FastAPI ingestion service with token authentication and stale-data status
- Durable snapshot history and case-insensitive watchlists with Alembic
  migrations
- Unit tests for signal agreement and risk sizing
- Pull-request CI for Android and backend validation

## Safety status

The Android UI uses labelled demo data unless a backend URL is injected at
build time. The bridge only exports market quotes and indicator values; it has
no order, position, account-balance, OpenAI API key, MT5 login, or password
capability. Bridge tokens are supplied to the backend and EA at runtime and
must never be committed to this repository or shipped in the Android app.

## Build

Open the repository in Android Studio with JDK 17, allow Gradle sync to finish,
then run the `app` configuration on an Android 8.0 (API 26) or newer device.

Command-line builds use Gradle 8.11.1:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions installs the pinned Gradle version, so a binary wrapper is not
required in this bootstrap.

Backend tests use Python 3.10 or newer:

```bash
python -m pip install -r backend/requirements-dev.txt
python -m pytest
```

See [docs/mt5-bridge.md](docs/mt5-bridge.md) for backend and MT5 EA setup, and
[docs/android-live-data.md](docs/android-live-data.md) for Android connectivity.
For production persistence and watchlist administration, see
[docs/postgresql.md](docs/postgresql.md). Multi-symbol Android and notification
behavior is documented in
[docs/android-notifications.md](docs/android-notifications.md). Readiness scoring
and confidence language are specified in
[docs/market-health.md](docs/market-health.md). The local simulation boundary is
documented in [docs/paper-trading.md](docs/paper-trading.md).
Manual analysis-journal behavior and privacy boundaries are documented in
[docs/analysis-journal.md](docs/analysis-journal.md).
Historical replay semantics and limitations are specified in
[docs/historical-replay.md](docs/historical-replay.md).

## Roadmap

1. Review and merge the stacked milestones in dependency order.
2. Validate paper behavior on physical devices and MT5 demo feeds.
3. Add economic-calendar risk context before broader v2 features.
4. Keep broker execution out of scope until paper behavior is validated and a
   separate security design is reviewed.

Trading involves substantial risk. Analysis and forecasts are not guarantees,
and the app must not increase a position merely to meet a broker minimum.
