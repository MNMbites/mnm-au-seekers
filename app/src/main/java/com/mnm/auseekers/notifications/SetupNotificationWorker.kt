package com.mnm.auseekers.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnm.auseekers.BuildConfig
import com.mnm.auseekers.data.DEFAULT_SYMBOL
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.HttpMarketDataProvider

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
                val notification = evaluator.evaluate(feed)
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
