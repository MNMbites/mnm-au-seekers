# PostgreSQL persistence and watchlists

The backend can persist complete market-snapshot history and a shared symbol
watchlist. With no database setting it keeps the in-memory development store;
set `DATABASE_URL` to activate SQLAlchemy-backed storage.

## Configure PostgreSQL

Create a dedicated database and least-privilege application user, then set the
runtime URL using the psycopg driver:

```bash
export DATABASE_URL='postgresql+psycopg://mnm_app:replace-me@localhost/mnm_au_seekers'
python -m alembic -c backend/alembic.ini upgrade head
```

Run migrations before starting or upgrading the API. The application does not
create production tables automatically.

```bash
export MT5_BRIDGE_TOKEN='replace-with-a-long-random-value'
export WATCHLIST_ADMIN_TOKEN='replace-with-a-different-random-value'
export ECONOMIC_CALENDAR_TOKEN='replace-with-a-third-random-value'
uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
```

Keep all tokens and the database URL in the deployment secret manager. Do not
reuse the bridge token for watchlist or calendar ingestion, and do not expose
any ingestion token to the Android app.

## Snapshot history API

The read-only history route returns the newest requested records in
chronological order:

```text
GET /api/v1/market-data/XAUUSD/history?limit=100
```

`limit` must be from 2 through 500. An unknown symbol returns an empty `items`
array. The in-memory development repository retains at most 500 snapshots per
symbol; PostgreSQL retains rows according to deployment retention policy and
applies the requested bound at query time.

## Watchlist API

Reading the watchlist is intentionally public, matching the market-data read
boundary:

```text
GET /api/v1/watchlist
```

Mutations require the separately configured admin token:

```text
PUT /api/v1/watchlist/XAUUSD
X-Admin-Token: <WATCHLIST_ADMIN_TOKEN>

DELETE /api/v1/watchlist/XAUUSD
X-Admin-Token: <WATCHLIST_ADMIN_TOKEN>
```

Symbols are matched case-insensitively. Repeated `PUT` requests are idempotent,
and deleting an absent symbol returns HTTP 404. If no admin token is configured,
mutation routes return HTTP 503 while reads remain available.

## Operations

`GET /health` reports whether storage is `memory`, `database`, or `postgresql`
and whether ingestion and watchlist writes are configured. Back up PostgreSQL
before destructive schema changes and test both upgrade and rollback procedures
against a non-production copy. Snapshot rows retain the full validated schema
`1.0` payload so newer readers can preserve the original bridge contract.
