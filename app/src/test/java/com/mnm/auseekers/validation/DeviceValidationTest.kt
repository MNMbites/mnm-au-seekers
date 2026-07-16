package com.mnm.auseekers.validation

import com.mnm.auseekers.analysis.EconomicCalendarAssessment
import com.mnm.auseekers.analysis.EconomicCalendarRiskLevel
import com.mnm.auseekers.analysis.MarketHealthAssessment
import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.LiveAnalysisInterval
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.notifications.NotificationInterval
import com.mnm.auseekers.premarket.PreMarketLeadTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DeviceValidationTest {
    private val evaluator = DeviceValidationEvaluator()

    @Test
    fun liveConfiguredInstallationPassesCoreDeviceChecks() {
        val report = evaluator.evaluate(
            generatedAtEpochMillis = NOW,
            appVersion = "0.14.0",
            liveServiceConfigured = true,
            feed = liveFeed(),
            marketHealth = health(MarketHealthLevel.READY),
            calendar = calendar(EconomicCalendarRiskLevel.CLEAR),
            notificationsEnabled = true,
            setupInterval = NotificationInterval.THIRTY_MINUTES,
            preMarketLeadTime = PreMarketLeadTime.SIXTY_MINUTES,
            notificationAudit = listOf(audit()),
            liveAnalysisInterval = LiveAnalysisInterval.FIFTEEN_SECONDS,
            buildCommit = BUILD_COMMIT,
        )

        assertEquals(BUILD_COMMIT, report.buildCommit)
        assertEquals(12, report.passCount)
        assertEquals(0, report.checkCount)
        assertEquals(0, report.blockedCount)
    }

    @Test
    fun demoInstallationIsExplicitlyBlockedAndSchedulesRemainOptional() {
        val report = evaluator.evaluate(
            generatedAtEpochMillis = NOW,
            appVersion = "0.14.0",
            liveServiceConfigured = false,
            feed = MarketDataFeed(
                symbol = "XAUUSD",
                capturedAt = null,
                state = FeedState.DEMO,
                snapshots = DemoMarket.snapshots,
                statusMessage = "secret endpoint text must not be exported",
            ),
            marketHealth = health(MarketHealthLevel.NOT_READY),
            calendar = calendar(EconomicCalendarRiskLevel.UNAVAILABLE),
            notificationsEnabled = false,
            setupInterval = NotificationInterval.OFF,
            preMarketLeadTime = PreMarketLeadTime.OFF,
            notificationAudit = emptyList(),
        )

        assertEquals(4, report.blockedCount)
        assertEquals(6, report.checkCount)
        assertEquals(2, report.passCount)
        assertTrue(report.items.any {
            it.label == "MT5 feed state" && it.status == ValidationStatus.BLOCKED
        })
    }

    @Test
    fun auditIsNewestFirstAndBounded() {
        val records = (1L..25L).map { index ->
            audit(publishedAt = NOW + index)
        }
        val report = evaluator.evaluate(
            generatedAtEpochMillis = NOW,
            appVersion = "0.14.0",
            liveServiceConfigured = true,
            feed = liveFeed(),
            marketHealth = health(MarketHealthLevel.READY),
            calendar = calendar(EconomicCalendarRiskLevel.CLEAR),
            notificationsEnabled = true,
            setupInterval = NotificationInterval.THIRTY_MINUTES,
            preMarketLeadTime = PreMarketLeadTime.SIXTY_MINUTES,
            notificationAudit = records,
        )

        assertEquals(20, report.notificationAudit.size)
        assertEquals(NOW + 25, report.notificationAudit.first().publishedAtEpochMillis)
        assertEquals(NOW + 6, report.notificationAudit.last().publishedAtEpochMillis)
    }

    @Test
    fun exportIsRedactedAndExplainsPublicationEvidence() {
        val report = evaluator.evaluate(
            generatedAtEpochMillis = NOW,
            appVersion = "0.14.0",
            liveServiceConfigured = true,
            feed = liveFeed().copy(statusMessage = "https://secret.example token=do-not-export"),
            marketHealth = health(MarketHealthLevel.READY),
            calendar = calendar(EconomicCalendarRiskLevel.CLEAR),
            notificationsEnabled = true,
            setupInterval = NotificationInterval.THIRTY_MINUTES,
            preMarketLeadTime = PreMarketLeadTime.SIXTY_MINUTES,
            notificationAudit = listOf(audit()),
            buildCommit = "https://secret.example/do-not-export",
        )

        val exported = DeviceValidationReportExporter().export(report)

        assertTrue(exported.contains("[PASS] MT5 feed state"))
        assertTrue(exported.contains("Android accepted the notification request"))
        assertTrue(exported.contains("no service endpoint, token, device identifier"))
        assertTrue(exported.contains("Build commit: local"))
        assertTrue(exported.contains("open offset 30s"))
        assertTrue(exported.contains("includes both operating-system delivery and user response"))
        assertFalse(exported.contains("secret.example"))
        assertFalse(exported.contains("do-not-export"))
        assertFalse(exported.contains("audit-$NOW"))
    }

    @Test
    fun publicationWithoutAlertOpenRemainsCheckEvidence() {
        val report = evaluator.evaluate(
            generatedAtEpochMillis = NOW,
            appVersion = "0.14.0",
            liveServiceConfigured = true,
            feed = liveFeed(),
            marketHealth = health(MarketHealthLevel.READY),
            calendar = calendar(EconomicCalendarRiskLevel.CLEAR),
            notificationsEnabled = true,
            setupInterval = NotificationInterval.THIRTY_MINUTES,
            preMarketLeadTime = PreMarketLeadTime.SIXTY_MINUTES,
            notificationAudit = listOf(audit(openedAt = null)),
        )

        assertTrue(report.items.any {
            it.label == "Notification open evidence" &&
                it.status == ValidationStatus.CHECK
        })
    }

    private fun liveFeed() = MarketDataFeed(
        symbol = "XAUUSD",
        capturedAt = Instant.ofEpochMilli(NOW - 30_000),
        state = FeedState.LIVE,
        snapshots = DemoMarket.snapshots,
        bid = 2_400.1,
        ask = 2_400.3,
        ageSeconds = 30,
        statusMessage = "live",
    )

    private fun health(level: MarketHealthLevel) = MarketHealthAssessment(
        score = if (level == MarketHealthLevel.READY) 85 else 20,
        level = level,
        confidenceLabel = "Technical agreement",
        confidenceExplanation = "test",
        factors = emptyList(),
    )

    private fun calendar(level: EconomicCalendarRiskLevel) = EconomicCalendarAssessment(
        level = level,
        detail = "test",
        nearestEvent = null,
    )

    private fun audit(
        publishedAt: Long = NOW,
        openedAt: Long? = publishedAt + 30_000,
    ) = NotificationAuditRecord(
        kind = NotificationAuditKind.PRE_MARKET,
        recordId = "audit-$publishedAt",
        symbol = "XAUUSD",
        publishedAtEpochMillis = publishedAt,
        expectedAtEpochMillis = NOW - 60_000,
        openedAtEpochMillis = openedAt,
        context = "London",
    )

    private companion object {
        val NOW: Long = Instant.parse("2026-07-16T06:30:00Z").toEpochMilli()
        const val BUILD_COMMIT = "ad65415877cf9f07b0272dd23f52f223e93d489b"
    }
}
