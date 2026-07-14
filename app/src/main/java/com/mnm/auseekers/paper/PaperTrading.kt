package com.mnm.auseekers.paper

import com.mnm.auseekers.domain.Direction
import java.util.UUID

data class PaperPosition(
    val id: String,
    val symbol: String,
    val direction: Direction,
    val lotSize: Double,
    val entryPrice: Double,
    val pointSize: Double,
    val valuePerPointPerLot: Double,
    val plannedStopPoints: Double,
    val maximumPlannedLoss: Double,
    val openedAtEpochMillis: Long,
)

data class ClosedPaperTrade(
    val id: String,
    val symbol: String,
    val direction: Direction,
    val lotSize: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val realizedPnl: Double,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long,
)

data class PaperPortfolio(
    val startingBalance: Double? = null,
    val openPositions: List<PaperPosition> = emptyList(),
    val closedTrades: List<ClosedPaperTrade> = emptyList(),
) {
    val realizedPnl: Double
        get() = closedTrades.sumOf { it.realizedPnl }

    val cashBalance: Double?
        get() = startingBalance?.plus(realizedPnl)
}

data class OpenPaperTradeRequest(
    val symbol: String,
    val direction: Direction,
    val lotSize: Double,
    val bid: Double,
    val ask: Double,
    val pointSize: Double,
    val valuePerPointPerLot: Double,
    val plannedStopPoints: Double,
    val maximumPlannedLoss: Double,
    val startingBalance: Double,
)

class PaperTradingLedger(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun open(
        portfolio: PaperPortfolio,
        request: OpenPaperTradeRequest,
    ): PaperPortfolio {
        validate(request)
        require(
            portfolio.openPositions.none { it.symbol.equals(request.symbol, ignoreCase = true) },
        ) { "A paper position is already open for this symbol" }

        val entryPrice = when (request.direction) {
            Direction.BUY -> request.ask
            Direction.SELL -> request.bid
            Direction.WAIT -> error("A paper position requires Buy or Sell direction")
        }
        val position = PaperPosition(
            id = idFactory(),
            symbol = request.symbol,
            direction = request.direction,
            lotSize = request.lotSize,
            entryPrice = entryPrice,
            pointSize = request.pointSize,
            valuePerPointPerLot = request.valuePerPointPerLot,
            plannedStopPoints = request.plannedStopPoints,
            maximumPlannedLoss = request.maximumPlannedLoss,
            openedAtEpochMillis = clock(),
        )
        return portfolio.copy(
            startingBalance = portfolio.startingBalance ?: request.startingBalance,
            openPositions = portfolio.openPositions + position,
        )
    }

    fun close(
        portfolio: PaperPortfolio,
        positionId: String,
        bid: Double,
        ask: Double,
    ): PaperPortfolio {
        requireValidQuote(bid, ask)
        val position = portfolio.openPositions.firstOrNull { it.id == positionId }
            ?: error("Paper position was not found")
        val exitPrice = executableExitPrice(position, bid, ask)
        val closed = ClosedPaperTrade(
            id = position.id,
            symbol = position.symbol,
            direction = position.direction,
            lotSize = position.lotSize,
            entryPrice = position.entryPrice,
            exitPrice = exitPrice,
            realizedPnl = profitAndLoss(position, exitPrice),
            openedAtEpochMillis = position.openedAtEpochMillis,
            closedAtEpochMillis = clock(),
        )
        return portfolio.copy(
            openPositions = portfolio.openPositions.filterNot { it.id == positionId },
            closedTrades = (listOf(closed) + portfolio.closedTrades).take(HISTORY_LIMIT),
        )
    }

    fun unrealizedPnl(
        position: PaperPosition,
        bid: Double,
        ask: Double,
    ): Double {
        requireValidQuote(bid, ask)
        return profitAndLoss(position, executableExitPrice(position, bid, ask))
    }

    fun reset(portfolio: PaperPortfolio): PaperPortfolio {
        require(portfolio.openPositions.isEmpty()) {
            "Close every paper position before resetting the portfolio"
        }
        return PaperPortfolio()
    }

    private fun validate(request: OpenPaperTradeRequest) {
        require(request.symbol.length in 2..32 && request.symbol.none(Char::isWhitespace)) {
            "Paper symbol is invalid"
        }
        require(request.direction != Direction.WAIT) {
            "A paper position requires Buy or Sell direction"
        }
        require(request.lotSize.isFinite() && request.lotSize > 0) {
            "Paper lot size must be greater than zero"
        }
        require(request.pointSize.isFinite() && request.pointSize > 0) {
            "Point size must be greater than zero"
        }
        require(request.valuePerPointPerLot.isFinite() && request.valuePerPointPerLot > 0) {
            "Point value must be greater than zero"
        }
        require(request.plannedStopPoints.isFinite() && request.plannedStopPoints > 0) {
            "Planned stop distance must be greater than zero"
        }
        require(request.maximumPlannedLoss.isFinite() && request.maximumPlannedLoss > 0) {
            "Maximum planned loss must be greater than zero"
        }
        require(request.startingBalance.isFinite() && request.startingBalance >= 15.0) {
            "Paper starting balance must be at least USD 15"
        }
        requireValidQuote(request.bid, request.ask)
    }

    private fun requireValidQuote(bid: Double, ask: Double) {
        require(bid.isFinite() && ask.isFinite() && bid > 0 && ask >= bid) {
            "A valid live bid and ask are required"
        }
    }

    private fun executableExitPrice(
        position: PaperPosition,
        bid: Double,
        ask: Double,
    ): Double = when (position.direction) {
        Direction.BUY -> bid
        Direction.SELL -> ask
        Direction.WAIT -> error("Stored paper position direction is invalid")
    }

    private fun profitAndLoss(position: PaperPosition, exitPrice: Double): Double {
        val signedMove = (exitPrice - position.entryPrice) * position.direction.sign
        val moveInPoints = signedMove / position.pointSize
        return moveInPoints * position.valuePerPointPerLot * position.lotSize
    }

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}
