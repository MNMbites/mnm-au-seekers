package com.mnm.auseekers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mnm.auseekers.domain.DemoMarket
import com.mnm.auseekers.domain.Direction
import com.mnm.auseekers.domain.MarketAnalysis
import com.mnm.auseekers.domain.RiskCalculator
import com.mnm.auseekers.domain.RiskPlan
import com.mnm.auseekers.domain.RiskProfile
import com.mnm.auseekers.domain.RiskRequest
import com.mnm.auseekers.domain.SignalEngine
import com.mnm.auseekers.domain.TimeframeSignal
import com.mnm.auseekers.domain.TradingMode
import com.mnm.auseekers.ui.theme.MnmAuSeekersTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MnmAuSeekersTheme {
                MnmAuSeekersApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnmAuSeekersApp() {
    var mode by rememberSaveable { mutableStateOf(TradingMode.PRIMARY) }
    var profile by rememberSaveable { mutableStateOf(RiskProfile.SAFE) }
    var balance by rememberSaveable { mutableStateOf("15.00") }
    var stopPoints by rememberSaveable { mutableStateOf("50") }
    var pointValue by rememberSaveable { mutableStateOf("0.10") }

    val analysis = remember(mode) {
        SignalEngine().analyse(DemoMarket.snapshots, mode)
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
                            "${DemoMarket.symbol} • analysis only",
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
            item { DemoDataBanner() }
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
private fun DemoDataBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "DEMO DATA • Live MT5 prices and trade execution are not connected.",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
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
