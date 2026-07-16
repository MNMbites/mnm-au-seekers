package com.mnm.auseekers.premarket

import com.mnm.auseekers.analysis.EconomicCalendarAssessment
import com.mnm.auseekers.analysis.EconomicCalendarRiskLevel
import com.mnm.auseekers.analysis.MarketHealthAssessment
import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.TradingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PreMarketBriefingTest {
    private val planner = PreMarketSessionPlanner()

    @Test
    fun usesDstAwareLondonAndNewYorkOpenTimes() {
        val londonSummer = planner.nextWindow(
            Instant.parse("2026-07-16T06:30:00Z"),
            PreMarketLeadTime.SIXTY_MINUTES,
        )
        val newYorkSummer = planner.nextWindow(
            Instant.parse("2026-07-16T07:30:00Z"),
            PreMarketLeadTime.SIXTY_MINUTES,
        )
        val londonWinter = planner.nextWindow(
            Instant.parse("2026-12-16T07:30:00Z"),
            PreMarketLeadTime.SIXTY_MINUTES,
        )

        requireNotNull(londonSummer)
        requireNotNull(newYorkSummer)
        requireNotNull(londonWinter)
        assertEquals(MarketSession.LONDON, londonSummer.session)
        assertEquals(Instant.parse("2026-07-16T07:00:00Z"), londonSummer.opensAt)
        assertTrue(londonSummer.active)
        assertEquals(MarketSession.NEW_YORK, newYorkSummer.session)
        assertEquals(Instant.parse("2026-07-16T12:00:00Z"), newYorkSummer.opensAt)
        assertFalse(newYorkSummer.active)
        assertEquals(Instant.parse("2026-12-16T08:00:00Z"), londonWinter.opensAt)
    }

    @Test
    fun offHasNoWindowAndThirtyMinuteWindowStartsExactly() {
        assertEquals(
            null,
            planner.nextWindow(Instant.parse("2026-07-16T06:30:00Z"), PreMarketLeadTime.OFF),
        )
        val before = planner.nextWindow(
            Instant.parse("2026-07-16T06:29:59Z"),
            PreMarketLeadTime.THIRTY_MINUTES,
        )
        val active = planner.nextWindow(
            Instant.parse("2026-07-16T06:30:00Z"),
            PreMarketLeadTime.THIRTY_MINUTES,
        )

        requireNotNull(before)
        requireNotNull(active)
        assertFalse(before.active)
        assertTrue(active.active)
    }

    @Test
    fun skipsWeekendSessionClocks() {
        val next = planner.nextWindow(
            Instant.parse("2026-07-17T18:00:00Z"),
            PreMarketLeadTime.SIXTY_MINUTES,
        )

        requireNotNull(next)
        assertEquals(MarketSession.LONDON, next.session)
        assertEquals(Instant.parse("2026-07-20T07:00:00Z"), next.opensAt)
    }

    @Test
    fun briefingIncludesLevelsSafeRiskAndAlternativeWait() {
        val analysis = SignalEngine().analyse(DemoMarket.snapshots, TradingMode.PRIMARY)
        val window = requireNotNull(
            planner.nextWindow(
                Instant.parse("2026-07-16T06:30:00Z"),
                PreMarketLeadTime.SIXTY_MINUTES,
            ),
        )
        val briefing = PreMarketBriefingGenerator().generate(
            window = window,
            feed = feed(),
            analysis = analysis,
            marketHealth = health(),
            calendar = EconomicCalendarAssessment(
                EconomicCalendarRiskLevel.CLEAR,
                "clear",
                null,
            ),
        )

        assertTrue(briefing.importantLevels.contains("H1 MA21"))
        assertTrue(briefing.alternativeScenario.contains("WAIT if H1"))
        assertTrue(briefing.riskGuidance.startsWith("SAFE"))
        assertTrue(briefing.sourceNotice.contains("validated live"))
    }

    private fun feed() = MarketDataFeed(
        symbol = "XAUUSD",
        capturedAt = Instant.parse("2026-07-16T06:29:30Z"),
        state = FeedState.LIVE,
        snapshots = DemoMarket.snapshots,
        bid = 2_400.1,
        ask = 2_400.3,
        ageSeconds = 30,
        statusMessage = "test",
    )

    private fun health() = MarketHealthAssessment(
        score = 82,
        level = MarketHealthLevel.READY,
        confidenceLabel = "Strong agreement",
        confidenceExplanation = "test",
        factors = emptyList(),
    )
}
