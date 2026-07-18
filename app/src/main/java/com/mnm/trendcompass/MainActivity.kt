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
fun TrendCompassApp() {
    var selected by remember { mutableStateOf(Timeframe.H4) }
    val previous = remember { PreviousDayCompass(high = 3367.4, low = 3321.8) }
    val results = remember { Timeframe.entries.associateWith { AnalysisEngine.analyze(it, DemoMarketData.candles(it), previous) } }
    val active = results.getValue(selected)
    val overall = results.values.sumOf { directionValue(it.direction) * it.score * it.timeframe.weight }
    val overallDirection = if (overall > 10) Direction.BULLISH else if (overall < -10) Direction.BEARISH else Direction.NEUTRAL

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = DeepGreen, surface = PanelGreen)) {
        Scaffold(containerColor = DeepGreen) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Header(overallDirection, kotlin.math.abs(overall).toInt())
                TimeframeTabs(selected) { selected = it }
                PriceChart(active)
                IndicatorCard(active)
                PreviousDayCard(active)
                TargetCard(active)
                TimeframeConsensus(results)
                Text("Prototype uses deterministic demo candles. The MarketDataProvider seam is reserved for the live feed adapter.", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun directionValue(d: Direction) = when (d) { Direction.BULLISH -> 1; Direction.BEARISH -> -1; Direction.NEUTRAL -> 0 }

@Composable
private fun Header(direction: Direction, score: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("MNM TREND COMPASS", color = SoftGold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("XAUUSD • specialized alignment engine", color = Color.White.copy(alpha = .65f))
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
private fun PriceChart(result: AnalysisResult) {
    Surface(color = PanelGreen, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Live analysis chart", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${result.candles.last().close.format()}  DEMO ●", color = Gold)
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
private fun IndicatorCard(r: AnalysisResult) = MetricPanel("Alignment components") {
    MetricRow("EMA 5", r.emaDirection.name)
    MetricRow("MA structure", r.maDirection.name)
    MetricRow("Bollinger direction", r.bbDirection.name)
    MetricRow("Fibonacci fan", r.fanText)
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
    r.targets.forEach { MetricRow(it.name, "${it.price.format()} • ${it.confidence}% reach grade") }
    MetricRow("Structural SL", r.stopLoss.format())
    Text("Forecast zones are analytical estimates, not guaranteed execution levels.", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TimeframeConsensus(results: Map<Timeframe, AnalysisResult>) = MetricPanel("Four-timeframe consensus") {
    results.forEach { (tf, r) -> MetricRow(tf.label, "${r.direction.name} • ${r.score}% • weight ${(tf.weight * 100).toInt()}%") }
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
        Text(label, color = Color.White.copy(alpha = .62f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun Double.format() = String.format("%.2f", this)
