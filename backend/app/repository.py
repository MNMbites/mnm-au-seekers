from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, datetime
from threading import RLock

from backend.app.models import MarketDataEnvelope, MarketSnapshot


class OutOfOrderSnapshotError(ValueError):
    """Raised when a bridge submits an older or duplicate snapshot."""


class SnapshotStore:
    """Thread-safe in-memory latest-snapshot store.

    Persistence and horizontal fan-out are deliberately deferred. This store is
    sufficient for the first bridge contract and keeps broker data out of the
    Android process.
    """

    def __init__(
        self,
        max_snapshot_age_seconds: int,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self._max_snapshot_age_seconds = max_snapshot_age_seconds
        self._clock = clock or (lambda: datetime.now(UTC))
        self._snapshots: dict[str, tuple[MarketSnapshot, datetime]] = {}
        self._lock = RLock()

    def upsert(self, snapshot: MarketSnapshot) -> None:
        key = snapshot.symbol.casefold()
        received_at = self._utc_now()
        with self._lock:
            current = self._snapshots.get(key)
            if current and snapshot.captured_at <= current[0].captured_at:
                raise OutOfOrderSnapshotError(
                    "captured_at must be newer than the stored snapshot"
                )
            self._snapshots[key] = (snapshot, received_at)

    def latest(self, symbol: str) -> MarketDataEnvelope | None:
        with self._lock:
            stored = self._snapshots.get(symbol.casefold())
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

    def _utc_now(self) -> datetime:
        value = self._clock()
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("SnapshotStore clock must return a timezone-aware datetime")
        return value.astimezone(UTC)
