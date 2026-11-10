package com.sanjay.pocketmind.core.tools

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Today's date and time from the phone's clock, and date arithmetic. A model only knows the world
 * up to its training data; without this it guesses what "today" is.
 *
 * V3: kotlinx-datetime (0.8.0) instead of java.time, so the same tool runs on iOS. Output is unchanged:
 * ISO date, English weekday name, 24-hour "HH:mm", IANA time-zone id.
 */
class DateTimeTool(
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : LocalTool {
    override val name = NAME
    override val description =
        "Gives the current date, time and weekday on the user's phone, or does date arithmetic. Use it ONLY for " +
            "questions about today, now, a weekday, or days between dates; never for other questions."
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
        val timeZone = zone()
        val today = now().toLocalDateTime(timeZone).date
        return when (val operation = arguments.text("operation") ?: NOW) {
            NOW -> buildJsonObject {
                val time = now().toLocalDateTime(timeZone)
                put("date", time.date.toString())
                put("weekday", time.dayOfWeek.englishName())
                put("time", "${time.hour.twoDigits()}:${time.minute.twoDigits()}")
                put("time_zone", timeZone.id)
            }

            DAYS_BETWEEN -> {
                val from = arguments.date("date") ?: today
                val to = arguments.date("other_date") ?: throw ToolArgumentException("'other_date' is missing")
                buildJsonObject { put("days", from.daysUntil(to)) }
            }

            ADD_DAYS -> {
                val days = arguments.requireNumber("days").longValue(exactRequired = false)
                val result = (arguments.date("date") ?: today).plus(days, DateTimeUnit.DAY)
                buildJsonObject {
                    put("date", result.toString())
                    put("weekday", result.dayOfWeek.englishName())
                }
            }

            else -> throw ToolArgumentException("unknown operation '$operation'")
        }
    }

    private fun JsonObject.date(name: String): LocalDate? = text(name)?.let { raw ->
        try {
            LocalDate.parse(raw)
        } catch (e: IllegalArgumentException) {
            // kotlinx-datetime's parse error (DateTimeFormatException) is an IllegalArgumentException.
            throw ToolArgumentException("'$name' must be a date like 2026-11-03, got '$raw'", e)
        }
    }

    /** "TUESDAY" → "Tuesday": what java.time's English full name gave in v2. */
    private fun DayOfWeek.englishName() = name.lowercase().replaceFirstChar { it.uppercase() }

    private fun Int.twoDigits() = toString().padStart(2, '0')

    companion object {
        const val NAME = "date_time"
        private const val NOW = "now"
        private const val DAYS_BETWEEN = "days_between"
        private const val ADD_DAYS = "add_days"
    }
}
