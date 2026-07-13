"""Create market snapshot history and watchlist tables."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0001_market_data_watchlist"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "market_snapshots",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("symbol", sa.String(length=32), nullable=False),
        sa.Column("symbol_key", sa.String(length=32), nullable=False),
        sa.Column("captured_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "symbol_key",
            "captured_at",
            name="uq_snapshot_symbol_time",
        ),
    )
    op.create_index(
        "ix_snapshot_symbol_captured",
        "market_snapshots",
        ["symbol_key", "captured_at"],
        unique=False,
    )
    op.create_table(
        "watchlist_symbols",
        sa.Column("symbol_key", sa.String(length=32), nullable=False),
        sa.Column("symbol", sa.String(length=32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("symbol_key"),
    )


def downgrade() -> None:
    op.drop_table("watchlist_symbols")
    op.drop_index("ix_snapshot_symbol_captured", table_name="market_snapshots")
    op.drop_table("market_snapshots")
