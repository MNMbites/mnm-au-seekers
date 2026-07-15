package com.mnm.auseekers.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MarketDataProviderTest {
    private val parser = MarketDataJsonParser()

    @Test
    fun parsesVersionedBackendEnvelope() {
        val feed = parser.parse(validEnvelope())

        assertEquals(FeedState.LIVE, feed.state)
        assertEquals("XAUUSD", feed.symbol)
        assertEquals(3, feed.snapshots.size)
        assertEquals(30, feed.ageSeconds)
        assertEquals(2420.10, feed.bid!!, 0.0001)
        assertEquals(2420.30, feed.ask!!, 0.0001)
    }

    @Test
    fun parsesChronologicalHistoryPayload() {
        val history = parser.parseHistory(
            """
                {"items": [
                  ${snapshot("2026-07-14T11:58:30Z", 2419.20)},
                  ${snapshot("2026-07-14T11:59:30Z", 2420.20)}
                ]}
            """.trimIndent(),
        )

        assertEquals(2, history.size)
        assertEquals(2419.10, history.first().bid, 0.0001)
        assertTrue(history.first().capturedAt < history.last().capturedAt)
    }

    @Test
    fun rejectsPayloadWithoutEveryRequiredTimeframe() {
        val invalid = validEnvelope().replace(
            "\"timeframe\": \"H4\"",
            "\"timeframe\": \"M15\"",
        )

        val error = runCatching { parser.parse(invalid) }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun fallsBackToLabelledDemoDataWhenLiveServiceFails() = runBlocking {
        val unavailable = object : MarketDataProvider {
            override suspend fun latest(symbol: String): MarketDataFeed {
                throw IOException("offline")
            }

            override suspend fun watchlist(): List<String> = throw IOException("offline")
        }
        val provider = FallbackMarketDataProvider(unavailable)

        val feed = provider.latest("XAUUSD")
        val history = provider.history("XAUUSD")

        assertEquals(FeedState.DEMO, feed.state)
        assertEquals(3, feed.snapshots.size)
        assertTrue(feed.statusMessage.contains("unavailable"))
        assertTrue(history.isEmpty())
    }

    @Test
    fun parsesWatchlistAndPreservesServerOrder() {
        val symbols = parser.parseWatchlist(
            """
                {
                  "items": [
                    {"symbol": "XAUUSD", "created_at": "2026-07-14T12:00:00Z"},
                    {"symbol": "EURUSD", "created_at": "2026-07-14T12:01:00Z"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(listOf("XAUUSD", "EURUSD"), symbols)
    }

    @Test
    fun rejectsCaseInsensitiveDuplicateWatchlistSymbols() {
        val invalid = """
            {
              "items": [
                {"symbol": "XAUUSD"},
                {"symbol": "xauusd"}
              ]
            }
        """.trimIndent()

        assertTrue(runCatching { parser.parseWatchlist(invalid) }.exceptionOrNull() is IOException)
    }

    private fun validEnvelope(): String = """
        {
          "state": "live",
          "age_seconds": 30,
          "received_at": "2026-07-14T11:59:31Z",
          "snapshot": {
            "schema_version": "1.0",
            "source": "mt5",
            "symbol": "XAUUSD",
            "captured_at": "2026-07-14T11:59:30Z",
            "bid": 2420.10,
            "ask": 2420.30,
            "timeframes": [
              ${timeframe("M15", 2420.20)},
              ${timeframe("H1", 2418.40)},
              ${timeframe("H4", 2412.80)}
            ]
          }
        }
    """.trimIndent()

    private fun snapshot(capturedAt: String, midpoint: Double): String = """
        {
          "schema_version": "1.0",
          "source": "mt5",
          "symbol": "XAUUSD",
          "captured_at": "$capturedAt",
          "bid": ${midpoint - 0.10},
          "ask": ${midpoint + 0.10},
          "timeframes": [
            ${timeframe("M15", midpoint)},
            ${timeframe("H1", midpoint)},
            ${timeframe("H4", midpoint)}
          ]
        }
    """.trimIndent()

    private fun timeframe(name: String, close: Double): String = """
        {
          "timeframe": "$name",
          "close": $close,
          "ema5": 2419.80,
          "ma9": 2419.40,
          "ma21": 2418.80,
          "ma63": 2412.20,
          "ma84": 2408.50,
          "bb_upper": 2425.00,
          "bb_lower": 2408.00,
          "rsi": 58.0,
          "macd_histogram": 0.42
        }
    """.trimIndent()
}
