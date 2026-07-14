# Market health and confidence explanations

The Android analysis screen includes a deterministic readiness assessment for
the current symbol. It explains whether the data and signal inputs are suitable
for review; it does not estimate win probability, expected return, or whether a
trade should be placed.

## Readiness score

The score is capped at 100 points and shows every contributing factor:

| Factor | Maximum | Interpretation |
| --- | ---: | --- |
| Data source | 25 | A validated live bridge snapshot is required. |
| Freshness | 20 | Up to 60 seconds is good; 61–120 seconds is caution. |
| Relative spread | 20 | Up to 2 basis points is good; up to 10 is caution. |
| Signal agreement | 35 | Existing timeframe strength plus setup maturity. |

`Ready` requires at least 75 points. `Caution` requires at least 50. A stale,
demo, or connecting feed; missing/invalid quotes; a snapshot older than 120
seconds; or a relative spread above 10 basis points always produces `Not ready`
regardless of the numeric total.

Relative spread is calculated from the quote, not a symbol-specific pip size:

```text
(ask - bid) / midpoint × 10,000
```

This keeps the display comparable across differently priced watchlist symbols.
Broker conditions vary, so the factor is an explicit review prompt rather than
a claim that a quoted spread is executable.

## Confidence explanation

Confidence labels describe technical agreement only:

- `Strong agreement` — a confirmed setup with at least 75% weighted alignment.
- `Confirmed agreement` — the entry timeframe confirms the direction.
- `Developing agreement` — higher-timeframe direction exists but an entry or
  supporting timeframe is still pending.
- `Low agreement` — the signal engine remains in its Watch state.

The explanation names supporting, pending, or opposing timeframes. Early setups
therefore show what is missing instead of presenting a directional label alone.

## Notification behavior

Opt-in background alerts use the same evaluator. A live Buy/Sell setup is not
notified when market health is `Not ready`. Ready or Caution alerts include the
health label and confidence explanation, while unchanged setup fingerprints
remain deduplicated.

Readiness is not outcome probability. It does not replace broker spread and
contract checks, risk sizing, or independent judgment, and it never sends an
order.
