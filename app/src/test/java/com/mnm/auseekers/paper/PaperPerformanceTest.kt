package com.mnm.auseekers.paper

import com.mnm.auseekers.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPerformanceTest {
    private val analyzer = PaperPerformanceAnalyzer()

    @Test
    fun replayVerifiesPnlAndComputesHistoricalStatistics() {
        val portfolio = PaperPortfolio(
            startingBalance = 100.0,
            closedTrades = listOf(
                trade(id = "win", realizedPnl = 8.0, exitPrice = 101.0, closedAt = 2_000),
                trade(id = "loss", realizedPnl = -4.0, exitPrice = 99.8, closedAt = 3_000),
            ),
        )

        val report = analyzer.analyze(portfolio)

        assertEquals(PaperAuditStatus.VERIFIED, report.status)
        assertEquals(2, report.tradeCount)
        assertEquals(1, report.wins)
        assertEquals(1, report.losses)
        assertEquals(50.0, report.historicalWinRatePercent!!, 0.0001)
        assertEquals(4.0, report.reportedNetPnl, 0.0001)
        assertEquals(4.0, report.replayedNetPnl!!, 0.0001)
        assertEquals(4.0, report.maximumDrawdown, 0.0001)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun replayFlagsTamperedOrLegacyPnlInputs() {
        val tampered = trade(
            id = "tampered",
            realizedPnl = 9.0,
            exitPrice = 101.0,
            closedAt = 2_000,
        )
        val legacy = trade(
            id = "legacy",
            realizedPnl = 8.0,
            exitPrice = 101.0,
            closedAt = 3_000,
        ).copy(pointSize = 0.0)

        val report = analyzer.analyze(
            PaperPortfolio(startingBalance = 100.0, closedTrades = listOf(tampered, legacy)),
        )

        assertEquals(PaperAuditStatus.NEEDS_REVIEW, report.status)
        assertEquals(2, report.issues.size)
        assertNull(report.replayedNetPnl)
    }

    @Test
    fun emptyPortfolioHasNoPredictiveStatistics() {
        val report = analyzer.analyze(PaperPortfolio())

        assertEquals(PaperAuditStatus.EMPTY, report.status)
        assertNull(report.historicalWinRatePercent)
        assertNull(report.replayedNetPnl)
    }

    @Test
    fun csvContainsReplayInputsAndNeutralizesFormulaText() {
        val portfolio = PaperPortfolio(
            startingBalance = 100.0,
            closedTrades = listOf(
                trade(id = "=formula", realizedPnl = 8.0, exitPrice = 101.0, closedAt = 2_000),
            ),
        )
        val report = analyzer.analyze(portfolio)

        val csv = PaperCsvExporter().export(
            portfolio,
            report,
            generatedAtEpochMillis = 4_000,
        )

        assertTrue(csv.contains("audit_status,Replay verified"))
        assertTrue(csv.contains("point_size,value_per_point_per_lot"))
        assertTrue(csv.contains("'=formula"))
        assertTrue(csv.contains("1970-01-01T00:00:04Z"))
    }

    private fun trade(
        id: String,
        realizedPnl: Double,
        exitPrice: Double,
        closedAt: Long,
    ) = ClosedPaperTrade(
        id = id,
        symbol = "XAUUSD",
        direction = Direction.BUY,
        lotSize = 0.5,
        entryPrice = 100.2,
        exitPrice = exitPrice,
        pointSize = 0.1,
        valuePerPointPerLot = 2.0,
        plannedStopPoints = 20.0,
        maximumPlannedLoss = 20.0,
        realizedPnl = realizedPnl,
        openedAtEpochMillis = 1_000,
        closedAtEpochMillis = closedAt,
    )
}
