package com.sanjay.pocketmind.feature.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.data.chat.PersonaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonasUiState(
    val isLoading: Boolean = true,
    val personas: List<Persona> = emptyList(),
    val prompts: List<SavedPrompt> = emptyList(),
    /** New custom personas are part of PocketMind Pro; existing ones keep working without it. */
    val canCreatePersonas: Boolean = true,
)

/** Manages custom personas and the prompt library. Built-in personas are shown read-only. */
class PersonasViewModel(private val repository: PersonaRepository, private val pro: ProStatus) : ViewModel() {

    val uiState: StateFlow<PersonasUiState> = combine(
        repository.observePersonas(),
        repository.observePrompts(),
        pro.isPro,
    ) { personas, prompts, isPro ->
        PersonasUiState(isLoading = false, personas = personas, prompts = prompts, canCreatePersonas = isPro)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PersonasUiState())

    /** [id] = null creates a new persona. [temperature] = null follows the Settings value. */
    fun savePersona(id: String?, name: String, instructions: String, temperature: Float?) {
        if (id == null && !pro.isPro.value) return // the screen offers no "New persona" without Pro
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
