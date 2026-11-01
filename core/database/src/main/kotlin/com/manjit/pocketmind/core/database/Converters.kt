package com.manjit.pocketmind.core.database

import androidx.room.TypeConverter
import com.manjit.pocketmind.core.model.BenchmarkPromptResult
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters: teach Room how to store a type SQLite doesn't know.
 * SQLite has only INTEGER, REAL, TEXT and BLOB, so a `List<BenchmarkPromptResult>` is saved
 * as TEXT (JSON) and parsed back when read.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun promptsToJson(prompts: List<BenchmarkPromptResult>): String = json.encodeToString(prompts)

    @TypeConverter
    fun promptsFromJson(text: String): List<BenchmarkPromptResult> = json.decodeFromString(text)
}
