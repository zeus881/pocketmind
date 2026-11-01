package com.manjit.pocketmind.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table `documents` (database v3): a PDF the user indexed for "Ask your PDF".
 * [embeddingModelId] records which embedding model produced the vectors — vectors from two
 * different models are not comparable, so a model change means re-indexing.
 */
@Entity(tableName = "documents", indices = [Index("createdAt")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pageCount: Int,
    val chunkCount: Int,
    val createdAt: Long,
    val embeddingModelId: String,
    val dimensions: Int,
)

/**
 * Table `chunks`: ~300-token passages of a document with their embedding vector.
 *
 * The vector (e.g. 768 floats) is stored as a BLOB of little-endian float32 bytes: compact
 * (4 bytes per number) and fast to read back. Deleting a document cascades to its chunks.
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val chunkIndex: Int,
    val startPage: Int,
    val endPage: Int,
    val text: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray,
) {
    // ByteArray uses identity equality; data classes need content equality for tests/diffing.
    override fun equals(other: Any?): Boolean = other is ChunkEntity &&
        id == other.id &&
        documentId == other.documentId &&
        chunkIndex == other.chunkIndex &&
        startPage == other.startPage &&
        endPage == other.endPage &&
        text == other.text &&
        embedding.contentEquals(other.embedding)

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + documentId.hashCode()) + embedding.contentHashCode()
}
