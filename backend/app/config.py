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
    watchlist_admin_token: str | None = None
    database_url: str | None = None
    max_snapshot_age_seconds: int = 120

    @classmethod
    def from_env(cls) -> "Settings":
        token = os.getenv("MT5_BRIDGE_TOKEN")
        admin_token = os.getenv("WATCHLIST_ADMIN_TOKEN")
        database_url = os.getenv("DATABASE_URL")
        max_age = int(os.getenv("MARKET_DATA_MAX_AGE_SECONDS", "120"))
        if max_age <= 0:
            raise ValueError("MARKET_DATA_MAX_AGE_SECONDS must be greater than zero")
        return cls(
            bridge_token=token.strip() if token and token.strip() else None,
            watchlist_admin_token=(
                admin_token.strip() if admin_token and admin_token.strip() else None
            ),
            database_url=(
                database_url.strip() if database_url and database_url.strip() else None
            ),
            max_snapshot_age_seconds=max_age,
        )
