package com.mnm.auseekers.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnm.auseekers.BuildConfig
import com.mnm.auseekers.data.DEFAULT_SYMBOL
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.HttpMarketDataProvider
import com.mnm.auseekers.data.HttpEconomicCalendarProvider
import com.mnm.auseekers.data.EconomicCalendarPolicyStore
import com.mnm.auseekers.data.calendarCurrenciesForSymbol
import com.mnm.auseekers.analysis.EconomicCalendarRiskEvaluator
import java.time.Instant

class SetupNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        if (!SetupNotificationPublisher.notificationsEnabled(applicationContext)) {
            return Result.success()
        }

        val baseUrl = BuildConfig.MARKET_DATA_BASE_URL.trim()
        if (baseUrl.isBlank()) return Result.success()

        val provider = runCatching { HttpMarketDataProvider(baseUrl) }
            .getOrElse { return Result.failure() }
        val calendarProvider = runCatching { HttpEconomicCalendarProvider(baseUrl) }
            .getOrElse { return Result.failure() }
        val calendarPolicy = EconomicCalendarPolicyStore.current(applicationContext)
        val notificationPolicy = NotificationPolicyStore.current(applicationContext)
        if (notificationPolicy.isQuietAt(Instant.now())) return Result.success()
        val symbols = runCatching { provider.watchlist() }
            .getOrElse { return Result.retry() }
            .ifEmpty { listOf(DEFAULT_SYMBOL) }
        val evaluator = SetupNotificationEvaluator()
        val deduplicator = SetupNotificationDeduplicator(applicationContext)
        var successfulFetches = 0

        symbols.forEach { symbol ->
            runCatching { provider.latest(symbol) }.onSuccess { feed ->
                successfulFetches += 1
                if (feed.state != FeedState.LIVE) return@onSuccess
                val now = Instant.now()
                val calendarFeed = calendarProvider.events(
                    currencies = calendarCurrenciesForSymbol(symbol),
                    from = now.minusSeconds(30 * 60L),
                    to = now.plusSeconds(60 * 60L),
                )
                val calendarAssessment = EconomicCalendarRiskEvaluator().evaluate(
                    calendarFeed,
                    now,
                )
                val notification = evaluator.evaluate(
                    feed,
                    calendarAssessment,
                    calendarPolicy,
                    notificationPolicy,
                )
                if (notification == null) {
                    deduplicator.clear(symbol)
                } else if (deduplicator.shouldNotify(notification)) {
                    SetupNotificationPublisher.publish(applicationContext, notification)
                }
            }
        }

        return if (successfulFetches > 0) Result.success() else Result.retry()
    }
}
