package com.mnm.auseekers.paper

import com.mnm.auseekers.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTradingLedgerTest {
    private var now = 1_000L
    private var nextId = 0
    private val ledger = PaperTradingLedger(
        clock = { now },
        idFactory = { "paper-${++nextId}" },
    )

    @Test
    fun buyOpensAtAskAndClosesAtBid() {
        val opened = ledger.open(PaperPortfolio(), request(Direction.BUY))
        val position = opened.openPositions.single()

        assertEquals(100.2, position.entryPrice, 0.0001)
        assertEquals(-2.0, ledger.unrealizedPnl(position, bid = 100.0, ask = 100.2), 0.0001)

        now = 2_000L
        val closed = ledger.close(opened, position.id, bid = 101.0, ask = 101.2)

        assertTrue(closed.openPositions.isEmpty())
        assertEquals(8.0, closed.closedTrades.single().realizedPnl, 0.0001)
        assertEquals(108.0, closed.cashBalance!!, 0.0001)
    }

    @Test
    fun sellOpensAtBidAndClosesAtAsk() {
        val opened = ledger.open(PaperPortfolio(), request(Direction.SELL))
        val position = opened.openPositions.single()
        val closed = ledger.close(opened, position.id, bid = 99.0, ask = 99.2)

        assertEquals(100.0, position.entryPrice, 0.0001)
        assertEquals(8.0, closed.closedTrades.single().realizedPnl, 0.0001)
    }

    @Test
    fun preventsStackingPaperPositionsForSameSymbol() {
        val opened = ledger.open(PaperPortfolio(), request(Direction.BUY))

        val error = runCatching {
            ledger.open(opened, request(Direction.BUY).copy(symbol = "xauusd"))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("already open"))
    }

    @Test
    fun firstTradeLocksStartingBalanceUntilPortfolioReset() {
        var portfolio = ledger.open(PaperPortfolio(), request(Direction.BUY))
        val first = portfolio.openPositions.single()
        portfolio = ledger.close(portfolio, first.id, bid = 101.0, ask = 101.2)
        portfolio = ledger.open(
            portfolio,
            request(Direction.SELL).copy(symbol = "EURUSD", startingBalance = 1_000.0),
        )

        assertEquals(100.0, portfolio.startingBalance!!, 0.0001)
    }

    @Test
    fun resetRequiresEveryPaperPositionToBeClosed() {
        val opened = ledger.open(PaperPortfolio(), request(Direction.BUY))

        assertTrue(runCatching { ledger.reset(opened) }.exceptionOrNull() is IllegalArgumentException)

        val closed = ledger.close(opened, opened.openPositions.single().id, 100.0, 100.2)
        assertEquals(PaperPortfolio(), ledger.reset(closed))
    }

    @Test
    fun rejectsWaitDirectionAndInvalidPointSize() {
        assertTrue(
            runCatching { ledger.open(PaperPortfolio(), request(Direction.WAIT)) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                ledger.open(PaperPortfolio(), request(Direction.BUY).copy(pointSize = 0.0))
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    private fun request(direction: Direction) = OpenPaperTradeRequest(
        symbol = "XAUUSD",
        direction = direction,
        lotSize = 0.5,
        bid = 100.0,
        ask = 100.2,
        pointSize = 0.1,
        valuePerPointPerLot = 2.0,
        plannedStopPoints = 20.0,
        maximumPlannedLoss = 20.0,
        startingBalance = 100.0,
    )
}
