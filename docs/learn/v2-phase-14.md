# v2 Phase 14 — Personas and the prompt library

Part D of the plan: different "ways of answering", and prompts the user reuses.

## What changed

| Where | Change |
|---|---|
| `core/model` | `Persona(id, name, instructions, temperature?, builtIn)`, `SavedPrompt`; `Chat.personaId` |
| `core/database` | Schema **v6** (AutoMigration 5→6): `personas`, `prompts`, `chats.personaId`; `PersonaDao` (deleting a persona clears it from chats in one transaction) |
| `data/chat/PersonaRepository.kt` (new) | Built-in personas in code (Coder 0.2, Tutor 0.5, Writer 0.8) + custom ones in Room; validation and clamping; prompt library |
| `OfflineChatRepository` | `createChat(personaId)`; `settingsFor(chat)` swaps in the persona's instructions (plus fixed honesty and safety rules) and temperature for every generation |
| Chat UI | "Answer as" chips on the welcome screen (Assistant / Coder / Tutor / Writer / custom); the persona name under the chat title; a bookmark button in the input inserts a saved prompt |
| `feature:personas` (new module) | Manage custom personas (name, instructions, optional own creativity) and saved prompts; reached from Settings |
| Tests | `PersonaRepositoryTest` (3), repository persona tests (2), `PersonasViewModelTest`, chat VM persona test, `MigrationTest` from v5, personas screenshot |

## Concepts

**A persona is configuration, not code.** It's a system prompt plus sampling parameters. The model and engine stay
the same; only what goes into `newSession(systemPrompt, history, sampling)` changes. The repository composes it per
chat, so the session key changes and the conversation is rebuilt when the persona differs.

**Built-in vs user data.** Built-in personas live in code with stable ids (`builtin:coder`): an app update can
improve them for everyone, and they can't be deleted by accident. User personas live in Room. One repository merges
both, so the UI doesn't care where a persona comes from.

**Non-negotiable rules stay in code.** Every persona's instructions get a fixed suffix (offline, answer the latest
message, admit uncertainty, refuse harm). A user persona can change the style, not remove the safety baseline.

**Validate at the boundary.** The repository trims and caps names and instructions, refuses blank ones, clamps the
temperature to the same range as Settings, and refuses to overwrite built-ins. The UI can't create invalid data even
if it tried.

**Schema change without a manual migration.** New tables and a nullable column are *additive*, so Room's
AutoMigration generates the SQL. The migration test now builds databases from every schema version (1–5), including
the v5 FTS triggers, and opens them with v6.

**A persona belongs to the chat, not to the screen.** It's chosen once, on the welcome screen, and stored on the chat
row. Reopening the chat from History later still uses it; switching persona mid-chat would make earlier answers
inconsistent, so it isn't offered.

## Code walkthrough

```kotlin
private suspend fun settingsFor(chatId: String, base: InferenceSettings): InferenceSettings {
    val persona = chatDao.get(chatId)?.personaId?.let { personas.persona(it) } ?: return base
    return base.copy(
        systemPrompt = "${persona.instructions}\n\n$PERSONA_RULES",
        sampling = persona.temperature?.let { base.sampling.copy(temperature = it.toDouble()) } ?: base.sampling,
    )
}
// sendMessage / regenerateLastAnswer / editAndResend:  is AppResult.Success -> settingsFor(chatId, loaded.value)
```

```kotlin
// RoomPersonaRepository.savePersona — validation in one place
if (cleanName.isEmpty() || cleanInstructions.isEmpty() || id?.startsWith(BuiltInPersonas.PREFIX) == true) return null
val clamped = temperature?.coerceIn(UserSettings.MIN_TEMPERATURE, UserSettings.MAX_TEMPERATURE)
```

```kotlin
// ChatViewModel: the persona for a NEW chat, then the chat's own
PersonaUi(personas, prompts, if (id == null) picked else current?.personaId)
val id = chatId.value ?: repository.createChat(pickedPersona.value).also { chatId.value = it }
```

## Trade-offs

- **Personas are free for now.** The approved plan makes them a Pro feature; the gate comes with billing (phase 20).
  Building the feature first and gating it later keeps billing logic in one place.
- **No persona switching inside a chat.** It keeps answers consistent and the code simple; starting a new chat with
  another persona is one tap.
- **Prompt insertion appends.** It never replaces half-typed text; the user can edit before sending.

## Interview questions

1. **How would you implement "personas" on top of an LLM API?**
   As data: a system prompt and sampling settings, composed into the session configuration per conversation. Store
   the choice with the conversation so it survives restarts and is applied to every request (send, regenerate, edit).

2. **Why keep built-in personas in code but custom ones in the database?**
   Built-ins should improve with app updates and can't be lost; user data must persist and be editable. A repository
   merges both behind one interface.

3. **How do you stop a user-defined prompt from removing safety rules?**
   Append non-negotiable rules from code to every persona's instructions, validate input length, and keep model-level
   protections. User text can change tone, not the baseline.

4. **When is a Room AutoMigration enough?**
   For additive changes (new tables, new nullable or defaulted columns, new indexes). Renames, deletions and data
   transformations need specs or manual migrations.

5. **Why does a persona change trigger a session rebuild?**
   The session key includes the inference settings (system prompt, sampling). A different persona means different
   settings, so the native conversation is recreated with the new system prompt.

6. **Where should input validation live, UI or repository?**
   In the repository (the single entry point), so every caller gets it. The UI can add friendly hints, but it isn't
   the guarantee.

7. **How do you test that a persona's temperature reaches the model?**
   With a fake engine that records `newSession` calls: create a chat with the persona, send a message, and assert the
   recorded system prompt and sampling temperature.

8. **Why add a new feature module instead of putting the screen in Settings?**
   It's a separate feature with its own ViewModel, strings and tests. A module keeps Settings small, builds in
   parallel, and enforces that features talk only through navigation callbacks.
