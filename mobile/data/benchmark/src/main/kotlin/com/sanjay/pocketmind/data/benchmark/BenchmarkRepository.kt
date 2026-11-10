package com.sanjay.pocketmind.data.benchmark

import com.sanjay.pocketmind.core.database.dao.BenchmarkDao
import com.sanjay.pocketmind.core.database.entity.asEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.model.BenchmarkResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Saved benchmark runs (Room). */
interface BenchmarkRepository {
    fun observeRuns(): Flow<List<BenchmarkResult>>

    suspend fun save(result: BenchmarkResult)

    suspend fun delete(id: String)
}

internal class RoomBenchmarkRepository @Inject constructor(private val dao: BenchmarkDao) : BenchmarkRepository {
    override fun observeRuns(): Flow<List<BenchmarkResult>> = dao.observeAll().map { list -> list.map { it.asModel() } }

    override suspend fun save(result: BenchmarkResult) = dao.insert(result.asEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
