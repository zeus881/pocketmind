package com.sanjay.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt

/** Table `personas`: the user's own personas (built-in ones live in code, not here). */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val instructions: String,
    val temperature: Float?,
    val createdAt: Long,
)

fun PersonaEntity.asModel() = Persona(id, name, instructions, temperature, builtIn = false)

/** Table `prompts`: the prompt library. */
@Entity(tableName = "prompts")
data class PromptEntity(@PrimaryKey val id: String, val title: String, val text: String, val createdAt: Long)

fun PromptEntity.asModel() = SavedPrompt(id, title, text)
