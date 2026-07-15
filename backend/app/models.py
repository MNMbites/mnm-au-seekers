from __future__ import annotations

from datetime import UTC, datetime
from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class Timeframe(StrEnum):
    M15 = "M15"
    H1 = "H1"
    H4 = "H4"


class EconomicImpact(StrEnum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class TimeframeIndicators(BaseModel):
    model_config = ConfigDict(extra="forbid")

    timeframe: Timeframe
    close: float = Field(gt=0)
    ema5: float = Field(gt=0)
    ma9: float = Field(gt=0)
    ma21: float = Field(gt=0)
    ma63: float = Field(gt=0)
    ma84: float = Field(gt=0)
    bb_upper: float = Field(gt=0)
    bb_lower: float = Field(gt=0)
    rsi: float = Field(ge=0, le=100)
    macd_histogram: float

    @model_validator(mode="after")
    def validate_bollinger_band(self) -> "TimeframeIndicators":
        if self.bb_lower > self.bb_upper:
            raise ValueError("bb_lower must not exceed bb_upper")
        return self


class MarketSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["1.0"]
    source: Literal["mt5"] = "mt5"
    symbol: str = Field(min_length=2, max_length=32)
    captured_at: datetime
    bid: float = Field(gt=0)
    ask: float = Field(gt=0)
    timeframes: list[TimeframeIndicators] = Field(min_length=3, max_length=3)

    @field_validator("symbol")
    @classmethod
    def normalize_symbol(cls, value: str) -> str:
        return validated_symbol(value)

    @field_validator("captured_at")
    @classmethod
    def require_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("captured_at must include a timezone")
        return value.astimezone(UTC)

    @model_validator(mode="after")
    def validate_market_snapshot(self) -> "MarketSnapshot":
        if self.ask < self.bid:
            raise ValueError("ask must be greater than or equal to bid")

        actual = {entry.timeframe for entry in self.timeframes}
        required = set(Timeframe)
        if actual != required or len(actual) != len(self.timeframes):
            raise ValueError("timeframes must contain M15, H1, and H4 exactly once")
        return self


class IngestResult(BaseModel):
    accepted: Literal[True] = True
    symbol: str
    captured_at: datetime


class MarketDataEnvelope(BaseModel):
    state: Literal["live", "stale"]
    age_seconds: int
    received_at: datetime
    snapshot: MarketSnapshot


class MarketSnapshotHistoryResponse(BaseModel):
    items: list[MarketSnapshot]


class EconomicCalendarEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    event_id: str = Field(min_length=1, max_length=128)
    source: str = Field(min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=240)
    currency: str = Field(min_length=3, max_length=3)
    impact: EconomicImpact
    scheduled_at: datetime

    @field_validator("event_id", "source", "title")
    @classmethod
    def normalize_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("calendar text must not be blank")
        return normalized

    @field_validator("currency")
    @classmethod
    def normalize_currency(cls, value: str) -> str:
        normalized = value.strip().upper()
        if not normalized.isalpha():
            raise ValueError("calendar currency must contain three letters")
        return normalized

    @field_validator("scheduled_at")
    @classmethod
    def require_event_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("scheduled_at must include a timezone")
        return value.astimezone(UTC)


class EconomicCalendarUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source: str = Field(min_length=1, max_length=64)
    fetched_at: datetime
    events: list[EconomicCalendarEvent] = Field(max_length=500)

    @field_validator("source")
    @classmethod
    def normalize_source(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("calendar source must not be blank")
        return normalized

    @field_validator("fetched_at")
    @classmethod
    def require_fetch_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("fetched_at must include a timezone")
        return value.astimezone(UTC)

    @model_validator(mode="after")
    def validate_update(self) -> "EconomicCalendarUpdate":
        if any(event.source.casefold() != self.source.casefold() for event in self.events):
            raise ValueError("every event source must match the update source")
        keys = [event.event_id.casefold() for event in self.events]
        if len(keys) != len(set(keys)):
            raise ValueError("calendar event IDs must be unique within an update")
        return self


class EconomicCalendarIngestResult(BaseModel):
    accepted: Literal[True] = True
    event_count: int
    fetched_at: datetime


class EconomicCalendarResponse(BaseModel):
    items: list[EconomicCalendarEvent]


class WatchlistEntry(BaseModel):
    symbol: str
    created_at: datetime


class WatchlistResponse(BaseModel):
    items: list[WatchlistEntry]


def validated_symbol(value: str) -> str:
    normalized = value.strip()
    if len(normalized) < 2 or len(normalized) > 32:
        raise ValueError("symbol must contain between 2 and 32 characters")
    if any(character.isspace() for character in normalized):
        raise ValueError("symbol must not contain whitespace")
    return normalized
