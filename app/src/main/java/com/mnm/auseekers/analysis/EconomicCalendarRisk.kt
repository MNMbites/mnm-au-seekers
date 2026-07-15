package com.mnm.auseekers.analysis

import com.mnm.auseekers.data.EconomicCalendarEvent
import com.mnm.auseekers.data.EconomicCalendarFeed
import com.mnm.auseekers.data.EconomicCalendarPolicy
import com.mnm.auseekers.data.EconomicImpact
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

enum class EconomicCalendarRiskLevel(val label: String) {
    UNAVAILABLE("Calendar unavailable"),
    CLEAR("No nearby event risk"),
    CAUTION("Calendar caution"),
    HIGH_IMPACT("High-impact window"),
}

data class EconomicCalendarAssessment(
    val level: EconomicCalendarRiskLevel,
    val detail: String,
    val nearestEvent: EconomicCalendarEvent?,
) {
    fun suppresses(policy: EconomicCalendarPolicy): Boolean =
        policy == EconomicCalendarPolicy.BLOCK_HIGH_IMPACT &&
            level == EconomicCalendarRiskLevel.HIGH_IMPACT
}

class EconomicCalendarRiskEvaluator {
    fun evaluate(
        feed: EconomicCalendarFeed,
        now: Instant = Instant.now(),
    ): EconomicCalendarAssessment {
        if (!feed.available) {
            return EconomicCalendarAssessment(
                EconomicCalendarRiskLevel.UNAVAILABLE,
                feed.statusMessage,
                null,
            )
        }
        val nearby = feed.events.sortedBy {
            abs(Duration.between(now, it.scheduledAt).seconds)
        }
        val highWithin30 = nearby.firstOrNull {
            it.impact == EconomicImpact.HIGH &&
                it.secondsFrom(now).absoluteValue <= HIGH_IMPACT_WINDOW_SECONDS
        }
        if (highWithin30 != null) {
            return EconomicCalendarAssessment(
                EconomicCalendarRiskLevel.HIGH_IMPACT,
                "${highWithin30.title} (${highWithin30.currency}) " +
                    highWithin30.relativeTime(now) +
                    "; reduce technical confidence and avoid a new setup if blocking is enabled.",
                highWithin30,
            )
        }
        val caution = nearby.firstOrNull {
            (it.impact == EconomicImpact.HIGH &&
                it.secondsFrom(now).absoluteValue <= CAUTION_WINDOW_SECONDS) ||
                (it.impact == EconomicImpact.MEDIUM &&
                    it.secondsFrom(now).absoluteValue <= HIGH_IMPACT_WINDOW_SECONDS)
        }
        if (caution != null) {
            return EconomicCalendarAssessment(
                EconomicCalendarRiskLevel.CAUTION,
                "${caution.title} (${caution.currency}) ${caution.relativeTime(now)}.",
                caution,
            )
        }
        return EconomicCalendarAssessment(
            EconomicCalendarRiskLevel.CLEAR,
            "No medium/high-impact event is inside the configured caution window.",
            nearby.firstOrNull(),
        )
    }

    private fun EconomicCalendarEvent.secondsFrom(now: Instant): Long =
        Duration.between(now, scheduledAt).seconds

    private fun EconomicCalendarEvent.relativeTime(now: Instant): String {
        val seconds = secondsFrom(now)
        val minutes = abs(seconds) / 60
        return when {
            seconds > 0 -> "in $minutes min"
            seconds < 0 -> "$minutes min ago"
            else -> "now"
        }
    }

    private companion object {
        const val HIGH_IMPACT_WINDOW_SECONDS = 30 * 60L
        const val CAUTION_WINDOW_SECONDS = 60 * 60L
    }
}

private val Long.absoluteValue: Long
    get() = if (this < 0) -this else this
