package com.sanjay.pocketmind.core.tools

import java.math.BigDecimal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A function the model may call while it answers ("tool calling"). The model writes the call
 * (a name and JSON arguments), the runtime runs it, and the model reads the JSON result before
 * writing the rest of its answer.
 *
 * Every tool runs on the phone and never touches the network. Tools exist for what small models
 * get wrong on their own: exact arithmetic, today's date, unit conversions, remembering notes.
 */
interface LocalTool {
    /** The id the model calls it by (snake_case). Also saved with the answers that used it. */
    val name: String

    /** Tells the model WHEN to use the tool. Small models follow short, concrete descriptions best. */
    val description: String

    val parameters: List<ToolParameter>

    /**
     * Runs the tool with the model's [arguments] and returns a JSON object for the model to read.
     * Called on the engine's thread in the middle of a generation: it must be quick.
     * Throws [ToolArgumentException] for arguments it can't use.
     */
    fun run(arguments: JsonObject): JsonObject
}

enum class ParameterType(val json: String) {
    STRING("string"),
    NUMBER("number"),
    INTEGER("integer"),
}

data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    /** The only values allowed (a JSON-schema "enum"), or empty for any value. */
    val allowed: List<String> = emptyList(),
)

/** The model's arguments can't be used; the model reads the message and can try again. */
class ToolArgumentException(message: String) : IllegalArgumentException(message)

/**
 * The JSON description of a tool, in the shape LiteRT-LM's own reflection tools produce
 * (checked in the 0.19 bytecode): `{"name", "description", "parameters": {"type": "object",
 * "properties": {…}, "required": […]}}`. The runtime puts it into the model's chat template.
 */
object ToolSchema {
    fun describe(tool: LocalTool): String = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                tool.parameters.forEach { parameter ->
                    putJsonObject(parameter.name) {
                        put("type", parameter.type.json)
                        put("description", parameter.description)
                        if (parameter.allowed.isNotEmpty()) {
                            putJsonArray("enum") { parameter.allowed.forEach { add(it) } }
                        }
                    }
                }
            }
            putJsonArray("required") { tool.parameters.filter { it.required }.forEach { add(it.name) } }
        }
    }.toString()
}

/**
 * Runs a tool with the JSON the model wrote. A bad call never crashes the generation: it becomes
 * `{"error": "…"}`, which the model reads like any other result (and usually corrects).
 */
object ToolRunner {
    fun run(tool: LocalTool, argumentsJson: String): String = try {
        val arguments = Json.parseToJsonElement(argumentsJson.ifBlank { "{}" }).jsonObject
        tool.run(arguments).toString()
    } catch (e: SerializationException) {
        error("arguments are not valid JSON (${e.message})")
    } catch (e: IllegalArgumentException) { // also ToolArgumentException and NumberFormatException
        error(e.message ?: "invalid arguments")
    } catch (e: ArithmeticException) {
        error(e.message ?: "arithmetic error")
    }

    private fun error(message: String): String = buildJsonObject { put("error", message) }.toString()
}

/** A text argument; models sometimes send numbers for text fields, which is accepted. */
fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

fun JsonObject.requireText(name: String): String = text(name) ?: throw ToolArgumentException("'$name' is missing")

/** A number argument, exact. Accepts JSON numbers and numbers written as text ("12.5", "1,000"). */
fun JsonObject.number(name: String): BigDecimal? = text(name)?.let { raw ->
    raw.replace(",", "").replace("_", "").toBigDecimalOrNull()
        ?: throw ToolArgumentException("'$name' must be a number, got '$raw'")
}

fun JsonObject.requireNumber(name: String): BigDecimal =
    number(name) ?: throw ToolArgumentException("'$name' is missing")
