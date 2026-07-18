package com.mnm.trendcompass

enum class IntelligenceLayer(val title: String) {
    MARKET("Market Intelligence"),
    TECHNICAL("Technical Intelligence"),
    TRADE("Trade Intelligence")
}

enum class GoldMarketInstrument(
    val label: String,
    val role: String,
    val inverseToGold: Boolean = false
) {
    SPOT_GOLD("Spot Gold", "Primary traded market"),
    GOLD_FUTURES("Gold Futures", "Institutional price discovery"),
    DXY("US Dollar Index", "Dollar pressure", inverseToGold = true),
    US10Y("US 10Y Yield", "Yield pressure", inverseToGold = true),
    SILVER("Silver", "Precious-metals confirmation"),
    VIX("VIX", "Risk-aversion confirmation"),
    OIL("Oil", "Inflation and macro context")
}

data class CrossMarketSignal(
    val instrument: GoldMarketInstrument,
    val direction: Direction,
    val strength: Int,
    val available: Boolean = true,
    val note: String? = null
) {
    init {
        require(strength in 0..100)
    }
}

data class GoldMarketSummary(
    val direction: Direction,
    val strength: Int,
    val macroSupport: Int,
    val crossMarketConfirmation: Int,
    val volatilitySupport: Int,
    val headline: String,
    val facts: List<String>,
    val signals: List<CrossMarketSignal>
) {
    init {
        require(strength in 0..100)
        require(macroSupport in 0..100)
        require(crossMarketConfirmation in 0..100)
        require(volatilitySupport in 0..100)
    }
}

object GoldMarketIntelligenceEngine {
    fun summarize(signals: List<CrossMarketSignal>): GoldMarketSummary {
        val available = signals.filter { it.available }
        if (available.isEmpty()) {
            return GoldMarketSummary(
                direction = Direction.NEUTRAL,
                strength = 0,
                macroSupport = 0,
                crossMarketConfirmation = 0,
                volatilitySupport = 0,
                headline = "Gold-market confirmation is waiting for external feeds.",
                facts = listOf("XAUUSD technical analysis remains available independently."),
                signals = signals
            )
        }

        fun contribution(signal: CrossMarketSignal): Int {
            val raw = when (signal.direction) {
                Direction.BULLISH -> signal.strength
                Direction.BEARISH -> -signal.strength
                Direction.NEUTRAL -> 0
            }
            return if (signal.instrument.inverseToGold) -raw else raw
        }

        val weighted = available.sumOf(::contribution)
        val normalized = weighted / available.size
        val direction = when {
            normalized > 12 -> Direction.BULLISH
            normalized < -12 -> Direction.BEARISH
            else -> Direction.NEUTRAL
        }
        val strength = kotlin.math.abs(normalized).coerceIn(0, 100)

        val macroSignals = available.filter {
            it.instrument == GoldMarketInstrument.DXY ||
                it.instrument == GoldMarketInstrument.US10Y ||
                it.instrument == GoldMarketInstrument.OIL
        }
        val macroSupport = if (macroSignals.isEmpty()) 0 else {
            kotlin.math.abs(macroSignals.sumOf(::contribution) / macroSignals.size).coerceIn(0, 100)
        }

        val crossSignals = available.filter {
            it.instrument == GoldMarketInstrument.GOLD_FUTURES ||
                it.instrument == GoldMarketInstrument.SILVER
        }
        val crossConfirmation = if (crossSignals.isEmpty()) 0 else {
            kotlin.math.abs(crossSignals.sumOf(::contribution) / crossSignals.size).coerceIn(0, 100)
        }

        val volatilitySupport = available.firstOrNull { it.instrument == GoldMarketInstrument.VIX }
            ?.let { kotlin.math.abs(contribution(it)) }
            ?.coerceIn(0, 100)
            ?: 0

        val facts = available
            .sortedByDescending { it.strength }
            .take(5)
            .map { signal ->
                val relationship = if (signal.instrument.inverseToGold) "inverse" else "confirming"
                "${signal.instrument.label}: ${signal.direction.name.lowercase()} ${signal.strength}% ($relationship relationship)."
            }

        val headline = when (direction) {
            Direction.BULLISH -> "The broader gold market is supporting bullish pressure."
            Direction.BEARISH -> "The broader gold market is applying bearish pressure."
            Direction.NEUTRAL -> "Cross-market gold evidence is mixed or balanced."
        }

        return GoldMarketSummary(
            direction = direction,
            strength = strength,
            macroSupport = macroSupport,
            crossMarketConfirmation = crossConfirmation,
            volatilitySupport = volatilitySupport,
            headline = headline,
            facts = facts,
            signals = signals
        )
    }
}

object DemoGoldMarketData {
    val signals = listOf(
        CrossMarketSignal(GoldMarketInstrument.SPOT_GOLD, Direction.BULLISH, 86),
        CrossMarketSignal(GoldMarketInstrument.GOLD_FUTURES, Direction.BULLISH, 82),
        CrossMarketSignal(GoldMarketInstrument.DXY, Direction.BEARISH, 74),
        CrossMarketSignal(GoldMarketInstrument.US10Y, Direction.BEARISH, 63),
        CrossMarketSignal(GoldMarketInstrument.SILVER, Direction.BULLISH, 68),
        CrossMarketSignal(GoldMarketInstrument.VIX, Direction.BULLISH, 54),
        CrossMarketSignal(GoldMarketInstrument.OIL, Direction.NEUTRAL, 35)
    )
}
