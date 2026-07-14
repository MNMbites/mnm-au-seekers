package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketAnalysis
import com.mnm.auseekers.domain.SetupStage
import java.util.Locale
import kotlin.math.roundToInt

enum class MarketHealthLevel(val label: String) {
    READY("Ready"),
    CAUTION("Caution"),
    NOT_READY("Not ready"),
}

enum class HealthFactorState(val label: String) {
    POSITIVE("Good"),
    CAUTION("Check"),
    BLOCKING("Block"),
}

data class MarketHealthFactor(
    val title: String,
    val state: HealthFactorState,
    val detail: String,
    val points: Int,
)

data class MarketHealthAssessment(
    val score: Int,
    val level: MarketHealthLevel,
    val confidenceLabel: String,
    val confidenceExplanation: String,
    val factors: List<MarketHealthFactor>,
)

class MarketHealthEvaluator {
    fun evaluate(
        feed: MarketDataFeed,
        analysis: MarketAnalysis,
    ): MarketHealthAssessment {
        val factors = listOf(
            sourceFactor(feed.state),
            freshnessFactor(feed.ageSeconds),
            spreadFactor(feed.bid, feed.ask),
            agreementFactor(analysis),
        )
        val score = factors.sumOf { it.points }.coerceIn(0, 100)
        val level = when {
            factors.any { it.state == HealthFactorState.BLOCKING } -> {
                MarketHealthLevel.NOT_READY
            }
            score >= READY_SCORE -> MarketHealthLevel.READY
            score >= CAUTION_SCORE -> MarketHealthLevel.CAUTION
            else -> MarketHealthLevel.NOT_READY
        }

        return MarketHealthAssessment(
            score = score,
            level = level,
            confidenceLabel = if (feed.state == FeedState.LIVE) {
                confidenceLabel(analysis)
            } else {
                "Unavailable"
            },
            confidenceExplanation = if (feed.state == FeedState.LIVE) {
                confidenceExplanation(analysis)
            } else {
                "Confidence requires a validated live snapshot."
            },
            factors = factors,
        )
    }

    private fun sourceFactor(state: FeedState): MarketHealthFactor = when (state) {
        FeedState.LIVE -> MarketHealthFactor(
            title = "Data source",
            state = HealthFactorState.POSITIVE,
            detail = "Validated live bridge snapshot",
            points = 25,
        )
        FeedState.STALE -> MarketHealthFactor(
            title = "Data source",
            state = HealthFactorState.BLOCKING,
            detail = "Backend marked the latest snapshot stale",
            points = 0,
        )
        FeedState.DEMO -> MarketHealthFactor(
            title = "Data source",
            state = HealthFactorState.BLOCKING,
            detail = "Bundled demo values are not current market data",
            points = 0,
        )
        FeedState.CONNECTING -> MarketHealthFactor(
            title = "Data source",
            state = HealthFactorState.BLOCKING,
            detail = "Waiting for a validated snapshot",
            points = 0,
        )
    }

    private fun freshnessFactor(ageSeconds: Int?): MarketHealthFactor = when {
        ageSeconds == null -> MarketHealthFactor(
            title = "Freshness",
            state = HealthFactorState.BLOCKING,
            detail = "Snapshot age is unavailable",
            points = 0,
        )
        ageSeconds <= 60 -> MarketHealthFactor(
            title = "Freshness",
            state = HealthFactorState.POSITIVE,
            detail = "$ageSeconds seconds old",
            points = 20,
        )
        ageSeconds <= 120 -> MarketHealthFactor(
            title = "Freshness",
            state = HealthFactorState.CAUTION,
            detail = "$ageSeconds seconds old; confirm the bridge is updating",
            points = 12,
        )
        else -> MarketHealthFactor(
            title = "Freshness",
            state = HealthFactorState.BLOCKING,
            detail = "$ageSeconds seconds old",
            points = 0,
        )
    }

    private fun spreadFactor(bid: Double?, ask: Double?): MarketHealthFactor {
        if (bid == null || ask == null || bid <= 0 || ask < bid) {
            return MarketHealthFactor(
                title = "Relative spread",
                state = HealthFactorState.BLOCKING,
                detail = "A valid bid and ask are required",
                points = 0,
            )
        }

        val spread = ask - bid
        val midpoint = (ask + bid) / 2.0
        val basisPoints = spread / midpoint * 10_000.0
        val measurements = "${basisPoints.format(2)} bps (${spread.format(4)})"
        return when {
            basisPoints <= 2.0 -> MarketHealthFactor(
                title = "Relative spread",
                state = HealthFactorState.POSITIVE,
                detail = "$measurements; tight",
                points = 20,
            )
            basisPoints <= 10.0 -> MarketHealthFactor(
                title = "Relative spread",
                state = HealthFactorState.CAUTION,
                detail = "$measurements; elevated",
                points = 12,
            )
            else -> MarketHealthFactor(
                title = "Relative spread",
                state = HealthFactorState.BLOCKING,
                detail = "$measurements; unusually wide",
                points = 0,
            )
        }
    }

    private fun agreementFactor(analysis: MarketAnalysis): MarketHealthFactor {
        val maturityPoints = when (analysis.stage) {
            SetupStage.CONFIRMED -> 10
            SetupStage.EARLY -> 6
            SetupStage.WATCH -> 0
        }
        val points = (analysis.strength * 0.25).roundToInt() + maturityPoints
        val state = when (analysis.stage) {
            SetupStage.CONFIRMED -> HealthFactorState.POSITIVE
            SetupStage.EARLY -> HealthFactorState.CAUTION
            SetupStage.WATCH -> HealthFactorState.CAUTION
        }
        return MarketHealthFactor(
            title = "Signal agreement",
            state = state,
            detail = "${analysis.strength}% • ${analysis.stage.label}",
            points = points.coerceAtMost(35),
        )
    }

    private fun confidenceLabel(analysis: MarketAnalysis): String = when {
        analysis.stage == SetupStage.CONFIRMED && analysis.strength >= 75 -> {
            "Strong agreement"
        }
        analysis.stage == SetupStage.CONFIRMED -> "Confirmed agreement"
        analysis.stage == SetupStage.EARLY -> "Developing agreement"
        else -> "Low agreement"
    }

    private fun confidenceExplanation(analysis: MarketAnalysis): String {
        val supportive = analysis.timeframeSignals
            .filter { it.direction == analysis.direction && analysis.direction != Direction.WAIT }
            .map { it.timeframe.label }
        val pending = analysis.timeframeSignals
            .filter { it.direction == Direction.WAIT }
            .map { it.timeframe.label }
        val opposing = analysis.timeframeSignals
            .filter {
                analysis.direction != Direction.WAIT &&
                    it.direction != Direction.WAIT &&
                    it.direction != analysis.direction
            }
            .map { it.timeframe.label }

        return when {
            analysis.direction == Direction.WAIT -> analysis.rationale
            analysis.stage == SetupStage.CONFIRMED -> {
                "${supportive.joinToString()} support ${analysis.direction.label}; " +
                    "the entry timeframe is confirmed."
            }
            pending.isNotEmpty() -> {
                "${supportive.joinToString()} support ${analysis.direction.label}; " +
                    "${pending.joinToString()} ${pending.verb()} not confirmed yet."
            }
            opposing.isNotEmpty() -> {
                "${supportive.joinToString()} support ${analysis.direction.label}; " +
                    "${opposing.joinToString()} ${opposing.verb()} opposing it."
            }
            else -> analysis.rationale
        }
    }

    private fun List<String>.verb(): String = if (size == 1) "has" else "have"

    private fun Double.format(decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", this)

    private companion object {
        const val READY_SCORE = 75
        const val CAUTION_SCORE = 50
    }
}
