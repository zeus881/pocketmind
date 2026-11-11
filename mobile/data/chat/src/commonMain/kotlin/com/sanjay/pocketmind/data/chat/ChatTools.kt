package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.dao.NoteDao
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import com.sanjay.pocketmind.core.tools.CalculatorTool
import com.sanjay.pocketmind.core.tools.DateTimeTool
import com.sanjay.pocketmind.core.tools.LocalTool
import com.sanjay.pocketmind.core.tools.Note
import com.sanjay.pocketmind.core.tools.NoteStore
import com.sanjay.pocketmind.core.tools.NotesTool
import com.sanjay.pocketmind.core.tools.UnitConverterTool
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

/** The tools a chat can offer the model. All of them run on the phone. */
@OptIn(ExperimentalUuidApi::class) // Uuid.random(): ids of new rows (v2 used the JDK class)
class ChatTools internal constructor(noteDao: NoteDao, clock: Clock) {
    val all: List<LocalTool> = listOf(
        CalculatorTool(),
        DateTimeTool(now = { Instant.fromEpochMilliseconds(clock.nowMillis()) }),
        UnitConverterTool(),
        NotesTool(RoomNoteStore(noteDao, clock)),
    )
}

/**
 * [NoteStore] in the app's Room database, next to the chats (so exports and "delete all" include them).
 *
 * Tools are synchronous: LiteRT-LM calls them on its own background thread during generation and needs
 * the result at once. The DAO is `suspend` (Room on Kotlin Multiplatform requires it), so each call waits
 * with [runBlocking] — on that background thread, never on the main thread.
 */
internal class RoomNoteStore(private val dao: NoteDao, private val clock: Clock) : NoteStore {
    override fun save(text: String): Note {
        val note = NoteEntity(Uuid.random().toString(), text, clock.nowMillis())
        runBlocking { dao.insert(note) }
        return note.asNote()
    }

    override fun recent(limit: Int) = runBlocking { dao.recent(limit) }.map { it.asNote() }

    // Same escaping as chat search: "%" and "_" in the query are literal characters, not wildcards.
    override fun search(query: String, limit: Int) =
        runBlocking { dao.search(SearchQuery.from(query).likePattern, limit) }.map { it.asNote() }

    override fun delete(id: String) = runBlocking { dao.delete(id) } > 0

    private fun NoteEntity.asNote() = Note(id, text, createdAt)
}
