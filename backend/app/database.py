from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import (
    JSON,
    DateTime,
    Index,
    Integer,
    String,
    UniqueConstraint,
    create_engine,
    delete,
    select,
)
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker
from sqlalchemy.pool import StaticPool

from backend.app.models import MarketDataEnvelope, MarketSnapshot, WatchlistEntry
from backend.app.repository import OutOfOrderSnapshotError


class Base(DeclarativeBase):
    pass


class MarketSnapshotRecord(Base):
    __tablename__ = "market_snapshots"
    __table_args__ = (
        UniqueConstraint("symbol_key", "captured_at", name="uq_snapshot_symbol_time"),
        Index("ix_snapshot_symbol_captured", "symbol_key", "captured_at"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    symbol: Mapped[str] = mapped_column(String(32), nullable=False)
    symbol_key: Mapped[str] = mapped_column(String(32), nullable=False)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    payload: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False)


class WatchlistRecord(Base):
    __tablename__ = "watchlist_symbols"

    symbol_key: Mapped[str] = mapped_column(String(32), primary_key=True)
    symbol: Mapped[str] = mapped_column(String(32), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class SqlAlchemyMarketDataRepository:
    def __init__(
        self,
        database_url: str,
        max_snapshot_age_seconds: int,
        clock: Callable[[], datetime] | None = None,
        create_schema: bool = False,
    ) -> None:
        engine_options: dict[str, Any] = {"pool_pre_ping": True}
        if database_url.startswith("sqlite"):
            engine_options["connect_args"] = {"check_same_thread": False}
            if database_url.endswith(":memory:"):
                engine_options["poolclass"] = StaticPool

        self._engine = create_engine(database_url, **engine_options)
        self._sessions = sessionmaker(self._engine, expire_on_commit=False)
        self._max_snapshot_age_seconds = max_snapshot_age_seconds
        self._clock = clock or (lambda: datetime.now(UTC))
        self.storage_kind = (
            "postgresql" if database_url.startswith("postgresql") else "database"
        )
        if create_schema:
            Base.metadata.create_all(self._engine)

    def upsert(self, snapshot: MarketSnapshot) -> None:
        key = snapshot.symbol.casefold()
        received_at = self._utc_now()
        try:
            with self._sessions.begin() as session:
                latest_time = session.scalar(
                    select(MarketSnapshotRecord.captured_at)
                    .where(MarketSnapshotRecord.symbol_key == key)
                    .order_by(MarketSnapshotRecord.captured_at.desc())
                    .limit(1)
                )
                if latest_time is not None and snapshot.captured_at <= _as_utc(latest_time):
                    raise OutOfOrderSnapshotError(
                        "captured_at must be newer than the stored snapshot"
                    )
                session.add(
                    MarketSnapshotRecord(
                        symbol=snapshot.symbol,
                        symbol_key=key,
                        captured_at=snapshot.captured_at,
                        received_at=received_at,
                        payload=snapshot.model_dump(mode="json"),
                    )
                )
        except IntegrityError as error:
            raise OutOfOrderSnapshotError(
                "captured_at must be newer than the stored snapshot"
            ) from error

    def latest(self, symbol: str) -> MarketDataEnvelope | None:
        with self._sessions() as session:
            record = session.scalar(
                select(MarketSnapshotRecord)
                .where(MarketSnapshotRecord.symbol_key == symbol.casefold())
                .order_by(MarketSnapshotRecord.captured_at.desc())
                .limit(1)
            )
        if record is None:
            return None

        snapshot = MarketSnapshot.model_validate(record.payload)
        age_seconds = max(0, int((self._utc_now() - snapshot.captured_at).total_seconds()))
        return MarketDataEnvelope(
            state=(
                "stale"
                if age_seconds > self._max_snapshot_age_seconds
                else "live"
            ),
            age_seconds=age_seconds,
            received_at=_as_utc(record.received_at),
            snapshot=snapshot,
        )

    def list_watchlist(self) -> list[WatchlistEntry]:
        with self._sessions() as session:
            records = session.scalars(
                select(WatchlistRecord).order_by(WatchlistRecord.symbol_key)
            ).all()
        return [
            WatchlistEntry(symbol=record.symbol, created_at=_as_utc(record.created_at))
            for record in records
        ]

    def add_watchlist_symbol(self, symbol: str) -> WatchlistEntry:
        key = symbol.casefold()
        try:
            with self._sessions.begin() as session:
                record = session.get(WatchlistRecord, key)
                if record is None:
                    record = WatchlistRecord(
                        symbol_key=key,
                        symbol=symbol,
                        created_at=self._utc_now(),
                    )
                    session.add(record)
        except IntegrityError:
            with self._sessions() as session:
                record = session.get(WatchlistRecord, key)
            if record is None:
                raise
        return WatchlistEntry(symbol=record.symbol, created_at=_as_utc(record.created_at))

    def remove_watchlist_symbol(self, symbol: str) -> bool:
        with self._sessions.begin() as session:
            result = session.execute(
                delete(WatchlistRecord).where(
                    WatchlistRecord.symbol_key == symbol.casefold()
                )
            )
            return bool(result.rowcount)

    def _utc_now(self) -> datetime:
        return _as_utc(self._clock())


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)
