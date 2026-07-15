"""Add provider-neutral economic calendar events."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0002_economic_calendar"
down_revision: str | None = "0001_market_data_watchlist"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "economic_calendar_events",
        sa.Column("event_key", sa.String(length=256), nullable=False),
        sa.Column("event_id", sa.String(length=128), nullable=False),
        sa.Column("source", sa.String(length=64), nullable=False),
        sa.Column("title", sa.String(length=240), nullable=False),
        sa.Column("currency", sa.String(length=3), nullable=False),
        sa.Column("impact", sa.String(length=16), nullable=False),
        sa.Column("scheduled_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("fetched_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("event_key"),
    )
    op.create_index(
        "ix_calendar_currency_scheduled",
        "economic_calendar_events",
        ["currency", "scheduled_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_calendar_currency_scheduled",
        table_name="economic_calendar_events",
    )
    op.drop_table("economic_calendar_events")
