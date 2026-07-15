# Local analysis journal

The Android app includes a manual journal for recording what its analysis showed
at a decision point. A journal entry is created only after the user taps
`Save current analysis snapshot`; refreshing market data or receiving a
notification never writes an entry automatically.

## Recorded fields

Each entry preserves:

- symbol and Primary or Scalping mode;
- Buy, Sell, or Wait direction and setup stage;
- signal strength and the analysis rationale;
- market-health level and score;
- the technical-agreement label;
- live, stale, connecting, or demo feed state;
- bid/ask and source capture time when the feed provides them;
- an optional note of up to 280 characters; and
- local record time.

Capturing feed state prevents demo or stale observations from being mistaken for
live-market analysis during later review. Notes are trimmed but otherwise remain
exactly as entered.

## Review statistics

The journal displays total observations, Ready observations, average recorded
health score, direction counts, and confirmed-stage count. These are descriptive
counts over manually saved snapshots. They do not measure profitability, win
rate, expected return, or the probability of a future outcome.

The newest three entries are shown in the dashboard. Up to 200 entries are
retained; when that bound is exceeded, the oldest entry is removed.

## Privacy and execution boundary

- The journal is stored in a dedicated private Android preferences file.
- Android backup remains disabled.
- No journal API or backend database table exists.
- Entries are not uploaded, shared, or included in the paper-audit CSV.
- The journal stores no broker login, password, account identifier, account
  balance, live position, or execution token.
- Saving an entry cannot place or modify an order.

Removing the app's data or uninstalling it removes the local journal. The
journal is an analysis-review aid, not an execution or performance claim.
