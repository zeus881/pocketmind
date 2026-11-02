package com.sanjay.pocketmind.feature.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.data.chat.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonasUiState(
    val isLoading: Boolean = true,
    val personas: List<Persona> = emptyList(),
    val prompts: List<SavedPrompt> = emptyList(),
)

/** Manages custom personas and the prompt library. Built-in personas are shown read-only. */
@HiltViewModel
class PersonasViewModel @Inject constructor(private val repository: PersonaRepository) : ViewModel() {

    val uiState: StateFlow<PersonasUiState> = combine(repository.observePersonas(), repository.observePrompts()) {
            p,
            q,
        ->
        PersonasUiState(isLoading = false, personas = p, prompts = q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PersonasUiState())

    /** [id] = null creates a new persona. [temperature] = null follows the Settings value. */
    fun savePersona(id: String?, name: String, instructions: String, temperature: Float?) {
        viewModelScope.launch { repository.savePersona(id, name, instructions, temperature) }
    }

    fun deletePersona(id: String) {
        viewModelScope.launch { repository.deletePersona(id) }
    }

    fun savePrompt(id: String?, title: String, text: String) {
        viewModelScope.launch { repository.savePrompt(id, title, text) }
    }

    fun deletePrompt(id: String) {
        viewModelScope.launch { repository.deletePrompt(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
