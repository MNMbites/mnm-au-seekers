from __future__ import annotations

from datetime import UTC, datetime
import os
from uuid import uuid4

import pytest

from backend.app.database import SqlAlchemyMarketDataRepository
from backend.app.models import MarketSnapshot
from backend.app.repository import OutOfOrderSnapshotError
from backend.tests.test_api import NOW, snapshot_payload


def repository(database_url: str) -> SqlAlchemyMarketDataRepository:
    return SqlAlchemyMarketDataRepository(
        database_url,
        max_snapshot_age_seconds=120,
        clock=lambda: NOW,
        create_schema=True,
    )


def test_snapshots_persist_and_lookup_is_case_insensitive(tmp_path) -> None:
    database_url = f"sqlite:///{tmp_path / 'market-data.db'}"
    first = repository(database_url)
    snapshot = MarketSnapshot.model_validate(snapshot_payload())

    first.upsert(snapshot)
    restored = repository(database_url).latest("xauusd")

    assert restored is not None
    assert restored.state == "live"
    assert restored.age_seconds == 30
    assert restored.snapshot == snapshot


def test_database_history_is_bounded_and_chronological(tmp_path) -> None:
    database_url = f"sqlite:///{tmp_path / 'market-data.db'}"
    store = repository(database_url)
    captured = (
        "2026-07-14T11:57:30Z",
        "2026-07-14T11:58:30Z",
        "2026-07-14T11:59:30Z",
    )
    for captured_at in captured:
        store.upsert(MarketSnapshot.model_validate(snapshot_payload(captured_at)))

    history = store.history("xauusd", limit=2)

    assert [snapshot.captured_at.isoformat() for snapshot in history] == [
        "2026-07-14T11:58:30+00:00",
        "2026-07-14T11:59:30+00:00",
    ]


def test_database_rejects_duplicate_or_older_snapshots(tmp_path) -> None:
    database_url = f"sqlite:///{tmp_path / 'market-data.db'}"
    store = repository(database_url)
    snapshot = MarketSnapshot.model_validate(snapshot_payload())
    older_payload = snapshot_payload("2026-07-14T11:58:30Z")

    store.upsert(snapshot)

    with pytest.raises(OutOfOrderSnapshotError):
        store.upsert(snapshot)
    with pytest.raises(OutOfOrderSnapshotError):
        store.upsert(MarketSnapshot.model_validate(older_payload))


def test_watchlist_persists_across_repository_instances(tmp_path) -> None:
    database_url = f"sqlite:///{tmp_path / 'market-data.db'}"
    first = repository(database_url)

    created = first.add_watchlist_symbol("XAUUSD")
    duplicate = first.add_watchlist_symbol("xauusd")
    restored = repository(database_url)

    assert created == duplicate
    assert created.created_at == datetime(2026, 7, 14, 12, 0, tzinfo=UTC)
    assert restored.list_watchlist() == [created]
    assert restored.remove_watchlist_symbol("xauusd") is True
    assert restored.list_watchlist() == []


@pytest.mark.skipif(
    not os.getenv("DATABASE_URL", "").startswith("postgresql"),
    reason="PostgreSQL integration runs in CI",
)
def test_postgresql_repository_round_trip() -> None:
    database_url = os.environ["DATABASE_URL"]
    store = SqlAlchemyMarketDataRepository(
        database_url,
        max_snapshot_age_seconds=120,
        clock=lambda: NOW,
    )
    symbol = f"TEST{uuid4().hex[:12]}"
    payload = snapshot_payload()
    payload["symbol"] = symbol
    snapshot = MarketSnapshot.model_validate(payload)

    store.upsert(snapshot)
    created = store.add_watchlist_symbol(symbol)

    assert store.storage_kind == "postgresql"
    assert store.latest(symbol.casefold()).snapshot == snapshot
    assert created in store.list_watchlist()
    assert store.remove_watchlist_symbol(symbol.casefold()) is True
