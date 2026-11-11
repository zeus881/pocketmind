package com.sanjay.pocketmind.data.chat

import android.content.Context
import androidx.core.net.toUri
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.DatabaseContents
import com.sanjay.pocketmind.core.database.UserDataStore
import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import com.sanjay.pocketmind.core.database.entity.PersonaEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.model.UserSettings
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class AndroidUserDataControl(
    private val context: Context,
    private val database: UserDataStore,
    private val settings: SettingsDataSource,
    private val images: ChatImageStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : UserDataControl {

    override suspend fun exportAll(uri: String): AppResult<Unit> = withContext(io) {
        try {
            val snapshot = snapshot()
            // "wt" = write + truncate: saving over an older export leaves no stale bytes at the end.
            val out = context.contentResolver.openOutputStream(uri.toUri(), "wt")
                ?: return@withContext AppResult.Failure(ErrorKind.FILE_UNREADABLE)
            ZipOutputStream(out).use { zip -> UserDataZip.write(zip, snapshot) }
            AppResult.Success(Unit)
        } catch (e: IOException) {
            AppResult.Failure(ErrorKind.FILE_UNREADABLE, e)
        } catch (e: SecurityException) {
            AppResult.Failure(ErrorKind.FILE_UNREADABLE, e)
        }
    }

    private suspend fun snapshot() = UserDataSnapshot(clock.nowMillis(), database.readAll(), settings.current())

    override suspend fun deleteAll() {
        withContext(io) { database.clearAll() } // every table, including the search index
        images.deleteAllExcept(emptySet())
        settings.resetKeepingModel()
    }
}

/** Everything that goes into an export, read in one go. */
internal data class UserDataSnapshot(val exportedAt: Long, val database: DatabaseContents, val settings: UserSettings) {
    val chats get() = database.chats
}

/** The ZIP layout, as plain functions (tested without Android's content resolver). */
internal object UserDataZip {
    const val JSON_NAME = "pocketmind-data.json"
    private const val README = "PocketMind data export\n\n" +
        "chats/   one Markdown file per chat (the version of the conversation you were looking at)\n" +
        "photos/  photos you attached to questions\n" +
        "$JSON_NAME  everything, machine-readable: every chat with all versions of every message,\n" +
        "         folders, personas, saved prompts, notes and settings.\n\n" +
        "Downloaded models are not included. This file is not encrypted: keep it somewhere safe.\n"

    fun write(zip: ZipOutputStream, data: UserDataSnapshot) {
        zip.text("README.txt", README)
        data.chats.forEachIndexed { index, (chat, messages) ->
            val visible = ConversationTree.path(messages.map { it.asModel() }, chat.currentLeafId)
            zip.text("chats/${chatFileName(index, chat.title)}", ChatMarkdown.build(chat.title, visible))
        }
        zip.text(JSON_NAME, json(data).toString(2))
        data.chats.flatMap { (_, messages) -> messages.mapNotNull { it.imagePath } }
            .map(::File)
            .filter(File::isFile)
            .forEach { photo ->
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    /** "003-How do drones fly.md": numbered (titles repeat) and safe on every file system. */
    fun chatFileName(index: Int, title: String): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(MAX_TITLE).ifBlank { "Chat" }
        return "%03d-%s.md".format(index + 1, safe)
    }

    fun json(data: UserDataSnapshot): JSONObject = JSONObject().apply {
        put("app", "PocketMind")
        put("formatVersion", 1)
        put("exportedAt", Instant.ofEpochMilli(data.exportedAt).toString())
        put("chats", JSONArray(data.chats.map { (chat, messages) -> chatJson(chat, messages) }))
        put("folders", JSONArray(data.database.folders.map { JSONObject().put("id", it.id).put("name", it.name) }))
        put("personas", JSONArray(data.database.personas.map(::personaJson)))
        put(
            "prompts",
            JSONArray(
                data.database.prompts.map { JSONObject().put("title", it.title).put("text", it.text) },
            ),
        )
        put("notes", JSONArray(data.database.notes.map(::noteJson)))
        put("settings", settingsJson(data.settings))
    }

    private fun chatJson(chat: ChatEntity, messages: List<MessageEntity>) = JSONObject().apply {
        put("id", chat.id)
        put("title", chat.title)
        put("createdAt", Instant.ofEpochMilli(chat.createdAt).toString())
        put("pinned", chat.pinned)
        put("folderId", chat.folderId ?: JSONObject.NULL)
        put("personaId", chat.personaId ?: JSONObject.NULL)
        put("currentMessageId", chat.currentLeafId ?: JSONObject.NULL)
        put("messages", JSONArray(messages.map(::messageJson)))
    }

    private fun messageJson(message: MessageEntity) = JSONObject().apply {
        put("id", message.id)
        put("parentId", message.parentId ?: JSONObject.NULL)
        put("role", message.role)
        put("text", message.text)
        put("createdAt", Instant.ofEpochMilli(message.createdAt).toString())
        put("status", message.status)
        put("photo", message.imagePath?.let { "photos/${File(it).name}" } ?: JSONObject.NULL)
        put("toolsUsed", JSONArray(message.toolsUsed?.split(",").orEmpty()))
    }

    private fun personaJson(persona: PersonaEntity) = JSONObject().apply {
        put("name", persona.name)
        put("instructions", persona.instructions)
        put("temperature", persona.temperature?.toDouble() ?: JSONObject.NULL)
    }

    private fun noteJson(note: NoteEntity) = JSONObject()
        .put("text", note.text)
        .put("createdAt", Instant.ofEpochMilli(note.createdAt).toString())

    private fun settingsJson(settings: UserSettings) = JSONObject().apply {
        put("selectedModel", settings.selectedModelId ?: JSONObject.NULL)
        put("preferGpu", settings.preferGpu)
        put("temperature", settings.temperature.toDouble())
        put("topK", settings.topK)
        put("customSystemPrompt", settings.customSystemPrompt ?: JSONObject.NULL)
        put("theme", settings.themeMode.name)
        put("dynamicColor", settings.dynamicColor)
        put("appLock", settings.appLockEnabled)
        put("allowMobileData", settings.allowMobileData)
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val MAX_TITLE = 60
}
