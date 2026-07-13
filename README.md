# MNM AU Seekers

MNM AU Seekers is an Android-first market analysis companion for XAU/USD and
other MT5 symbols. The first milestone focuses on explainable analysis and
capital protection; it does **not** place trades.

## What is included

- Primary mode for M15/H1/H4 trend alignment
- Optional Scalping mode with an H1 trend guard
- EMA 5, MA 9/21/63/84, Bollinger Bands 21/2, RSI, and MACD inputs
- Safe, Semi-aggressive, and Aggressive risk profiles
- A small-account guard that refuses a trade when the broker's minimum lot
  would exceed the selected risk, including balances around USD 15
- Demo market snapshots so the UI can be reviewed before a data provider is
  connected
- Unit tests for signal agreement and risk sizing
- Pull-request CI for unit tests, lint, and debug APK assembly

## Safety status

The current app uses labelled demo data. It has no broker credentials, OpenAI
API key, MT5 login, order placement, or background notification service. A live
price feed and an authorised MT5 bridge should be added as separate, reviewable
changes. Passwords must never be stored in the app or committed to this
repository.

## Build

Open the repository in Android Studio with JDK 17, allow Gradle sync to finish,
then run the `app` configuration on an Android 8.0 (API 26) or newer device.

Command-line builds use Gradle 8.11.1:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions installs the pinned Gradle version, so a binary wrapper is not
required in this bootstrap.

## Roadmap

1. Add a versioned market-data provider interface and live quotes.
2. Persist watchlists and optional 30–60 minute setup notifications.
3. Add a user-authorised MT5 bridge with connection health and read-only mode.
4. Add paper trading before any opt-in execution feature is considered.

Trading involves substantial risk. Analysis and forecasts are not guarantees,
and the app must not increase a position merely to meet a broker minimum.
