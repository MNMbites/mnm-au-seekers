package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.EconomicCalendarEvent
import com.mnm.auseekers.data.EconomicCalendarFeed
import com.mnm.auseekers.data.EconomicCalendarJsonParser
import com.mnm.auseekers.data.EconomicCalendarPolicy
import com.mnm.auseekers.data.EconomicImpact
import com.mnm.auseekers.data.calendarCurrenciesForSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EconomicCalendarRiskTest {
    private val now = Instant.parse("2026-07-14T12:00:00Z")
    private val evaluator = EconomicCalendarRiskEvaluator()

    @Test
    fun highImpactInsideThirtyMinutesReducesConfidenceAndCanSuppress() {
        val assessment = evaluator.evaluate(
            feed(event(EconomicImpact.HIGH, "2026-07-14T12:20:00Z")),
            now,
        )

        assertEquals(EconomicCalendarRiskLevel.HIGH_IMPACT, assessment.level)
        assertTrue(assessment.detail.contains("reduce technical confidence"))
        assertFalse(assessment.suppresses(EconomicCalendarPolicy.WARN_ONLY))
        assertTrue(assessment.suppresses(EconomicCalendarPolicy.BLOCK_HIGH_IMPACT))
    }

    @Test
    fun widerHighImpactAndNearbyMediumEventsAreCautionOnly() {
        val high = evaluator.evaluate(
            feed(event(EconomicImpact.HIGH, "2026-07-14T12:45:00Z")),
            now,
        )
        val medium = evaluator.evaluate(
            feed(event(EconomicImpact.MEDIUM, "2026-07-14T11:40:00Z")),
            now,
        )

        assertEquals(EconomicCalendarRiskLevel.CAUTION, high.level)
        assertEquals(EconomicCalendarRiskLevel.CAUTION, medium.level)
    }

    @Test
    fun unavailableFeedIsExplicitAndNeverSilentlyClear() {
        val assessment = evaluator.evaluate(
            EconomicCalendarFeed(false, emptyList(), "offline"),
            now,
        )

        assertEquals(EconomicCalendarRiskLevel.UNAVAILABLE, assessment.level)
        assertEquals("offline", assessment.detail)
    }

    @Test
    fun parsesProviderNeutralCalendarAndMapsRelevantCurrencies() {
        val events = EconomicCalendarJsonParser().parse(
            """
                {"items": [{
                  "event_id": "us-cpi",
                  "source": "provider",
                  "title": "US CPI",
                  "currency": "usd",
                  "impact": "high",
                  "scheduled_at": "2026-07-14T12:20:00Z"
                }]}
            """.trimIndent(),
        )

        assertEquals("USD", events.single().currency)
        assertEquals(EconomicImpact.HIGH, events.single().impact)
        assertEquals(setOf("USD"), calendarCurrenciesForSymbol("XAUUSD"))
        assertEquals(setOf("EUR", "USD"), calendarCurrenciesForSymbol("EURUSD"))
    }

    private fun feed(event: EconomicCalendarEvent) = EconomicCalendarFeed(
        available = true,
        events = listOf(event),
        statusMessage = "available",
    )

    private fun event(impact: EconomicImpact, at: String) = EconomicCalendarEvent(
        eventId = "event-1",
        source = "test",
        title = "US CPI",
        currency = "USD",
        impact = impact,
        scheduledAt = Instant.parse(at),
    )
}
