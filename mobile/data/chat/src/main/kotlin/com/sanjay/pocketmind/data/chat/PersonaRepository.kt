package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.dao.PersonaDao
import com.sanjay.pocketmind.core.database.entity.PersonaEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.model.UserSettings
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Built-in and custom personas, and the prompt library. */
interface PersonaRepository {
    /** Built-in personas first, then the user's own (by name). */
    fun observePersonas(): Flow<List<Persona>>

    suspend fun persona(id: String): Persona?

    /** Creates ([id] = null) or updates a custom persona; returns its id, or null for invalid input. */
    suspend fun savePersona(id: String?, name: String, instructions: String, temperature: Float?): String?

    /** Built-in personas can't be deleted; chats that used a deleted persona fall back to the default. */
    suspend fun deletePersona(id: String)

    fun observePrompts(): Flow<List<SavedPrompt>>

    suspend fun savePrompt(id: String?, title: String, text: String): String?

    suspend fun deletePrompt(id: String)
}

/**
 * The personas that ship with the app. They are code, not database rows: an app update can
 * improve their instructions for everyone, and they can't be deleted by accident.
 * Their temperatures follow the job: precise for code, warmer for writing.
 */
object BuiltInPersonas {
    const val PREFIX = "builtin:"

    val all = listOf(
        Persona(
            id = "${PREFIX}coder",
            name = "Coder",
            instructions = "You are PocketMind in Coder mode, an expert programmer. Think through the logic first. " +
                "Give ONE complete, runnable program in a single fenced code block with its language tag, " +
                "including imports and a main function where needed. Then explain briefly and show the expected " +
                "output. When fixing code, show the full corrected code and what was wrong. Never invent APIs.",
            temperature = 0.2f,
            builtIn = true,
        ),
        Persona(
            id = "${PREFIX}tutor",
            name = "Tutor",
            instructions = "You are PocketMind in Tutor mode, a patient teacher. Explain step by step in simple " +
                "words, use a small example, and end with one short question that checks understanding. " +
                "If the learner is wrong, say so kindly and show why.",
            temperature = 0.5f,
            builtIn = true,
        ),
        Persona(
            id = "${PREFIX}writer",
            name = "Writer",
            instructions = "You are PocketMind in Writer mode, a clear and friendly writer. Match the requested " +
                "tone and length, use short paragraphs, and when asked for one text offer two alternative " +
                "openings at the end.",
            temperature = 0.8f,
            builtIn = true,
        ),
    )

    fun find(id: String): Persona? = all.firstOrNull { it.id == id }
}

internal class RoomPersonaRepository(private val dao: PersonaDao, private val clock: Clock) : PersonaRepository {

    override fun observePersonas(): Flow<List<Persona>> =
        dao.observePersonas().map { custom -> BuiltInPersonas.all + custom.map { it.asModel() } }

    override suspend fun persona(id: String): Persona? = BuiltInPersonas.find(id) ?: dao.persona(id)?.asModel()

    override suspend fun savePersona(id: String?, name: String, instructions: String, temperature: Float?): String? {
        val cleanName = name.trim().take(MAX_NAME)
        val cleanInstructions = instructions.trim().take(MAX_TEXT)
        if (cleanName.isEmpty() || cleanInstructions.isEmpty() ||
            id?.startsWith(BuiltInPersonas.PREFIX) == true
        ) {
            return null
        }
        val personaId = id ?: UUID.randomUUID().toString()
        val clamped = temperature?.coerceIn(UserSettings.MIN_TEMPERATURE, UserSettings.MAX_TEMPERATURE)
        dao.upsertPersona(PersonaEntity(personaId, cleanName, cleanInstructions, clamped, clock.nowMillis()))
        return personaId
    }

    override suspend fun deletePersona(id: String) {
        if (!id.startsWith(BuiltInPersonas.PREFIX)) dao.deletePersona(id)
    }

    override fun observePrompts(): Flow<List<SavedPrompt>> = dao.observePrompts().map { list ->
        list.map { it.asModel() }
    }

    override suspend fun savePrompt(id: String?, title: String, text: String): String? {
        val cleanTitle = title.trim().take(MAX_NAME)
        val cleanText = text.trim().take(MAX_TEXT)
        if (cleanTitle.isEmpty() || cleanText.isEmpty()) return null
        val promptId = id ?: UUID.randomUUID().toString()
        dao.upsertPrompt(PromptEntity(promptId, cleanTitle, cleanText, clock.nowMillis()))
        return promptId
    }

    override suspend fun deletePrompt(id: String) = dao.deletePrompt(id)

    private companion object {
        const val MAX_NAME = 40
        const val MAX_TEXT = 2_000
    }
}
