# Economic-calendar risk filter

MNM AU Seekers adds economic-event context without coupling the app to a
specific third-party calendar. An external deployment adapter obtains licensed
calendar data and submits a normalized batch to the FastAPI service. Provider
API keys stay in that adapter or its secret manager and never enter Android,
MT5, or this repository.

## Ingestion

Set a distinct runtime secret:

```bash
export ECONOMIC_CALENDAR_TOKEN='replace-with-a-long-random-value'
```

Submit at most 500 normalized events per request:

```text
POST /api/v1/economic-calendar/events
X-Calendar-Token: <ECONOMIC_CALENDAR_TOKEN>
```

Each event includes a provider event ID, source, title, three-letter currency,
`low`, `medium`, or `high` impact, and a timezone-aware scheduled time. Every
event source must match its batch source. The database key combines source and
event ID, so a later batch updates the same event rather than duplicating it.

Calendar ingestion is disabled with HTTP 503 when the runtime token is absent.
The bridge, watchlist, and calendar tokens are separate trust boundaries.

## Read boundary

Android reads a currency- and time-bounded public endpoint:

```text
GET /api/v1/economic-calendar/events
    ?currencies=USD
    &from=2026-07-14T11:30:00Z
    &to=2026-07-15T12:00:00Z
```

The requested range must be positive and no longer than seven days, and each
response is capped at 500 events. XAU/USD uses USD events. Conventional
six-letter FX symbols use both base and quote currencies.

## Risk windows

- A high-impact event from 30 minutes before through 30 minutes after its
  scheduled time is `High-impact window`.
- A high-impact event 31–60 minutes away, or a medium-impact event within 30
  minutes, is `Calendar caution`.
- Otherwise the calendar is clear.
- A failed or unconfigured calendar is explicitly `Calendar unavailable`; it is
  never silently treated as clear.

During a high-impact window the UI warns that technical confidence should be
reduced. `Warn only` is the default and preserves the existing paper workflow.
If the user selects `Block high impact`, new paper setups and background setup
notifications are suppressed during the 30-minute window. Existing paper
positions are never modified or closed.

Calendar impact labels and scheduled times depend on the upstream provider and
can be incomplete, delayed, revised, or wrong. The filter is context for
analysis, not a forecast, execution instruction, or guarantee of volatility.
