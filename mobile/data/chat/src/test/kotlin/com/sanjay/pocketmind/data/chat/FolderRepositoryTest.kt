package com.sanjay.pocketmind.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {
    private lateinit var db: PocketMindDatabase
    private lateinit var folders: FolderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
        folders = RoomFolderRepository(db.folderDao()) { 1L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `folders are created trimmed, sorted by name, renamed and deleted`() = runTest {
        val work = folders.createFolder("  Work  ")!!
        folders.createFolder("drones")
        assertNull(folders.createFolder("   "))
        assertEquals(listOf("drones", "Work"), folders.observeFolders().first().map { it.name })

        folders.renameFolder(work, "Office")
        folders.renameFolder(work, " ") // ignored
        assertEquals(listOf("drones", "Office"), folders.observeFolders().first().map { it.name })

        folders.deleteFolder(work)
        assertEquals(listOf("drones"), folders.observeFolders().first().map { it.name })
    }
}
