# MNM AU Seekers - Codex Instructions

## Current task
Implement the MNM Signal Engine v2 described in `docs/SIGNAL_ENGINE_V2.md`.

## Core rule
RSI must contribute **zero weight** to BUY/SELL/WAIT/PULLBACK decisions. Do not use RSI as a reversal trigger, trend filter, score input, or confirmation requirement. If an RSI display already exists later, keep it informational only and label it `Reference only`.

## Signal priority
1. Price structure and candlestick behaviour
2. Bollinger Bands (period 21, deviation 2.0)
3. EMA5 and MA21 short-term alignment
4. MA63 and MA84 trend alignment
5. Support/resistance and breakout/retest behaviour
6. MACD confirmation
7. Multi-timeframe agreement across M15, H1, H4, D1 and W1

## Implementation requirements
- Keep indicator calculations separate from scoring/business logic.
- Make signal rules deterministic and testable.
- Expose clear reasons for every signal so the UI can explain why a signal is BUY, SELL, WAIT, PULLBACK WATCH, or DANGER.
- Do not hard-code XAU/USD-only assumptions into reusable indicator classes.
- Preserve Primary Mode and optional Scalping Mode architecture.
- Do not execute live trades from analysis signals without a separate explicit execution layer and user control.
- Add unit tests for false-signal protection, especially cases where RSI would previously have suggested a reversal but price/MA/BB structure remains trend-following.

## Completion criteria
- Signal engine matches the rules in `docs/SIGNAL_ENGINE_V2.md`.
- RSI has zero decision weight.
- Tests cover bullish continuation, bearish continuation, pullback watch, confirmed reversal, range/wait, breakout, and false reversal cases.
- Dashboard can consume a structured signal result with status, confidence, reasons, key levels, and timeframe agreement.
