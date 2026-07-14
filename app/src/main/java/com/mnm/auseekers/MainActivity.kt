package com.mnm.auseekers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mnm.auseekers.data.DEFAULT_SYMBOL
import com.mnm.auseekers.data.DemoMarketDataProvider
import com.mnm.auseekers.data.FallbackMarketDataProvider
import com.mnm.auseekers.data.FeedState
import com.mnm.auseekers.data.HttpMarketDataProvider
import com.mnm.auseekers.data.MarketDataFeed
import com.mnm.auseekers.data.MarketDataProvider
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketAnalysis
import com.mnm.auseekers.domain.RiskCalculator
import com.mnm.auseekers.domain.RiskPlan
import com.mnm.auseekers.domain.RiskProfile
import com.mnm.auseekers.domain.RiskRequest
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.TimeframeSignal
import com.mnm.auseekers.domain.TradingMode
import com.mnm.auseekers.notifications.NotificationInterval
import com.mnm.auseekers.notifications.SetupNotificationScheduler
import com.mnm.auseekers.ui.theme.MnmAuSeekersTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MnmAuSeekersTheme {
                val provider = remember { configuredMarketDataProvider() }
                MnmAuSeekersApp(
                    marketDataProvider = provider,
                    initialFeed = initialMarketDataFeed(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnmAuSeekersApp(
    marketDataProvider: MarketDataProvider,
    initialFeed: MarketDataFeed,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(TradingMode.PRIMARY) }
    var profile by rememberSaveable { mutableStateOf(RiskProfile.SAFE) }
    var balance by rememberSaveable { mutableStateOf("15.00") }
    var stopPoints by rememberSaveable { mutableStateOf("50") }
    var pointValue by rememberSaveable { mutableStateOf("0.10") }
    var refreshRequest by rememberSaveable { mutableIntStateOf(0) }
    var selectedSymbol by rememberSaveable { mutableStateOf(DEFAULT_SYMBOL) }
    var notificationInterval by rememberSaveable {
        mutableStateOf(SetupNotificationScheduler.currentInterval(context))
    }
    var pendingNotificationInterval by remember { mutableStateOf<NotificationInterval?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingNotificationInterval
        if (granted && pending != null) {
            SetupNotificationScheduler.schedule(context, pending)
            notificationInterval = pending
        }
        pendingNotificationInterval = null
    }

    val symbols by produceState(
        initialValue = listOf(DEFAULT_SYMBOL),
        marketDataProvider,
        refreshRequest,
    ) {
        value = marketDataProvider.watchlist().ifEmpty { listOf(DEFAULT_SYMBOL) }
    }

    LaunchedEffect(symbols) {
        if (selectedSymbol !in symbols) selectedSymbol = symbols.first()
    }

    val feed by produceState(
        initialValue = initialFeed,
        marketDataProvider,
        selectedSymbol,
        refreshRequest,
    ) {
        value = initialFeed.copy(
            symbol = selectedSymbol,
            state = FeedState.CONNECTING,
            statusMessage = "Loading $selectedSymbol from the configured data source…",
        )
        value = marketDataProvider.latest(selectedSymbol)
    }

    val analysis = remember(mode, feed.snapshots) {
        SignalEngine().analyse(feed.snapshots, mode)
    }
    val riskPlan = remember(mode, profile, balance, stopPoints, pointValue) {
        RiskCalculator().calculate(
            RiskRequest(
                balance = balance.toDoubleOrNull() ?: 0.0,
                profile = profile,
                mode = mode,
                stopDistancePoints = stopPoints.toDoubleOrNull() ?: 0.0,
                valuePerPointPerLot = pointValue.toDoubleOrNull() ?: 0.0,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MNM AU Seekers", fontWeight = FontWeight.Bold)
                        Text(
                            "$selectedSymbol • analysis only",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ChoiceSection(
                    title = "Watchlist symbol",
                    options = symbols,
                    selected = selectedSymbol,
                    label = { it },
                    onSelected = { selectedSymbol = it },
                )
            }
            item {
                ConnectionBanner(
                    feed = feed,
                    onRefresh = { refreshRequest += 1 },
                )
            }
            item {
                ChoiceSection(
                    title = "Trading mode",
                    options = TradingMode.entries,
                    selected = mode,
                    label = { it.label },
                    onSelected = { mode = it },
                )
            }
            item { AnalysisCard(analysis) }
            item {
                NotificationSettings(
                    selected = notificationInterval,
                    liveServiceConfigured = BuildConfig.MARKET_DATA_BASE_URL.isNotBlank(),
                    onSelected = { interval ->
                        if (interval == NotificationInterval.OFF) {
                            SetupNotificationScheduler.cancel(context)
                            notificationInterval = interval
                        } else if (notificationPermissionGranted(context)) {
                            SetupNotificationScheduler.schedule(context, interval)
                            notificationInterval = interval
                        } else {
                            pendingNotificationInterval = interval
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            item { SectionTitle("Timeframe agreement") }
            items(analysis.timeframeSignals, key = { it.timeframe }) { signal ->
                TimeframeCard(signal)
            }
            item {
                ChoiceSection(
                    title = "Risk profile",
                    options = RiskProfile.entries,
                    selected = profile,
                    label = { it.label },
                    onSelected = { profile = it },
                )
            }
            item {
                RiskInputs(
                    balance = balance,
                    stopPoints = stopPoints,
                    pointValue = pointValue,
                    onBalanceChange = { balance = it.numericInput() },
                    onStopChange = { stopPoints = it.numericInput() },
                    onPointValueChange = { pointValue = it.numericInput() },
                )
            }
            item { RiskCard(riskPlan) }
            item { SafetyFooter() }
        }
    }
}

@Composable
private fun ConnectionBanner(
    feed: MarketDataFeed,
    onRefresh: () -> Unit,
) {
    val containerColor = when (feed.state) {
        FeedState.LIVE -> MaterialTheme.colorScheme.primaryContainer
        FeedState.STALE -> MaterialTheme.colorScheme.errorContainer
        FeedState.CONNECTING, FeedState.DEMO -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (feed.state) {
        FeedState.LIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        FeedState.STALE -> MaterialTheme.colorScheme.onErrorContainer
        FeedState.CONNECTING, FeedState.DEMO -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val heading = when (feed.state) {
        FeedState.CONNECTING -> "CONNECTING"
        FeedState.DEMO -> "DEMO DATA"
        FeedState.LIVE -> "LIVE DATA"
        FeedState.STALE -> "STALE DATA"
    }
    val quote = if (feed.bid != null && feed.ask != null) {
        "Bid ${feed.bid.format(2)} • Ask ${feed.ask.format(2)}"
    } else {
        null
    }
    val age = feed.ageSeconds?.let { "Age ${it}s" }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                )
                Text(
                    text = feed.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
                listOfNotNull(quote, age).takeIf { it.isNotEmpty() }?.let { details ->
                    Text(
                        text = details.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            }
            OutlinedButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun NotificationSettings(
    selected: NotificationInterval,
    liveServiceConfigured: Boolean,
    onSelected: (NotificationInterval) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceSection(
            title = "Setup notifications",
            options = NotificationInterval.entries,
            selected = selected,
            label = { it.label },
            optionEnabled = {
                it == NotificationInterval.OFF || liveServiceConfigured
            },
            onSelected = onSelected,
        )
        Text(
            text = if (liveServiceConfigured) {
                "Optional approximate checks use live watchlist data only. " +
                    "Alerts are analysis signals, not orders or guarantees."
            } else {
                "Configure a live-service URL at build time to enable setup notifications."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    optionEnabled: (T) -> Boolean = { true },
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options) { option ->
                FilterChip(
                    selected = selected == option,
                    enabled = optionEnabled(option),
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: MarketAnalysis) {
    val accent = directionColor(analysis.direction)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(analysis.mode.label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        analysis.direction.label.uppercase(Locale.US),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = accent,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(analysis.stage.label, fontWeight = FontWeight.SemiBold)
                    Text("${analysis.strength}% alignment")
                }
            }
            Text(analysis.rationale, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TimeframeCard(signal: TimeframeSignal) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(signal.timeframe.label, fontWeight = FontWeight.Bold)
            Text(
                signal.direction.label,
                color = directionColor(signal.direction),
                fontWeight = FontWeight.Bold,
            )
            Text("${signal.strength}%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RiskInputs(
    balance: String,
    stopPoints: String,
    pointValue: String,
    onBalanceChange: (String) -> Unit,
    onStopChange: (String) -> Unit,
    onPointValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Position inputs")
        OutlinedTextField(
            value = balance,
            onValueChange = onBalanceChange,
            label = { Text("Balance (USD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = stopPoints,
                onValueChange = onStopChange,
                label = { Text("Stop points") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = pointValue,
                onValueChange = onPointValueChange,
                label = { Text("USD/point/lot") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RiskCard(plan: RiskPlan) {
    val accent = if (plan.allowed) Color(0xFF006C4C) else MaterialTheme.colorScheme.error
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (plan.allowed) "WITHIN RISK LIMIT" else "NO TRADE",
                color = accent,
                fontWeight = FontWeight.Black,
            )
            Text("Risk: ${plan.effectiveRiskPercent.format(2)}%")
            Text("Maximum planned loss: USD ${plan.maximumLoss.format(2)}")
            Text("Suggested lot: ${plan.suggestedLot?.format(2) ?: "—"}")
            Spacer(Modifier.height(2.dp))
            Text(plan.reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SafetyFooter() {
    Text(
        text = "Verify contract size, tick value, spread, stop distance, and minimum lot with your broker. " +
            "This preview never sends an order.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun directionColor(direction: Direction): Color = when (direction) {
    Direction.BUY -> Color(0xFF006C4C)
    Direction.SELL -> MaterialTheme.colorScheme.error
    Direction.WAIT -> MaterialTheme.colorScheme.secondary
}

private fun Double.format(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

private fun String.numericInput(): String = filterIndexed { index, character ->
    character.isDigit() || (character == '.' && index == indexOf('.'))
}

private fun configuredMarketDataProvider(): MarketDataProvider {
    val baseUrl = BuildConfig.MARKET_DATA_BASE_URL.trim()
    val demo = DemoMarketDataProvider()
    if (baseUrl.isBlank()) return demo

    return runCatching {
        FallbackMarketDataProvider(
            primary = HttpMarketDataProvider(baseUrl),
            fallback = demo,
        )
    }.getOrElse {
        DemoMarketDataProvider("Invalid live-service configuration; using bundled demo data.")
    }
}

private fun initialMarketDataFeed(): MarketDataFeed = if (
    BuildConfig.MARKET_DATA_BASE_URL.isBlank()
) {
    DemoMarketDataProvider.feed(
        statusMessage = "Bundled demo snapshot; no live service is configured.",
    )
} else {
    DemoMarketDataProvider.feed(
        statusMessage = "Connecting to the configured live service…",
    ).copy(
        state = FeedState.CONNECTING,
    )
}

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
