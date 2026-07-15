package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.HistoricalMarketDataPoint
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.Timeframe
import com.mnm.auseekers.domain.TradingMode
import java.util.Locale
import kotlin.math.abs

enum class HistoricalReplayStatus(val label: String) {
    EMPTY("No stored history"),
    INSUFFICIENT("Need more history"),
    VERIFIED("Replay complete"),
    NEEDS_REVIEW("Needs review"),
}

data class HistoricalReplayReport(
    val status: HistoricalReplayStatus,
    val observationCount: Int,
    val transitionCount: Int,
    val actionableSignalCount: Int,
    val buySignalCount: Int,
    val sellSignalCount: Int,
    val confirmedSignalCount: Int,
    val favorableMoveCount: Int,
    val adverseMoveCount: Int,
    val flatMoveCount: Int,
    val averageSignedMoveBps: Double?,
    val maximumAdverseMoveBps: Double,
    val issue: String?,
)

class HistoricalSignalReplay(
    private val signalEngine: SignalEngine = SignalEngine(),
) {
    fun replay(
        points: List<HistoricalMarketDataPoint>,
        mode: TradingMode,
    ): HistoricalReplayReport {
        if (points.isEmpty()) return emptyReport(HistoricalReplayStatus.EMPTY, 0)

        validate(points)?.let { issue ->
            return emptyReport(
                status = HistoricalReplayStatus.NEEDS_REVIEW,
                observationCount = points.size,
                issue = issue,
            )
        }
        if (points.size < 2) {
            return emptyReport(HistoricalReplayStatus.INSUFFICIENT, points.size)
        }

        val results = points.zipWithNext().mapNotNull { (current, next) ->
            val analysis = signalEngine.analyse(current.snapshots, mode)
            if (analysis.direction == Direction.WAIT) return@mapNotNull null

            val currentMidpoint = (current.bid + current.ask) / 2.0
            val nextMidpoint = (next.bid + next.ask) / 2.0
            val rawMoveBps = (nextMidpoint - currentMidpoint) / currentMidpoint * 10_000.0
            ReplayObservation(
                direction = analysis.direction,
                stage = analysis.stage,
                signedMoveBps = rawMoveBps * analysis.direction.sign,
            )
        }
        return HistoricalReplayReport(
            status = HistoricalReplayStatus.VERIFIED,
            observationCount = points.size,
            transitionCount = points.size - 1,
            actionableSignalCount = results.size,
            buySignalCount = results.count { it.direction == Direction.BUY },
            sellSignalCount = results.count { it.direction == Direction.SELL },
            confirmedSignalCount = results.count { it.stage == SetupStage.CONFIRMED },
            favorableMoveCount = results.count { it.signedMoveBps > MOVE_TOLERANCE },
            adverseMoveCount = results.count { it.signedMoveBps < -MOVE_TOLERANCE },
            flatMoveCount = results.count { abs(it.signedMoveBps) <= MOVE_TOLERANCE },
            averageSignedMoveBps = results.takeIf { it.isNotEmpty() }
                ?.map { it.signedMoveBps }
                ?.average(),
            maximumAdverseMoveBps = abs(
                results.minOfOrNull { it.signedMoveBps }?.coerceAtMost(0.0) ?: 0.0,
            ),
            issue = null,
        )
    }

    private fun validate(points: List<HistoricalMarketDataPoint>): String? {
        val symbol = points.first().symbol.lowercase(Locale.US)
        points.forEachIndexed { index, point ->
            if (point.symbol.lowercase(Locale.US) != symbol) {
                return "History contains more than one symbol"
            }
            if (!point.bid.isFinite() || !point.ask.isFinite() ||
                point.bid <= 0 || point.ask < point.bid
            ) {
                return "History contains an invalid quote"
            }
            if (point.snapshots.size != Timeframe.entries.size ||
                point.snapshots.map { it.timeframe }.toSet() != Timeframe.entries.toSet()
            ) {
                return "History is missing a required timeframe"
            }
            if (index > 0 && point.capturedAt <= points[index - 1].capturedAt) {
                return "History is not strictly chronological"
            }
        }
        return null
    }

    private fun emptyReport(
        status: HistoricalReplayStatus,
        observationCount: Int,
        issue: String? = null,
    ) = HistoricalReplayReport(
        status = status,
        observationCount = observationCount,
        transitionCount = 0,
        actionableSignalCount = 0,
        buySignalCount = 0,
        sellSignalCount = 0,
        confirmedSignalCount = 0,
        favorableMoveCount = 0,
        adverseMoveCount = 0,
        flatMoveCount = 0,
        averageSignedMoveBps = null,
        maximumAdverseMoveBps = 0.0,
        issue = issue,
    )

    private data class ReplayObservation(
        val direction: Direction,
        val stage: SetupStage,
        val signedMoveBps: Double,
    )

    private companion object {
        const val MOVE_TOLERANCE = 1e-9
    }
}
