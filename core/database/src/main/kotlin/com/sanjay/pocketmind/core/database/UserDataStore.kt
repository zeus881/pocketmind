package com.sanjay.pocketmind.core.database

import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.FolderEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import com.sanjay.pocketmind.core.database.entity.PersonaEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Everything the user created that lives in the database, read in one go. */
data class DatabaseContents(
    val chats: List<Pair<ChatEntity, List<MessageEntity>>>,
    val folders: List<FolderEntity>,
    val personas: List<PersonaEntity>,
    val prompts: List<PromptEntity>,
    val notes: List<NoteEntity>,
)

/**
 * The whole database for "Export all my data" and "Delete all my data". It lives here so other
 * modules never need Room itself (RoomDatabase stays an implementation detail of core:database).
 */
class UserDataStore @Inject constructor(private val database: PocketMindDatabase) {
    suspend fun readAll(): DatabaseContents = DatabaseContents(
        chats = database.chatDao().getAll().map { it to database.messageDao().getAll(it.id) },
        folders = database.folderDao().observeAll().first(),
        personas = database.personaDao().observePersonas().first(),
        prompts = database.personaDao().observePrompts().first(),
        notes = database.noteDao().recent(Int.MAX_VALUE),
    )

    /** Empties every table, including the search index. Blocking: call it off the main thread. */
    fun clearAll() = database.clearAllTables()
}
