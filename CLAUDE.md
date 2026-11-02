# PocketMind — Project Instructions for Claude Code

You are a senior Android engineer building **PocketMind**, an industry-grade **offline AI assistant Android app**. An LLM runs fully on the phone. After the one-time model download the app needs no internet, has no login and sends no data anywhere.

This file is the single source of truth. Read it fully at the start of every session.

---

## 0. v2 — current work (read first)

v1 (sections 1–7 below) is complete and must keep working. v2 turns it into a store-quality product.
**Plan: [V2_PLAN.md](V2_PLAN.md) (approved 2026-11-02). Audit: [AUDIT.md](AUDIT.md). Status: [PROGRESS.md](PROGRESS.md).**
If a session restarts: read this file, V2_PLAN.md and PROGRESS.md, then continue with the next unfinished phase.

### v2 workflow
- Work on git branch `v2`. Phases run in order without stopping for approval.
- Per phase: implement → tests → `./gradlew spotlessApply detekt lint testDebugUnitTest koverVerifyApp assembleDebug`
  → fix until green → conventional commit(s) → update PROGRESS.md → write `docs/learn/v2-phase-N.md`
  (what changed, why, concepts, code walkthrough, trade-offs, 8 interview questions with model answers) → next phase.
- Things only the owner can do go under **"MANUAL STEPS FOR SANJAY"** in PROGRESS.md; do not stop for them.
- Same error 3 times → record under **BLOCKERS** in PROGRESS.md, choose the safest working alternative, continue.
- Anything that needs the real phone is marked "to verify on device" until it has been run there.
- Verify every new library version (Google Maven / Maven Central) and every API (javap or sources of the resolved
  artifact) before use. Never invent versions, prices, sizes or checksums.

### v2 decisions (approved)
1. **No third-party crash SDK.** Local crash log + user-initiated "Share diagnostic report" via the share sheet.
2. **PocketMind Pro** (one-time Play Billing purchase, server-free, verified with `queryPurchasesAsync`) unlocks bigger
   models, unlimited PDF size, personas, extra themes. **Voice stays free** (accessibility).
3. A bigger Pro model (likely Gemma 4 E4B) is added only after its real URL, size and SHA-256 are verified.
4. The Pro price is set by the owner in Play Console; never hard-coded.
5. **Network rule (amends §4):** PocketMind's own code makes no network calls except model downloads. Google Play
   features (Billing, In-App Updates, In-App Review) run through the Play Store app and send no user content.
6. Chats are encrypted with SQLCipher (key wrapped by Android Keystore); the APK size cost is measured and reported.

### v2 quality gates
- Kover: `koverVerifyApp` (merged debug-only variant; the default `koverVerify` would also compile every release
  variant for nothing) rises phase by phase to **≥ 80 % line coverage on data, domain and ViewModel classes**.
  Compose UI is covered by Roborazzi screenshot tests (light/dark, phone/tablet, font 2.0) and Compose UI tests.
- Performance budget lives in `docs/performance-budget.md`; numbers there are measured, never estimated.
- Room schema changes ship with migration tests from every earlier schema version.

### v2 modules (added during v2)
`core:speech` (on-device speech in/out), `core:tools` (LLM tool calling), `core:billing` (Play Billing),
`feature:personas` (personas + prompt library). Dependency rules from §3 apply unchanged.

---

## 1. Product scope

### v1.0 (must ship first)
1. **Offline chat**: streaming answers token by token, stop button, retry, copy, markdown rendering, "12.4 tok/s · GPU" label under each reply.
2. **Chat history**: list, search, rename, delete with undo, auto-title from the first message.
3. **Model manager**: model catalog, RAM-based recommendation, background download with progress, pause/resume/cancel, HTTP Range resume, SHA-256 verification, delete model.
4. **Settings**: model, GPU/CPU preference, temperature, top-K, system prompt, theme (light/dark/system), allow mobile-data downloads.
5. **Benchmark screen**: 3 fixed prompts; measures model load time, time to first token, tokens/sec, peak memory, backend used; stores results; share as text.
6. **Onboarding**: what the app does, model size, privacy promise, "answers can be wrong" disclaimer.

### v1.1 (after v1.0 is working and committed)
7. **PDF summarize**: system file picker, page-by-page text extraction, token-aware chunking, map-reduce summary with progress ("part X of Y") and cancel.
8. **Translate**: language pickers, swap button, fresh LLM session per request, strict prompt template.
9. **Ask your PDF (on-device RAG)**: chunk + embed + store in Room, cosine top-k retrieval, grounded answers citing pages as `[p. N]`, "I don't know" when not in context, tappable source chips.

---

## 2. Tech stack (do not substitute without asking)

| Area | Choice |
|---|---|
| Language | Kotlin 2.x (K2), JDK 17 |
| UI | Jetpack Compose + Material 3, Navigation Compose (type-safe routes) |
| LLM runtime | **LiteRT-LM** — `com.google.ai.edge.litertlm:litertlm-android` |
| Models | `.litertlm` files from Hugging Face `litert-community`: Gemma 4 E2B (default, ~2.6 GB, Apache 2.0), Gemma 3 1B or Qwen2.5 0.5B (lite) |
| DI | Hilt |
| Async | Coroutines + Flow (`StateFlow` for UI state) |
| Storage | Room (with exported schemas), DataStore Preferences |
| Background | WorkManager (foreground `CoroutineWorker`) + OkHttp |
| PDF | PdfBox-Android (`com.tom-roush:pdfbox-android`) |
| Logging | Timber |
| Tests | JUnit, MockK, Turbine, kotlinx-coroutines-test, Room in-memory, Compose UI test, Macrobenchmark + Baseline Profile |
| Quality | Spotless + ktlint, Detekt, Android Lint (warnings as errors) |
| CI | GitHub Actions |

**Never use the MediaPipe LLM Inference API.** Google has put it in maintenance-only mode; LiteRT-LM replaces it.

### LiteRT-LM API (verify before use)
Google's Android guide (https://developers.google.com/edge/litert-lm/android) shows this API. Signatures may differ by version. Before writing engine code, **check the actual classes in the resolved artifact** (inspect the AAR/JAR in the Gradle cache, or the sources jar), and adapt the code to what exists. Tell me if anything differs.

```kotlin
val engine = Engine(EngineConfig(modelPath = path, backend = Backend.GPU())) // or Backend.CPU()
engine.initialize()                       // slow (~10 s): never on the main thread
val conversation = engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of("You are a helpful assistant."),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
    )
)
conversation.sendMessageAsync("Hello").collect { piece -> /* Flow of message pieces */ }
conversation.close(); engine.close()      // always release
```
Calls can throw `LiteRtLmJniException` or `IllegalStateException`.

GPU requires these inside `<application>` in the manifest:
```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

---

## 3. Architecture

Multi-module clean architecture + MVVM + unidirectional data flow.

```
pocketmind/
├── app/                      # Application, MainActivity, NavHost, Hilt
├── build-logic/              # Convention plugins (android-library, android-feature, compose, hilt)
├── core/
│   ├── model/                # Pure Kotlin models: Chat, Message, LlmModelInfo, BenchmarkResult
│   ├── common/               # Result type, dispatcher qualifiers, extensions
│   ├── designsystem/         # Theme, typography, shared composables
│   ├── database/             # Room DB, entities, DAOs, migrations
│   ├── datastore/            # Settings
│   └── llm/                  # LlmEngine interface + LiteRtLmEngine + FakeLlmEngine
├── data/
│   ├── chat/                 # ChatRepository
│   ├── models/               # ModelRepository, ModelDownloadWorker, Sha256, RamChecker
│   └── documents/            # PdfTextExtractor, Chunker, Summarizer, Embedder, VectorStore
├── feature/
│   ├── chat/  history/  models/  settings/  benchmark/  onboarding/
│   ├── documents/  translate/            # v1.1
├── benchmark/                # Macrobenchmark + Baseline Profile generator
├── gradle/libs.versions.toml
├── .github/workflows/        # ci.yml, release.yml
└── docs/                     # architecture.md, benchmarks.md, privacy-policy.md, screenshots
```

**Dependency rules (enforce strictly)**
- `feature:*` → `data:*`, `core:*`. Never `feature` → `feature`.
- `data:*` → `core:*` only.
- **Only `core:llm` imports LiteRT-LM**, behind this interface:

```kotlin
interface LlmEngine {
    val state: StateFlow<EngineState>          // Idle | Loading | Ready(backend, loadMs) | Failed(reason)
    suspend fun load(modelPath: String, preferGpu: Boolean)
    fun newSession(systemPrompt: String, history: List<ChatTurn> = emptyList())
    fun generate(prompt: String): Flow<String>
    suspend fun unload()
}
```

**Engine requirements**
- Singleton; one model in memory at a time.
- `initialize()` on an injected IO/Default dispatcher.
- GPU first, automatic CPU fallback; record which backend loaded.
- `Mutex` so only one generation runs at once; cancellation supported.
- In `flow {}` use `flowOn(...)`, never `withContext`.
- Restore chat history into the conversation when reopening a chat.
- Unload on `onTrimMemory` (critical levels); lazy-load when the chat screen opens, not at app start.
- Trim history to fit the context window; summarize older turns.

**Room is the source of truth.** The streaming answer is held in ViewModel state and written to Room once when finished (or on stop/cancel, marked "stopped"). UI updates from streaming are throttled to at most one every 50 ms.

**Models are never bundled in the APK.** Catalog in `assets/models.json`:
`id, displayName, url (Hugging Face resolve URL pinned to a commit), sizeBytes, sha256, minRamGb`.
Download to `filesDir/models/<id>/model.part`, verify SHA-256 while streaming, then atomic rename to `model.litertlm`. Check free storage ≥ size × 1.2 first. Unmetered network by default.
If you can't obtain the real SHA-256/size for a model file, leave a clearly marked constant and tell me — do not invent values.

---

## 4. Code rules

- Production quality. No `TODO`, no placeholder logic, no fake data in release code.
- Give **complete files** (package + imports) and the file path for each one.
- All versions in `gradle/libs.versions.toml`, pinned to real current releases (never `latest.release`, never `+`). If you can't verify a version, say so.
- Each screen has loading, empty, error and content states. Errors are user-readable with a retry action.
- `StateFlow<UiState>` per ViewModel; one-off events via a `Channel`/`SharedFlow`.
- All user-facing text in `strings.xml` (plus `values-hi/strings.xml` in phase 8).
- Accessibility: content descriptions, 48 dp touch targets, works with TalkBack and large fonts.
- No network calls except model download. HTTPS only (network security config).
- `minSdk 26`, `abiFilters = arm64-v8a`, R8 minify + shrink resources on release.
- `allowBackup` off or backup rules excluding chats and models.
- Unit tests for every ViewModel, repository, chunker, prompt builder and checksum utility, using `FakeLlmEngine`.
- Conventional commits (`feat:`, `fix:`, `test:`, `perf:`, `docs:`, `chore:`).

---

## 5. Build phases

Work **one phase at a time**. For each phase:
1. List the files you will create or change, then implement them.
2. Run `./gradlew spotlessApply detekt lint testDebugUnitTest assembleDebug` and fix every failure.
3. Commit with a conventional commit message.
4. Report: what was built, test results, exact steps for me to verify on my phone, and anything I must do manually.
5. **Stop and wait for me to say "next"** before starting the next phase.

| Phase | Build | Done when |
|---|---|---|
| 1 | Gradle multi-module skeleton, convention plugins, version catalog, Hilt, M3 theme + dark mode, NavHost with empty screens, Spotless, Detekt, CI workflow | `assembleDebug` passes locally and in CI |
| 2 | `core:llm`: `LlmEngine`, `EngineState`, `LiteRtLmEngine`, `FakeLlmEngine`, manifest GPU entries, temporary debug screen loading `/data/local/tmp/llm/model.litertlm` | Phone streams a reply offline |
| 3 | Room (chats, messages), `ChatRepository`, `ChatViewModel`, `ChatScreen`, `HistoryScreen` | Chat works and survives app restart |
| 4 | Model catalog, `ModelRepository`, `ModelDownloadWorker`, RAM check, model picker screen, onboarding; remove debug screen | Fresh install downloads model in-app and chats |
| 5 | DataStore settings, benchmark screen + Room table, Macrobenchmark module, Baseline Profile | Benchmark numbers shown on phone |
| 6 | PDF extraction, chunker, map-reduce summarizer, documents screen, translate screen | 30-page PDF summarized |
| 7 | Embedder interface + implementation, chunk embeddings in Room, cosine retrieval, grounded prompt with page citations, source chips | PDF Q&A cites correct pages |
| 8 | Release config (keystore from env vars), `release.yml` (signed AAB + APK on tag → GitHub Release), network security config, backup rules, biometric lock (optional), OSS licenses screen, privacy policy, Hindi strings, accessibility pass, README, CHANGELOG | `bundleRelease` + `assembleRelease` produce signed files |

### README must include
One-line pitch + demo GIF; Play Store link, APK link, CI badge; features with screenshots; architecture diagram + module graph; benchmark table across phones; "Design decisions and trade-offs" (why LiteRT-LM, why brute-force vector search first, why no analytics); build in 3 commands; roadmap; license.

---

## 6. Things I do manually (remind me at the right phase)
- Push a model to the phone for phase 2: `adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/model.litertlm`
- Run on a real 64-bit phone with 6 GB+ RAM (emulators are too slow for LLMs).
- Create the release keystore and add GitHub secrets (phase 8).
- Play Console: listing, Data safety form, closed test with 12+ testers for 14 days (new personal accounts).
- Record real benchmark numbers on 3 phones for the README and resume.

## 7. When unsure
Ask instead of guessing — especially for library API signatures, version numbers, model URLs and checksums. Never present an unverified number as measured.
