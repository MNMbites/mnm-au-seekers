package com.mnm.trendcompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private val DeepGreen = Color(0xFF071B14)
private val PanelGreen = Color(0xFF0D2A20)
private val Gold = Color(0xFFD5AF46)
private val SoftGold = Color(0xFFF3D77D)
private val Bull = Color(0xFF65D6A6)
private val Bear = Color(0xFFFF8C82)
private val Grid = Color(0xFF24463A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrendCompassApp() }
    }
}

@Composable
fun TrendCompassApp(provider: MarketDataProvider = AppMarketData.provider) {
    var selected by remember { mutableStateOf(Timeframe.H4) }
    val demoProvider = remember { DemoMarketDataProvider() }
    val initialSnapshots = remember {
        Timeframe.entries.associateWith { demoProvider.snapshot("XAUUSD", it) }
    }
    val snapshots by produceState(initialValue = initialSnapshots, provider) {
        value = withContext(Dispatchers.IO) {
            Timeframe.entries.associateWith { provider.snapshot("XAUUSD", it) }
        }
    }
    val results = remember(snapshots) {
        snapshots.mapValues { (_, snapshot) ->
            AnalysisEngine.analyze(snapshot.timeframe, snapshot.candles, snapshot.previousDay)
        }
    }
    val active = results.getValue(selected)
    val activeSnapshot = snapshots.getValue(selected)
    val overall = results.values.sumOf { directionValue(it.direction) * it.score * it.timeframe.weight }
    val overallDirection = if (overall > 10) Direction.BULLISH else if (overall < -10) Direction.BEARISH else Direction.NEUTRAL

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = DeepGreen, surface = PanelGreen)) {
        Scaffold(containerColor = DeepGreen) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Header(overallDirection, kotlin.math.abs(overall).toInt(), activeSnapshot)
                TimeframeTabs(selected) { selected = it }
                ExecutiveSummaryCard(active)
                PriceChart(active, activeSnapshot)
                IndicatorCard(active)
                PreviousDayCard(active)
                TargetCard(active)
                TimeframeConsensus(results)
                SourceCard(activeSnapshot)
            }
        }
    }
}

private fun directionValue(d: Direction) = when (d) { Direction.BULLISH -> 1; Direction.BEARISH -> -1; Direction.NEUTRAL -> 0 }

@Composable
private fun Header(direction: Direction, score: Int, snapshot: MarketSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("MNM TREND COMPASS", color = SoftGold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("${snapshot.symbol} • specialized alignment engine", color = Color.White.copy(alpha = .65f))
        }
        DirectionBadge(direction, score)
    }
}

@Composable
private fun DirectionBadge(direction: Direction, score: Int) {
    val color = when (direction) { Direction.BULLISH -> Bull; Direction.BEARISH -> Bear; Direction.NEUTRAL -> Gold }
    Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            Text(direction.name, color = color, fontWeight = FontWeight.Bold)
            Text("$score% consensus", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TimeframeTabs(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Timeframe.entries.forEach { tf ->
            FilterChip(selected = selected == tf, onClick = { onSelect(tf) }, label = { Text(tf.label) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExecutiveSummaryCard(r: AnalysisResult) {
    val summary = r.executiveSummary
    val accent = when (r.direction) { Direction.BULLISH -> Bull; Direction.BEARISH -> Bear; Direction.NEUTRAL -> Gold }
    Surface(color = accent.copy(alpha = .10f), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("EXECUTIVE SUMMARY", color = SoftGold, fontWeight = FontWeight.Black)
                Text("${summary.confidence}%", color = accent, fontWeight = FontWeight.Black)
            }
            Text(summary.headline, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(summary.conclusion, color = Color.White.copy(alpha = .78f))
            HorizontalDivider(color = Color.White.copy(alpha = .10f))
            Text("Fact chain", color = SoftGold, fontWeight = FontWeight.SemiBold)
            summary.facts.forEachIndexed { index, fact ->
                Text("${index + 1}. $fact", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PriceChart(result: AnalysisResult, snapshot: MarketSnapshot) {
    val modeColor = when (snapshot.mode) { MarketDataMode.LIVE -> Bull; MarketDataMode.FALLBACK -> Bear; MarketDataMode.DEMO -> Gold }
    Surface(color = PanelGreen, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Analysis chart", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${result.candles.last().close.format()}  ${snapshot.mode.name} ●", color = modeColor)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(320.dp).background(DeepGreen, RoundedCornerShape(14.dp))) {
                val candles = result.candles.takeLast(70)
                val offset = result.candles.size - candles.size
                val values = candles.flatMap { listOf(it.high, it.low) } + listOf(result.previousDay.high, result.previousDay.low)
                val top = values.max() + 2.0
                val bottom = values.min() - 2.0
                fun y(price: Double) = ((top - price) / (top - bottom) * size.height).toFloat()
                fun x(i: Int) = (i + .5f) * size.width / candles.size

                repeat(5) { i -> drawLine(Grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f) }
                drawLine(Gold.copy(alpha = .75f), Offset(0f, y(result.previousDay.midpoint)), Offset(size.width, y(result.previousDay.midpoint)), 2f)
                drawLine(Gold.copy(alpha = .35f), Offset(0f, y(result.previousDay.high)), Offset(size.width, y(result.previousDay.high)), 1f)
                drawLine(Gold.copy(alpha = .35f), Offset(0f, y(result.previousDay.low)), Offset(size.width, y(result.previousDay.low)), 1f)

                fun line(series: List<Double>, color: Color, width: Float = 2f) {
                    val visible = series.drop(offset)
                    val path = Path()
                    visible.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                    drawPath(path, color, style = Stroke(width))
                }
                line(result.bbUpper, Gold.copy(alpha = .55f), 1.5f)
                line(result.bbLower, Gold.copy(alpha = .55f), 1.5f)
                line(result.ema5, SoftGold, 2.6f)
                line(result.ma21, Color.White.copy(alpha = .75f), 2f)
                line(result.ma63, Color.Cyan.copy(alpha = .55f), 1.5f)
                line(result.ma84, Color.Magenta.copy(alpha = .45f), 1.5f)

                candles.forEachIndexed { i, c ->
                    val color = if (c.close >= c.open) Bull else Bear
                    val cx = x(i); val half = max(2f, size.width / candles.size * .28f)
                    drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), 1.2f)
                    drawRect(color, topLeft = Offset(cx - half, min(y(c.open), y(c.close))), size = androidx.compose.ui.geometry.Size(half * 2, max(2f, kotlin.math.abs(y(c.open) - y(c.close)))))
                }

                val fanStartIndex = result.candles.indexOf(result.fan.start) - offset
                if (fanStartIndex in candles.indices) {
                    val sx = x(fanStartIndex); val sy = y(if (result.fan.bullish) result.fan.start.low else result.fan.start.high)
                    result.fan.ratios.forEach { ratio ->
                        val endPrice = result.fan.start.close + (result.fan.end.close - result.fan.start.close) * ratio
                        drawLine(Gold.copy(alpha = .35f), Offset(sx, sy), Offset(size.width, y(endPrice)), 1.2f)
                    }
                }
            }
            Text("EMA5 • MA21/63/84 • BB21/2 • previous-day midpoint • adaptive Fib fan", color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun IndicatorCard(r: AnalysisResult) = MetricPanel("Back-to-back evidence") {
    MetricRow("EMA 5", r.emaDirection.name)
    MetricRow("MA structure", r.maDirection.name)
    MetricRow("Bollinger direction", r.bbDirection.name)
    MetricRow("Fan phase", r.fanText)
    MetricRow("Retracement depth", "${r.fanSummary.retracementDepth}%")
    MetricRow("Fan respect", "${r.fanSummary.respectScore}%")
    MetricRow("Follow-through", "${r.fanSummary.followThroughScore}%")
    MetricRow("Breakdown risk", "${r.fanSummary.breakdownRisk}%")
    MetricRow("Current fan zone", r.fanSummary.currentZone)
    MetricRow("Timeframe score", "${r.score}% ${r.direction.name}")
}

@Composable
private fun PreviousDayCard(r: AnalysisResult) = MetricPanel("Previous-day compass") {
    MetricRow("PDH", r.previousDay.high.format())
    MetricRow("Midpoint", r.previousDay.midpoint.format())
    MetricRow("PDL", r.previousDay.low.format())
    MetricRow("Current zone", r.compassText)
}

@Composable
private fun TargetCard(r: AnalysisResult) = MetricPanel("Forecast travel map") {
    MetricRow("Fan support", r.fanSummary.nearestSupportRay.format())
    MetricRow("Fan resistance", r.fanSummary.nearestResistanceRay.format())
    MetricRow("Fan invalidation", r.fanSummary.invalidationPrice.format())
    r.targets.forEach { MetricRow(it.name, "${it.price.format()} • ${it.confidence}% reach grade") }
    MetricRow("Structural SL", r.stopLoss.format())
    Text("Forecast zones are analytical estimates, not guaranteed execution levels.", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TimeframeConsensus(results: Map<Timeframe, AnalysisResult>) = MetricPanel("Four-timeframe consensus") {
    results.forEach { (tf, r) -> MetricRow(tf.label, "${r.direction.name} • ${r.fanSummary.phase.name.replace('_', ' ')} • ${r.score}%") }
}

@Composable
private fun SourceCard(snapshot: MarketSnapshot) = MetricPanel("Market data source") {
    MetricRow("Provider", snapshot.providerName)
    MetricRow("Mode", snapshot.mode.name)
    MetricRow("Candles", snapshot.candles.size.toString())
    snapshot.note?.let { Text(it, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MetricPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PanelGreen, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = SoftGold, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = .62f), modifier = Modifier.weight(.45f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.55f))
    }
}

private fun Double.format() = String.format("%.2f", this)
