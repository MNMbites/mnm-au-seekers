# Physical-device and MT5 demo-feed validation

The Android dashboard includes a deterministic device-validation checklist and
a redacted plain-text report. It is intended to collect comparable evidence
from a physical device connected to the read-only bridge running against an MT5
demo terminal.

## CI validation APK

Every successful Android CI job uploads a `mnm-au-seekers-debug-<run>` artifact
for 14 days. The artifact contains:

- `app-debug.apk`, a debuggable Android build for validation only; and
- `app-debug.apk.sha256`, its SHA-256 checksum.

The CI build embeds the workflow commit in `BuildConfig`. The device-validation
card and exported report show the app version and normalized commit, allowing a
report to be matched to the tested source and checksum. Local builds use
`local` instead of inventing a commit identity.

The live-service origin is not a credential, but it is compiled into the APK.
To make a CI artifact usable with the read-only bridge, configure the repository
Actions variable `MNM_MARKET_DATA_BASE_URL` with the deployed HTTPS origin. Do
not put a bridge token, watchlist token, calendar token, MT5 password, query
credential, or other secret in that value. When the variable is absent, the CI
artifact remains demo-only and the checklist correctly blocks live validation.

After downloading and extracting the artifact, verify it before installation:

```bash
sha256sum -c app-debug.apk.sha256
adb install app-debug.apk
```

CI runners generate an ephemeral debug signing key. A device that already has a
build signed by a different CI run may reject an update. In that case, export
any needed validation report first, then remove the previous debug install and
install the new artifact:

```bash
adb uninstall com.mnm.auseekers
adb install app-debug.apk
```

Uninstalling clears private app state, including notification evidence and
settings. CI artifacts are not production releases and must not be distributed
as signed release builds.

## Checklist

The card evaluates the current installation and selected symbol for:

- a configured market-data service;
- a backend-validated live MT5 bridge snapshot;
- the required M15, H1, and H4 indicator snapshots;
- current market-readiness status and score;
- economic-calendar availability;
- Android notification permission and the app notification switch;
- the setup-alert and pre-market schedule settings;
- the active confidence, setup-stage, session, and quiet-hours filters; and
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

The report includes app version, normalized build commit, symbol, feed capture
time and age, checklist results, and bounded notification publication evidence.
It excludes the service endpoint, all tokens, device identifiers, MT5 or broker
account data, paper positions and results, and analysis-journal notes.

The checklist performs no connectivity mutation, notification test blast,
broker login, or order action. It adds no execution capability. Share the report
only through the Android system share sheet after an explicit user action.
