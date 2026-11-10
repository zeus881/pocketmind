package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.dao.FolderDao
import com.sanjay.pocketmind.core.database.entity.FolderEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.model.Folder
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Folders for grouping chats in History. Deleting a folder never deletes its chats. */
interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    /** Creates a folder and returns its id. Blank names are rejected (null). */
    suspend fun createFolder(name: String): String?

    suspend fun renameFolder(id: String, name: String)

    suspend fun deleteFolder(id: String)
}

internal class RoomFolderRepository @Inject constructor(private val dao: FolderDao, private val clock: Clock) :
    FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = dao.observeAll().map { list -> list.map { it.asModel() } }

    override suspend fun createFolder(name: String): String? {
        val clean = name.trim().take(MAX_NAME_LENGTH).ifBlank { return null }
        val id = UUID.randomUUID().toString()
        dao.upsert(FolderEntity(id, clean, clock.nowMillis()))
        return id
    }

    override suspend fun renameFolder(id: String, name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        if (clean.isNotBlank()) dao.rename(id, clean)
    }

    override suspend fun deleteFolder(id: String) = dao.deleteFolder(id)

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
