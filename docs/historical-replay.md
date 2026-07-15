# Historical signal replay

MNM AU Seekers can replay its existing Primary or Scalping signal engine over
recent snapshots stored by the read-only backend. This is a deterministic
review tool, not a broker simulator or strategy optimizer.

## Data flow

The Android app requests up to 100 records for the selected symbol from:

```text
GET /api/v1/market-data/{symbol}/history?limit=100
```

The backend accepts limits from 2 through 500 and returns records in ascending
capture-time order. The route is read-only and requires no bridge or watchlist
administration token. If the live service is unavailable, Android returns an
empty replay sample; it never substitutes bundled demo values for historical
market data.

Before replay, Android verifies that every observation:

- belongs to the same symbol;
- has a finite, positive bid and a valid ask;
- contains M15, H1, and H4 inputs; and
- is strictly newer than the preceding observation.

Invalid history is labelled `Needs review` rather than partially evaluated.

## Replay method

For each observation except the last, the current unmodified signal engine is
run in the selected mode. `Wait` observations are counted as evaluated
transitions but do not create directional results. Each Buy or Sell signal is
compared with the midpoint of the next stored observation:

```text
midpoint = (bid + ask) / 2
raw move bps = (next midpoint - current midpoint) / current midpoint × 10,000
signed move bps = raw move bps × direction sign
```

A positive signed move is labelled favorable, a negative move adverse, and an
unchanged move flat. The report also shows signal direction counts, confirmed
count, average signed move, and maximum single-transition adverse move.

## Limitations

The next stored observation is not a fixed holding period when bridge capture
intervals vary. The replay does not model executable bid/ask exits, spread,
commission, swaps, slippage, stops, partial fills, margin, position sizing, or
P&L. It does not search parameters or select a best configuration. Results are
descriptive for the stored sample and do not estimate future return or win
probability.
