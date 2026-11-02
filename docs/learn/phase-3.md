# Phase 3 — Chat with streaming, Room history, search, rename, delete with undo

## 1. What we built

A real chat app. Messages are saved in a Room (SQLite) database, so chats survive app
restarts. The chat screen streams answers token by token (throttled to 20 UI updates per
second), has Stop, Retry, Copy, Markdown rendering and a "12.4 tok/s · GPU" label. The
history screen lists chats newest first, searches titles *and* message text, renames, and
deletes with a 10-second Undo. New chats get an automatic title from the first message.

## 2. Big picture

```
 ChatScreen ──events──▶ ChatViewModel ──▶ ChatRepository (data:chat) ──▶ ChatDao / MessageDao ──▶ SQLite
     ▲                       │                 │   ▲                         (core:database)
     │  StateFlow<UiState>   │                 │   └── Flow<List<Message>> (Room re-emits on change)
     └───────────────────────┘                 ▼
                                         LlmEngine (core:llm) ── Flow<String> pieces
 HistoryScreen ──▶ HistoryViewModel ──▶ ChatRepository.observeChats(query)

 Room is the source of truth. The answer being generated lives only in ViewModel memory
 (streamingText) and is written to Room ONCE: finished, stopped, or failed.
```

Module graph after phase 3:
```
app ─▶ feature:chat ─▶ data:chat ─▶ core:database ─▶ core:model
    ─▶ feature:history ─┘        └─▶ core:llm ──────▶ core:model
```

## 3. Concepts you need

### Room (entities, DAOs, database)
- **What:** Google's library over SQLite. `@Entity` = table, `@Dao` = queries, `@Database`
  = the whole DB. Room checks SQL at compile time and generates the code.
- **Analogy:** A filing cabinet with labelled drawers (tables) and a clerk (DAO) who only
  accepts pre-approved request forms (queries checked at build time).
- **Why:** Structured data with relations, search and transactions. Survives restarts.
- **Alternatives:** Raw SQLite (verbose, no compile-time checks), DataStore (key-value, no
  queries), Realm/ObjectBox (third-party), SQLDelight (great, but Room is the Android standard).

### Foreign keys and CASCADE
- A message row stores `chatId`; the foreign key guarantees the chat exists, and
  `onDelete = CASCADE` makes SQLite delete messages when their chat is deleted.
- **Why:** Integrity is enforced by the database, not by code that someone can forget.

### Indices
- `Index("chatId")` lets SQLite find one chat's messages without scanning every row
  (like a book index vs reading every page).

### Observable queries (`Flow` from Room)
- A DAO function returning `Flow<List<T>>` re-runs and emits whenever its tables change.
- **Why:** The UI never has to "refresh": save a message and every screen showing it updates.

### `@Transaction`
- Several writes that succeed or fail *together*. Used to restore a deleted chat with its
  messages.

### Repository pattern
- One class owns a kind of data and hides where it comes from (Room + engine). ViewModels
  call `sendMessage()`, not DAOs. Swap storage → ViewModels unchanged.

### ViewModel + `StateFlow<UiState>` + `Channel` events
- **ViewModel:** survives screen rotation; holds screen logic; `viewModelScope` cancels its
  work when the screen is gone for good.
- **UiState:** one immutable data class describing the screen. Compose redraws from it.
- **Events:** things that must happen *once* (a snackbar). A `StateFlow` would replay them
  after rotation; a `Channel` delivers each exactly once.
- **Alternatives:** LiveData (older, Android-only, weaker operators); MVI libraries (more
  ceremony than we need).

### `combine`, `flatMapLatest`, `stateIn`, `debounce`
- `combine(a, b, c)`: new value whenever any input changes → builds the UiState.
- `flatMapLatest`: when `chatId` changes, stop observing the old chat, observe the new one.
- `stateIn(scope, WhileSubscribed(5000), initial)`: turns a cold Flow into a StateFlow that
  stops upstream work 5 s after the UI disappears (5 s survives a rotation).
- `debounce(250)`: wait for typing to pause before searching.

### `NonCancellable`
- In a cancelled coroutine, suspending calls throw immediately. `withContext(NonCancellable)`
  lets us still *save the partial answer* when the user leaves the screen mid-answer.

### SavedStateHandle & type-safe arguments
- `ChatRoute(chatId)` is stored by Navigation in the ViewModel's `SavedStateHandle` under
  the key `"chatId"` — it survives process death, so reopening the app lands in the same chat.

### Compose lists: `LazyColumn`, `reverseLayout`, keys
- `LazyColumn` only composes visible rows (like RecyclerView). `reverseLayout = true` sticks
  the list to the bottom like a chat. `key = message.id` keeps each bubble stable.

### Robolectric
- Runs Android code (SQLite, Compose UI tests) on the JVM — no phone or emulator in CI.

## 4. Code walkthrough

### `core/database/entity/MessageEntity.kt`
```kotlin
@Entity(tableName = "messages",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"],
                              childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId")])
data class MessageEntity(@PrimaryKey val id: String, val chatId: String, val role: String, ...,
                         val tokensPerSecond: Double?, val backend: String?)
```
Enums are stored as names (TEXT), not ordinals — reordering the enum can't corrupt data.
Performance numbers are stored with the answer so the label is right when reopened.

### `core/database/dao/ChatDao.kt` — search in SQL
```sql
SELECT c.id, ..., (SELECT m.text FROM messages m WHERE m.chatId = c.id
                   ORDER BY m.createdAt DESC LIMIT 1) AS lastMessage
FROM chats c
WHERE :query = '' OR c.title LIKE '%' || :query || '%'
   OR EXISTS (SELECT 1 FROM messages m2 WHERE m2.chatId = c.id AND m2.text LIKE '%' || :query || '%')
ORDER BY c.updatedAt DESC
```
One query returns the list *with* a preview of the latest message. Search covers message
text too. Bound parameters (`:query`) prevent SQL injection.

### `data/chat/OfflineChatRepository.kt` — keeping two memories in sync
The model's native conversation and Room can disagree. `prepareSessionLocked` rebuilds the
conversation only when needed:
```kotlin
val key = SessionKey(chatId, settings)
val upToDate = session == key && sessionInSync && historyTokens <= budget
if (upToDate) return
val trimmed = HistoryTrimmer.trim(turns, budget)
engine.newSession(systemPrompt + trimmed.summaryNote, trimmed.turns, settings.sampling)
```
Rebuild triggers: other chat, changed settings, last answer stopped/failed (`sessionInSync
= false`), history no longer fits the context window, or the model was reloaded (e.g. after
`onTrimMemory`). Rebuilding costs "prefill" time, so we avoid it when possible.

`budget = contextWindow − 1536 (answer) − systemPrompt − prompt`.
`nextTimestamp()` makes timestamps strictly increasing so two messages saved in the same
millisecond keep their order.

### `ChatTitles.fromFirstMessage`
First non-empty line, whitespace collapsed, trailing `?.!` removed, cut at a word boundary
before 40 chars plus "…". No extra LLM call.

### `feature/chat/ChatViewModel.kt` — how a message travels
```
user taps Send
 → ChatViewModel.send("How do drones hover?")
   → chatId null? repository.createChat()
   → streaming.value = ""                      (shows "Thinking…" bubble)
   → repository.sendMessage(id, text)
       → load model if needed → prepare session → save USER message (+auto-title)
       → returns engine.generate(text)          (cold Flow)
   → collectAnswer():
       pieces ─onEach{append}─▶ map{full text} ─throttleLatest(50ms)─▶ streaming.value
   → finally (NonCancellable): saveAssistantMessage(text, COMPLETE|STOPPED|FAILED, stats)
                               streaming.value = null
 → Room emits new message list → combine → UiState → ChatScreen recomposes
```
Stop = `generationJob.cancel()` → `CancellationException` → status STOPPED → partial
answer saved → engine calls `cancelProcess()` (phase 2).

### `ChatScreen.kt` — stateful vs stateless
`ChatScreen` gets the ViewModel and collects events; `ChatContent(state, callbacks)` is a
pure function of state — it's what the Compose UI tests render. States shown: model
loading banner, no model (button to the Models screen), model failed (Retry), empty chat,
content.

### `component/MessageList.kt`
`LazyColumn(reverseLayout = true)`, newest-first items, streaming bubble on top (= bottom of
the screen). Assistant bubbles render Markdown (`Markdown(content = text)` from
`multiplatform-markdown-renderer`, API verified in the AAR), have a 48 dp Copy button
(`LocalClipboard`), a Regenerate button on the last answer, and the stats label.

### `feature/history/HistoryViewModel.kt`
```kotlin
combine(query.debounce { if (it.isEmpty()) 0L else 250L }, retryTrigger) { q, _ -> q }
    .flatMapLatest { q -> repository.observeChats(q).map { Content(it) }.catch { emit(Error) } }
```
Delete returns a `DeletedChat` snapshot → event → snackbar "Chat deleted · Undo" →
`restoreChat(snapshot)` writes chat + messages back in one transaction with the same ids.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| Save answer once at the end | Save each token | 1 write instead of hundreds; Room stays the truth for *finished* content. Risk: a crash mid-answer loses the partial text (acceptable). |
| UUID string ids | Auto-increment Long | Id known before insert; undo restores the exact same ids. Slightly bigger index. |
| Search with `LIKE` | SQLite FTS4/FTS5 | Simple and fine for thousands of messages. FTS is the upgrade path for big histories. |
| Rebuild session only when needed | Rebuild every message | Rebuilding re-prefills the whole history (seconds on a phone). |
| Extractive auto-title | Ask the LLM for a title | Instant, no battery cost; user can rename. |
| Snapshot undo | Soft-delete column | No "deleted" flag to filter in every query; snapshot kept only while snackbar shows. |
| Fake repository in feature tests | Real repo + Room | ViewModel tests stay pure-JVM and fast; the real repo has its own Robolectric tests. |
| ViewModel-held streaming text | Write partial text to Room | Smooth UI without DB churn; NonCancellable save covers leaving the screen. |

## 6. Common mistakes

1. **Collecting flows in Compose without lifecycle** — keeps working in background.
   We use `collectAsStateWithLifecycle()`.
2. **Using `StateFlow` for one-off events** — snackbar shows again after rotation. `Channel`.
3. **Forgetting `NonCancellable` in `finally`** — the save itself gets cancelled.
4. **Swallowing `CancellationException`** in the generic `catch (e: Exception)` — we catch
   it first, mark STOPPED, rethrow.
5. **No index on foreign key column** — slow queries and a Room build warning.
6. **Creating the ViewModel before `Dispatchers.setMain`** in tests — nothing runs. We hit
   this in phase 2; tests now create ViewModels lazily.
7. **Querying the DB on every keystroke** — `debounce`.
8. **Double bottom padding with the keyboard** — fixed with `consumeWindowInsets` on the NavHost.
9. **`advanceUntilIdle()` ignores work that exists only in `backgroundScope`.** Our history tests
   stayed in "Loading" because the collector launched in `backgroundScope` never subscribed.
   Fix (Android's recommended pattern): `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }`.
10. **Reading strings with `LocalContext.current.getString()` in Compose** — Lint flags it as not
    configuration-aware (a language change would show stale text). Use `LocalResources.current`.

### What we learned about the toolchain (real problems hit while building this phase)
- **Robolectric's Android 35+ images need Java 21**, and the Markdown library we use ships Java 21
  bytecode. The app still *compiles* to Java 17 bytecode, but unit tests now *run* on JDK 21
  through a Gradle toolchain (`javaLauncher` in `KotlinAndroid.kt`). CI installs both JDKs.
- On modern JDKs Robolectric must reach into JDK internals, so test JVMs get
  `--add-opens java.base/java.io` and `--add-exports java.base/jdk.internal.access`.
- Lesson for interviews: "compile target" (bytecode level) and "runtime JDK" are different
  things; Gradle toolchains let you choose each one explicitly.

## 7. Try it yourself

1. **Pin chats.** Add `pinned: Boolean` to `ChatEntity` (bump DB version to 2 with a migration
   `ALTER TABLE chats ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0`) and sort pinned first.
   Hint: `ORDER BY c.pinned DESC, c.updatedAt DESC`.
2. **Share a chat.** Add a share action to the chat top bar that builds plain text
   ("You: … / PocketMind: …") and fires `Intent.ACTION_SEND`. Hint: see how the benchmark
   screen shares results in phase 5.
3. **LLM-written titles.** After the first answer, run a second short generation
   "Give a 4-word title for: …" and rename the chat. Measure how much latency it adds.

## 8. Interview questions

1. **Why is Room the source of truth, and where does the streaming text live?**
   The UI observes Room flows, so every screen agrees on saved data. The in-progress answer
   lives only in ViewModel state and is written once at the end (complete, stopped or
   failed) — avoiding hundreds of writes per answer and keeping DB data final.

2. **How do you keep the model's conversation consistent with the database?**
   The repository tracks which chat/settings the native session was built for and whether it
   is in sync. Switching chats, changed settings, a stopped/failed answer, a reloaded model or
   history exceeding the context budget trigger a rebuild from Room with trimmed history.

3. **What happens when a user leaves the chat mid-answer?**
   `viewModelScope` is cancelled, the collector gets `CancellationException`, the status
   becomes STOPPED, and the `finally` block saves the partial text inside
   `withContext(NonCancellable)`. The engine cancels native generation.

4. **How does delete-with-undo work?**
   Delete returns a snapshot (chat + messages); FK cascade removes messages. The snapshot goes
   in a one-shot event; if Undo is tapped, a `@Transaction` DAO method re-inserts both with
   the original ids.

5. **Why `flatMapLatest` for the chat id?**
   When the user starts a new chat or opens another, we must stop listening to the old
   chat's messages immediately. `flatMapLatest` cancels the previous inner flow on every new
   id.

6. **Explain `stateIn(WhileSubscribed(5000))`.**
   Shares one upstream among collectors and keeps the latest value. Upstream stops 5 s after
   the last collector leaves — long enough to survive rotation, short enough not to waste
   work in background.

7. **How did you make search efficient?**
   Debounced input (250 ms), one SQL query with bound parameters that matches title or any
   message via `EXISTS`, and an index on `chatId`. FTS would be the next step for very large
   histories.

8. **How did you test this without a device?**
   DAO and repository tests on an in-memory Room DB under Robolectric with `FakeLlmEngine`;
   ViewModel tests with a fake repository and virtual time (`advanceTimeBy` to stop
   mid-stream); Compose UI tests for Send/Stop/empty/no-model states.

9. **Why throttle at 50 ms?**
   Tokens can arrive at 30–60/s; recomposing a Markdown bubble at that rate janks. 20 updates
   per second is visually smooth and cuts work by ~2–3x. The last value is never dropped.

10. **What's the trade-off of saving only at the end?**
    If the process crashes mid-answer (not a normal exit), the partial text is lost. We
    accepted that for fewer writes; a periodic checkpoint every few seconds would be the fix.

## 9. Check your understanding

1. What does `onDelete = CASCADE` do?
2. Why does the chat list use `reverseLayout = true`?
3. When does the repository rebuild the native conversation?
4. Why is the ViewModel created lazily in tests?
5. What is the difference between `ChatScreen` and `ChatContent`?

---

<details>
<summary>Answers</summary>

1. When a chat row is deleted, SQLite deletes all its messages automatically.
2. It anchors the list to the bottom so the newest message is visible without scroll code.
3. Different chat, different settings, after a stopped/failed answer, after a model reload,
   or when history no longer fits the context budget.
4. `viewModelScope` captures `Dispatchers.Main` on creation; the test rule must replace Main first.
5. `ChatScreen` owns the ViewModel and events; `ChatContent` is stateless (state in, callbacks out)
   so it can be previewed and UI-tested.
</details>
