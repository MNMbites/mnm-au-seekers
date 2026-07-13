package com.mnm.auseekers.domain

import kotlin.math.abs
import kotlin.math.roundToInt

class SignalEngine {
    fun analyse(
        snapshots: List<MarketSnapshot>,
        mode: TradingMode,
    ): MarketAnalysis {
        val byTimeframe = snapshots.associateBy { it.timeframe }
        val signals = Timeframe.entries.map { timeframe ->
            byTimeframe[timeframe]?.let(::score) ?: TimeframeSignal(
                timeframe = timeframe,
                direction = Direction.WAIT,
                strength = 0,
                score = 0,
            )
        }

        return when (mode) {
            TradingMode.PRIMARY -> analysePrimary(signals)
            TradingMode.SCALPING -> analyseScalping(signals)
        }
    }

    private fun score(snapshot: MarketSnapshot): TimeframeSignal {
        var score = 0
        score += comparison(snapshot.close, snapshot.ema5)
        score += comparison(snapshot.ema5, snapshot.ma9)
        score += comparison(snapshot.ma9, snapshot.ma21)
        score += comparison(snapshot.ma21, snapshot.ma63)
        score += comparison(snapshot.ma63, snapshot.ma84)
        score += when {
            snapshot.rsi >= 55.0 -> 1
            snapshot.rsi <= 45.0 -> -1
            else -> 0
        }
        score += comparison(snapshot.macdHistogram, 0.0)
        score += when {
            snapshot.close > snapshot.bbUpper -> 1
            snapshot.close < snapshot.bbLower -> -1
            else -> 0
        }

        val direction = when {
            score >= SIGNAL_THRESHOLD -> Direction.BUY
            score <= -SIGNAL_THRESHOLD -> Direction.SELL
            else -> Direction.WAIT
        }
        return TimeframeSignal(
            timeframe = snapshot.timeframe,
            direction = direction,
            strength = (abs(score) * 100.0 / MAX_SCORE).roundToInt(),
            score = score,
        )
    }

    private fun analysePrimary(signals: List<TimeframeSignal>): MarketAnalysis {
        val m15 = signals.forTimeframe(Timeframe.M15)
        val h1 = signals.forTimeframe(Timeframe.H1)
        val h4 = signals.forTimeframe(Timeframe.H4)
        val higherTimeframesAgree = h1.direction != Direction.WAIT && h1.direction == h4.direction
        val entryTimeframeOpposes = m15.direction != Direction.WAIT && m15.direction != h1.direction
        val direction = if (higherTimeframesAgree && !entryTimeframeOpposes) {
            h1.direction
        } else {
            Direction.WAIT
        }
        val strength = weightedStrength(
            m15 to 0.20,
            h1 to 0.35,
            h4 to 0.45,
        )
        val rationale = when {
            !higherTimeframesAgree -> "H1 and H4 are not aligned; wait for the primary trend."
            entryTimeframeOpposes -> "M15 opposes the higher-timeframe trend; wait for confirmation."
            m15.direction == Direction.WAIT -> "H1 and H4 agree; M15 is forming an early entry setup."
            else -> "M15, H1, and H4 agree with the primary trend."
        }
        return MarketAnalysis(
            mode = TradingMode.PRIMARY,
            direction = direction,
            strength = strength,
            stage = stage(direction, strength, m15.direction),
            rationale = rationale,
            timeframeSignals = signals,
        )
    }

    private fun analyseScalping(signals: List<TimeframeSignal>): MarketAnalysis {
        val m15 = signals.forTimeframe(Timeframe.M15)
        val h1 = signals.forTimeframe(Timeframe.H1)
        val h1Opposes = h1.direction != Direction.WAIT && h1.direction != m15.direction
        val direction = if (m15.direction != Direction.WAIT && !h1Opposes) {
            m15.direction
        } else {
            Direction.WAIT
        }
        val strength = weightedStrength(m15 to 0.70, h1 to 0.30)
        val rationale = when {
            m15.direction == Direction.WAIT -> "M15 has no clear momentum; stand by."
            h1Opposes -> "H1 opposes the M15 move; the scalping guard blocks the setup."
            else -> "M15 momentum is supported or not opposed by H1."
        }
        return MarketAnalysis(
            mode = TradingMode.SCALPING,
            direction = direction,
            strength = strength,
            stage = stage(direction, strength, m15.direction),
            rationale = rationale,
            timeframeSignals = signals,
        )
    }

    private fun weightedStrength(vararg values: Pair<TimeframeSignal, Double>): Int =
        values.sumOf { (signal, weight) -> signal.strength * weight }.roundToInt().coerceIn(0, 100)

    private fun stage(
        direction: Direction,
        strength: Int,
        entryDirection: Direction,
    ): SetupStage = when {
        direction == Direction.WAIT -> SetupStage.WATCH
        strength >= 65 && entryDirection != Direction.WAIT -> SetupStage.CONFIRMED
        else -> SetupStage.EARLY
    }

    private fun comparison(left: Double, right: Double): Int = when {
        left > right -> 1
        left < right -> -1
        else -> 0
    }

    private fun List<TimeframeSignal>.forTimeframe(timeframe: Timeframe): TimeframeSignal =
        first { it.timeframe == timeframe }

    private companion object {
        const val SIGNAL_THRESHOLD = 3
        const val MAX_SCORE = 8
    }
}
