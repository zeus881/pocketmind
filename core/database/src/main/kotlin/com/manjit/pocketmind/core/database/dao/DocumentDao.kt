package com.manjit.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.manjit.pocketmind.core.database.entity.ChunkEntity
import com.manjit.pocketmind.core.database.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    /** All chunks of a document, vectors included: the brute-force search reads them all. */
    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY chunkIndex")
    suspend fun chunks(documentId: String): List<ChunkEntity>

    @Insert
    suspend fun insertDocument(document: DocumentEntity)

    @Insert
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    /** Document + all chunks in ONE transaction: a half-indexed document can never be seen. */
    @Transaction
    suspend fun insertIndexed(document: DocumentEntity, chunks: List<ChunkEntity>) {
        insertDocument(document)
        insertChunks(chunks)
    }

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)
}
