# PocketMind architecture

## Layers

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ UI — Jetpack Compose screens (feature modules)                              │
│ Chat · History · Documents (Summarize / Ask) · Translate · Models ·         │
│ Settings · Benchmark · Onboarding                                           │
├──────────────────────────────────────────────────────────────────────────────┤
│ ViewModels — one StateFlow<UiState> per screen, one-off events via Channel   │
├──────────────────────────────────────────────────────────────────────────────┤
│ Data — repositories                                                          │
│ ChatRepository · ModelRepository · BenchmarkRunner · Summarizer · Translator│
│ DocumentRepository (RAG)                                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│ Core — data sources                                                          │
│ Room (chats, messages, benchmark_runs, documents, chunks) · DataStore        │
│ LlmEngine + TextEmbedder (LiteRT-LM, GPU→CPU) · ModelFiles · dispatchers     │
└──────────────────────────────────────────────────────────────────────────────┘
                 │ one-time download (WorkManager + OkHttp, HTTPS, allowlisted)
                 ▼
         huggingface.co/litert-community  (the ONLY network call)
```

## Module graph

```
                                   :app
       ┌─────────┬─────────┬──────────┼──────────┬───────────┬───────────┬──────────┐
 feature:chat history models settings benchmark onboarding documents translate
       │         │        │      │        │          │          │          │
       ▼         ▼        ▼      ▼        ▼          ▼          ▼          ▼
   data:chat ◀───┘   data:models ◀──┴─────────┘ data:benchmark  data:documents ◀──┘
       │                  │                         │              │
       └──────────┬───────┴─────────────┬───────────┴──────────────┘
                  ▼                     ▼
   core:database  core:datastore  core:llm (only module importing LiteRT-LM)
                  │                     │
                  └──── core:model ◀────┘   core:common   core:designsystem
 core:testing (test-only: FakeLlmEngine, FakeTextEmbedder, InMemoryPreferencesDataStore)
 :benchmark (Macrobenchmark + Baseline Profile, drives :app)
```

Rules (enforced by a Gradle check in the root `build.gradle.kts`):
- `feature:*` → `data:*`, `core:*` — never another feature.
- `data:*` → `core:*` only.
- `core:*` → `core:*` only.
- LiteRT-LM is an `implementation` dependency of `core:llm`, so no other module can even compile
  against it.

## Key flows

**Chat message** — `ChatScreen → ChatViewModel.send → ChatRepository.sendMessage` (load model if needed,
rebuild the native conversation from Room only when it's stale, save user message, auto-title) →
`LlmEngine.generate` (Flow on IO, Mutex, cancel → `cancelProcess()`) → pieces throttled to 50 ms →
`StateFlow<ChatUiState>` → on finish/stop/fail the answer is written to Room once.

**Model download** — `ModelsViewModel.download → WorkManagerModelRepository` (storage check, unique work,
Wi-Fi constraint) → `ModelDownloadWorker` (foreground `dataSync`) → `ModelDownloader` (Range resume,
SHA-256 while streaming, atomic rename) → status derived from WorkInfo + files.

**Ask your PDF** — index: extract → chunk per page → embed → Room (transaction); ask: embed question →
cosine top-4 → grounded prompt → fresh session → streamed answer with `[p. N]` → highlighted chips.

## Concurrency model

- One `LlmEngine` (singleton) with one native conversation, guarded by a `Mutex`.
- Several features share it; `LlmEngine.sessionId` is an ownership token so each feature rebuilds its
  session if another feature replaced it.
- All native calls on the injected IO dispatcher; CPU-heavy work (similarity search) on Default.
- `onTrimMemory` unloads the model; screens lazily reload it.
