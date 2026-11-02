package com.sanjay.pocketmind.feature.personas

import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.chat.PersonaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonasViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeRepo : PersonaRepository {
        val personas = MutableStateFlow(listOf(Persona("builtin:coder", "Coder", "code", 0.2f, builtIn = true)))
        val prompts = MutableStateFlow<List<SavedPrompt>>(emptyList())

        override fun observePersonas(): Flow<List<Persona>> = personas
        override suspend fun persona(id: String) = personas.value.firstOrNull { it.id == id }
        override suspend fun savePersona(id: String?, name: String, instructions: String, temperature: Float?): String {
            val newId = id ?: "p${personas.value.size}"
            personas.value =
                personas.value.filterNot { it.id == newId } + Persona(newId, name, instructions, temperature)
            return newId
        }
        override suspend fun deletePersona(id: String) {
            personas.value = personas.value.filterNot { it.id == id }
        }
        override fun observePrompts(): Flow<List<SavedPrompt>> = prompts
        override suspend fun savePrompt(id: String?, title: String, text: String): String {
            val newId = id ?: "q${prompts.value.size}"
            prompts.value = prompts.value.filterNot { it.id == newId } + SavedPrompt(newId, title, text)
            return newId
        }
        override suspend fun deletePrompt(id: String) {
            prompts.value = prompts.value.filterNot { it.id == id }
        }
    }

    private val repo = FakeRepo()
    private val viewModel by lazy { PersonasViewModel(repo) }

    @Test
    fun `personas and prompts are listed, saved and deleted`() = runTest {
        assertEquals(listOf("Coder"), viewModel.uiState.first { !it.isLoading }.personas.map { it.name })

        viewModel.savePersona(null, "Pirate", "Arr.", null)
        viewModel.savePrompt(null, "Email", "Write an email")
        var state = viewModel.uiState.first { it.prompts.isNotEmpty() && it.personas.size == 2 }
        assertEquals(listOf("Coder", "Pirate"), state.personas.map { it.name })

        viewModel.deletePersona("p1")
        viewModel.deletePrompt("q0")
        state = viewModel.uiState.first { it.prompts.isEmpty() && it.personas.size == 1 }
        assertEquals(listOf("Coder"), state.personas.map { it.name })
    }
}
