package com.sanjay.pocketmind.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonaRepositoryTest {
    private lateinit var db: PocketMindDatabase
    private lateinit var repository: PersonaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPersonaRepository(db.personaDao()) { 1L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `built-in personas come first and can't be changed or deleted`() = runTest {
        repository.savePersona(null, "Analyst", "Answer with numbers.", 0.3f)

        val names = repository.observePersonas().first().map { it.name }
        assertEquals(listOf("Coder", "Tutor", "Writer", "Analyst"), names)
        assertNull(repository.savePersona("builtin:coder", "Hacked", "x", null))
        repository.deletePersona("builtin:coder")
        assertNotNull(repository.persona("builtin:coder"))
    }

    @Test
    fun `custom personas are validated, clamped, updated and deleted`() = runTest {
        assertNull(repository.savePersona(null, "  ", "instructions", null))
        assertNull(repository.savePersona(null, "Name", "   ", null))

        val id = repository.savePersona(null, " Hot ", " Be wild. ", 9f)!!
        val saved = repository.persona(id)!!
        assertEquals("Hot", saved.name)
        assertEquals(UserSettings.MAX_TEMPERATURE, saved.temperature)

        repository.savePersona(id, "Calm", "Be calm.", null)
        assertEquals("Calm", repository.persona(id)?.name)

        repository.deletePersona(id)
        assertNull(repository.persona(id))
    }

    @Test
    fun `prompts are saved, sorted and deleted`() = runTest {
        assertNull(repository.savePrompt(null, "", "text"))
        val email = repository.savePrompt(null, "email", "Write a polite email about")!!
        repository.savePrompt(null, "Bug report", "Turn this into a bug report:")

        assertEquals(listOf("Bug report", "email"), repository.observePrompts().first().map { it.title })
        repository.deletePrompt(email)
        assertTrue(repository.observePrompts().first().none { it.id == email })
    }
}
