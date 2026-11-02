package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanjay.pocketmind.core.database.entity.PersonaEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity
import kotlinx.coroutines.flow.Flow

/** Custom personas and the prompt library. */
@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE")
    fun observePersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun persona(id: String): PersonaEntity?

    @Upsert
    suspend fun upsertPersona(persona: PersonaEntity)

    /** Chats that used the persona fall back to the default assistant. */
    @Query("UPDATE chats SET personaId = NULL WHERE personaId = :id")
    suspend fun clearPersonaFromChats(id: String)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deletePersonaRow(id: String)

    @androidx.room.Transaction
    suspend fun deletePersona(id: String) {
        clearPersonaFromChats(id)
        deletePersonaRow(id)
    }

    @Query("SELECT * FROM prompts ORDER BY title COLLATE NOCASE")
    fun observePrompts(): Flow<List<PromptEntity>>

    @Upsert
    suspend fun upsertPrompt(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deletePrompt(id: String)
}
