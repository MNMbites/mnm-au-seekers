package com.mnm.auseekers.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

enum class EconomicImpact {
    LOW,
    MEDIUM,
    HIGH,
}

data class EconomicCalendarEvent(
    val eventId: String,
    val source: String,
    val title: String,
    val currency: String,
    val impact: EconomicImpact,
    val scheduledAt: Instant,
)

data class EconomicCalendarFeed(
    val available: Boolean,
    val events: List<EconomicCalendarEvent>,
    val statusMessage: String,
)

interface EconomicCalendarProvider {
    suspend fun events(
        currencies: Set<String>,
        from: Instant,
        to: Instant,
    ): EconomicCalendarFeed
}

class UnavailableEconomicCalendarProvider(
    private val reason: String = "No economic-calendar service is configured.",
) : EconomicCalendarProvider {
    override suspend fun events(
        currencies: Set<String>,
        from: Instant,
        to: Instant,
    ) = EconomicCalendarFeed(false, emptyList(), reason)
}

class HttpEconomicCalendarProvider(
    baseUrl: String,
    private val parser: EconomicCalendarJsonParser = EconomicCalendarJsonParser(),
) : EconomicCalendarProvider {
    private val transport = HttpMarketDataTransport(baseUrl)

    override suspend fun events(
        currencies: Set<String>,
        from: Instant,
        to: Instant,
    ): EconomicCalendarFeed = withContext(Dispatchers.IO) {
        require(currencies.isNotEmpty()) { "At least one calendar currency is required" }
        require(to > from) { "Calendar range must be positive" }
        runCatching {
            EconomicCalendarFeed(
                available = true,
                events = parser.parse(transport.economicCalendar(currencies, from, to)),
                statusMessage = "Economic calendar loaded from the configured service.",
            )
        }.getOrElse { error ->
            EconomicCalendarFeed(
                available = false,
                events = emptyList(),
                statusMessage = error.message ?: "Economic calendar is unavailable.",
            )
        }
    }
}

class EconomicCalendarJsonParser {
    fun parse(json: String): List<EconomicCalendarEvent> = try {
        val root = JsonParser.parseString(json).calendarObject("response")
        root.calendarArray("items").map { element ->
            val event = element.calendarObject("calendar event")
            EconomicCalendarEvent(
                eventId = event.calendarString("event_id").validatedCalendarText(128),
                source = event.calendarString("source").validatedCalendarText(64),
                title = event.calendarString("title").validatedCalendarText(240),
                currency = event.calendarString("currency").trim().uppercase(Locale.US).also {
                    require(it.length == 3 && it.all(Char::isLetter)) {
                        "Calendar currency is invalid"
                    }
                },
                impact = EconomicImpact.valueOf(
                    event.calendarString("impact").uppercase(Locale.US),
                ),
                scheduledAt = Instant.parse(event.calendarString("scheduled_at")),
            )
        }
    } catch (error: JsonParseException) {
        throw IOException("Calendar service returned malformed JSON.", error)
    } catch (error: DateTimeParseException) {
        throw IOException("Calendar service returned an invalid timestamp.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("Calendar service returned an invalid payload.", error)
    } catch (error: IllegalStateException) {
        throw IOException("Calendar service returned an invalid payload.", error)
    }
}

enum class EconomicCalendarPolicy(val label: String) {
    WARN_ONLY("Warn only"),
    BLOCK_HIGH_IMPACT("Block high impact"),
}

object EconomicCalendarPolicyStore {
    fun current(context: Context): EconomicCalendarPolicy {
        val stored = preferences(context).getString(POLICY_KEY, null)
        return EconomicCalendarPolicy.entries.firstOrNull { it.name == stored }
            ?: EconomicCalendarPolicy.WARN_ONLY
    }

    fun set(context: Context, policy: EconomicCalendarPolicy) {
        preferences(context).edit().putString(POLICY_KEY, policy.name).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        "economic_calendar_settings",
        Context.MODE_PRIVATE,
    )

    private const val POLICY_KEY = "high_impact_policy"
}

fun calendarCurrenciesForSymbol(symbol: String): Set<String> {
    val normalized = symbol.filter(Char::isLetter).uppercase(Locale.US)
    if (normalized.length != 6) return setOf("USD")
    val base = normalized.take(3)
    val quote = normalized.takeLast(3)
    return buildSet {
        if (base != "XAU") add(base)
        add(quote)
    }
}

private fun com.google.gson.JsonElement.calendarObject(label: String): JsonObject =
    takeIf { isJsonObject }?.asJsonObject
        ?: throw IllegalArgumentException("$label must be an object")

private fun JsonObject.calendarArray(name: String): com.google.gson.JsonArray =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun JsonObject.calendarString(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun String.validatedCalendarText(maxLength: Int): String = trim().also {
    require(it.isNotEmpty() && it.length <= maxLength) { "Calendar text is invalid" }
}
