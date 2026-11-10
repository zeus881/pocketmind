package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sanjay.pocketmind.core.database.entity.NoteEntity

/**
 * Notes saved by the notes tool.
 *
 * V3: `suspend`, because Room on Kotlin Multiplatform only allows suspend or Flow DAO functions (iOS has
 * no blocking database calls). In v2 they were blocking, because a tool runs inside the model's
 * generation on a background thread and must return its result directly; that adapter
 * (`RoomNoteStore` in data:chat) now waits for them with `runBlocking` on that same background thread.
 */
@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NoteEntity>

    /** Notes containing [escaped] (a LIKE-escaped text: `\` before `%`, `_` and `\`), newest first. */
    @Query(
        "SELECT * FROM notes WHERE text LIKE '%' || :escaped || '%' ESCAPE '\\' " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    suspend fun search(escaped: String, limit: Int): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String): Int
}
