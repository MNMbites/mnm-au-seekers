# Read-only MT5 bridge

The Phase 2 bridge moves quotes and completed-bar indicator values from an
authorised MetaTrader 5 terminal to the versioned backend API. It has no order,
position, account-balance, login, or password capability.

## Data flow

1. `MnmAuSeekersBridge.mq5` reads the current bid/ask and M15, H1, and H4 data.
2. The EA calculates EMA 5, MA 9/21/63/84, Bollinger Bands 21/2, RSI 14, and
   MACD 12/26/9 values from the current symbol.
3. Every 30 seconds, it posts a schema `1.0` snapshot to the backend with an
   `X-Bridge-Token` header.
4. The backend validates and stores only the newest snapshot for each symbol.
5. Read clients use `GET /api/v1/market-data/{symbol}` and receive an explicit
   `live` or `stale` state.

## Run the backend

Requires Python 3.10 or newer.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
export MT5_BRIDGE_TOKEN='replace-with-a-long-random-value'
uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
```

Do not commit the token. For anything beyond local testing, terminate TLS in
front of the API and use an HTTPS endpoint.

## Install the EA

1. Copy `mt5_bridge/MnmAuSeekersBridge.mq5` into the terminal's
   `MQL5/Experts` directory and compile it in MetaEditor.
2. In MT5, open **Tools > Options > Expert Advisors** and add the backend origin
   to the allowed WebRequest URLs.
3. Attach the EA to the desired symbol chart.
4. Set `InpEndpoint` and set `InpBridgeToken` to the same runtime token used by
   the backend. The token is an EA input, not source code.
5. Confirm the Experts log reports successful HTTP 202 publishes.

MT5 blocks WebRequest calls in the Strategy Tester. Exercise the HTTP contract
with the backend test suite and validate the compiled EA on a demo terminal.

## API contract

The ingestion route is `POST /api/v1/market-data/mt5/snapshots`. A snapshot
must include all three supported timeframes exactly once. Duplicate or older
timestamps return HTTP 409; malformed data returns HTTP 422; a missing or
invalid bridge token returns HTTP 401. If the server has no bridge token
configured, ingestion returns HTTP 503.

The latest-data route is intentionally read-only:

```text
GET /api/v1/market-data/XAUUSD
```

The current in-memory store is a single-process milestone implementation.
Durable PostgreSQL storage and multi-instance fan-out belong in a later change.
