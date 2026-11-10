package com.sanjay.pocketmind.core.tools

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A short note the user asked the assistant to remember. */
data class Note(val id: String, val text: String, val createdAt: Long)

/**
 * Where notes live (Room, in the app). Blocking calls on purpose: a tool runs inside the model's
 * generation on a background thread and must return its result directly.
 */
interface NoteStore {
    fun save(text: String): Note

    /** Newest first. */
    fun recent(limit: Int): List<Note>

    /** Notes containing [query], newest first. */
    fun search(query: String, limit: Int): List<Note>

    /** True if a note with this id existed. */
    fun delete(id: String): Boolean
}

/**
 * "Remember that my locker code is 4312" → the model saves a note; later "what is my locker code?"
 * → it searches the notes. Notes stay on the phone like chats do.
 */
class NotesTool(private val store: NoteStore, private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) :
    LocalTool {
    override val name = NAME
    override val description =
        "Saves and finds the user's personal notes on the phone. Use save when the user asks you to remember " +
            "something, search or list when they ask what they told you before, delete when they ask to forget a " +
            "note. Use it ONLY for those requests, never to answer other questions."
    override val parameters = listOf(
        ToolParameter("action", ParameterType.STRING, "What to do", allowed = listOf(SAVE, SEARCH, LIST, DELETE)),
        ToolParameter("text", ParameterType.STRING, "The note to save (for save)", required = false),
        ToolParameter("query", ParameterType.STRING, "Words to look for (for search)", required = false),
        ToolParameter("id", ParameterType.STRING, "The id of the note (for delete)", required = false),
    )

    override fun run(arguments: JsonObject): JsonObject = when (val action = arguments.requireText("action")) {
        SAVE -> {
            val text = arguments.requireText("text").take(MAX_NOTE_LENGTH)
            val note = store.save(text)
            buildJsonObject {
                put("saved", true)
                put("id", note.id)
            }
        }

        SEARCH -> notes(store.search(arguments.requireText("query"), MAX_RESULTS))

        LIST -> notes(store.recent(MAX_RESULTS))

        DELETE -> buildJsonObject { put("deleted", store.delete(arguments.requireText("id"))) }

        else -> throw ToolArgumentException("unknown action '$action'")
    }

    private fun notes(found: List<Note>): JsonObject = buildJsonObject {
        putJsonArray("notes") {
            found.forEach { note ->
                addJsonObject {
                    put("id", note.id)
                    put("text", note.text)
                    put("date", Instant.fromEpochMilliseconds(note.createdAt).toLocalDateTime(zone()).date.toString())
                }
            }
        }
    }

    companion object {
        const val NAME = "notes"
        private const val SAVE = "save"
        private const val SEARCH = "search"
        private const val LIST = "list"
        private const val DELETE = "delete"

        /** Keeps one note (and the model's context when notes are listed) small. */
        const val MAX_NOTE_LENGTH = 1000
        private const val MAX_RESULTS = 20
    }
}
