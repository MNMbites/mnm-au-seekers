from __future__ import annotations

import hmac
from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response, status

from backend.app.config import Settings
from backend.app.database import SqlAlchemyMarketDataRepository
from backend.app.models import (
    EconomicCalendarIngestResult,
    EconomicCalendarResponse,
    EconomicCalendarUpdate,
    IngestResult,
    MarketDataEnvelope,
    MarketSnapshot,
    MarketSnapshotHistoryResponse,
    WatchlistEntry,
    WatchlistResponse,
    validated_symbol,
)
from backend.app.repository import (
    MarketDataRepository,
    OutOfOrderSnapshotError,
    SnapshotStore,
)


def create_app(
    settings: Settings | None = None,
    store: MarketDataRepository | None = None,
) -> FastAPI:
    runtime_settings = settings or Settings.from_env()
    if store is not None:
        repository = store
    elif runtime_settings.database_url:
        repository = SqlAlchemyMarketDataRepository(
            runtime_settings.database_url,
            runtime_settings.max_snapshot_age_seconds,
        )
    else:
        repository = SnapshotStore(runtime_settings.max_snapshot_age_seconds)

    app = FastAPI(
        title="MNM AU Seekers Market Data API",
        version="0.6.0",
        description="Read-only MT5 market data with calendar risk context.",
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

    def authorize_watchlist_admin(
        token: Annotated[str | None, Header(alias="X-Admin-Token")] = None,
    ) -> None:
        expected = runtime_settings.watchlist_admin_token
        if expected is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=(
                    "Watchlist mutations are disabled until "
                    "WATCHLIST_ADMIN_TOKEN is configured"
                ),
            )
        if token is None or not hmac.compare_digest(token, expected):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid admin token",
            )

    def authorize_economic_calendar(
        token: Annotated[str | None, Header(alias="X-Calendar-Token")] = None,
    ) -> None:
        expected = runtime_settings.economic_calendar_token
        if expected is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=(
                    "Calendar ingestion is disabled until "
                    "ECONOMIC_CALENDAR_TOKEN is configured"
                ),
            )
        if token is None or not hmac.compare_digest(token, expected):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid calendar token",
            )

    def normalize_path_symbol(symbol: str) -> str:
        try:
            return validated_symbol(symbol)
        except ValueError as error:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail=str(error),
            ) from error

    @app.get("/health", tags=["operations"])
    def health() -> dict[str, str]:
        return {
            "status": "ok",
            "ingestion": "enabled" if runtime_settings.bridge_token else "disabled",
            "storage": repository.storage_kind,
            "watchlist_writes": (
                "enabled" if runtime_settings.watchlist_admin_token else "disabled"
            ),
            "calendar_ingestion": (
                "enabled" if runtime_settings.economic_calendar_token else "disabled"
            ),
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
            repository.upsert(snapshot)
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
        snapshot = repository.latest(normalize_path_symbol(symbol))
        if snapshot is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="No market data is available for this symbol",
            )
        return snapshot

    @app.get(
        "/api/v1/market-data/{symbol}/history",
        response_model=MarketSnapshotHistoryResponse,
        tags=["market-data"],
    )
    def snapshot_history(
        symbol: str,
        limit: int = Query(default=100, ge=2, le=500),
    ) -> MarketSnapshotHistoryResponse:
        return MarketSnapshotHistoryResponse(
            items=repository.history(normalize_path_symbol(symbol), limit),
        )

    @app.post(
        "/api/v1/economic-calendar/events",
        response_model=EconomicCalendarIngestResult,
        status_code=status.HTTP_202_ACCEPTED,
        tags=["economic-calendar"],
    )
    def ingest_economic_calendar(
        update: EconomicCalendarUpdate,
        _: None = Depends(authorize_economic_calendar),
    ) -> EconomicCalendarIngestResult:
        repository.upsert_calendar(update)
        return EconomicCalendarIngestResult(
            event_count=len(update.events),
            fetched_at=update.fetched_at,
        )

    @app.get(
        "/api/v1/economic-calendar/events",
        response_model=EconomicCalendarResponse,
        tags=["economic-calendar"],
    )
    def list_economic_calendar(
        currencies: str = Query(default="USD"),
        starts_at: datetime = Query(alias="from"),
        ends_at: datetime = Query(alias="to"),
    ) -> EconomicCalendarResponse:
        normalized_currencies = {
            value.strip().upper()
            for value in currencies.split(",")
            if value.strip()
        }
        if not normalized_currencies or any(
            len(value) != 3 or not value.isalpha()
            for value in normalized_currencies
        ):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="currencies must be a comma-separated list of three-letter codes",
            )
        if (
            starts_at.tzinfo is None or starts_at.utcoffset() is None or
            ends_at.tzinfo is None or ends_at.utcoffset() is None
        ):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="calendar range must include timezones",
            )
        start = starts_at.astimezone(UTC)
        end = ends_at.astimezone(UTC)
        if end <= start or end - start > timedelta(days=7):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="calendar range must be positive and no longer than seven days",
            )
        return EconomicCalendarResponse(
            items=repository.calendar_events(normalized_currencies, start, end),
        )

    @app.get(
        "/api/v1/watchlist",
        response_model=WatchlistResponse,
        tags=["watchlist"],
    )
    def list_watchlist() -> WatchlistResponse:
        return WatchlistResponse(items=repository.list_watchlist())

    @app.put(
        "/api/v1/watchlist/{symbol}",
        response_model=WatchlistEntry,
        tags=["watchlist"],
    )
    def add_watchlist_symbol(
        symbol: str,
        _: None = Depends(authorize_watchlist_admin),
    ) -> WatchlistEntry:
        return repository.add_watchlist_symbol(normalize_path_symbol(symbol))

    @app.delete(
        "/api/v1/watchlist/{symbol}",
        status_code=status.HTTP_204_NO_CONTENT,
        tags=["watchlist"],
    )
    def remove_watchlist_symbol(
        symbol: str,
        _: None = Depends(authorize_watchlist_admin),
    ) -> Response:
        normalized = normalize_path_symbol(symbol)
        if not repository.remove_watchlist_symbol(normalized):
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Symbol is not in the watchlist",
            )
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return app


app = create_app()
