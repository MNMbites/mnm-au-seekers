# MNM AU Seekers

MNM AU Seekers is an Android-first market analysis companion for XAU/USD and
other MT5 symbols. The current milestones cover explainable analysis, capital
protection, and a read-only MT5 market-data path; the project does **not** place
trades.

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
- A read-only MT5 EA that publishes quotes and indicator snapshots
- A FastAPI ingestion service with token authentication and stale-data status
- Unit tests for signal agreement and risk sizing
- Pull-request CI for Android and backend validation

## Safety status

The Android UI still uses labelled demo data. The bridge only exports market
quotes and indicator values; it has no order, position, account-balance, OpenAI
API key, MT5 login, or password capability. Bridge tokens are supplied at
runtime and must never be committed to this repository.

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

See [docs/mt5-bridge.md](docs/mt5-bridge.md) for backend and MT5 EA setup.

## Roadmap

1. Connect the Android provider to the versioned backend and expose connection
   health while retaining the demo fallback.
2. Persist market snapshots and watchlists in PostgreSQL.
3. Add optional 30–60 minute setup notifications and extra symbols.
4. Add paper trading before any opt-in execution feature is considered.

Trading involves substantial risk. Analysis and forecasts are not guarantees,
and the app must not increase a position merely to meet a broker minimum.
