package com.manjit.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.manjit.pocketmind.core.model.BenchmarkPromptResult
import com.manjit.pocketmind.core.model.BenchmarkResult

/**
 * Table `benchmark_runs` (added in database version 2).
 *
 * The 3 per-prompt results are stored in ONE column as a JSON list (see Converters).
 * Trade-off: we never query by a single prompt's numbers, only show whole runs, so a separate
 * table + join would add complexity without any benefit.
 */
@Entity(tableName = "benchmark_runs", indices = [Index("createdAt")])
data class BenchmarkRunEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val modelId: String,
    val modelName: String,
    val backend: String,
    val loadMs: Long,
    val prompts: List<BenchmarkPromptResult>,
    val peakMemoryMb: Int,
    val device: String,
    val androidVersion: String,
)

fun BenchmarkRunEntity.asModel() = BenchmarkResult(
    id, createdAt, modelId, modelName, backend, loadMs, prompts, peakMemoryMb, device, androidVersion,
)

fun BenchmarkResult.asEntity() = BenchmarkRunEntity(
    id, createdAt, modelId, modelName, backend, loadMs, prompts, peakMemoryMb, device, androidVersion,
)
