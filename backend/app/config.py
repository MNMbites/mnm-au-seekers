from __future__ import annotations

from dataclasses import dataclass
import os


@dataclass(frozen=True, slots=True)
class Settings:
    """Runtime settings loaded from the process environment.

    The bridge token is intentionally required at runtime and has no repository
    default. When it is absent, ingestion is disabled while health and read
    endpoints remain available.
    """

    bridge_token: str | None = None
    max_snapshot_age_seconds: int = 120

    @classmethod
    def from_env(cls) -> "Settings":
        token = os.getenv("MT5_BRIDGE_TOKEN")
        max_age = int(os.getenv("MARKET_DATA_MAX_AGE_SECONDS", "120"))
        if max_age <= 0:
            raise ValueError("MARKET_DATA_MAX_AGE_SECONDS must be greater than zero")
        return cls(
            bridge_token=token.strip() if token and token.strip() else None,
            max_snapshot_age_seconds=max_age,
        )
