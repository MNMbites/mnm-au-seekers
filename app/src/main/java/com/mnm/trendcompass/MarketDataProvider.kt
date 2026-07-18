package com.mnm.trendcompass

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class MarketDataMode { LIVE, FALLBACK, DEMO }

data class MarketSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val previousDay: PreviousDayCompass,
    val mode: MarketDataMode,
    val providerName: String,
    val updatedAt: Long,
    val note: String? = null
)

interface MarketDataProvider {
    val name: String
    fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot
}

class DemoMarketDataProvider : MarketDataProvider {
    override val name: String = "Deterministic demo feed"

    override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot {
        val candles = DemoMarketData.candles(timeframe)
        val dailyReference = PreviousDayCompass(high = 3367.4, low = 3321.8)
        return MarketSnapshot(
            symbol = symbol,
            timeframe = timeframe,
            candles = candles,
            previousDay = dailyReference,
            mode = MarketDataMode.DEMO,
            providerName = name,
            updatedAt = candles.last().time,
            note = "Synthetic candles for interface and engine validation"
        )
    }
}

class RenderMarketDataProvider(
    private val baseUrl: String = "https://mnm-au-seekers-api.onrender.com",
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 15_000
) : MarketDataProvider {
    override val name: String = "MNM Render market feed"

    override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot {
        val endpoint = buildString {
            append(baseUrl.trimEnd('/'))
            append("/market-data?symbol=")
            append(symbol)
            append("&timeframe=")
            append(timeframe.name)
            append("&limit=160")
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MNM-AU-Seekers-Android/0.1")

            val status = connection.responseCode
            require(status in 200..299) { "Render feed returned HTTP $status" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseSnapshot(symbol, timeframe, body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSnapshot(symbol: String, timeframe: Timeframe, body: String): MarketSnapshot {
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "Render feed returned an empty response" }

        val root = if (trimmed.startsWith("[")) null else JSONObject(trimmed)
        val candleArray = when {
            root == null -> JSONArray(trimmed)
            root.has("candles") -> root.getJSONArray("candles")
            root.has("data") && root.get("data") is JSONArray -> root.getJSONArray("data")
            root.has("data") && root.get("data") is JSONObject -> root.getJSONObject("data").getJSONArray("candles")
            else -> error("Render response does not contain a candle array")
        }

        val candles = buildList {
            for (index in 0 until candleArray.length()) {
                val item = candleArray.getJSONObject(index)
                add(
                    Candle(
                        time = item.longValue("time", "timestamp", "t"),
                        open = item.doubleValue("open", "o"),
                        high = item.doubleValue("high", "h"),
                        low = item.doubleValue("low", "l"),
                        close = item.doubleValue("close", "c")
                    )
                )
            }
        }.sortedBy { it.time }

        require(candles.size >= 90) { "Render feed returned only ${candles.size} candles" }
        require(candles.all { it.high >= maxOf(it.open, it.close) && it.low <= minOf(it.open, it.close) }) {
            "Render feed contains invalid OHLC values"
        }

        val previous = root?.optJSONObject("previousDay") ?: root?.optJSONObject("previous_day")
        val previousDay = if (previous != null) {
            PreviousDayCompass(
                high = previous.doubleValue("high", "previousHigh", "pdh"),
                low = previous.doubleValue("low", "previousLow", "pdl")
            )
        } else {
            derivePreviousDay(candles)
        }

        val updatedAt = root?.optLong("updatedAt", 0L)?.takeIf { it > 0 }
            ?: root?.optLong("updated_at", 0L)?.takeIf { it > 0 }
            ?: candles.last().time

        return MarketSnapshot(
            symbol = root?.optString("symbol")?.takeIf { it.isNotBlank() } ?: symbol,
            timeframe = timeframe,
            candles = candles,
            previousDay = previousDay,
            mode = MarketDataMode.LIVE,
            providerName = name,
            updatedAt = updatedAt,
            note = "Live candles supplied by the MNM Render endpoint"
        )
    }

    private fun derivePreviousDay(candles: List<Candle>): PreviousDayCompass {
        val reference = candles.dropLast(1).takeLast(24).ifEmpty { candles.takeLast(24) }
        return PreviousDayCompass(
            high = reference.maxOf { it.high },
            low = reference.minOf { it.low }
        )
    }

    private fun JSONObject.doubleValue(vararg keys: String): Double {
        keys.forEach { key -> if (has(key)) return getDouble(key) }
        error("Missing numeric field: ${keys.joinToString()}")
    }

    private fun JSONObject.longValue(vararg keys: String): Long {
        keys.forEach { key ->
            if (has(key)) {
                val value = get(key)
                return when (value) {
                    is Number -> normalizeTimestamp(value.toLong())
                    is String -> normalizeTimestamp(value.toLong())
                    else -> error("Invalid timestamp field $key")
                }
            }
        }
        error("Missing timestamp field: ${keys.joinToString()}")
    }

    private fun normalizeTimestamp(value: Long): Long = if (value < 10_000_000_000L) value * 1000 else value
}

class FallbackMarketDataProvider(
    private val primary: MarketDataProvider,
    private val fallback: MarketDataProvider
) : MarketDataProvider {
    override val name: String = "${primary.name} with ${fallback.name} fallback"

    override fun snapshot(symbol: String, timeframe: Timeframe): MarketSnapshot = try {
        primary.snapshot(symbol, timeframe).also {
            require(it.candles.size >= 90) { "Provider returned insufficient candle history" }
        }
    } catch (error: Exception) {
        fallback.snapshot(symbol, timeframe).copy(
            mode = MarketDataMode.FALLBACK,
            providerName = fallback.name,
            note = "Primary feed unavailable: ${error.message ?: "unknown error"}"
        )
    }
}

object AppMarketData {
    val provider: MarketDataProvider = FallbackMarketDataProvider(
        primary = RenderMarketDataProvider(),
        fallback = DemoMarketDataProvider()
    )
}
