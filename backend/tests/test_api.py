from __future__ import annotations

from datetime import UTC, datetime

from fastapi.testclient import TestClient

from backend.app.config import Settings
from backend.app.main import create_app
from backend.app.repository import SnapshotStore


NOW = datetime(2026, 7, 14, 12, 0, tzinfo=UTC)


def make_client(*, token: str | None = "test-bridge-token", max_age: int = 120) -> TestClient:
    settings = Settings(bridge_token=token, max_snapshot_age_seconds=max_age)
    store = SnapshotStore(max_snapshot_age_seconds=max_age, clock=lambda: NOW)
    return TestClient(create_app(settings=settings, store=store))


def snapshot_payload(captured_at: str = "2026-07-14T11:59:30Z") -> dict[str, object]:
    return {
        "schema_version": "1.0",
        "source": "mt5",
        "symbol": "XAUUSD",
        "captured_at": captured_at,
        "bid": 2420.10,
        "ask": 2420.30,
        "timeframes": [
            indicators("M15", 2420.20),
            indicators("H1", 2418.40),
            indicators("H4", 2412.80),
        ],
    }


def indicators(timeframe: str, close: float) -> dict[str, object]:
    return {
        "timeframe": timeframe,
        "close": close,
        "ema5": close - 0.20,
        "ma9": close - 0.40,
        "ma21": close - 0.80,
        "ma63": close - 1.20,
        "ma84": close - 1.50,
        "bb_upper": close + 4.00,
        "bb_lower": close - 4.00,
        "rsi": 58.0,
        "macd_histogram": 0.42,
    }


def test_health_reports_when_ingestion_is_disabled() -> None:
    response = make_client(token=None).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "ingestion": "disabled"}


def test_ingestion_requires_runtime_token_configuration() -> None:
    response = make_client(token=None).post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload(),
        headers={"X-Bridge-Token": "anything"},
    )

    assert response.status_code == 503


def test_ingestion_rejects_invalid_token() -> None:
    response = make_client().post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload(),
        headers={"X-Bridge-Token": "wrong"},
    )

    assert response.status_code == 401


def test_ingests_and_returns_latest_snapshot() -> None:
    client = make_client()
    accepted = client.post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload(),
        headers={"X-Bridge-Token": "test-bridge-token"},
    )

    assert accepted.status_code == 202
    assert accepted.json()["accepted"] is True

    latest = client.get("/api/v1/market-data/XAUUSD")
    assert latest.status_code == 200
    assert latest.json()["state"] == "live"
    assert latest.json()["age_seconds"] == 30
    assert latest.json()["snapshot"]["symbol"] == "XAUUSD"


def test_rejects_duplicate_or_out_of_order_snapshot() -> None:
    client = make_client()
    headers = {"X-Bridge-Token": "test-bridge-token"}

    assert client.post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload(),
        headers=headers,
    ).status_code == 202
    duplicate = client.post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload(),
        headers=headers,
    )

    assert duplicate.status_code == 409


def test_marks_old_market_data_stale() -> None:
    client = make_client(max_age=60)
    accepted = client.post(
        "/api/v1/market-data/mt5/snapshots",
        json=snapshot_payload("2026-07-14T11:58:00Z"),
        headers={"X-Bridge-Token": "test-bridge-token"},
    )

    assert accepted.status_code == 202
    latest = client.get("/api/v1/market-data/xauusd")
    assert latest.json()["state"] == "stale"
    assert latest.json()["age_seconds"] == 120


def test_requires_each_supported_timeframe_exactly_once() -> None:
    payload = snapshot_payload()
    payload["timeframes"] = [
        indicators("M15", 2420.20),
        indicators("M15", 2420.20),
        indicators("H4", 2412.80),
    ]
    response = make_client().post(
        "/api/v1/market-data/mt5/snapshots",
        json=payload,
        headers={"X-Bridge-Token": "test-bridge-token"},
    )

    assert response.status_code == 422
