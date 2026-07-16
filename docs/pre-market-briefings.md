# London and New York pre-market briefings

MNM AU Seekers can prepare an analysis-only XAU/USD plan before the London and
New York sessions. Briefings are deterministic summaries of existing market,
health, and calendar inputs; they do not call a generative model or an order
API.

## Session clock

- London opens at 08:00 in `Europe/London`.
- New York opens at 08:00 in `America/New_York`.

The app uses IANA time zones, so daylight-saving changes are applied by the
platform instead of hard-coded UTC offsets. The dashboard shows the next open
in the device's local time and identifies the session time zone.

The alert setting is `Off`, `30 min`, or `60 min`. Off is the default. When Off,
the dashboard still previews the next 60-minute plan but no briefing work is
scheduled. Enabling a lead time requests Android notification permission only
after that user action.

The notification policy can include both sessions, London only, or New York
only. The dashboard preview and background worker use the same selection.
Device-local quiet hours can suppress publication even during an active session
window. Full policy behavior is described in
[notification-filters.md](notification-filters.md).

## Preparation plan

Each plan contains:

- overall trend rationale from the unchanged signal engine;
- technical agreement, market-health score, and calendar state;
- H1 MA21, H4 MA21, and the H1 Bollinger range as reference levels;
- expected behavior based on higher-timeframe bias and M15 confirmation;
- the Primary-mode Buy, Sell, or Wait bias;
- an alternative `WAIT` scenario if H1 MA21 is lost or reclaimed;
- a `SAFE` risk posture plus calendar guidance; and
- an explicit live, stale, connecting, or demo source notice.

The app does not calculate a numeric pullback probability because the available
indicator snapshots do not justify a calibrated probability estimate. It uses
plain-language confirmation/pullback context instead.

## Background behavior

When enabled, WorkManager checks approximately every 15 minutes under a network
constraint. Android periodic work is inexact and may run late because of battery
optimization, Doze, connectivity, or operating-system scheduling.

The worker:

1. exits outside the selected briefing window;
2. requires a validated live XAU/USD snapshot;
3. loads the relevant economic-calendar context;
4. generates the same Primary-mode plan shown in the dashboard; and
5. publishes at most one notification for each symbol/session/open time.

London and New York briefings have a separate channel and deduplication record
from setup alerts. They never include a bridge, calendar-ingestion, or watchlist
administration token.

Session hours can change around holidays or venue-specific schedules. The
current milestone models regular weekday clocks and does not include an exchange
holiday calendar. A briefing is preparation context, not a prediction, trade
instruction, or guaranteed opportunity.
