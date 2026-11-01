# Phase 2 — The on-device LLM engine (`core:llm`)

## 1. What we built

We wrapped Google's LiteRT-LM runtime behind our own small `LlmEngine` interface. The real
implementation, `LiteRtLmEngine`, loads a `.litertlm` model (GPU first, CPU if the GPU fails),
keeps one conversation, streams the answer as a Kotlin `Flow`, can be stopped mid-answer,
measures tokens/second, and frees memory when Android asks. A `FakeLlmEngine` lets every
later test run without a model. A temporary "Engine test" screen proves it on a phone.

## 2. Big picture

```
 DebugLlmScreen (Compose)                      ← phase 3 replaces this with ChatScreen
      │ taps "Send"
      ▼
 DebugLlmViewModel ──generate(prompt)──▶ LlmEngine (interface, core:llm)
      ▲                                         │ implemented by
      │ Flow<String> pieces                     ▼
      │ (throttled 50 ms)               LiteRtLmEngine  ── Mutex ── IO dispatcher
      │                                         │
      └─────────────────────────────────────────┤ JNI (Java ↔ C++)
                                                ▼
                                   LiteRT-LM native runtime (liblitertlm_jni.so)
                                                │
                                   GPU (OpenCL)  or  CPU (XNNPACK)
                                                │
                              /data/local/tmp/llm/model.litertlm (adb push)

 PocketMindApp.onTrimMemory ─(critical)─▶ engine.unload()
 Tests: FakeLlmEngine (core:testing) ──implements──▶ LlmEngine
```

## 3. Concepts you need

### On-device inference runtime (LiteRT-LM)
- **What:** A C++ library (shipped as `.so` files inside the AAR) that runs a quantized LLM
  on the phone's CPU/GPU/NPU, plus a Kotlin API around it.
- **Analogy:** A car engine (C++) with a dashboard (Kotlin API).
- **Why:** Google's supported runtime for Gemma on Android; the older MediaPipe LLM
  Inference API is maintenance-only.
- **Alternatives:** llama.cpp (great, but you build/maintain JNI yourself; GGUF models),
  ONNX Runtime GenAI, MLC-LLM. LiteRT-LM has first-party Gemma support and a Kotlin API.

### JNI (Java Native Interface)
- **What:** How Kotlin calls C++ code. Native calls block the calling thread and can throw
  exceptions that Kotlin code didn't anticipate.
- **Why it matters:** Never call native inference on the main thread (the UI would freeze
  and Android shows "App not responding" after 5 s).

### Coroutines, `suspend`, dispatchers, `withContext`
- **What:** `suspend` functions can pause without blocking a thread. `withContext(io)`
  runs a block on another dispatcher and comes back with the result.
- **Analogy:** You hand a long job to a colleague (IO thread) and keep answering the phone
  (UI thread); when they finish they hand you the result.
- **Why here:** `engine.initialize()` takes ~10 s; it runs inside `withContext(io)`.

### Cold `Flow`, `flow {}` and `flowOn`
- **What:** A `Flow` is a stream of values over time. *Cold* means nothing happens until
  someone calls `collect`. `flow { emit(x) }` builds one. `flowOn(io)` makes the *producer*
  run on IO while the *collector* stays where it is.
- **Analogy:** A tap (Flow) — water only runs when someone opens it (collects).
- **Rule:** Never call `withContext` around `emit` inside `flow {}` — Flow's "context
  preservation" rule throws `IllegalStateException`. Use `flowOn` instead.
- **Alternative:** Callbacks (the low-level `MessageCallback`). Flows compose better
  (`runningFold`, `throttleLatest`, `catch`) and support cancellation for free.

### `StateFlow`
- **What:** A Flow that always has a current value and replays it to new collectors.
- **Why:** `engine.state` — any screen can read "Loading / Ready(GPU) / Failed" at any time.

### `Mutex` (mutual exclusion)
- **What:** A coroutine-friendly lock: `lock.withLock { ... }` lets only one coroutine in at
  a time; others *suspend* (not block) until it's free.
- **Analogy:** A single-key bathroom. Whoever has the key goes in; others wait outside.
- **Why:** The native conversation is not thread-safe. Without the lock, "new chat" could
  close the conversation while a generation is still writing into it → native crash.
- **Alternative:** `synchronized` blocks — they *block* threads and don't work across
  suspension points. A single-threaded dispatcher — works, but hides the intent.

### Cancellation (and a real gotcha we found)
- **What:** Cancelling a coroutine throws `CancellationException` at its next suspension
  point; `finally` blocks run; resources should be released.
- **Gotcha:** We read the LiteRT-LM 0.19 bytecode and found its Flow ends with an *empty*
  `awaitClose {}` — cancelling the Flow stops *delivery*, but the C++ code keeps generating.
  So our engine catches `CancellationException`, calls `conversation.cancelProcess()`, and
  rethrows. This is the "Stop" button working for real.

### Singleton + Hilt `@Binds`
- **What:** `@Singleton` = one instance per app process. `@Binds` maps `LlmEngine` →
  `LiteRtLmEngine`.
- **Why:** A model is 0.6–2.6 GB of RAM. Two copies would crash the app.

### `implementation` vs `api` dependencies
- **What:** `implementation(litertlm)` makes LiteRT-LM visible *only inside* `core:llm`.
  `api(core:model)` re-exports `ChatTurn` to whoever uses `core:llm`.
- **Why:** The rule "only `core:llm` imports LiteRT-LM" is now enforced by the compiler:
  a feature module *can't* see those classes.

### Manifest merging & `uses-native-library`
- **What:** Each library module has its own `AndroidManifest.xml`; Gradle merges them into
  the app's. Android 12+ requires apps to declare vendor native libraries they load (OpenCL
  for the GPU). `required="false"` = still installable without them.

### `onTrimMemory`
- **What:** Android's callback saying "memory is low, give some back or I'll kill you".
- **Why:** The model is the biggest thing we own, and reloading costs only a few seconds.

### Test doubles (fakes)
- **What:** `FakeLlmEngine` implements the same interface with scripted answers and
  records what it was asked. It lives in `core:testing`, which modules only use as
  `testImplementation` — so it can never ship in the APK.

## 4. Code walkthrough

### `LlmEngine.kt` — the contract
```kotlin
interface LlmEngine {
    val state: StateFlow<EngineState>
    val lastStats: GenerationStats?
    val contextWindowTokens: Int
    suspend fun load(modelPath: String, preferGpu: Boolean)
    suspend fun newSession(systemPrompt: String, history: List<ChatTurn> = emptyList(), sampling: SamplingParams = SamplingParams())
    fun generate(prompt: String): Flow<String>
    suspend fun unload()
}
```
Compared to CLAUDE.md: `newSession` became `suspend` (it must wait for the lock) and gained
`sampling`; we added `lastStats` and `contextWindowTokens`. `generate` is *not* suspend: it
returns a cold Flow instantly; work starts on `collect`.

### `EngineState.kt` — a sealed state machine
`Idle → Loading → Ready(backend, loadMs) | Failed(reason)`. Sealed means a `when` over it
must handle every case — the compiler reminds you when a new state is added.

### `LiteRtLmEngine.load()` — GPU first, CPU fallback
```kotlin
override suspend fun load(modelPath: String, preferGpu: Boolean) = withContext(io) {   // 1
    lock.withLock {                                                                    // 2
        if (engine != null && loadedPath == modelPath && loadedPreferGpu == preferGpu) return@withLock // 3
        closeAllLocked()                                                               // 4
        if (!File(modelPath).canRead()) { _state.value = Failed(..., FILE_NOT_FOUND); return@withLock }
        _state.value = EngineState.Loading(modelPath)
        for (backend in EngineMath.backendOrder(preferGpu)) {                          // 5
            val created = tryCreateEngine(modelPath, backend) ?: continue              // 6
            ... _state.value = EngineState.Ready(modelPath, backend, loadMs); return@withLock
        }
        _state.value = EngineState.Failed(modelPath, FailureReason.INIT_FAILED)
    }
}
```
1. Move off the main thread. 2. Take the lock. 3. Idempotent: screens can call `load` every
time they open. 4. One model at a time — free the old one first. 5. `[GPU, CPU]` or `[CPU]`.
6. `tryCreateEngine` returns null on failure (and closes the half-built engine so no native
memory leaks), so the loop moves on to CPU.

`EngineConfig(modelPath, backend, maxNumTokens = 4096, cacheDir = ...)`: `maxNumTokens` sets
the context window (KV-cache size → RAM); `cacheDir` lets the GPU backend cache compiled
shader programs so the *second* load is faster.

### `newSession()` — system prompt, history, sampling
```kotlin
loaded.createConversation(ConversationConfig(
    systemInstruction = Contents.of(systemPrompt),
    initialMessages = history.map { it.toLiteRt() },       // Message.user()/Message.model()
    samplerConfig = SamplerConfig(topK, topP, temperature, seed),
    thinkingConfig = ThinkingConfig(enableThinking = false),
))
```
`initialMessages` is how a reopened chat "remembers" earlier turns. Thinking is disabled
because "reasoning" models (Qwen3) would otherwise write long hidden reasoning first.

### `generate()` — the streaming heart
```kotlin
override fun generate(prompt: String): Flow<String> = flow {
    lock.withLock {
        val conv = checkNotNull(conversation) { "newSession() must be called before generate()" }
        try {
            conv.sendMessageAsync(prompt).collect { message ->
                val text = message.text()
                if (text.isNotEmpty()) { ...; emit(text) }
            }
        } catch (e: CancellationException) {
            conv.cancelProcess()   // the library's Flow does NOT stop native work on cancel
            throw e                // always rethrow cancellation
        } finally {
            if (firstPieceAt >= 0) lastStats = buildStats(...)
        }
    }
}.flowOn(io)
```
How a request travels:
```
user taps Send → DebugLlmViewModel.send() → engine.newSession() (IO, locked)
→ engine.generate(prompt).collect on IO → native decodes token → callback → library Flow
→ our emit(text) → runningFold builds "Hel", "Hello"... → throttleLatest(50 ms)
→ MutableStateFlow update on Main → Compose recomposes the Text
```

### `buildStats()` — honest numbers
We turn on `ExperimentalFlags.enableBenchmark` and read `conv.getBenchmarkInfo()`
(exact time-to-first-token and decode tokens/s from the runtime). If that fails we fall back
to our estimate from `EngineMath.estimateDecodeRate` and mark `fromEngine = false`, so the
UI never presents an estimate as a measurement.

### `TokenEstimator` and `HistoryTrimmer`
`TokenEstimator.estimate` ≈ ASCII chars/4 + other chars/2, rounded up. `HistoryTrimmer.trim`
keeps the newest turns that fit the budget, drops a leading orphan assistant turn, and turns
the dropped turns into a short "Earlier in this conversation:" note. Used in phase 3.

### `FakeLlmEngine` and `MainDispatcherRule`
The fake's `generate` emits `"Hello", " from", " fake"` (configurable), optionally with a
virtual-time delay so tests can press "Stop" mid-answer; it can also fail load/generation.
`MainDispatcherRule` replaces `Dispatchers.Main` because `viewModelScope` needs it.

### `PocketMindApp.onTrimMemory` + `MemoryPressure`
Unloads on `RUNNING_CRITICAL` (15) or `BACKGROUND` (40) and above. The logic sits in a tiny
pure object so it is unit-tested.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| Wrap LiteRT-LM behind an interface | Call it from ViewModels | Testable without a model; runtime swappable; only 1 file knows the vendor API. |
| One Mutex for all native ops | Separate locks, actor | Simplest correct model: one model, one conversation, one generation at a time. Trade-off: `unload()` during a long answer waits for it to finish (or be cancelled). |
| GPU first, CPU fallback | User picks, or CPU only | GPU is usually faster for decode, but OpenCL drivers are missing or buggy on some phones. Fallback = it always works; we record which one won. |
| Context window 4096 tokens | 8k / 32k | KV cache grows linearly with context. 4k fits 6–8 GB phones with a 2B model; long chats are handled by trimming. |
| Engine benchmark counters | Count pieces | Exact; but experimental API — so we fall back and flag estimates. |
| Thinking disabled | Show reasoning | Halves latency on phones; reasoning text isn't useful in a chat bubble. |
| Fake in `core:testing` | Fake inside `core:llm` (as CLAUDE.md sketched) | Keeps test code out of the release APK ("no fake data in release code"). |
| Extractive history note | LLM summary of old turns | Zero latency, deterministic, testable. Quality trade-off documented. |

## 6. Common mistakes

1. **Calling `initialize()` on the main thread** → ANR. We use `withContext(io)`.
2. **`withContext` inside `flow {}`** → "Flow invariant is violated". We use `flowOn(io)`.
3. **Assuming Flow cancellation stops native work** → battery drain + next request blocked.
   We call `cancelProcess()`.
4. **Catching `Exception` and swallowing `CancellationException`** → coroutine that won't
   die. We catch `CancellationException` first and rethrow.
5. **Two engines in memory** (e.g. ViewModel creates its own) → OOM. Singleton via Hilt.
6. **Leaking native memory on failed GPU init** → we `close()` the half-initialized engine.
7. **Updating Compose on every token** → jank. `throttleLatest(50)`.
8. **Reading `lastStats` from another thread without `@Volatile`** → stale values.

## 7. Try it yourself

1. **Show load progress text.** In `DebugLlmScreen`, when `state.engine` is `Loading`, also
   show a `CircularProgressIndicator`. Hint: wrap it in `if (state.engine is EngineState.Loading)`.
2. **Force CPU.** Change `preferGpu = true` to `false` in `DebugLlmViewModel.load()` and compare
   the tok/s label on your phone. Write both numbers down — you'll need them for the README.
3. **Add a `maxOutputTokens` option.** `ConversationConfig` has a `maxOutputToken` parameter
   (we saw it with `javap`). Add it to `SamplingParams` and pass it in `newSession`. Hint:
   it's an `Int?`; `null` means "no limit".

## 8. Interview questions

1. **How does your app stream tokens from a native library to Compose?**
   LiteRT-LM exposes `sendMessageAsync` as a Flow of message pieces. My engine wraps it in
   `flow {}` + `flowOn(io)`, emits text pieces, and the ViewModel folds them into the growing
   answer with `runningFold`, throttles to one update per 50 ms with a conflating operator,
   and writes into a `StateFlow` that Compose collects with lifecycle awareness.

2. **How do you stop generation when the user taps Stop?**
   The ViewModel cancels the collecting Job. I verified in the library's bytecode that its
   `callbackFlow` has an empty `awaitClose`, so cancelling the Flow doesn't stop native work.
   My engine catches `CancellationException`, calls `conversation.cancelProcess()`, and
   rethrows so structured concurrency still works.

3. **Why a Mutex and not `synchronized`?**
   `synchronized` blocks a thread and can't be held across suspension points. `Mutex`
   suspends waiters instead, works across `emit` calls inside the flow, and makes the rule
   "one native operation at a time" explicit: load, new session, generate, unload.

4. **What happens if GPU initialization fails?**
   `tryCreateEngine` catches the failure, closes the partially created engine to free native
   memory, logs it, and the loop tries CPU. The state records the backend that actually loaded,
   which is shown in the UI and the benchmark.

5. **Why is the engine a singleton?**
   The model occupies 0.6–2.6 GB. Two instances would exceed most phones' per-app memory.
   Hilt `@Singleton` guarantees one instance per process, shared by chat, translate, documents.

6. **How do you test code that depends on a 2.6 GB model?**
   Everything depends on the `LlmEngine` interface. Unit tests inject `FakeLlmEngine`, which
   streams scripted pieces with virtual-time delays and can simulate failures. Pure logic
   (backend order, decode-rate math, history trimming) is extracted into plain functions with
   their own tests. Only the real model load is tested manually on a phone.

7. **What is `flowOn` and why not `withContext` inside the flow?**
   `flowOn` changes the dispatcher of the upstream (producer) only, using a channel between
   producer and collector. `withContext` around `emit` would emit from a different context than
   the collector, which violates Flow's context-preservation invariant and throws.

8. **How do you handle low memory?**
   The Application overrides `onTrimMemory`; at running-critical or background levels it
   unloads the model on an application-scoped coroutine. The model is lazily reloaded when a
   screen needs it. Without that, Android would kill the whole process.

9. **How do you keep LiteRT-LM from leaking into other modules?**
   It's an `implementation` dependency of `core:llm`, so its types are not on the compile
   classpath of any other module. Only our interface and value types are public.

10. **How do you measure tokens per second honestly?**
    I prefer the runtime's own counters (`getBenchmarkInfo`: decode tok/s, time to first
    token). If unavailable I estimate from streamed pieces after the first one, excluding
    prefill, and flag the result as an estimate.

## 9. Check your understanding

1. Which dispatcher runs `engine.initialize()` and why?
2. What would happen without `conv.cancelProcess()` when the user taps Stop?
3. Why does `newSession` need the Mutex?
4. Why is `FakeLlmEngine` in `core:testing` and not `core:llm`?
5. What does `required="false"` on `uses-native-library` mean?

---

<details>
<summary>Answers</summary>

1. The injected IO dispatcher — it's a long blocking native call; on Main it would freeze the UI.
2. The UI would stop updating, but the model would keep generating in the background,
   wasting battery and delaying the next request (which waits for the lock).
3. It closes and replaces the conversation; doing that during a generation would free memory
   the native code is still using.
4. So test code is never compiled into the release APK; modules use it only via `testImplementation`.
5. The app still installs and runs on phones without that library; GPU init then fails and we fall back to CPU.
</details>
