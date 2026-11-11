package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.result.AppResult

/**
 * The user's control over their own data (Settings → Your data). Everything PocketMind stores about
 * the user lives on the phone; these two actions make that visible and reversible.
 */
interface UserDataControl {
    /**
     * Writes ONE ZIP file to [uri] (picked with the system "Save as" screen): every chat as Markdown,
     * all data as JSON (chats with every version of every message, folders, personas, prompts, notes,
     * settings), and attached photos. Downloaded models are not included.
     */
    suspend fun exportAll(uri: String): AppResult<Unit>

    /**
     * Deletes chats, notes, personas, prompts, folders, the PDF index, photos and settings. Downloaded
     * models stay (they are not personal data, and downloading 2.6 GB again would be a waste).
     */
    suspend fun deleteAll()
}
