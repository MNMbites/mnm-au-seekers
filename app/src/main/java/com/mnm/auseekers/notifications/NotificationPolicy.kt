package com.mnm.auseekers.notifications

import android.content.Context
import com.mnm.auseekers.analysis.MarketHealthLevel
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.premarket.MarketSession
import java.time.Instant
import java.time.ZoneId

enum class NotificationConfidenceFilter(val label: String) {
    READY_OR_CAUTION("Ready + Caution"),
    READY_ONLY("Ready only"),
}

enum class SetupStageFilter(val label: String) {
    EARLY_AND_CONFIRMED("Early + Confirmed"),
    CONFIRMED_ONLY("Confirmed only"),
}

enum class NotificationSessionFilter(val label: String) {
    BOTH("London + New York"),
    LONDON_ONLY("London only"),
    NEW_YORK_ONLY("New York only"),
}

enum class NotificationQuietHours(
    val label: String,
    val startMinute: Int?,
    val endMinute: Int?,
) {
    OFF("Off", null, null),
    NIGHT_22_TO_07("22:00–07:00", 22 * 60, 7 * 60),
    MIDNIGHT_TO_06("00:00–06:00", 0, 6 * 60),
}

data class NotificationPolicy(
    val confidenceFilter: NotificationConfidenceFilter =
        NotificationConfidenceFilter.READY_OR_CAUTION,
    val setupStageFilter: SetupStageFilter = SetupStageFilter.EARLY_AND_CONFIRMED,
    val sessionFilter: NotificationSessionFilter = NotificationSessionFilter.BOTH,
    val quietHours: NotificationQuietHours = NotificationQuietHours.OFF,
) {
    fun allowsMarketHealth(level: MarketHealthLevel): Boolean = when (confidenceFilter) {
        NotificationConfidenceFilter.READY_OR_CAUTION -> level != MarketHealthLevel.NOT_READY
        NotificationConfidenceFilter.READY_ONLY -> level == MarketHealthLevel.READY
    }

    fun allowsSetupStage(stage: SetupStage): Boolean = when (setupStageFilter) {
        SetupStageFilter.EARLY_AND_CONFIRMED -> stage != SetupStage.WATCH
        SetupStageFilter.CONFIRMED_ONLY -> stage == SetupStage.CONFIRMED
    }

    fun allowedSessions(): Set<MarketSession> = when (sessionFilter) {
        NotificationSessionFilter.BOTH -> MarketSession.entries.toSet()
        NotificationSessionFilter.LONDON_ONLY -> setOf(MarketSession.LONDON)
        NotificationSessionFilter.NEW_YORK_ONLY -> setOf(MarketSession.NEW_YORK)
    }

    fun isQuietAt(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val start = quietHours.startMinute ?: return false
        val end = quietHours.endMinute ?: return false
        val localTime = instant.atZone(zoneId).toLocalTime()
        val minute = localTime.hour * 60 + localTime.minute
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }

    fun summary(): String =
        "${confidenceFilter.label}; ${setupStageFilter.label}; ${sessionFilter.label}; " +
            "quiet hours ${quietHours.label} device time"
}

object NotificationPolicyStore {
    fun current(context: Context): NotificationPolicy {
        val preferences = preferences(context)
        return NotificationPolicy(
            confidenceFilter = preferences.enumValue(
                CONFIDENCE_KEY,
                NotificationConfidenceFilter.READY_OR_CAUTION,
            ),
            setupStageFilter = preferences.enumValue(
                STAGE_KEY,
                SetupStageFilter.EARLY_AND_CONFIRMED,
            ),
            sessionFilter = preferences.enumValue(
                SESSION_KEY,
                NotificationSessionFilter.BOTH,
            ),
            quietHours = preferences.enumValue(
                QUIET_HOURS_KEY,
                NotificationQuietHours.OFF,
            ),
        )
    }

    fun set(context: Context, policy: NotificationPolicy) {
        preferences(context).edit()
            .putString(CONFIDENCE_KEY, policy.confidenceFilter.name)
            .putString(STAGE_KEY, policy.setupStageFilter.name)
            .putString(SESSION_KEY, policy.sessionFilter.name)
            .putString(QUIET_HOURS_KEY, policy.quietHours.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: default

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private const val PREFERENCES_NAME = "notification_policy"
    private const val CONFIDENCE_KEY = "confidence"
    private const val STAGE_KEY = "setup_stage"
    private const val SESSION_KEY = "session"
    private const val QUIET_HOURS_KEY = "quiet_hours"
}
