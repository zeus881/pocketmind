package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message

/**
 * Pure functions over a chat's message TREE (see `Chat.currentLeafId`). No database, no
 * Android: everything here is tested with plain lists.
 *
 * Messages arrive sorted oldest first (createdAt, then id), the order the DAO returns.
 */
object ConversationTree {

    /**
     * The visible conversation: the path from [leafId] up to the first message, oldest first.
     * A missing or unknown leaf (should not happen after the v5 migration) falls back to the
     * newest message, so a chat is never shown empty by mistake. Cycles are cut defensively.
     */
    fun path(messages: List<Message>, leafId: String?): List<Message> {
        if (messages.isEmpty()) return emptyList()
        val byId = messages.associateBy { it.id }
        var current = byId[leafId] ?: messages.last()
        val path = ArrayDeque<Message>()
        val seen = HashSet<String>()
        while (seen.add(current.id)) {
            path.addFirst(current)
            current = current.parentId?.let(byId::get) ?: break
        }
        return path.toList()
    }

    /** All versions of [message] (messages with the same parent), oldest first. */
    fun siblings(messages: List<Message>, message: Message): List<Message> =
        messages.filter { it.parentId == message.parentId }

    /** "Version i of n" for every message on [path] that has alternatives. */
    fun branchInfo(messages: List<Message>, path: List<Message>): Map<String, BranchInfo> {
        val byParent = messages.groupBy { it.parentId }
        return path.mapNotNull { message ->
            val versions = byParent[message.parentId].orEmpty()
            if (versions.size < 2) null else message.id to BranchInfo(versions.indexOf(message), versions.size)
        }.toMap()
    }

    /**
     * The leaf to show when the user switches to version [rootId]: follow the NEWEST child at
     * every step, i.e. the conversation as it last continued on that branch.
     */
    fun newestLeafUnder(messages: List<Message>, rootId: String): String {
        val children = messages.groupBy { it.parentId }
        var current = rootId
        val seen = HashSet<String>()
        while (seen.add(current)) {
            current = children[current]?.lastOrNull()?.id ?: break
        }
        return current
    }
}
