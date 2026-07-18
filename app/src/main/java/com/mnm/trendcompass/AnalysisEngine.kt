package com.mnm.trendcompass

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double)
enum class Direction { BULLISH, BEARISH, NEUTRAL }
enum class Timeframe(val label: String, val weight: Double) { M15("15m", .10), H1("1h", .25), H4("4h", .35), D1("1D", .30) }

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
        val bbDir = if (widthNow < widthPast * .92) Direction.NEUTRAL else bbMidDir

        val trendBullish = listOf(emaDir, maDir, bbDir).count { it == Direction.BULLISH } >= 2
        val fan = latestTrendFan(candles, trendBullish)
        val price = closes.last()
        val compassScore = previousDay.score(price)
        val indicatorScore = (directionValue(emaDir) * .20) + (directionValue(maDir) * .25) +
            (directionValue(bbDir) * .25) + (compassScore * .15) + (fanScore(candles, fan) * .15)
        val score = (abs(indicatorScore) * 100).toInt().coerceIn(0, 100)
        val direction = when { indicatorScore > .15 -> Direction.BULLISH; indicatorScore < -.15 -> Direction.BEARISH; else -> Direction.NEUTRAL }

        val avgRange = candles.takeLast(20).map { it.high - it.low }.average()
        val sign = if (direction == Direction.BEARISH) -1 else 1
        val targets = listOf(.75, 1.35, 2.0).mapIndexed { index, factor ->
            TargetTier("TP${index + 1}", price + sign * avgRange * factor, (82 - index * 17).coerceAtLeast(35))
        }
        val structural = if (direction == Direction.BEARISH) max(ma21.last(), previousDay.midpoint) else min(ma21.last(), previousDay.midpoint)
        val stop = structural - sign * avgRange * .25

        return AnalysisResult(
            timeframe, direction, score, emaDir, maDir, bbDir,
            compassText(price, previousDay),
            if (fan.bullish) "Bull fan active" else "Bear fan active",
            targets, stop, candles, ema5, ma9, ma21, ma63, ma84, bbUpper, bbLower, previousDay, fan
        )
    }

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
    private fun fanScore(candles: List<Candle>, fan: FibFan): Double {
        val travel = fan.end.close - fan.start.close
        val current = candles.last().close - fan.start.close
        if (abs(travel) < 1e-9) return 0.0
        val progress = current / travel
        return if (fan.bullish) progress.coerceIn(-1.0, 1.0) else (-progress).coerceIn(-1.0, 1.0)
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
