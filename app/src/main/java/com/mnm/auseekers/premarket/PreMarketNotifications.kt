package com.mnm.auseekers.premarket

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnm.auseekers.BuildConfig
import com.mnm.auseekers.MainActivity
import com.mnm.auseekers.R
import com.mnm.auseekers.analysis.EconomicCalendarRiskEvaluator
import com.mnm.auseekers.analysis.MarketHealthEvaluator
import com.mnm.auseekers.data.DEFAULT_SYMBOL
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.HttpEconomicCalendarProvider
import com.mnm.auseekers.data.HttpMarketDataProvider
import com.mnm.auseekers.data.calendarCurrenciesForSymbol
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.TradingMode
import com.mnm.auseekers.notifications.NotificationPolicyStore
import com.mnm.auseekers.notifications.SetupNotificationPublisher
import com.mnm.auseekers.validation.NotificationAuditKind
import com.mnm.auseekers.validation.NotificationAuditRecorder
import java.time.Instant
import java.util.concurrent.TimeUnit

object PreMarketBriefingScheduler {
    fun currentLeadTime(context: Context): PreMarketLeadTime {
        val stored = preferences(context).getString(LEAD_TIME_KEY, null)
        return PreMarketLeadTime.entries.firstOrNull { it.name == stored }
            ?: PreMarketLeadTime.OFF
    }

    fun schedule(context: Context, leadTime: PreMarketLeadTime) {
        if (leadTime == PreMarketLeadTime.OFF) {
            cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<PreMarketBriefingWorker>(
            CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        preferences(context).edit().putString(LEAD_TIME_KEY, leadTime.name).apply()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        preferences(context).edit().putString(LEAD_TIME_KEY, PreMarketLeadTime.OFF.name).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        "pre_market_briefing_settings",
        Context.MODE_PRIVATE,
    )

    private const val CHECK_INTERVAL_MINUTES = 15L
    private const val LEAD_TIME_KEY = "lead_time"
    private const val UNIQUE_WORK_NAME = "pre-market-briefing-check"
}

class PreMarketBriefingWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        if (!SetupNotificationPublisher.notificationsEnabled(applicationContext)) {
            return Result.success()
        }
        val leadTime = PreMarketBriefingScheduler.currentLeadTime(applicationContext)
        if (leadTime == PreMarketLeadTime.OFF) return Result.success()
        val now = Instant.now()
        val notificationPolicy = NotificationPolicyStore.current(applicationContext)
        if (notificationPolicy.isQuietAt(now)) return Result.success()
        val window = PreMarketSessionPlanner().nextWindow(
            now,
            leadTime,
            notificationPolicy.allowedSessions(),
        )
            ?: return Result.success()
        if (!window.active) return Result.success()
        val baseUrl = BuildConfig.MARKET_DATA_BASE_URL.trim()
        if (baseUrl.isBlank()) return Result.success()

        val marketProvider = runCatching { HttpMarketDataProvider(baseUrl) }
            .getOrElse { return Result.failure() }
        val calendarProvider = runCatching { HttpEconomicCalendarProvider(baseUrl) }
            .getOrElse { return Result.failure() }
        val feed = runCatching { marketProvider.latest(DEFAULT_SYMBOL) }
            .getOrElse { return Result.retry() }
        if (feed.state != FeedState.LIVE) return Result.retry()

        val analysis = SignalEngine().analyse(feed.snapshots, TradingMode.PRIMARY)
        val health = MarketHealthEvaluator().evaluate(feed, analysis)
        val calendarFeed = calendarProvider.events(
            currencies = calendarCurrenciesForSymbol(DEFAULT_SYMBOL),
            from = now.minusSeconds(30 * 60L),
            to = window.opensAt.plusSeconds(60 * 60L),
        )
        val calendar = EconomicCalendarRiskEvaluator().evaluate(calendarFeed, now)
        val briefing = PreMarketBriefingGenerator().generate(
            window,
            feed,
            analysis,
            health,
            calendar,
        )
        val fingerprint = "${DEFAULT_SYMBOL}:${window.fingerprint}"
        if (PreMarketBriefingDeduplicator(applicationContext).shouldPublish(fingerprint)) {
            PreMarketBriefingPublisher.publish(applicationContext, DEFAULT_SYMBOL, briefing)
        }
        return Result.success()
    }
}

class PreMarketBriefingDeduplicator(context: Context) {
    private val preferences = context.getSharedPreferences(
        "pre_market_briefing_history",
        Context.MODE_PRIVATE,
    )

    fun shouldPublish(fingerprint: String): Boolean {
        if (preferences.getString(LAST_FINGERPRINT_KEY, null) == fingerprint) return false
        preferences.edit().putString(LAST_FINGERPRINT_KEY, fingerprint).apply()
        return true
    }

    private companion object {
        const val LAST_FINGERPRINT_KEY = "last_fingerprint"
    }
}

object PreMarketBriefingPublisher {
    @SuppressLint("MissingPermission")
    fun publish(context: Context, symbol: String, briefing: PreMarketBriefing) {
        if (!SetupNotificationPublisher.notificationsEnabled(context)) return
        createChannel(context)
        val auditRecorder = NotificationAuditRecorder(context)
        val auditId = NotificationAuditRecorder.newRecordId()
        val auditRecorded = runCatching {
            auditRecorder.record(
                recordId = auditId,
                kind = NotificationAuditKind.PRE_MARKET,
                symbol = symbol,
                expectedAtEpochMillis = briefing.window.briefingStartsAt.toEpochMilli(),
                context = briefing.window.session.label,
            )
        }.getOrDefault(false)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.NOTIFICATION_AUDIT_ID_EXTRA, auditId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            auditId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "${briefing.primaryBias} • ${briefing.confidence}. " +
            "${briefing.riskGuidance} ${briefing.alternativeScenario} Analysis only."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("${briefing.window.session.label} pre-market • $symbol")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                briefing.window.fingerprint.hashCode() and Int.MAX_VALUE,
                notification,
            )
        } catch (error: RuntimeException) {
            if (auditRecorded) runCatching { auditRecorder.remove(auditId) }
            throw error
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pre-market briefings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Analysis-only London and New York session preparation"
            },
        )
    }

    private const val CHANNEL_ID = "pre-market-briefings"
}
