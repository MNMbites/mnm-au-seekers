package com.mnm.auseekers.premarket

import com.mnm.auseekers.analysis.EconomicCalendarAssessment
import com.mnm.auseekers.analysis.EconomicCalendarRiskLevel
import com.mnm.auseekers.analysis.MarketHealthAssessment
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketAnalysis
import com.mnm.auseekers.domain.Timeframe
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

enum class MarketSession(
    val label: String,
    val zoneId: ZoneId,
    val opensAt: LocalTime,
) {
    LONDON("London", ZoneId.of("Europe/London"), LocalTime.of(8, 0)),
    NEW_YORK("New York", ZoneId.of("America/New_York"), LocalTime.of(8, 0)),
}

enum class PreMarketLeadTime(val minutes: Long, val label: String) {
    OFF(0, "Off"),
    THIRTY_MINUTES(30, "30 min"),
    SIXTY_MINUTES(60, "60 min"),
}

data class PreMarketWindow(
    val session: MarketSession,
    val opensAt: Instant,
    val briefingStartsAt: Instant,
    val active: Boolean,
) {
    val fingerprint: String
        get() = "${session.name}:${opensAt}"
}

class PreMarketSessionPlanner {
    fun nextWindow(
        now: Instant,
        leadTime: PreMarketLeadTime,
        allowedSessions: Set<MarketSession> = MarketSession.entries.toSet(),
    ): PreMarketWindow? {
        if (leadTime == PreMarketLeadTime.OFF || allowedSessions.isEmpty()) return null
        val candidates = MarketSession.entries.filter { it in allowedSessions }.flatMap { session ->
            val localDate = now.atZone(session.zoneId).toLocalDate()
            (0L..4L)
                .map { localDate.plusDays(it) }
                .filter { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
                .map { date ->
                    val opensAt = ZonedDateTime.of(
                        date,
                        session.opensAt,
                        session.zoneId,
                    ).toInstant()
                    val startsAt = opensAt.minusSeconds(leadTime.minutes * 60)
                    PreMarketWindow(
                        session = session,
                        opensAt = opensAt,
                        briefingStartsAt = startsAt,
                        active = now >= startsAt && now < opensAt,
                    )
                }
        }
        return candidates.firstOrNull { it.active }
            ?: candidates.filter { it.opensAt > now }.minByOrNull { it.opensAt }
    }
}

data class PreMarketBriefing(
    val window: PreMarketWindow,
    val overallTrend: String,
    val confidence: String,
    val importantLevels: String,
    val expectedBehavior: String,
    val primaryBias: String,
    val alternativeScenario: String,
    val riskGuidance: String,
    val sourceNotice: String,
)

class PreMarketBriefingGenerator {
    fun generate(
        window: PreMarketWindow,
        feed: MarketDataFeed,
        analysis: MarketAnalysis,
        marketHealth: MarketHealthAssessment,
        calendar: EconomicCalendarAssessment,
    ): PreMarketBriefing {
        val h1 = feed.snapshots.firstOrNull { it.timeframe == Timeframe.H1 }
        val h4 = feed.snapshots.firstOrNull { it.timeframe == Timeframe.H4 }
        val m15Direction = analysis.timeframeSignals
            .firstOrNull { it.timeframe == Timeframe.M15 }
            ?.direction
        val importantLevels = if (h1 != null && h4 != null) {
            "H1 MA21 ${h1.ma21.format(2)} • H4 MA21 ${h4.ma21.format(2)} • " +
                "H1 band ${h1.bbLower.format(2)}–${h1.bbUpper.format(2)}"
        } else {
            "Required H1/H4 levels are unavailable."
        }
        val expectedBehavior = when {
            analysis.direction == Direction.WAIT -> {
                "Higher-timeframe alignment is incomplete; expect observation, not a setup."
            }
            m15Direction == Direction.WAIT -> {
                "Higher timeframes support ${analysis.direction.label}; M15 confirmation or a " +
                    "pullback may still form near the session open."
            }
            else -> {
                "M15 is aligned with the higher-timeframe bias; re-check spread and session " +
                    "volatility before treating it as actionable."
            }
        }
        val alternative = when (analysis.direction) {
            Direction.BUY -> h1?.let {
                "Alternative: WAIT if H1 closes below MA21 ${it.ma21.format(2)}."
            } ?: "Alternative: WAIT if H1 loses MA21."
            Direction.SELL -> h1?.let {
                "Alternative: WAIT if H1 closes above MA21 ${it.ma21.format(2)}."
            } ?: "Alternative: WAIT if H1 reclaims MA21."
            Direction.WAIT -> "Alternative: remain WAIT until H1 and H4 align."
        }
        val calendarGuidance = when (calendar.level) {
            EconomicCalendarRiskLevel.HIGH_IMPACT -> "High-impact calendar window; stand aside."
            EconomicCalendarRiskLevel.CAUTION -> "Calendar caution; reduce confidence."
            EconomicCalendarRiskLevel.UNAVAILABLE -> "Calendar unavailable; verify news manually."
            EconomicCalendarRiskLevel.CLEAR -> "No nearby calendar block is identified."
        }
        return PreMarketBriefing(
            window = window,
            overallTrend = analysis.rationale,
            confidence = "${marketHealth.confidenceLabel} • " +
                "health ${marketHealth.score}/100 • ${calendar.level.label}",
            importantLevels = importantLevels,
            expectedBehavior = expectedBehavior,
            primaryBias = if (analysis.direction == Direction.WAIT) {
                "WAIT"
            } else {
                "${analysis.direction.label.uppercase(Locale.US)} • ${analysis.stage.label}"
            },
            alternativeScenario = alternative,
            riskGuidance = "SAFE risk posture. $calendarGuidance",
            sourceNotice = when (feed.state) {
                FeedState.LIVE -> "Prepared from a validated live MT5 snapshot."
                FeedState.STALE -> "Preview only: the latest MT5 snapshot is stale."
                FeedState.DEMO -> "Preview only: bundled demo data is not current market data."
                FeedState.CONNECTING -> "Preview only: waiting for a validated live snapshot."
            },
        )
    }

    private fun Double.format(decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", this)
}
