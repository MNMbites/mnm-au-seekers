# Local paper trading

The Android app includes an isolated paper ledger for rehearsing the existing
analysis and risk workflow. Every position and closed-trade record remains in
private on-device preferences. Opening or closing a paper position never calls
the backend, MT5, a broker SDK, or an order API.

## Opening a paper position

The Paper only card becomes available when all safeguards pass:

- the selected symbol has a validated live quote;
- market health is `Ready`;
- analysis returns Buy or Sell rather than Wait;
- the risk calculator allows the suggested lot;
- bid, ask, planned stop distance, point value, and point size are valid; and
- no paper position is already open for that symbol.

The first paper position fixes the portfolio starting balance to the current
Balance input. Later edits to that field do not rewrite paper history or the
locked starting balance. After that, new paper positions are risk-sized from
paper cash (starting balance plus realized P&L), not later Balance edits. One
open position per symbol prevents accidental stacking.

Buy simulations enter at ask and Sell simulations enter at bid. The position
records the suggested lot, planned stop distance, maximum planned loss, point
size, point value, and local open time.

## Marking and closing

Open P&L is marked only when the selected symbol has a current live quote:

```text
Buy exit = bid
Sell exit = ask
move in points = signed(exit - entry) / point size
P&L = move in points × USD per point per lot × lot size
```

Manual paper closure uses the same quote side and stores entry, exit, realized
P&L, size, direction, and timestamps. Up to 100 recent closed trades are kept.
The portfolio can be reset only after every paper position is closed.

The calculation intentionally does not invent unavailable broker behavior. It
does not simulate commissions, swaps, slippage, partial fills, margin calls, or
automatic stop execution. Point size and point value must match the selected
broker symbol or the displayed P&L will be wrong.

## Isolation boundary

- No backend paper-order route exists.
- No MT5 order function is called or added.
- No broker login, password, account balance, position, or API credential is
  stored.
- Android backup remains disabled, and the paper portfolio is local app data.
- Removing app data or uninstalling the app removes the portfolio.

Paper results are hypothetical and are not evidence of future live performance.
Broker execution remains outside this milestone and requires separate design,
security review, explicit opt-in, and demo-account validation before it can be
considered.
