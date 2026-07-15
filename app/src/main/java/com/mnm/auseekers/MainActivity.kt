package com.mnm.auseekers

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
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
import com.mnm.auseekers.analysis.HealthFactorState
import com.mnm.auseekers.analysis.MarketHealthAssessment
import com.mnm.auseekers.analysis.MarketHealthEvaluator
import com.mnm.auseekers.analysis.MarketHealthLevel
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
import com.mnm.auseekers.paper.AndroidPaperTradingRepository
import com.mnm.auseekers.paper.OpenPaperTradeRequest
import com.mnm.auseekers.paper.PaperAuditStatus
import com.mnm.auseekers.paper.PaperCsvExporter
import com.mnm.auseekers.paper.PaperPerformanceAnalyzer
import com.mnm.auseekers.paper.PaperPerformanceReport
import com.mnm.auseekers.paper.PaperPortfolio
import com.mnm.auseekers.paper.PaperPosition
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
    var pointSize by rememberSaveable { mutableStateOf("0.01") }
    var refreshRequest by rememberSaveable { mutableIntStateOf(0) }
    var selectedSymbol by rememberSaveable { mutableStateOf(DEFAULT_SYMBOL) }
    var notificationInterval by rememberSaveable {
        mutableStateOf(SetupNotificationScheduler.currentInterval(context))
    }
    var pendingNotificationInterval by remember { mutableStateOf<NotificationInterval?>(null) }
    val paperRepository = remember(context) {
        AndroidPaperTradingRepository(context.applicationContext)
    }
    var paperPortfolio by remember { mutableStateOf(paperRepository.load()) }
    var paperMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val paperReport = remember(paperPortfolio) {
        PaperPerformanceAnalyzer().analyze(paperPortfolio)
    }

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
    val marketHealth = remember(feed, analysis) {
        MarketHealthEvaluator().evaluate(feed, analysis)
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
    val paperRiskBalance = paperPortfolio.cashBalance ?: (balance.toDoubleOrNull() ?: 0.0)
    val paperRiskPlan = remember(
        mode,
        profile,
        paperRiskBalance,
        stopPoints,
        pointValue,
    ) {
        RiskCalculator().calculate(
            RiskRequest(
                balance = paperRiskBalance,
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
            item { MarketHealthCard(marketHealth) }
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
                    pointSize = pointSize,
                    onBalanceChange = { balance = it.numericInput() },
                    onStopChange = { stopPoints = it.numericInput() },
                    onPointValueChange = { pointValue = it.numericInput() },
                    onPointSizeChange = { pointSize = it.numericInput() },
                )
            }
            item { RiskCard(riskPlan) }
            item {
                val selectedPosition = paperPortfolio.openPositions.firstOrNull {
                    it.symbol.equals(selectedSymbol, ignoreCase = true)
                }
                val unrealizedPnl = selectedPosition?.let { position ->
                    val bid = feed.bid
                    val ask = feed.ask
                    if (feed.state == FeedState.LIVE && bid != null && ask != null) {
                        runCatching {
                            paperRepository.unrealizedPnl(position, bid, ask)
                        }.getOrNull()
                    } else {
                        null
                    }
                }
                PaperTradingCard(
                    portfolio = paperPortfolio,
                    performanceReport = paperReport,
                    selectedSymbol = selectedSymbol,
                    selectedPosition = selectedPosition,
                    unrealizedPnl = unrealizedPnl,
                    feed = feed,
                    analysis = analysis,
                    marketHealth = marketHealth,
                    riskPlan = paperRiskPlan,
                    pointSize = pointSize.toDoubleOrNull(),
                    pointValuePerLot = pointValue.toDoubleOrNull(),
                    stopPoints = stopPoints.toDoubleOrNull(),
                    startingBalance = balance.toDoubleOrNull(),
                    message = paperMessage,
                    onOpen = {
                        val result = runCatching {
                            paperRepository.open(
                                OpenPaperTradeRequest(
                                    symbol = selectedSymbol,
                                    direction = analysis.direction,
                                    lotSize = requireNotNull(paperRiskPlan.suggestedLot),
                                    bid = requireNotNull(feed.bid),
                                    ask = requireNotNull(feed.ask),
                                    pointSize = requireNotNull(pointSize.toDoubleOrNull()),
                                    valuePerPointPerLot = requireNotNull(
                                        pointValue.toDoubleOrNull(),
                                    ),
                                    plannedStopPoints = requireNotNull(
                                        stopPoints.toDoubleOrNull(),
                                    ),
                                    maximumPlannedLoss = paperRiskPlan.maximumLoss,
                                    startingBalance = requireNotNull(balance.toDoubleOrNull()),
                                ),
                            )
                        }
                        result.onSuccess {
                            paperPortfolio = it
                            paperMessage = "Paper position opened locally."
                        }.onFailure {
                            paperMessage = it.message ?: "Paper position could not be opened."
                        }
                    },
                    onClose = { position ->
                        val result = runCatching {
                            paperRepository.close(
                                position.id,
                                requireNotNull(feed.bid),
                                requireNotNull(feed.ask),
                            )
                        }
                        result.onSuccess {
                            paperPortfolio = it
                            paperMessage = "Paper position closed locally."
                        }.onFailure {
                            paperMessage = it.message ?: "Paper position could not be closed."
                        }
                    },
                    onReset = {
                        runCatching { paperRepository.reset() }
                            .onSuccess {
                                paperPortfolio = it
                                paperMessage = "Paper portfolio reset."
                            }
                            .onFailure {
                                paperMessage = it.message ?: "Paper portfolio could not be reset."
                            }
                    },
                    onExport = {
                        runCatching {
                            val csv = PaperCsvExporter().export(paperPortfolio, paperReport)
                            sharePaperCsv(context, csv)
                        }.onSuccess {
                            paperMessage = "Paper audit CSV opened in the Android share sheet."
                        }.onFailure {
                            paperMessage = it.message ?: "Paper audit CSV could not be shared."
                        }
                    },
                )
            }
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
private fun MarketHealthCard(assessment: MarketHealthAssessment) {
    val accent = when (assessment.level) {
        MarketHealthLevel.READY -> Color(0xFF006C4C)
        MarketHealthLevel.CAUTION -> Color(0xFF8A5300)
        MarketHealthLevel.NOT_READY -> MaterialTheme.colorScheme.error
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("MARKET HEALTH", fontWeight = FontWeight.Black, color = accent)
                    Text(assessment.level.label, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${assessment.score}/100",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = accent,
                    )
                    Text("readiness")
                }
            }
            Text(assessment.confidenceLabel, fontWeight = FontWeight.Bold)
            Text(assessment.confidenceExplanation)
            assessment.factors.forEach { factor ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "●",
                        color = healthFactorColor(factor.state),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(factor.title, fontWeight = FontWeight.SemiBold)
                        Text(factor.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "+${factor.points}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Readiness measures data quality and signal agreement—not outcome probability.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun healthFactorColor(state: HealthFactorState): Color = when (state) {
    HealthFactorState.POSITIVE -> Color(0xFF006C4C)
    HealthFactorState.CAUTION -> Color(0xFF8A5300)
    HealthFactorState.BLOCKING -> MaterialTheme.colorScheme.error
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
    pointSize: String,
    onBalanceChange: (String) -> Unit,
    onStopChange: (String) -> Unit,
    onPointValueChange: (String) -> Unit,
    onPointSizeChange: (String) -> Unit,
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
        OutlinedTextField(
            value = pointSize,
            onValueChange = onPointSizeChange,
            label = { Text("Price units per point (paper P&L)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun PaperTradingCard(
    portfolio: PaperPortfolio,
    performanceReport: PaperPerformanceReport,
    selectedSymbol: String,
    selectedPosition: PaperPosition?,
    unrealizedPnl: Double?,
    feed: MarketDataFeed,
    analysis: MarketAnalysis,
    marketHealth: MarketHealthAssessment,
    riskPlan: RiskPlan,
    pointSize: Double?,
    pointValuePerLot: Double?,
    stopPoints: Double?,
    startingBalance: Double?,
    message: String?,
    onOpen: () -> Unit,
    onClose: (PaperPosition) -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
) {
    val quoteIsValid = feed.state == FeedState.LIVE &&
        feed.bid != null && feed.ask != null && feed.bid > 0 && feed.ask >= feed.bid
    val openBlockReason = when {
        selectedPosition != null -> "One paper position per symbol is already open."
        feed.state != FeedState.LIVE -> "Live data is required to open a paper position."
        marketHealth.level != MarketHealthLevel.READY -> {
            "Market health must be Ready before opening a paper position."
        }
        analysis.direction == Direction.WAIT -> "A Buy or Sell analysis direction is required."
        !riskPlan.allowed || riskPlan.suggestedLot == null -> riskPlan.reason
        !quoteIsValid -> "A valid live bid and ask are required."
        pointSize == null || pointSize <= 0 -> "Enter a valid price-units-per-point value."
        pointValuePerLot == null || pointValuePerLot <= 0 -> "Enter a valid point value."
        stopPoints == null || stopPoints <= 0 -> "Enter a valid planned stop distance."
        startingBalance == null || startingBalance < 15 -> {
            "Paper starting balance must be at least USD 15."
        }
        else -> null
    }
    val displayCash = portfolio.cashBalance ?: startingBalance
    val paperAccent = Color(0xFF345995)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = paperAccent.copy(alpha = 0.10f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("PAPER ONLY • LOCAL SIMULATION", color = paperAccent, fontWeight = FontWeight.Black)
            Text(
                "No request from this card is sent to MT5 or the backend.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Paper cash", style = MaterialTheme.typography.labelMedium)
                    Text(
                        displayCash?.let { "USD ${it.format(2)}" } ?: "Not started",
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Realized P&L", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "USD ${portfolio.realizedPnl.format(2)}",
                        color = paperPnlColor(portfolio.realizedPnl),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text("Open paper positions: ${portfolio.openPositions.size}")

            if (selectedPosition == null) {
                Button(
                    onClick = onOpen,
                    enabled = openBlockReason == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Open paper ${analysis.direction.label} " +
                            "${riskPlan.suggestedLot?.format(2) ?: "—"} lot",
                    )
                }
                openBlockReason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "$selectedSymbol ${selectedPosition.direction.label.uppercase(Locale.US)} " +
                        "${selectedPosition.lotSize.format(2)} lot",
                    fontWeight = FontWeight.Black,
                )
                Text("Entry ${selectedPosition.entryPrice.format(4)}")
                Text(
                    "Planned stop ${selectedPosition.plannedStopPoints.format(1)} points • " +
                        "max loss USD ${selectedPosition.maximumPlannedLoss.format(2)}",
                )
                Text(
                    text = unrealizedPnl?.let { "Open P&L: USD ${it.format(2)}" }
                        ?: "Open P&L unavailable until a live quote is present.",
                    color = paperPnlColor(unrealizedPnl ?: 0.0),
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { onClose(selectedPosition) },
                    enabled = quoteIsValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close paper position at live quote")
                }
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Text("Paper replay audit", fontWeight = FontWeight.Bold)
            Text(
                performanceReport.status.label,
                color = paperAuditColor(performanceReport.status),
                fontWeight = FontWeight.Black,
            )
            if (performanceReport.tradeCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Closed", style = MaterialTheme.typography.labelMedium)
                        Text(performanceReport.tradeCount.toString(), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Historical W/L", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${performanceReport.wins}/${performanceReport.losses}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Max drawdown", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "USD ${performanceReport.maximumDrawdown.format(2)}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    "Historical win rate: " +
                        "${performanceReport.historicalWinRatePercent?.format(1) ?: "—"}% • " +
                        "Net USD ${performanceReport.reportedNetPnl.format(2)}",
                )
                performanceReport.replayedNetPnl?.let {
                    Text(
                        "Replayed net USD ${it.format(2)} matches stored trade inputs.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                performanceReport.issues.firstOrNull()?.let { issue ->
                    Text(
                        "${performanceReport.issues.size} audit issue(s). " +
                            "${issue.tradeId}: ${issue.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share paper audit CSV")
                }
                Text(
                    "Historical paper results describe this local sample; they do not forecast " +
                        "future performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (portfolio.closedTrades.isNotEmpty()) {
                Text("Recent paper history", fontWeight = FontWeight.Bold)
                portfolio.closedTrades.take(3).forEach { trade ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${trade.symbol} ${trade.direction.label} " +
                                "${trade.lotSize.format(2)} lot",
                        )
                        Text(
                            "USD ${trade.realizedPnl.format(2)}",
                            color = paperPnlColor(trade.realizedPnl),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (portfolio.openPositions.isEmpty() && portfolio.startingBalance != null) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset local paper portfolio")
                }
            }
            Text(
                "Paper P&L uses the entered point size/value and excludes commission, swaps, " +
                    "slippage, margin calls, and automatic stop execution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun paperPnlColor(value: Double): Color = when {
    value > 0 -> Color(0xFF006C4C)
    value < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun paperAuditColor(status: PaperAuditStatus): Color = when (status) {
    PaperAuditStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
    PaperAuditStatus.VERIFIED -> Color(0xFF006C4C)
    PaperAuditStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
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

private fun sharePaperCsv(context: Context, csv: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "MNM AU Seekers paper audit")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Share paper audit CSV"),
    )
}
