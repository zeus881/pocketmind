package com.manjit.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.manjit.pocketmind.core.database.entity.BenchmarkRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_runs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BenchmarkRunEntity>>

    @Insert
    suspend fun insert(run: BenchmarkRunEntity)

    @Query("DELETE FROM benchmark_runs WHERE id = :id")
    suspend fun delete(id: String)
}
