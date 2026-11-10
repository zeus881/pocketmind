package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sanjay.pocketmind.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** Folders for grouping chats in History. */
@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE chats SET folderId = NULL WHERE folderId = :id")
    suspend fun unfileChats(id: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    /** Deletes the folder but never its chats: they move back to "no folder", in one transaction. */
    @Transaction
    suspend fun deleteFolder(id: String) {
        unfileChats(id)
        delete(id)
    }
}
