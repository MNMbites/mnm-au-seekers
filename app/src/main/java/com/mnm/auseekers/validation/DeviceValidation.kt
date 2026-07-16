package com.mnm.auseekers.validation

import com.mnm.auseekers.analysis.EconomicCalendarAssessment
import com.mnm.auseekers.analysis.EconomicCalendarRiskLevel
import com.mnm.auseekers.analysis.MarketHealthAssessment
import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Timeframe
import com.mnm.auseekers.notifications.NotificationInterval
import com.mnm.auseekers.notifications.NotificationPolicy
import com.mnm.auseekers.premarket.PreMarketLeadTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class ValidationStatus(val label: String) {
    PASS("Pass"),
    CHECK("Check"),
    BLOCKED("Blocked"),
}

data class ValidationItem(
    val label: String,
    val status: ValidationStatus,
    val detail: String,
)

enum class NotificationAuditKind(val label: String) {
    SETUP("Setup alert"),
    PRE_MARKET("Pre-market briefing"),
}

data class NotificationAuditRecord(
    val kind: NotificationAuditKind,
    val symbol: String,
    val publishedAtEpochMillis: Long,
    val expectedAtEpochMillis: Long? = null,
    val context: String,
)

data class DeviceValidationReport(
    val generatedAtEpochMillis: Long,
    val appVersion: String,
    val symbol: String,
    val feedCapturedAtEpochMillis: Long?,
    val feedAgeSeconds: Int?,
    val items: List<ValidationItem>,
    val notificationAudit: List<NotificationAuditRecord>,
) {
    val passCount: Int get() = items.count { it.status == ValidationStatus.PASS }
    val checkCount: Int get() = items.count { it.status == ValidationStatus.CHECK }
    val blockedCount: Int get() = items.count { it.status == ValidationStatus.BLOCKED }
}

class DeviceValidationEvaluator {
    fun evaluate(
        generatedAtEpochMillis: Long,
        appVersion: String,
        liveServiceConfigured: Boolean,
        feed: MarketDataFeed,
        marketHealth: MarketHealthAssessment,
        calendar: EconomicCalendarAssessment,
        notificationsEnabled: Boolean,
        setupInterval: NotificationInterval,
        preMarketLeadTime: PreMarketLeadTime,
        notificationAudit: List<NotificationAuditRecord>,
        notificationPolicy: NotificationPolicy = NotificationPolicy(),
    ): DeviceValidationReport {
        val timeframes = feed.snapshots.map { it.timeframe }.toSet()
        val requiredTimeframes = setOf(Timeframe.M15, Timeframe.H1, Timeframe.H4)
        val missing = requiredTimeframes - timeframes
        val boundedAudit = notificationAudit
            .sortedByDescending { it.publishedAtEpochMillis }
            .take(MAX_AUDIT_RECORDS)

        return DeviceValidationReport(
            generatedAtEpochMillis = generatedAtEpochMillis,
            appVersion = appVersion,
            symbol = feed.symbol,
            feedCapturedAtEpochMillis = feed.capturedAt?.toEpochMilli(),
            feedAgeSeconds = feed.ageSeconds,
            items = listOf(
                ValidationItem(
                    "Live service configuration",
                    if (liveServiceConfigured) ValidationStatus.PASS else ValidationStatus.BLOCKED,
                    if (liveServiceConfigured) {
                        "A build-time market-data service is configured."
                    } else {
                        "No live service is configured; only bundled demo data can be validated."
                    },
                ),
                feedItem(feed),
                ValidationItem(
                    "Required timeframes",
                    if (missing.isEmpty()) ValidationStatus.PASS else ValidationStatus.BLOCKED,
                    if (missing.isEmpty()) {
                        "M15, H1, and H4 snapshots are present."
                    } else {
                        "Missing ${missing.sortedBy { it.ordinal }.joinToString { it.label }}."
                    },
                ),
                ValidationItem(
                    "Market readiness",
                    when (marketHealth.level) {
                        MarketHealthLevel.READY -> ValidationStatus.PASS
                        MarketHealthLevel.CAUTION -> ValidationStatus.CHECK
                        MarketHealthLevel.NOT_READY -> ValidationStatus.BLOCKED
                    },
                    "${marketHealth.level.label}; score ${marketHealth.score}/100.",
                ),
                ValidationItem(
                    "Economic calendar",
                    if (calendar.level == EconomicCalendarRiskLevel.UNAVAILABLE) {
                        ValidationStatus.CHECK
                    } else {
                        ValidationStatus.PASS
                    },
                    calendar.level.label,
                ),
                ValidationItem(
                    "Notification permission",
                    if (notificationsEnabled) ValidationStatus.PASS else ValidationStatus.BLOCKED,
                    if (notificationsEnabled) {
                        "Android notifications are enabled for the app."
                    } else {
                        "Permission or the app notification switch is disabled."
                    },
                ),
                scheduleItem("Setup alert schedule", setupInterval.label, setupInterval.minutes > 0),
                scheduleItem(
                    "Pre-market schedule",
                    preMarketLeadTime.label,
                    preMarketLeadTime != PreMarketLeadTime.OFF,
                ),
                ValidationItem(
                    "Notification filters",
                    ValidationStatus.PASS,
                    notificationPolicy.summary(),
                ),
                ValidationItem(
                    "Notification publication evidence",
                    if (boundedAudit.isEmpty()) ValidationStatus.CHECK else ValidationStatus.PASS,
                    if (boundedAudit.isEmpty()) {
                        "No notification publication has been recorded on this installation."
                    } else {
                        "${boundedAudit.size} recent publication record(s) are available."
                    },
                ),
            ),
            notificationAudit = boundedAudit,
        )
    }

    private fun feedItem(feed: MarketDataFeed): ValidationItem = when (feed.state) {
        FeedState.LIVE -> ValidationItem(
            "MT5 feed state",
            ValidationStatus.PASS,
            "The backend validated a live bridge snapshot.",
        )
        FeedState.STALE -> ValidationItem(
            "MT5 feed state",
            ValidationStatus.BLOCKED,
            "The backend marked the latest bridge snapshot stale.",
        )
        FeedState.DEMO -> ValidationItem(
            "MT5 feed state",
            ValidationStatus.BLOCKED,
            "Bundled demo values are not an MT5 demo-feed validation.",
        )
        FeedState.CONNECTING -> ValidationItem(
            "MT5 feed state",
            ValidationStatus.BLOCKED,
            "The app is still waiting for a validated bridge snapshot.",
        )
    }

    private fun scheduleItem(label: String, value: String, enabled: Boolean) = ValidationItem(
        label,
        if (enabled) ValidationStatus.PASS else ValidationStatus.CHECK,
        if (enabled) "$value is selected." else "Off; enable only when timing validation is planned.",
    )

    private companion object {
        const val MAX_AUDIT_RECORDS = 20
    }
}

class DeviceValidationReportExporter {
    fun export(report: DeviceValidationReport): String = buildString {
        appendLine("MNM AU Seekers device validation report")
        appendLine("Generated: ${report.generatedAtEpochMillis.utcTime()}")
        appendLine("App version: ${report.appVersion}")
        appendLine("Symbol: ${report.symbol}")
        appendLine("Feed captured: ${report.feedCapturedAtEpochMillis?.utcTime() ?: "Unavailable"}")
        appendLine("Feed age: ${report.feedAgeSeconds?.let { "$it seconds" } ?: "Unavailable"}")
        appendLine(
            "Summary: ${report.passCount} pass, ${report.checkCount} check, " +
                "${report.blockedCount} blocked",
        )
        appendLine()
        appendLine("Checklist")
        report.items.forEach { item ->
            appendLine("[${item.status.name}] ${item.label}: ${item.detail}")
        }
        appendLine()
        appendLine("Notification publication evidence")
        if (report.notificationAudit.isEmpty()) {
            appendLine("None recorded.")
        } else {
            report.notificationAudit.forEach { record ->
                append("- ${record.publishedAtEpochMillis.utcTime()} | ${record.kind.label} | ")
                append("${record.symbol} | ${record.context}")
                record.expectedAtEpochMillis?.let { expected ->
                    val delaySeconds = (record.publishedAtEpochMillis - expected) / 1_000
                    append(" | expected ${expected.utcTime()} | offset ${delaySeconds}s")
                }
                appendLine()
            }
        }
        appendLine()
        appendLine(
            "Redaction: no service endpoint, token, device identifier, broker account data, " +
                "paper ledger, or journal note is included.",
        )
        appendLine(
            "A publication record means Android accepted the notification request; it does not " +
                "prove that the user saw it or that the operating system delivered it exactly on time.",
        )
        append("Analysis-only validation; no broker execution capability.")
    }

    private fun Long.utcTime(): String = REPORT_TIME_FORMATTER.format(Instant.ofEpochMilli(this))

    private companion object {
        val REPORT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneOffset.UTC)
    }
}
