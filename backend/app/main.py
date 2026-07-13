from __future__ import annotations

import hmac
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, status

from backend.app.config import Settings
from backend.app.models import IngestResult, MarketDataEnvelope, MarketSnapshot
from backend.app.repository import OutOfOrderSnapshotError, SnapshotStore


def create_app(
    settings: Settings | None = None,
    store: SnapshotStore | None = None,
) -> FastAPI:
    runtime_settings = settings or Settings.from_env()
    snapshot_store = store or SnapshotStore(runtime_settings.max_snapshot_age_seconds)

    app = FastAPI(
        title="MNM AU Seekers Market Data API",
        version="0.2.0",
        description="Read-only MT5 quote and indicator ingestion boundary.",
    )

    def authorize_bridge(
        token: Annotated[str | None, Header(alias="X-Bridge-Token")] = None,
    ) -> None:
        expected = runtime_settings.bridge_token
        if expected is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="MT5 ingestion is disabled until MT5_BRIDGE_TOKEN is configured",
            )
        if token is None or not hmac.compare_digest(token, expected):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid bridge token",
            )

    @app.get("/health", tags=["operations"])
    def health() -> dict[str, str]:
        return {
            "status": "ok",
            "ingestion": "enabled" if runtime_settings.bridge_token else "disabled",
        }

    @app.post(
        "/api/v1/market-data/mt5/snapshots",
        response_model=IngestResult,
        status_code=status.HTTP_202_ACCEPTED,
        tags=["mt5"],
    )
    def ingest_snapshot(
        snapshot: MarketSnapshot,
        _: None = Depends(authorize_bridge),
    ) -> IngestResult:
        try:
            snapshot_store.upsert(snapshot)
        except OutOfOrderSnapshotError as error:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=str(error),
            ) from error
        return IngestResult(symbol=snapshot.symbol, captured_at=snapshot.captured_at)

    @app.get(
        "/api/v1/market-data/{symbol}",
        response_model=MarketDataEnvelope,
        tags=["market-data"],
    )
    def latest_snapshot(symbol: str) -> MarketDataEnvelope:
        snapshot = snapshot_store.latest(symbol)
        if snapshot is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="No market data is available for this symbol",
            )
        return snapshot

    return app


app = create_app()
