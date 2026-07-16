# Physical-device and MT5 demo-feed validation

The Android dashboard includes a deterministic device-validation checklist and
a redacted plain-text report. It is intended to collect comparable evidence
from a physical device connected to the read-only bridge running against an MT5
demo terminal.

## Checklist

The card evaluates the current installation and selected symbol for:

- a configured market-data service;
- a backend-validated live MT5 bridge snapshot;
- the required M15, H1, and H4 indicator snapshots;
- current market-readiness status and score;
- economic-calendar availability;
- Android notification permission and the app notification switch;
- the setup-alert and pre-market schedule settings; and
- recent notification publication records.

`Pass` means the current app state supplies the requested evidence. `Check`
means an optional schedule is off, the calendar is unavailable, or no
notification has yet been published. `Blocked` means a core live-device
validation condition is missing. These labels assess the validation setup, not
the likelihood or profitability of a trade.

## Notification publication evidence

After Android accepts a setup or pre-market notification request, the app stores
a local record containing:

- the notification type and symbol;
- the publication time;
- the London or New York context for pre-market briefings; and
- the selected briefing-window start and observed publication offset.

At most 20 records are retained in a dedicated private preferences file. Setup
records deliberately omit calendar event IDs and notification body text.
Pre-market offsets help assess WorkManager timing, but periodic work is inexact.
A publication record does not prove that the operating system displayed the
notification promptly or that the user saw it.

## Suggested validation run

1. Build the app with the HTTPS market-data service origin and connect that
   service to the read-only EA on an MT5 demo terminal.
2. Select XAU/USD and refresh until the feed is `Live`, all three timeframes are
   present, and snapshot age remains within the configured freshness boundary.
3. Enable the desired setup interval and a 30- or 60-minute pre-market window.
4. Leave the device under the intended battery and network conditions through a
   London or New York briefing window.
5. Open the app, select `Refresh evidence`, and use `Share report` to review the
   checklist and publication offsets.
6. Repeat with battery optimization and background restrictions documented for
   the test device. Keep late or missing operating-system delivery distinct from
   an app publication record.

## Redaction and safety boundary

The report includes app version, symbol, feed capture time and age, checklist
results, and bounded notification publication evidence. It excludes the service
endpoint, all tokens, device identifiers, MT5 or broker account data, paper
positions and results, and analysis-journal notes.

The checklist performs no connectivity mutation, notification test blast,
broker login, or order action. It adds no execution capability. Share the report
only through the Android system share sheet after an explicit user action.
