package com.sanjay.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Table `notes` (v8): what the user asked the assistant to remember, saved by the notes tool. */
@Entity(tableName = "notes", indices = [Index("createdAt")])
data class NoteEntity(@PrimaryKey val id: String, val text: String, val createdAt: Long)
