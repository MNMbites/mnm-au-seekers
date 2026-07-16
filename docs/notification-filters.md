# Optional notification filters

MNM AU Seekers keeps setup alerts and pre-market briefings optional and
independently configurable. Every control is stored privately on the Android
device. Changing a filter does not send a notification, contact MT5, or create
an order.

## Categories

- **Setup alerts** use their own `Off`, `30 min`, or `60 min` schedule.
- **Pre-market briefings** use a separate `Off`, `30 min`, or `60 min` lead
  window.

Selecting `Off` cancels only that category's unique WorkManager task. Both
categories are Off on a new installation until the user opts in.

## Shared filters

The notification-filter card provides four persisted controls:

| Filter | Options | Default |
| --- | --- | --- |
| Minimum confidence | `Ready + Caution`, `Ready only` | `Ready + Caution` |
| Setup stage | `Early + Confirmed`, `Confirmed only` | `Early + Confirmed` |
| Pre-market sessions | `London + New York`, `London only`, `New York only` | `London + New York` |
| Quiet hours | `Off`, `22:00–07:00`, `00:00–06:00` | `Off` |

The defaults preserve the pre-filter alert behavior. `Ready only` and
`Confirmed only` are stricter options; they never make an otherwise ineligible
setup eligible. The confidence and stage filters apply to setup alerts. The
session filter applies to pre-market previews and notifications.

Quiet hours use the Android device's current time zone and apply to both
categories. The start is inclusive and the end is exclusive. Overnight ranges
wrap across midnight, so `22:00–07:00` suppresses a worker that runs at 22:00 or
06:59 but not one that runs at 07:00.

## Background behavior

Each worker loads the latest policy at run time. During quiet hours it exits
without fetching or publishing. A filtered setup or session also exits without
publication. The periodic schedule remains registered, so a later eligible
check can run normally.

WorkManager timing remains approximate. A filter decision is made when the
worker actually runs, not at the ideal interval boundary. The app does not send
a test blast or bypass Android notification permission when settings change.

All notifications remain analysis-only. Filters do not add broker credentials,
account access, automatic trade execution, or an order API.
