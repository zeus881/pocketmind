package com.sanjay.pocketmind.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Date, unit and notes tools, and the JSON schema the model sees: all without a model. */
class ToolsTest {
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun call(tool: LocalTool, json: String): JsonObject =
        Json.parseToJsonElement(ToolRunner.run(tool, json)).jsonObject

    // Tuesday 3 November 2026, 14:05 in India.
    private val dates = DateTimeTool(
        now = { Instant.parse("2026-11-03T08:35:00Z") },
        zone = { TimeZone.of("Asia/Kolkata") },
    )

    @Test
    fun nowGivesThePhonesLocalDateWeekdayAndTime() {
        val now = call(dates, """{"operation": "now"}""")

        assertEquals("2026-11-03", now.str("date"))
        assertEquals("Tuesday", now.str("weekday"))
        assertEquals("14:05", now.str("time"))
        assertEquals("Asia/Kolkata", now.str("time_zone"))
    }

    @Test
    fun dateArithmetic() {
        assertEquals("52", call(dates, """{"operation": "days_between", "other_date": "2026-12-25"}""").str("days"))
        val later = call(dates, """{"operation": "add_days", "date": "2026-02-27", "days": 2}""")
        assertEquals("2026-03-01", later.str("date"))
        assertEquals("Sunday", later.str("weekday"))
        val wrong = call(dates, """{"operation": "add_days", "date": "3/11/26", "days": 1}""")
        assertTrue(wrong.str("error").contains("date"))
    }

    private val units = UnitConverterTool()

    private fun convert(value: String, from: String, to: String) =
        call(units, """{"value": $value, "from": "$from", "to": "$to"}""").str("result")

    @Test
    fun conversionsUseExactFactors() {
        assertEquals("6.21371192237334", convert("10", "km", "mi"))
        assertEquals("2.54", convert("1", "inch", "cm"))
        assertEquals("1", convert("0.45359237", "kg", "lb"))
        assertEquals("100", convert("27.7777777777778", "m/s", "km/h").take(3))
        assertEquals("3.785411784", convert("1", "gal", "l"))
        assertEquals("1000", convert("1", "GB", "MB"))
    }

    @Test
    fun temperaturesConvertWithOffsets() {
        assertEquals("212", convert("100", "c", "f"))
        assertEquals("37", convert("98.6", "fahrenheit", "celsius"))
        assertEquals("0", convert("273.15", "k", "c"))
        assertEquals("-40", convert("-40", "°F", "°C"))
    }

    @Test
    fun wrongUnitsAreExplainedToTheModel() {
        assertEquals("unknown unit 'parsec'", call(units, """{"value": 1, "from": "parsec", "to": "m"}""").str("error"))
        assertEquals(
            "can't convert length (m) to mass (kg)",
            call(units, """{"value": 1, "from": "m", "to": "kg"}""").str("error"),
        )
        val notANumber = call(units, """{"value": "ten", "from": "m", "to": "km"}""")
        assertEquals("'value' must be a number, got 'ten'", notANumber.str("error"))
    }

    private class MemoryNotes : NoteStore {
        val notes = mutableListOf<Note>()

        override fun save(text: String) = Note("n${notes.size + 1}", text, 1_762_158_900_000).also { notes += it }

        override fun recent(limit: Int) = notes.reversed().take(limit)

        override fun search(query: String, limit: Int) =
            recent(limit).filter { it.text.contains(query, ignoreCase = true) }

        override fun delete(id: String) = notes.removeAll { it.id == id }
    }

    @Test
    fun notesAreSavedFoundListedAndDeleted() {
        val store = MemoryNotes()
        val tool = NotesTool(store, zone = { TimeZone.of("Asia/Kolkata") })

        assertEquals("n1", call(tool, """{"action": "save", "text": "Locker code is 4312"}""").str("id"))
        call(tool, """{"action": "save", "text": "Buy milk"}""")
        val found = call(tool, """{"action": "search", "query": "locker"}""").getValue("notes").jsonArray
        assertEquals(listOf("Locker code is 4312"), found.map { it.jsonObject.str("text") })
        assertEquals("2025-11-03", found.single().jsonObject.str("date"))
        assertEquals(2, call(tool, """{"action": "list"}""").getValue("notes").jsonArray.size)
        assertEquals("true", call(tool, """{"action": "delete", "id": "n1"}""").str("deleted"))
        assertEquals(listOf("Buy milk"), store.notes.map { it.text })
        assertEquals("'text' is missing", call(tool, """{"action": "save"}""").str("error"))
    }

    @Test
    fun theSchemaListsParametersRequiredOnesAndAllowedValues() {
        val schema = Json.parseToJsonElement(ToolSchema.describe(units)).jsonObject

        assertEquals("unit_converter", schema.str("name"))
        val parameters = schema.getValue("parameters").jsonObject
        assertEquals("object", parameters.str("type"))
        val required = parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("value", "from", "to"), required)
        val value = parameters.getValue("properties").jsonObject.getValue("value").jsonObject
        assertEquals("number", value.str("type"))

        val operation = Json.parseToJsonElement(ToolSchema.describe(dates)).jsonObject
            .getValue("parameters").jsonObject.getValue("properties").jsonObject.getValue("operation").jsonObject
        val allowed = operation.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("now", "days_between", "add_days"), allowed)
    }
}
