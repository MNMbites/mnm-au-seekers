package com.mnm.trendcompass

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double)
enum class Direction { BULLISH, BEARISH, NEUTRAL }
enum class Timeframe(val label: String, val weight: Double) { M15("15m", .10), H1("1h", .25), H4("4h", .35), D1("1D", .30) }
enum class FanPhase { FOLLOW_THROUGH, HEALTHY_RETRACEMENT, DEEP_RETRACEMENT, BREAKDOWN, BREAKOUT_ACCELERATION, CHOPPED_OR_UNRELIABLE }

data class PreviousDayCompass(val high: Double, val low: Double) {
    val midpoint = (high + low) / 2.0
    val range = high - low
    val q25 = low + range * .25
    val q75 = low + range * .75
    fun score(price: Double): Double = when {
        price > high -> 1.0
        price >= q75 -> .75
        price >= midpoint -> .35
        price > q25 -> -.35
        price >= low -> -.75
        else -> -1.0
    }
}

data class FibFan(val start: Candle, val end: Candle, val bullish: Boolean) {
    val ratios = listOf(.382, .5, .618)
}

data class FanSummary(
    val phase: FanPhase,
    val trendDirection: Direction,
    val anchorQuality: Int,
    val retracementDepth: Int,
    val respectScore: Int,
    val followThroughScore: Int,
    val breakdownRisk: Int,
    val currentZone: String,
    val nearestSupportRay: Double,
    val nearestResistanceRay: Double,
    val invalidationPrice: Double
)

data class ExecutiveSummary(
    val headline: String,
    val conclusion: String,
    val confidence: Int,
    val facts: List<String>
)

data class TargetTier(val name: String, val price: Double, val confidence: Int)
data class AnalysisResult(
    val timeframe: Timeframe,
    val direction: Direction,
    val score: Int,
    val emaDirection: Direction,
    val maDirection: Direction,
    val bbDirection: Direction,
    val compassText: String,
    val fanText: String,
    val fanSummary: FanSummary,
    val executiveSummary: ExecutiveSummary,
    val targets: List<TargetTier>,
    val stopLoss: Double,
    val candles: List<Candle>,
    val ema5: List<Double>,
    val ma9: List<Double>,
    val ma21: List<Double>,
    val ma63: List<Double>,
    val ma84: List<Double>,
    val bbUpper: List<Double>,
    val bbLower: List<Double>,
    val previousDay: PreviousDayCompass,
    val fan: FibFan
)

object AnalysisEngine {
    fun analyze(timeframe: Timeframe, candles: List<Candle>, previousDay: PreviousDayCompass): AnalysisResult {
        require(candles.size >= 90)
        val closes = candles.map { it.close }
        val ema5 = ema(closes, 5)
        val ma9 = sma(closes, 9)
        val ma21 = sma(closes, 21)
        val ma63 = sma(closes, 63)
        val ma84 = sma(closes, 84)
        val deviation = rollingStd(closes, 21)
        val bbUpper = ma21.zip(deviation) { mean, sd -> mean + sd * 2 }
        val bbLower = ma21.zip(deviation) { mean, sd -> mean - sd * 2 }

        val emaDir = slopeDirection(ema5)
        val bullishStack = ema5.last() > ma9.last() && ma9.last() > ma21.last() && ma21.last() > ma63.last() && ma63.last() > ma84.last()
        val bearishStack = ema5.last() < ma9.last() && ma9.last() < ma21.last() && ma21.last() < ma63.last() && ma63.last() < ma84.last()
        val maDir = when { bullishStack -> Direction.BULLISH; bearishStack -> Direction.BEARISH; else -> slopeDirection(ma21) }
        val bbMidDir = slopeDirection(ma21)
        val widthNow = bbUpper.last() - bbLower.last()
        val widthPast = bbUpper[bbUpper.lastIndex - 5] - bbLower[bbLower.lastIndex - 5]
        val bbExpanding = widthNow >= widthPast * 1.04
        val bbContracting = widthNow < widthPast * .92
        val bbDir = if (bbContracting) Direction.NEUTRAL else bbMidDir

        val trendBullish = listOf(emaDir, maDir, bbDir).count { it == Direction.BULLISH } >= 2
        val fan = latestTrendFan(candles, trendBullish)
        val price = closes.last()
        val fanSummary = summarizeFan(candles, fan, emaDir, maDir, bbDir, bbExpanding)
        val compassScore = previousDay.score(price)
        val indicatorScore = (directionValue(emaDir) * .20) + (directionValue(maDir) * .25) +
            (directionValue(bbDir) * .25) + (compassScore * .15) + (fanDirectionScore(fanSummary) * .15)
        val score = (abs(indicatorScore) * 100).toInt().coerceIn(0, 100)
        val direction = when { indicatorScore > .15 -> Direction.BULLISH; indicatorScore < -.15 -> Direction.BEARISH; else -> Direction.NEUTRAL }

        val avgRange = candles.takeLast(20).map { it.high - it.low }.average()
        val sign = if (direction == Direction.BEARISH) -1 else 1
        val fanTravel = abs(fanSummary.nearestResistanceRay - fanSummary.nearestSupportRay).coerceAtLeast(avgRange * .45)
        val targets = listOf(.75, 1.35, 2.0).mapIndexed { index, factor ->
            val projected = price + sign * max(avgRange * factor, fanTravel * (index + 1) * .65)
            TargetTier("TP${index + 1}", projected, (fanSummary.followThroughScore - index * 14).coerceIn(35, 92))
        }
        val structural = if (direction == Direction.BEARISH) {
            max(max(ma21.last(), previousDay.midpoint), fanSummary.invalidationPrice)
        } else {
            min(min(ma21.last(), previousDay.midpoint), fanSummary.invalidationPrice)
        }
        val stop = structural - sign * avgRange * .25
        val executive = executiveSummary(direction, score, emaDir, maDir, bbDir, bbExpanding, compassText(price, previousDay), fanSummary)

        return AnalysisResult(
            timeframe, direction, score, emaDir, maDir, bbDir,
            compassText(price, previousDay), fanLabel(fanSummary), fanSummary, executive,
            targets, stop, candles, ema5, ma9, ma21, ma63, ma84, bbUpper, bbLower, previousDay, fan
        )
    }

    private fun summarizeFan(
        candles: List<Candle>,
        fan: FibFan,
        ema: Direction,
        ma: Direction,
        bb: Direction,
        bbExpanding: Boolean
    ): FanSummary {
        val price = candles.last().close
        val swingLow = min(fan.start.low, fan.end.low)
        val swingHigh = max(fan.start.high, fan.end.high)
        val range = (swingHigh - swingLow).coerceAtLeast(1e-9)
        val depth = if (fan.bullish) (swingHigh - price) / range else (price - swingLow) / range
        val depthPercent = (depth * 100).toInt().coerceIn(0, 140)
        val levels = if (fan.bullish) {
            fan.ratios.map { swingHigh - range * it }.sorted()
        } else {
            fan.ratios.map { swingLow + range * it }.sorted()
        }
        val support = levels.filter { it <= price }.maxOrNull() ?: swingLow
        val resistance = levels.filter { it >= price }.minOrNull() ?: swingHigh
        val distanceToRay = levels.minOf { abs(price - it) }
        val avgRange = candles.takeLast(20).map { it.high - it.low }.average().coerceAtLeast(1e-9)
        val respect = (100 - (distanceToRay / avgRange * 35).toInt()).coerceIn(20, 96)
        val trendDirection = if (fan.bullish) Direction.BULLISH else Direction.BEARISH
        val confirmations = listOf(ema, ma, bb).count { it == trendDirection }
        val against = listOf(ema, ma, bb).count { it != Direction.NEUTRAL && it != trendDirection }
        val followThrough = (42 + confirmations * 14 + if (bbExpanding) 10 else 0 - (depthPercent - 38).coerceAtLeast(0) / 2).coerceIn(5, 96)
        val breakdown = (18 + against * 20 + (depthPercent - 50).coerceAtLeast(0)).coerceIn(4, 96)
        val phase = when {
            depthPercent < 15 && confirmations >= 2 && bbExpanding -> FanPhase.BREAKOUT_ACCELERATION
            depthPercent <= 38 && confirmations >= 2 -> FanPhase.FOLLOW_THROUGH
            depthPercent <= 62 && against <= 1 -> FanPhase.HEALTHY_RETRACEMENT
            depthPercent <= 78 && confirmations >= 1 -> FanPhase.DEEP_RETRACEMENT
            depthPercent > 78 || against >= 2 -> FanPhase.BREAKDOWN
            else -> FanPhase.CHOPPED_OR_UNRELIABLE
        }
        val zone = when {
            depthPercent < 24 -> "0–23.6% continuation corridor"
            depthPercent < 39 -> "23.6–38.2% shallow retracement"
            depthPercent < 51 -> "38.2–50.0% retracement"
            depthPercent < 63 -> "50.0–61.8% decision zone"
            else -> "Beyond 61.8% deep-risk zone"
        }
        val invalidation = if (fan.bullish) swingHigh - range * .618 else swingLow + range * .618
        val anchorTravel = abs(fan.end.close - fan.start.close)
        val anchorQuality = ((anchorTravel / avgRange) * 12 + confirmations * 12).toInt().coerceIn(35, 96)
        return FanSummary(phase, trendDirection, anchorQuality, depthPercent, respect, followThrough, breakdown, zone, support, resistance, invalidation)
    }

    private fun executiveSummary(
        direction: Direction,
        score: Int,
        ema: Direction,
        ma: Direction,
        bb: Direction,
        bbExpanding: Boolean,
        compass: String,
        fan: FanSummary
    ): ExecutiveSummary {
        val headline = when (fan.phase) {
            FanPhase.BREAKOUT_ACCELERATION -> "${direction.name.lowercase().replaceFirstChar { it.uppercase() }} acceleration"
            FanPhase.FOLLOW_THROUGH -> "${direction.name.lowercase().replaceFirstChar { it.uppercase() }} follow-through"
            FanPhase.HEALTHY_RETRACEMENT -> "Trend intact, healthy retracement"
            FanPhase.DEEP_RETRACEMENT -> "Trend under deeper retracement"
            FanPhase.BREAKDOWN -> "Structure breakdown risk"
            FanPhase.CHOPPED_OR_UNRELIABLE -> "Mixed structure, confirmation required"
        }
        val conclusion = when {
            direction == fan.trendDirection && fan.phase in listOf(FanPhase.FOLLOW_THROUGH, FanPhase.BREAKOUT_ACCELERATION) -> "MA structure, Bollinger behaviour and fan geometry support continued travel."
            direction == fan.trendDirection && fan.phase == FanPhase.HEALTHY_RETRACEMENT -> "The primary direction remains intact while price retraces inside the active fan."
            fan.phase == FanPhase.BREAKDOWN -> "The fan is losing structural support; continuation should not be assumed until MA and BB recover."
            else -> "The evidence is split, so the analyzer should wait for stronger back-to-back confirmation."
        }
        val facts = listOf(
            "EMA5 direction: ${ema.name.lowercase()}.",
            "MA structure: ${ma.name.lowercase()}.",
            "Bollinger state: ${bb.name.lowercase()}${if (bbExpanding) " with expansion" else " without confirmed expansion"}.",
            "Fibonacci fan: ${fan.phase.name.lowercase().replace('_', ' ')} at ${fan.retracementDepth}% retracement; respect score ${fan.respectScore}%.",
            "Previous-day compass: ${compass.lowercase()}.",
            "Fan follow-through ${fan.followThroughScore}% versus breakdown risk ${fan.breakdownRisk}%."
        )
        return ExecutiveSummary(headline, conclusion, score, facts)
    }

    private fun fanDirectionScore(summary: FanSummary): Double {
        val sign = if (summary.trendDirection == Direction.BULLISH) 1.0 else -1.0
        val conviction = (summary.followThroughScore - summary.breakdownRisk) / 100.0
        return (sign * conviction).coerceIn(-1.0, 1.0)
    }

    private fun fanLabel(summary: FanSummary) = summary.phase.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    private fun directionValue(direction: Direction) = when (direction) { Direction.BULLISH -> 1.0; Direction.BEARISH -> -1.0; Direction.NEUTRAL -> 0.0 }
    private fun compassText(price: Double, p: PreviousDayCompass) = when {
        price > p.high -> "Above previous high"
        price >= p.q75 -> "Upper bullish zone"
        price >= p.midpoint -> "Above midpoint"
        price > p.q25 -> "Below midpoint"
        price >= p.low -> "Lower bearish zone"
        else -> "Below previous low"
    }
    private fun slopeDirection(values: List<Double>): Direction {
        val delta = values.last() - values[values.lastIndex - 5]
        val threshold = abs(values.last()) * .00015
        return when { delta > threshold -> Direction.BULLISH; delta < -threshold -> Direction.BEARISH; else -> Direction.NEUTRAL }
    }
    private fun latestTrendFan(candles: List<Candle>, bullish: Boolean): FibFan {
        val window = candles.takeLast(60)
        val low = window.minBy { it.low }
        val high = window.maxBy { it.high }
        return if (bullish) FibFan(low, high, true) else FibFan(high, low, false)
    }
    fun sma(values: List<Double>, period: Int): List<Double> = values.indices.map { i ->
        val from = max(0, i - period + 1); values.subList(from, i + 1).average()
    }
    fun ema(values: List<Double>, period: Int): List<Double> {
        val alpha = 2.0 / (period + 1); val output = mutableListOf(values.first())
        for (i in 1 until values.size) output += alpha * values[i] + (1 - alpha) * output.last()
        return output
    }
    private fun rollingStd(values: List<Double>, period: Int): List<Double> = values.indices.map { i ->
        val from = max(0, i - period + 1); val section = values.subList(from, i + 1); val mean = section.average()
        kotlin.math.sqrt(section.sumOf { (it - mean) * (it - mean) } / section.size)
    }
}

object DemoMarketData {
    fun candles(timeframe: Timeframe): List<Candle> {
        val step = when (timeframe) { Timeframe.M15 -> 15 * 60_000L; Timeframe.H1 -> 60 * 60_000L; Timeframe.H4 -> 4 * 60 * 60_000L; Timeframe.D1 -> 24 * 60 * 60_000L }
        var price = 3325.0
        return List(140) { i ->
            val wave = kotlin.math.sin(i / 7.0) * 3.1
            val drift = when (timeframe) { Timeframe.M15 -> .11; Timeframe.H1 -> .20; Timeframe.H4 -> .34; Timeframe.D1 -> .58 }
            val open = price
            val close = open + drift + wave * .10
            val high = max(open, close) + 1.5 + abs(kotlin.math.sin(i.toDouble()))
            val low = min(open, close) - 1.4 - abs(kotlin.math.cos(i.toDouble()))
            price = close
            Candle(System.currentTimeMillis() - (139 - i) * step, open, high, low, close)
        }
    }
}
