package com.mnm.auseekers.notifications

import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.premarket.MarketSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NotificationPolicyTest {
    @Test
    fun defaultsPreserveExistingEligibleAlerts() {
        val policy = NotificationPolicy()

        assertTrue(policy.allowsMarketHealth(MarketHealthLevel.READY))
        assertTrue(policy.allowsMarketHealth(MarketHealthLevel.CAUTION))
        assertTrue(policy.allowsSetupStage(SetupStage.EARLY))
        assertTrue(policy.allowsSetupStage(SetupStage.CONFIRMED))
        assertTrue(policy.allowedSessions().containsAll(MarketSession.entries))
        assertFalse(policy.isQuietAt(Instant.parse("2026-07-16T23:30:00Z"), UTC))
    }

    @Test
    fun stricterConfidenceAndStageFiltersSuppressLowerReadiness() {
        val policy = NotificationPolicy(
            confidenceFilter = NotificationConfidenceFilter.READY_ONLY,
            setupStageFilter = SetupStageFilter.CONFIRMED_ONLY,
        )

        assertTrue(policy.allowsMarketHealth(MarketHealthLevel.READY))
        assertFalse(policy.allowsMarketHealth(MarketHealthLevel.CAUTION))
        assertFalse(policy.allowsMarketHealth(MarketHealthLevel.NOT_READY))
        assertTrue(policy.allowsSetupStage(SetupStage.CONFIRMED))
        assertFalse(policy.allowsSetupStage(SetupStage.EARLY))
        assertFalse(policy.allowsSetupStage(SetupStage.WATCH))
    }

    @Test
    fun overnightQuietHoursUseDeviceLocalTimeAndEndExclusively() {
        val policy = NotificationPolicy(
            quietHours = NotificationQuietHours.NIGHT_22_TO_07,
        )

        assertTrue(policy.isQuietAt(Instant.parse("2026-07-16T22:00:00Z"), UTC))
        assertTrue(policy.isQuietAt(Instant.parse("2026-07-17T06:59:59Z"), UTC))
        assertFalse(policy.isQuietAt(Instant.parse("2026-07-17T07:00:00Z"), UTC))
        assertFalse(policy.isQuietAt(Instant.parse("2026-07-16T21:59:59Z"), UTC))
    }

    @Test
    fun sessionFilterLimitsPreMarketCandidates() {
        val london = NotificationPolicy(
            sessionFilter = NotificationSessionFilter.LONDON_ONLY,
        )
        val newYork = NotificationPolicy(
            sessionFilter = NotificationSessionFilter.NEW_YORK_ONLY,
        )

        assertTrue(london.allowedSessions() == setOf(MarketSession.LONDON))
        assertTrue(newYork.allowedSessions() == setOf(MarketSession.NEW_YORK))
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
