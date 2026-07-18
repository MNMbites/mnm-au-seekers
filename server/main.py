from __future__ import annotations

import os
import time
from collections import defaultdict
from threading import RLock
from typing import Dict, List, Literal

from fastapi import FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field, model_validator

app = FastAPI(title="MNM AU Seekers Market API", version="0.1.0")

_ALLOWED_TIMEFRAMES = {"M15", "H1", "H4", "D1"}
_MAX_CANDLES = 2000
_store: Dict[str, Dict[str, List["Candle"]]] = defaultdict(lambda: defaultdict(list))
_lock = RLock()


class Candle(BaseModel):
    time: int = Field(gt=0)
    open: float
    high: float
    low: float
    close: float

    @model_validator(mode="after")
    def validate_ohlc(self) -> "Candle":
        if self.high < max(self.open, self.close):
            raise ValueError("high must be at least open and close")
        if self.low > min(self.open, self.close):
            raise ValueError("low must be at most open and close")
        if self.low > self.high:
            raise ValueError("low must not exceed high")
        return self


class IngestRequest(BaseModel):
    symbol: str = Field(min_length=3, max_length=20)
    timeframe: Literal["M15", "H1", "H4", "D1"]
    candles: List[Candle] = Field(min_length=1, max_length=500)


class PreviousDay(BaseModel):
    high: float
    low: float


class MarketDataResponse(BaseModel):
    symbol: str
    timeframe: str
    updatedAt: int
    previousDay: PreviousDay
    candles: List[Candle]


def _normalise_symbol(symbol: str) -> str:
    return symbol.strip().upper().replace("/", "")


def _authorise(api_key: str | None) -> None:
    expected = os.getenv("MNM_INGEST_API_KEY", "").strip()
    if expected and api_key != expected:
        raise HTTPException(status_code=401, detail="Invalid ingest API key")


def _derive_previous_day(candles: List[Candle]) -> PreviousDay:
    reference = candles[:-1][-24:] or candles[-24:]
    return PreviousDay(
        high=max(candle.high for candle in reference),
        low=min(candle.low for candle in reference),
    )


@app.get("/")
def root() -> dict:
    return {
        "service": "MNM AU Seekers Market API",
        "status": "online",
        "routes": ["/health", "/market-data", "/ingest"],
    }


@app.get("/health")
def health() -> dict:
    with _lock:
        streams = sum(len(timeframes) for timeframes in _store.values())
        candles = sum(
            len(items)
            for timeframes in _store.values()
            for items in timeframes.values()
        )
    return {
        "status": "ok",
        "serverTime": int(time.time() * 1000),
        "streams": streams,
        "candles": candles,
    }


@app.post("/ingest")
def ingest(
    request: IngestRequest,
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> dict:
    _authorise(x_api_key)
    symbol = _normalise_symbol(request.symbol)
    timeframe = request.timeframe.upper()

    with _lock:
        merged = {
            candle.time: candle
            for candle in _store[symbol][timeframe]
        }
        for candle in request.candles:
            merged[candle.time] = candle
        ordered = sorted(merged.values(), key=lambda candle: candle.time)[-_MAX_CANDLES:]
        _store[symbol][timeframe] = ordered

    return {
        "accepted": len(request.candles),
        "stored": len(ordered),
        "symbol": symbol,
        "timeframe": timeframe,
        "updatedAt": ordered[-1].time,
    }


@app.get("/market-data", response_model=MarketDataResponse)
def market_data(
    symbol: str = Query(default="XAUUSD", min_length=3, max_length=20),
    timeframe: str = Query(default="H4"),
    limit: int = Query(default=160, ge=90, le=500),
) -> MarketDataResponse:
    symbol = _normalise_symbol(symbol)
    timeframe = timeframe.strip().upper()
    if timeframe not in _ALLOWED_TIMEFRAMES:
        raise HTTPException(status_code=400, detail="Unsupported timeframe")

    with _lock:
        candles = list(_store.get(symbol, {}).get(timeframe, []))[-limit:]

    if len(candles) < 90:
        raise HTTPException(
            status_code=503,
            detail={
                "message": "Insufficient live candle history",
                "symbol": symbol,
                "timeframe": timeframe,
                "available": len(candles),
                "required": 90,
            },
        )

    return MarketDataResponse(
        symbol=symbol,
        timeframe=timeframe,
        updatedAt=candles[-1].time,
        previousDay=_derive_previous_day(candles),
        candles=candles,
    )
