package com.mnm.auseekers.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mnm.auseekers.MainActivity
import com.mnm.auseekers.R
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.SetupStage
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.TradingMode
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class NotificationInterval(
    val minutes: Long,
    val label: String,
) {
    OFF(0, "Off"),
    THIRTY_MINUTES(30, "30 min"),
    SIXTY_MINUTES(60, "60 min"),
}

data class SetupNotification(
    val symbol: String,
    val title: String,
    val body: String,
    val fingerprint: String,
)

class SetupNotificationEvaluator(
    private val signalEngine: SignalEngine = SignalEngine(),
) {
    fun evaluate(feed: MarketDataFeed): SetupNotification? {
        if (feed.state != FeedState.LIVE) return null

        val analysis = signalEngine.analyse(feed.snapshots, TradingMode.PRIMARY)
        if (analysis.direction == Direction.WAIT || analysis.stage == SetupStage.WATCH) {
            return null
        }

        return SetupNotification(
            symbol = feed.symbol,
            title = "${feed.symbol} ${analysis.direction.label} setup",
            body = "${analysis.stage.label} • ${analysis.strength}% alignment. Analysis only.",
            fingerprint = "${analysis.direction.name}:${analysis.stage.name}",
        )
    }
}

object SetupNotificationScheduler {
    fun currentInterval(context: Context): NotificationInterval {
        val stored = preferences(context).getString(INTERVAL_KEY, null)
        return NotificationInterval.entries.firstOrNull { it.name == stored }
            ?: NotificationInterval.OFF
    }

    fun schedule(context: Context, interval: NotificationInterval) {
        if (interval == NotificationInterval.OFF) {
            cancel(context)
            return
        }

        val request = PeriodicWorkRequestBuilder<SetupNotificationWorker>(
            interval.minutes,
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
        preferences(context).edit().putString(INTERVAL_KEY, interval.name).apply()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        preferences(context).edit().putString(
            INTERVAL_KEY,
            NotificationInterval.OFF.name,
        ).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private const val PREFERENCES_NAME = "setup_notification_settings"
    private const val INTERVAL_KEY = "interval"
    private const val UNIQUE_WORK_NAME = "setup-notification-check"
}

class SetupNotificationDeduplicator(context: Context) {
    private val preferences = context.getSharedPreferences(
        "setup_notification_history",
        Context.MODE_PRIVATE,
    )

    fun shouldNotify(notification: SetupNotification): Boolean {
        val key = notification.symbol.lowercase(Locale.US)
        if (preferences.getString(key, null) == notification.fingerprint) return false
        preferences.edit().putString(key, notification.fingerprint).apply()
        return true
    }

    fun clear(symbol: String) {
        preferences.edit().remove(symbol.lowercase(Locale.US)).apply()
    }
}

object SetupNotificationPublisher {
    fun notificationsEnabled(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun publish(context: Context, notification: SetupNotification) {
        if (!notificationsEnabled(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val rendered = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        NotificationManagerCompat.from(context).notify(
            notification.symbol.hashCode() and Int.MAX_VALUE,
            rendered,
        )
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Market setup alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Analysis-only alerts for live watchlist setups"
            },
        )
    }

    private const val CHANNEL_ID = "market-setup-alerts"
}
