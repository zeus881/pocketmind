package com.sanjay.pocketmind.core.tools

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Today's date and time from the phone's clock, and date arithmetic. A model only knows the world
 * up to its training data; without this it guesses what "today" is.
 */
class DateTimeTool(
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : LocalTool {
    override val name = NAME
    override val description =
        "Gives the current date, time and weekday on the user's phone, or does date arithmetic. Use it for any " +
            "question about today, now, a weekday, or how many days are between two dates."
    override val parameters = listOf(
        ToolParameter(
            "operation",
            ParameterType.STRING,
            "now: current date and time; days_between: days from date to other_date; add_days: date plus days",
            allowed = listOf(NOW, DAYS_BETWEEN, ADD_DAYS),
        ),
        ToolParameter("date", ParameterType.STRING, "A date as YYYY-MM-DD (default: today)", required = false),
        ToolParameter("other_date", ParameterType.STRING, "The second date for days_between", required = false),
        ToolParameter("days", ParameterType.INTEGER, "Days to add for add_days (negative goes back)", required = false),
    )

    override fun run(arguments: JsonObject): JsonObject {
        val zoneId = zone()
        val today = now().atZone(zoneId).toLocalDate()
        return when (val operation = arguments.text("operation") ?: NOW) {
            NOW -> buildJsonObject {
                val time = now().atZone(zoneId)
                put("date", time.toLocalDate().toString())
                put("weekday", time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                put("time", time.format(DateTimeFormatter.ofPattern("HH:mm")))
                put("time_zone", zoneId.id)
            }

            DAYS_BETWEEN -> {
                val from = arguments.date("date") ?: today
                val to = arguments.date("other_date") ?: throw ToolArgumentException("'other_date' is missing")
                buildJsonObject { put("days", ChronoUnit.DAYS.between(from, to)) }
            }

            ADD_DAYS -> {
                val days = arguments.requireNumber("days").toLong()
                val result = (arguments.date("date") ?: today).plusDays(days)
                buildJsonObject {
                    put("date", result.toString())
                    put("weekday", result.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                }
            }

            else -> throw ToolArgumentException("unknown operation '$operation'")
        }
    }

    private fun JsonObject.date(name: String): LocalDate? = text(name)?.let { raw ->
        try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            throw ToolArgumentException("'$name' must be a date like 2026-11-03, got '$raw' (${e.errorIndex})")
        }
    }

    companion object {
        const val NAME = "date_time"
        private const val NOW = "now"
        private const val DAYS_BETWEEN = "days_between"
        private const val ADD_DAYS = "add_days"
    }
}
