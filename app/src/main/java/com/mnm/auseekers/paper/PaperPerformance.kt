package com.mnm.auseekers.paper

import com.mnm.auseekers.domain.Direction
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class PaperAuditStatus(val label: String) {
    EMPTY("No closed trades"),
    VERIFIED("Replay verified"),
    NEEDS_REVIEW("Needs review"),
}

data class PaperReplayIssue(
    val tradeId: String,
    val detail: String,
)

data class PaperPerformanceReport(
    val status: PaperAuditStatus,
    val tradeCount: Int,
    val wins: Int,
    val losses: Int,
    val breakeven: Int,
    val historicalWinRatePercent: Double?,
    val reportedNetPnl: Double,
    val replayedNetPnl: Double?,
    val maximumDrawdown: Double,
    val issues: List<PaperReplayIssue>,
)

class PaperPerformanceAnalyzer {
    fun analyze(portfolio: PaperPortfolio): PaperPerformanceReport {
        val trades = portfolio.closedTrades
        if (trades.isEmpty()) {
            return PaperPerformanceReport(
                status = PaperAuditStatus.EMPTY,
                tradeCount = 0,
                wins = 0,
                losses = 0,
                breakeven = 0,
                historicalWinRatePercent = null,
                reportedNetPnl = 0.0,
                replayedNetPnl = null,
                maximumDrawdown = 0.0,
                issues = emptyList(),
            )
        }

        val replayResults = trades.map { trade -> trade to replay(trade) }
        val issues = replayResults.mapNotNull { (trade, result) ->
            result.exceptionOrNull()?.let { error ->
                PaperReplayIssue(
                    tradeId = trade.id,
                    detail = error.message ?: "Replay validation failed",
                )
            }
        }
        val reportedNet = trades.sumOf { it.realizedPnl }
        val wins = trades.count { it.realizedPnl > PNL_TOLERANCE }
        val losses = trades.count { it.realizedPnl < -PNL_TOLERANCE }
        val breakeven = trades.size - wins - losses
        return PaperPerformanceReport(
            status = if (issues.isEmpty()) {
                PaperAuditStatus.VERIFIED
            } else {
                PaperAuditStatus.NEEDS_REVIEW
            },
            tradeCount = trades.size,
            wins = wins,
            losses = losses,
            breakeven = breakeven,
            historicalWinRatePercent = wins * 100.0 / trades.size,
            reportedNetPnl = reportedNet,
            replayedNetPnl = if (issues.isEmpty()) {
                replayResults.sumOf { it.second.getOrThrow() }
            } else {
                null
            },
            maximumDrawdown = maximumDrawdown(portfolio),
            issues = issues,
        )
    }

    private fun replay(trade: ClosedPaperTrade): Result<Double> = runCatching {
        require(trade.id.isNotBlank()) { "Trade ID is missing" }
        require(trade.symbol.length in 2..32 && trade.symbol.none(Char::isWhitespace)) {
            "Symbol is invalid"
        }
        require(trade.direction != Direction.WAIT) { "Direction is invalid" }
        require(trade.lotSize.isFinite() && trade.lotSize > 0) { "Lot size is invalid" }
        require(trade.entryPrice.isFinite() && trade.entryPrice > 0) {
            "Entry price is invalid"
        }
        require(trade.exitPrice.isFinite() && trade.exitPrice > 0) { "Exit price is invalid" }
        require(trade.pointSize.isFinite() && trade.pointSize > 0) {
            "Point size is missing or invalid"
        }
        require(trade.valuePerPointPerLot.isFinite() && trade.valuePerPointPerLot > 0) {
            "Point value is missing or invalid"
        }
        require(trade.plannedStopPoints.isFinite() && trade.plannedStopPoints > 0) {
            "Planned stop distance is missing or invalid"
        }
        require(trade.maximumPlannedLoss.isFinite() && trade.maximumPlannedLoss > 0) {
            "Maximum planned loss is missing or invalid"
        }
        require(trade.openedAtEpochMillis > 0) { "Open timestamp is invalid" }
        require(trade.closedAtEpochMillis >= trade.openedAtEpochMillis) {
            "Close timestamp precedes open timestamp"
        }
        require(trade.realizedPnl.isFinite()) { "Reported P&L is invalid" }

        val signedMove = (trade.exitPrice - trade.entryPrice) * trade.direction.sign
        val expected = signedMove / trade.pointSize * trade.valuePerPointPerLot * trade.lotSize
        val tolerance = max(PNL_TOLERANCE, abs(expected) * RELATIVE_TOLERANCE)
        require(abs(expected - trade.realizedPnl) <= tolerance) {
            "Reported P&L does not match deterministic replay"
        }
        expected
    }

    private fun maximumDrawdown(portfolio: PaperPortfolio): Double {
        var equity = portfolio.startingBalance ?: 0.0
        var peak = equity
        var drawdown = 0.0
        portfolio.closedTrades
            .sortedBy { it.closedAtEpochMillis }
            .forEach { trade ->
                equity += trade.realizedPnl
                peak = max(peak, equity)
                drawdown = max(drawdown, peak - equity)
            }
        return drawdown
    }

    private companion object {
        const val PNL_TOLERANCE = 1e-6
        const val RELATIVE_TOLERANCE = 1e-9
    }
}

class PaperCsvExporter {
    fun export(
        portfolio: PaperPortfolio,
        report: PaperPerformanceReport,
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("section,key,value")
        row("summary", "generated_at", Instant.ofEpochMilli(generatedAtEpochMillis).toString())
        row("summary", "audit_status", report.status.label)
        row("summary", "starting_balance", portfolio.startingBalance.format())
        row("summary", "closed_trades", report.tradeCount.toString())
        row("summary", "wins", report.wins.toString())
        row("summary", "losses", report.losses.toString())
        row("summary", "breakeven", report.breakeven.toString())
        row("summary", "historical_win_rate_percent", report.historicalWinRatePercent.format())
        row("summary", "reported_net_pnl", report.reportedNetPnl.format())
        row("summary", "replayed_net_pnl", report.replayedNetPnl.format())
        row("summary", "maximum_drawdown", report.maximumDrawdown.format())
        appendLine()
        appendLine(
            "trade_id,symbol,direction,lot_size,entry_price,exit_price,point_size," +
                "value_per_point_per_lot,planned_stop_points,maximum_planned_loss," +
                "realized_pnl,opened_at,closed_at",
        )
        portfolio.closedTrades.sortedBy { it.closedAtEpochMillis }.forEach { trade ->
            appendLine(
                listOf(
                    trade.id.safeText(),
                    trade.symbol.safeText(),
                    trade.direction.name,
                    trade.lotSize.format(),
                    trade.entryPrice.format(),
                    trade.exitPrice.format(),
                    trade.pointSize.format(),
                    trade.valuePerPointPerLot.format(),
                    trade.plannedStopPoints.format(),
                    trade.maximumPlannedLoss.format(),
                    trade.realizedPnl.format(),
                    Instant.ofEpochMilli(trade.openedAtEpochMillis).toString(),
                    Instant.ofEpochMilli(trade.closedAtEpochMillis).toString(),
                ).joinToString(",", transform = ::csvCell),
            )
        }
    }

    private fun StringBuilder.row(section: String, key: String, value: String) {
        appendLine(listOf(section, key, value).joinToString(",", transform = ::csvCell))
    }

    private fun String.safeText(): String {
        val first = firstOrNull()
        return if (first != null && first in setOf('=', '+', '-', '@')) {
            "'$this"
        } else {
            this
        }
    }

    private fun csvCell(value: String): String = if (
        value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    ) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

    private fun Double?.format(): String = this?.let {
        String.format(Locale.US, "%.8f", it)
    } ?: ""
}
