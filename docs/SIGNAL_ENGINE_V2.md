# MNM AU Seekers Signal Engine v2

## Objective
Reduce noisy reversal calls by removing RSI from the decision engine and making price action, trend structure, volatility context, and multi-timeframe agreement the primary evidence.

## Decision hierarchy

### 1. Price structure and candlesticks - highest priority
Evaluate:
- Higher highs / higher lows
- Lower highs / lower lows
- Break of structure
- Support/resistance rejection
- Breakout and retest
- Candle rejection at key levels
- Bearish/bullish engulfing
- Pin bars / shooting stars / hammers
- Inside-bar compression
- Failed breakouts

Candlestick patterns must never trigger a trade by themselves. They need location and trend context.

### 2. Bollinger Bands
Use BB period 21, deviation 2.0.
Evaluate:
- Price riding upper/lower band
- Upper/lower-band rejection
- Band expansion
- Band contraction/squeeze
- Mean reversion toward BB basis

Do not interpret upper-band contact alone as SELL or lower-band contact alone as BUY.

### 3. Moving-average structure
Primary moving averages:
- EMA5: immediate momentum
- MA21: short-term trend
- MA63: established trend
- MA84: major/growing trend

Preferred interpretation:
- Bullish continuation: EMA5 > MA21 with rising slope, ideally price above MA63/MA84
- Bearish continuation: EMA5 < MA21 with falling slope, ideally price below MA63/MA84
- Pullback watch: fast averages lose slope or cross against the larger MA63/MA84 trend without a confirmed structural break
- Reversal confirmation requires price structure plus MA deterioration, not a fast-MA cross alone

### 4. Support/resistance
Key levels are first-class inputs. The engine should identify or accept:
- Nearest resistance
- Nearest support
- Breakout trigger
- Failure/invalidation level
- TP1 / TP2 / extended target

Signals near major support/resistance require stronger confirmation.

### 5. MACD
MACD is confirmation only.
Use current project preference MACD(5,21,9) unless configuration overrides it.
Evaluate:
- Main/signal cross
- Histogram expansion/contraction
- Momentum agreement with price structure
- Divergence as a warning, not a standalone reversal trigger

### 6. RSI
RSI is removed from the decision engine.
- Weight: 0
- No BUY/SELL trigger
- No reversal trigger
- No confidence contribution
- Optional display only as `Reference only`

## Multi-timeframe model
Use M15, H1, H4, D1 and W1.

### Primary Mode
Weight higher timeframes more heavily:
- W1: strategic backdrop
- D1: dominant market direction
- H4: primary setup direction
- H1: confirmation / trade management
- M15: timing only

Suggested default weights for implementation/testing:
- W1 20%
- D1 25%
- H4 30%
- H1 20%
- M15 5%

These are configuration defaults, not immutable constants.

### Scalping Mode
Higher emphasis on H1/M15 while retaining H4 as guardrail. Do not let M15 alone override a strong H4/D1 trend.

## Signal states
The engine should return one of:
- `BUY`
- `SELL`
- `WAIT`
- `PULLBACK_WATCH`
- `DANGER`

### BUY
Require most of:
- Bullish structure
- EMA5/MA21 bullish alignment or bullish recovery
- Price holding above important support
- BB behaviour consistent with continuation/recovery
- MACD confirmation or improving momentum
- H4/D1 agreement preferred

### SELL
Require most of:
- Bearish structure
- EMA5/MA21 bearish alignment or bearish recovery after rally
- Price rejecting/breaking important support/resistance appropriately
- BB behaviour consistent with downside continuation
- MACD confirmation or deteriorating bullish momentum
- H4/D1 agreement preferred

### PULLBACK_WATCH
Use when the larger trend remains intact but short-term structure loses momentum. Example: D1/H4 bullish while H1 develops rejection, lower high, fast-MA rollover, and BB mean-reversion behaviour.

### WAIT
Use when evidence conflicts, price is trapped in a range, or no structural confirmation exists.

### DANGER
Use for trade management when an open position approaches its invalidation level or price breaks the thesis-defining level.

## Confirmation scoring
Use evidence groups rather than raw indicator votes.

Recommended groups:
- Price structure/candles: 30
- MA alignment/slope: 25
- Support/resistance/breakout context: 20
- Bollinger context: 15
- MACD confirmation: 10
- RSI: 0

Return both a score and human-readable reasons. Confidence must not imply certainty.

## False-signal protection
The following must return WAIT or continuation unless structural confirmation exists:
- RSI overbought during a strong bullish trend
- RSI oversold during a strong bearish trend
- Upper BB touch while price rides an expanding upper band
- Lower BB touch while price rides an expanding lower band
- MACD cross against a strong higher-timeframe trend without price-structure break
- Single reversal candle in the middle of a range

## Current XAU/USD trade-management example
This example is for dashboard behaviour and tests, not permanent market levels.

Reference levels from the August 2026 analysis:
- Resistance/danger: 4353-4360
- Intermediate support: 4328 area
- Structural support: 4316 area
- Primary exit target: 4292 area
- Secondary target: 4280 area
- Deeper target: 4265 area

Expected behaviour:
- H1 rejection from 4353-4360 + bearish structural confirmation -> PULLBACK_WATCH / SELL-management improvement
- H1 close below 4316 with MA/MACD agreement -> stronger bearish pullback confirmation toward 4292/4280
- H1 break and hold above 4356 -> DANGER for existing SELL thesis

## Structured result contract
Codex should implement or prepare a model equivalent to:

```text
SignalResult
- symbol
- mode
- status
- confidenceScore
- primaryTrend
- shortTermState
- timeframeSignals[]
- reasons[]
- warnings[]
- keyLevels
  - support[]
  - resistance[]
  - trigger
  - invalidation
  - tp1
  - tp2
- timestamp
```

## Dashboard requirements
Show:
- Master market compass
- W1, D1, H4, H1, M15 status cards
- Primary trend vs short-term condition
- Price/candle condition
- BB21 state
- EMA5/MA21 state
- MA63/MA84 state
- MACD confirmation
- Key support/resistance
- Open-trade exit strategy
- Reason list explaining the signal

Do not show RSI as a decision badge. If retained visually, place it under an optional diagnostics/reference section.

## Testing acceptance criteria
Add deterministic tests for:
1. Strong bullish continuation despite overbought RSI input
2. Strong bearish continuation despite oversold RSI input
3. Bullish trend with short-term pullback watch
4. Confirmed bearish reversal after structure break
5. Confirmed bullish reversal after structure break
6. Sideways range -> WAIT
7. Upper-BB ride -> no automatic SELL
8. Lower-BB ride -> no automatic BUY
9. MACD countertrend cross without structure -> WAIT
10. Existing SELL crossing invalidation -> DANGER
11. H4/D1 trend agreement outweighing M15 noise
12. RSI changes do not change the final signal when all other inputs are identical
