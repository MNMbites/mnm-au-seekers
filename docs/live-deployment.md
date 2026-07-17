# Live market-data deployment

This deployment keeps broker credentials out of the Android app and backend.
The read-only MT5 EA publishes indicator snapshots using a separate bridge
token; Android only reads public market-data routes.

## 1. Deploy the backend container

### Render Blueprint (recommended)

Create a Render account with GitHub access to this repository, then select
**New > Blueprint** and choose the repository. Render reads the root
`render.yaml` and proposes a free Docker web service in Singapore with the
`/health` check already configured.

When prompted for `MT5_BRIDGE_TOKEN`, enter a unique random value of at least
32 characters and save it in a password manager. The same value is entered in
the MT5 EA later; never commit it to Git or place it in the Android app.

After the first deployment, copy the service's HTTPS `onrender.com` origin.
The free service can spin down after 15 minutes without inbound traffic; the
EA's 30-second publishing interval normally keeps it awake while MT5 is
running. Upgrade the web service instance if continuous production uptime is
required.

### Other container hosts

Deploy the repository root with `Dockerfile` on a container host that provides
a public HTTPS origin. Configure these runtime values in the host's secret or
environment settings:

```text
MT5_BRIDGE_TOKEN=<unique long random value>
MARKET_DATA_MAX_AGE_SECONDS=120
```

The container listens on the host-provided `PORT`, defaults to `8000`, runs as
an unprivileged user, and reports health at `/health`.

For persistent history and a shared watchlist, also provide `DATABASE_URL` and
run the migrations before starting a new backend version:

```bash
python -m alembic -c backend/alembic.ini upgrade head
```

The in-memory backend is sufficient for an initial live test. A restart clears
its history, but the next accepted MT5 publish restores the latest snapshot.

Verify the deployment before configuring MT5:

```text
https://YOUR_HOST/health
```

The response must report `"status":"ok"` and `"ingestion":"enabled"`.

## 2. Connect the read-only MT5 EA

Copy `mt5_bridge/MnmAuSeekersBridge.mq5` into `MQL5/Experts`, compile it in
MetaEditor, and allow the HTTPS origin under **Tools > Options > Expert
Advisors > Allow WebRequest for listed URL**.

Attach the EA to the broker's XAU/USD chart and set:

```text
InpEndpoint=https://YOUR_HOST/api/v1/market-data/mt5/snapshots
InpBridgeToken=<the same server-side bridge token>
InpPublishSeconds=30
InpUseClosedBar=true
```

The Experts log must show a successful publish. Then confirm that this route
returns a current `live` snapshot with M15, H1, and H4 values:

```text
https://YOUR_HOST/api/v1/market-data/XAUUSD
```

If the broker uses a suffix such as `XAUUSD.a`, the Android watchlist and URL
must use that exact symbol apart from letter case.

## 3. Compile the live-enabled Android APK

Set the GitHub Actions repository variable `MNM_MARKET_DATA_BASE_URL` to the
HTTPS origin only, without an API path:

```text
https://YOUR_HOST
```

Run the existing **Android CI** workflow. Its APK artifact will contain the
public service origin and will show `LIVE DATA` after it validates the latest
snapshot. Never place the bridge token, MT5 login, MT5 password, database
password, or a credential-bearing URL in this repository variable.

For a local build, use:

```bash
export MNM_MARKET_DATA_BASE_URL='https://YOUR_HOST'
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

## 4. Operational boundary

- MT5 must remain running and connected for new snapshots to arrive.
- The EA publishes every 30 seconds and never sends an order.
- Android polls at the selected manual, 15-, 30-, or 60-second interval.
- A snapshot older than the configured limit becomes `STALE DATA` and blocks
  readiness-dependent actions.
- Rotate `MT5_BRIDGE_TOKEN` immediately if it is exposed; update both the host
  secret and EA input together.
