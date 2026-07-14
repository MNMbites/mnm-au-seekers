package com.mnm.auseekers.data

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mnm.auseekers.domain.MarketSnapshot
import com.mnm.auseekers.domain.Timeframe
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

class MarketDataJsonParser {
    fun parse(json: String): MarketDataFeed = try {
        val root = JsonParser.parseString(json).requiredObject("response")
        val state = root.requiredString("state").uppercase(Locale.US).toFeedState()
        val ageSeconds = root.requiredInt("age_seconds")
        val snapshot = root.requiredObject("snapshot")
        val symbol = snapshot.requiredString("symbol")
        val capturedAt = Instant.parse(snapshot.requiredString("captured_at"))
        val timeframes = snapshot.requiredArray("timeframes").map { element ->
            element.requiredObject("timeframe").toMarketSnapshot()
        }

        require(timeframes.map { it.timeframe }.toSet() == Timeframe.entries.toSet()) {
            "Market data must include M15, H1, and H4 exactly once"
        }

        MarketDataFeed(
            symbol = symbol,
            capturedAt = capturedAt,
            state = state,
            snapshots = timeframes,
            bid = snapshot.requiredDouble("bid"),
            ask = snapshot.requiredDouble("ask"),
            ageSeconds = ageSeconds,
            statusMessage = when (state) {
                FeedState.LIVE -> "Live MT5 snapshot received from the authorised bridge."
                FeedState.STALE -> "The latest MT5 snapshot is stale; verify bridge connectivity."
                else -> error("Backend state must be live or stale")
            },
        )
    } catch (error: IOException) {
        throw error
    } catch (error: JsonParseException) {
        throw IOException("Live service returned malformed JSON.", error)
    } catch (error: DateTimeParseException) {
        throw IOException("Live service returned an invalid timestamp.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("Live service returned an invalid market-data payload.", error)
    } catch (error: IllegalStateException) {
        throw IOException("Live service returned an invalid market-data payload.", error)
    }

    fun parseWatchlist(json: String): List<String> = try {
        val root = JsonParser.parseString(json).requiredObject("response")
        val symbols = root.requiredArray("items").map { element ->
            element.requiredObject("watchlist item")
                .requiredString("symbol")
                .validatedSymbol()
        }
        require(symbols.distinctBy { it.lowercase(Locale.US) }.size == symbols.size) {
            "Watchlist symbols must be unique"
        }
        symbols
    } catch (error: JsonParseException) {
        throw IOException("Live service returned malformed watchlist JSON.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("Live service returned an invalid watchlist payload.", error)
    } catch (error: IllegalStateException) {
        throw IOException("Live service returned an invalid watchlist payload.", error)
    }

    private fun JsonObject.toMarketSnapshot(): MarketSnapshot = MarketSnapshot(
        timeframe = Timeframe.valueOf(requiredString("timeframe")),
        close = requiredDouble("close"),
        ema5 = requiredDouble("ema5"),
        ma9 = requiredDouble("ma9"),
        ma21 = requiredDouble("ma21"),
        ma63 = requiredDouble("ma63"),
        ma84 = requiredDouble("ma84"),
        bbUpper = requiredDouble("bb_upper"),
        bbLower = requiredDouble("bb_lower"),
        rsi = requiredDouble("rsi"),
        macdHistogram = requiredDouble("macd_histogram"),
    )

    private fun String.toFeedState(): FeedState = when (this) {
        "LIVE" -> FeedState.LIVE
        "STALE" -> FeedState.STALE
        else -> throw IllegalArgumentException("Unsupported backend feed state")
    }
}

private fun String.validatedSymbol(): String = trim().also { symbol ->
    require(symbol.length in 2..32 && symbol.none(Char::isWhitespace)) {
        "Invalid watchlist symbol"
    }
}

private fun com.google.gson.JsonElement.requiredObject(label: String): JsonObject =
    takeIf { isJsonObject }?.asJsonObject
        ?: throw IllegalArgumentException("$label must be an object")

private fun JsonObject.requiredObject(name: String): JsonObject =
    get(name)?.requiredObject(name)
        ?: throw IllegalArgumentException("Missing $name")

private fun JsonObject.requiredArray(name: String): com.google.gson.JsonArray =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun JsonObject.requiredString(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun JsonObject.requiredDouble(name: String): Double =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
        ?.takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun JsonObject.requiredInt(name: String): Int =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        ?.takeIf { it >= 0 }
        ?: throw IllegalArgumentException("Missing or invalid $name")
