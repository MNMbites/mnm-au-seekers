# Moving-average and Bollinger direction

MNM AU Seekers treats recent moving-average and Bollinger Band direction as
primary deterministic signal inputs. Direction is calculated independently for
M15, H1, and H4 from the analysed bar and the immediately preceding completed
bar supplied in the same read-only MT5 snapshot.

## Moving-average direction

The engine compares EMA 5 and MA 9/21/63/84 with their previous-bar values. A
timeframe is `Rising` when at least three of the five averages rise and more are
rising than falling. It is `Falling` under the inverse rule. Complete but mixed
or tied movement is `Flat/mixed`.

## Bollinger direction

The engine compares the upper band, MA 21 as the band midline, and the lower
band with their previous-bar values. At least two of the three must move in the
same direction, with more supporting than opposing values, to label the band
`Rising` or `Falling`. Expansion in opposite directions is `Flat/mixed`; it is
not assigned a directional bias.

## Signal effect

Each rising or falling MA direction contributes `+2` or `-2` to the timeframe
score. Bollinger direction contributes another `+2` or `-2`. Existing price,
MA-structure, RSI, MACD, and band-break inputs contribute the remaining eight
possible points. A directional timeframe now requires an absolute score of at
least five out of twelve.

This makes MA and Bollinger direction materially capable of confirming or
blocking an otherwise directional structure. Older snapshots without the
previous-bar fields remain readable; their trend labels are `Unavailable` and
contribute no points. Partially supplied trend data is rejected.

The multi-timeframe roles are unchanged: H4 is the main direction, H1 the
trading bias, and M15 the entry timing. These labels represent technical
agreement only, not outcome probability, and add no broker or order capability.
