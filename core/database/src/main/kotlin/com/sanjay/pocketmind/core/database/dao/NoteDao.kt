package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sanjay.pocketmind.core.database.entity.NoteEntity

/**
 * Notes saved by the notes tool. These calls are BLOCKING (not `suspend`) on purpose: a tool runs
 * inside the model's generation on a background thread and must return its result directly.
 * Room still refuses them on the main thread, which is exactly the guarantee we want.
 */
@Dao
interface NoteDao {
    @Insert
    fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): List<NoteEntity>

    /** Notes containing [escaped] (a LIKE-escaped text: `\` before `%`, `_` and `\`), newest first. */
    @Query(
        "SELECT * FROM notes WHERE text LIKE '%' || :escaped || '%' ESCAPE '\\' " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    fun search(escaped: String, limit: Int): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    fun delete(id: String): Int
}
