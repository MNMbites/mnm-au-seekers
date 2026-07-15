from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, datetime
from threading import RLock
from typing import Protocol

from backend.app.models import (
    EconomicCalendarEvent,
    EconomicCalendarUpdate,
    MarketDataEnvelope,
    MarketSnapshot,
    WatchlistEntry,
)


class OutOfOrderSnapshotError(ValueError):
    """Raised when a bridge submits an older or duplicate snapshot."""


class MarketDataRepository(Protocol):
    storage_kind: str

    def upsert(self, snapshot: MarketSnapshot) -> None: ...

    def latest(self, symbol: str) -> MarketDataEnvelope | None: ...

    def history(self, symbol: str, limit: int) -> list[MarketSnapshot]: ...

    def upsert_calendar(self, update: EconomicCalendarUpdate) -> None: ...

    def calendar_events(
        self,
        currencies: set[str],
        starts_at: datetime,
        ends_at: datetime,
    ) -> list[EconomicCalendarEvent]: ...

    def list_watchlist(self) -> list[WatchlistEntry]: ...

    def add_watchlist_symbol(self, symbol: str) -> WatchlistEntry: ...

    def remove_watchlist_symbol(self, symbol: str) -> bool: ...


class SnapshotStore:
    """Thread-safe in-memory latest-snapshot store.

    Persistence and horizontal fan-out are deliberately deferred. This store is
    sufficient for the first bridge contract and keeps broker data out of the
    Android process.
    """

    storage_kind = "memory"

    def __init__(
        self,
        max_snapshot_age_seconds: int,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self._max_snapshot_age_seconds = max_snapshot_age_seconds
        self._clock = clock or (lambda: datetime.now(UTC))
        self._snapshots: dict[str, list[tuple[MarketSnapshot, datetime]]] = {}
        self._watchlist: dict[str, WatchlistEntry] = {}
        self._calendar_events: dict[str, EconomicCalendarEvent] = {}
        self._lock = RLock()

    def upsert(self, snapshot: MarketSnapshot) -> None:
        key = snapshot.symbol.casefold()
        received_at = self._utc_now()
        with self._lock:
            history = self._snapshots.setdefault(key, [])
            if history and snapshot.captured_at <= history[-1][0].captured_at:
                raise OutOfOrderSnapshotError(
                    "captured_at must be newer than the stored snapshot"
                )
            history.append((snapshot, received_at))
            del history[:-MEMORY_HISTORY_LIMIT]

    def latest(self, symbol: str) -> MarketDataEnvelope | None:
        with self._lock:
            history = self._snapshots.get(symbol.casefold())
            stored = history[-1] if history else None
        if stored is None:
            return None

        snapshot, received_at = stored
        age_seconds = max(0, int((self._utc_now() - snapshot.captured_at).total_seconds()))
        state = "stale" if age_seconds > self._max_snapshot_age_seconds else "live"
        return MarketDataEnvelope(
            state=state,
            age_seconds=age_seconds,
            received_at=received_at,
            snapshot=snapshot,
        )

    def history(self, symbol: str, limit: int) -> list[MarketSnapshot]:
        with self._lock:
            history = self._snapshots.get(symbol.casefold(), [])
            return [snapshot for snapshot, _ in history[-limit:]]

    def upsert_calendar(self, update: EconomicCalendarUpdate) -> None:
        with self._lock:
            for event in update.events:
                key = f"{event.source.casefold()}:{event.event_id.casefold()}"
                self._calendar_events[key] = event

    def calendar_events(
        self,
        currencies: set[str],
        starts_at: datetime,
        ends_at: datetime,
    ) -> list[EconomicCalendarEvent]:
        with self._lock:
            return sorted(
                (
                    event
                    for event in self._calendar_events.values()
                    if event.currency in currencies and
                    starts_at <= event.scheduled_at <= ends_at
                ),
                key=lambda event: event.scheduled_at,
            )[:CALENDAR_READ_LIMIT]

    def list_watchlist(self) -> list[WatchlistEntry]:
        with self._lock:
            return sorted(
                self._watchlist.values(),
                key=lambda entry: entry.symbol.casefold(),
            )

    def add_watchlist_symbol(self, symbol: str) -> WatchlistEntry:
        key = symbol.casefold()
        with self._lock:
            existing = self._watchlist.get(key)
            if existing is not None:
                return existing
            entry = WatchlistEntry(symbol=symbol, created_at=self._utc_now())
            self._watchlist[key] = entry
            return entry

    def remove_watchlist_symbol(self, symbol: str) -> bool:
        with self._lock:
            return self._watchlist.pop(symbol.casefold(), None) is not None

    def _utc_now(self) -> datetime:
        value = self._clock()
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("SnapshotStore clock must return a timezone-aware datetime")
        return value.astimezone(UTC)


MEMORY_HISTORY_LIMIT = 500
CALENDAR_READ_LIMIT = 500
