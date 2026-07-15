package com.mnm.auseekers.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HttpMarketDataProvider(
    baseUrl: String,
    private val parser: MarketDataJsonParser = MarketDataJsonParser(),
) : MarketDataProvider {
    private val transport = HttpMarketDataTransport(baseUrl)

    override suspend fun latest(symbol: String): MarketDataFeed = withContext(Dispatchers.IO) {
        parser.parse(transport.latest(symbol))
    }

    override suspend fun watchlist(): List<String> = withContext(Dispatchers.IO) {
        parser.parseWatchlist(transport.watchlist())
    }

    override suspend fun history(
        symbol: String,
        limit: Int,
    ): List<HistoricalMarketDataPoint> = withContext(Dispatchers.IO) {
        require(limit in 2..500) { "Market-data history limit must be from 2 to 500" }
        parser.parseHistory(transport.history(symbol, limit))
    }
}

internal class HttpMarketDataTransport(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri?.scheme == "https" || uri?.scheme == "http") {
            "Market-data base URL must use HTTP or HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Market-data base URL must include a host" }
        require(uri.userInfo == null) { "Credentials must not be embedded in the market-data URL" }
    }

    fun latest(symbol: String): String {
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString())
        return get("/api/v1/market-data/$encodedSymbol")
    }

    fun watchlist(): String = get("/api/v1/watchlist")

    fun history(symbol: String, limit: Int): String {
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString())
        return get("/api/v1/market-data/$encodedSymbol/history?limit=$limit")
    }

    private fun get(path: String): String {
        val connection = URI("$normalizedBaseUrl$path")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Live service returned HTTP ${connection.responseCode}.")
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}
