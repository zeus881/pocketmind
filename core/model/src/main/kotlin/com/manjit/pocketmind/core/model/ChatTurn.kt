package com.manjit.pocketmind.core.model

/**
 * One message in a conversation as the LLM sees it: who said it and what was said.
 *
 * WHY a separate type from the database's message entity? The engine must not know about
 * Room, and the database must not know about the engine. Both depend on this tiny type
 * instead, which lives in pure-Kotlin `core:model`.
 */
data class ChatTurn(val role: TurnRole, val text: String)

/** Who produced a turn. */
enum class TurnRole {
    USER,
    ASSISTANT,
}
