package com.mnm.auseekers.data

import android.content.Context
import java.time.Duration
import java.time.Instant

enum class LiveAnalysisInterval(
    val seconds: Long,
    val label: String,
) {
    MANUAL(0, "Manual"),
    FIFTEEN_SECONDS(15, "15 sec"),
    THIRTY_SECONDS(30, "30 sec"),
    SIXTY_SECONDS(60, "60 sec"),
    ;

    val enabled: Boolean
        get() = seconds > 0
}

class LiveAnalysisRefreshStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun current(): LiveAnalysisInterval {
        val stored = preferences.getString(INTERVAL_KEY, null)
        return LiveAnalysisInterval.entries.firstOrNull { it.name == stored }
            ?: LiveAnalysisInterval.MANUAL
    }

    fun set(interval: LiveAnalysisInterval) {
        preferences.edit().putString(INTERVAL_KEY, interval.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "live_analysis_refresh"
        const val INTERVAL_KEY = "interval"
    }
}

class LiveAnalysisFeedReducer {
    fun merge(
        previous: MarketDataFeed,
        refreshed: MarketDataFeed,
        checkedAt: Instant,
    ): MarketDataFeed {
        val canRetainPrevious = refreshed.state == FeedState.DEMO &&
            previous.symbol == refreshed.symbol &&
            (previous.state == FeedState.LIVE || previous.state == FeedState.STALE) &&
            previous.capturedAt != null
        if (!canRetainPrevious) return refreshed

        val capturedAt = requireNotNull(previous.capturedAt)
        val elapsed = Duration.between(capturedAt, checkedAt).seconds
            .coerceAtLeast(previous.ageSeconds?.toLong() ?: 0)
            .coerceIn(0, Int.MAX_VALUE.toLong())
            .toInt()
        return previous.copy(
            state = FeedState.STALE,
            ageSeconds = elapsed,
            statusMessage =
                "Live refresh failed; showing the last validated MT5 snapshot as stale.",
        )
    }
}
