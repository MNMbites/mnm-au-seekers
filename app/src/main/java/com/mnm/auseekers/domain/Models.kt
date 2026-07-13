package com.mnm.auseekers.domain

enum class TradingMode(val label: String) {
    PRIMARY("Primary"),
    SCALPING("Scalping"),
}

enum class RiskProfile(val label: String, val baseRiskPercent: Double) {
    SAFE("Safe", 0.5),
    SEMI_AGGRESSIVE("Semi-aggressive", 1.0),
    AGGRESSIVE("Aggressive", 2.0),
}

enum class Timeframe(val label: String) {
    M15("M15"),
    H1("H1"),
    H4("H4"),
}

enum class Direction(val label: String, val sign: Int) {
    BUY("Buy", 1),
    WAIT("Wait", 0),
    SELL("Sell", -1),
}

enum class SetupStage(val label: String) {
    WATCH("Watch"),
    EARLY("Early setup"),
    CONFIRMED("Confirmed"),
}

data class MarketSnapshot(
    val timeframe: Timeframe,
    val close: Double,
    val ema5: Double,
    val ma9: Double,
    val ma21: Double,
    val ma63: Double,
    val ma84: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val rsi: Double,
    val macdHistogram: Double,
)

data class TimeframeSignal(
    val timeframe: Timeframe,
    val direction: Direction,
    val strength: Int,
    val score: Int,
)

data class MarketAnalysis(
    val mode: TradingMode,
    val direction: Direction,
    val strength: Int,
    val stage: SetupStage,
    val rationale: String,
    val timeframeSignals: List<TimeframeSignal>,
)

data class RiskRequest(
    val balance: Double,
    val profile: RiskProfile,
    val mode: TradingMode,
    val stopDistancePoints: Double,
    val valuePerPointPerLot: Double,
    val brokerMinimumLot: Double = 0.01,
    val brokerLotStep: Double = 0.01,
    val brokerMaximumLot: Double = 100.0,
)

data class RiskPlan(
    val allowed: Boolean,
    val effectiveRiskPercent: Double,
    val maximumLoss: Double,
    val suggestedLot: Double?,
    val reason: String,
)
