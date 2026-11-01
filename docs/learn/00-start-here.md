# 00 — Start here: the Android basics behind PocketMind

You know AI and systems engineering; this file gives you the Android-specific foundation so the
phase lessons make sense. Read it once, then read the phases in order (see the reading order at the end).

---

## 1. What an Android app is

An app is an **APK** (or, for Google Play, an **AAB** that Play turns into APKs): a zip file with
- **DEX bytecode** — your Kotlin compiled for Android's runtime (ART),
- **resources** — strings, icons, XML (`res/`),
- **native libraries** — C/C++ `.so` files per CPU type (LiteRT-LM's `liblitertlm_jni.so` for `arm64-v8a`),
- **AndroidManifest.xml** — the app's declaration: name, permissions, components,
- a **signature** — proof of who built it; updates must be signed with the same key.

The phone installs it into a private sandbox: each app runs as its own Linux user and can only see its own
files (`filesDir`) unless the user explicitly grants access.

## 2. Components and the Activity lifecycle

Android starts apps through **components** declared in the manifest. PocketMind uses:
- one **Activity** (`MainActivity`) — a window with UI,
- an **Application** class (`PocketMindApp`) — created once per process, before anything else,
- **WorkManager's foreground service** — for the background model download.

An Activity moves through lifecycle states as the user navigates:

```
created → started (visible) → resumed (in focus)
   ▲                                  │ user presses Home / another app opens
   │                                  ▼
destroyed ◀── stopped (not visible) ◀── paused
```

Important consequences:
- **Rotation destroys and recreates the Activity.** Anything stored only in the Activity is lost → we keep
  screen state in **ViewModels** (they survive rotation) and small things in `rememberSaveable`/`SavedStateHandle`.
- **Android may kill a background app process** to free memory. When the user returns, the app restarts;
  `SavedStateHandle` restores where they were, and **Room** (the database) holds the real data.
- `onTrimMemory` tells you memory is low → we unload the 2.6 GB model before Android kills us.

## 3. Threads: the main thread rule

All UI work happens on the **main thread**. If it is blocked for ~5 seconds, Android shows "App Not
Responding". So: model loading (~10 s), PDF parsing, file I/O, database queries and network must run on
**background threads**. Kotlin **coroutines** make that readable:

```kotlin
suspend fun load() = withContext(Dispatchers.IO) { engine.initialize() }   // runs on a background pool
```
`suspend` = "can pause without blocking a thread". **Flow** = a stream of values over time (e.g. tokens).

## 4. Kotlin essentials used everywhere

| Feature | Example | Why it matters here |
|---|---|---|
| `val` / `var` | `val id = 1` | Prefer immutable values; state changes are explicit |
| Null safety | `String?`, `?.`, `?:` | `settings.current() ?: return Failure(NO_MODEL)` |
| `data class` | `data class Chat(...)` | Value objects with equals/copy — UI state is data classes |
| `sealed interface` | `EngineState` | The compiler forces every state to be handled in `when` |
| Extension functions | `fun Flow<T>.throttleLatest()` | Add helpers without inheritance |
| Lambdas | `onClick = { send() }` | Compose and callbacks |
| `object` | `TextChunker` | Singletons for pure helpers |
| Coroutines / Flow | `viewModelScope.launch {}` | Async without callback hell |

## 5. Gradle: how the app is built

**Gradle** is the build tool; the **Android Gradle Plugin (AGP)** teaches it Android. A build is a graph
of tasks: compile Kotlin → generate code (Hilt, Room via KSP) → merge resources/manifests → convert to DEX →
(release) shrink with R8 → package → sign.

Key files:
- `settings.gradle.kts` — which modules exist.
- `gradle/libs.versions.toml` — every library version.
- `build-logic/` — our reusable build recipes (convention plugins).
- `<module>/build.gradle.kts` — what a module is and depends on.
- `./gradlew assembleDebug` — build a debug APK; `testDebugUnitTest` — run JVM tests; `lint` — Android checks.

**Debug vs release:** debug is fast to build, debuggable and unoptimized; release is minified by R8,
resource-shrunk and signed with your release key.

## 6. How an APK gets onto your phone

1. Enable **Developer options** (tap *Build number* 7 times) and **USB debugging**.
2. Connect USB; `adb devices` shows the phone (adb = Android Debug Bridge, in `platform-tools`).
3. `./gradlew installDebug` (or `adb install app-debug.apk`).
4. The system verifies the signature, extracts native libs for the phone's ABI, compiles DEX ahead of time
   using the Baseline Profile where available, and adds the launcher icon.

## 7. Jetpack Compose in one page

UI is written as Kotlin functions marked `@Composable`. They describe what the screen should look like **for
the current state**. When state changes, Compose re-runs ("recomposes") only the affected functions.

```kotlin
@Composable
fun Greeting(name: String) { Text("Hello $name") }
```
- `remember { }` keeps a value across recompositions; `rememberSaveable` also across rotation.
- `collectAsStateWithLifecycle()` turns a `StateFlow` from a ViewModel into Compose state, pausing when
  the app is in the background.
- `Modifier` chains layout/behaviour: `.padding(16.dp).fillMaxWidth()`.
- `LazyColumn` shows long lists efficiently (only visible rows exist).

## 8. The PocketMind architecture

```
┌──────────────────────────── :app ─────────────────────────────┐
│ MainActivity → theme → NavHost (bottom tabs) → feature screens│
└───────────────────────────────────────────────────────────────┘
   feature:chat  history  documents  translate  settings  models  benchmark  onboarding
        │  (Compose screen + ViewModel; StateFlow<UiState> down, events up)
        ▼
   data:chat   data:models   data:documents   data:benchmark      (repositories)
        │
        ▼
   core:llm (LlmEngine/TextEmbedder → LiteRT-LM)   core:database (Room)   core:datastore
   core:model (plain data)   core:common (dispatchers, Result)   core:designsystem (theme)
```

**Unidirectional data flow:** the screen sends an event (`onSend("hi")`) → the ViewModel calls a repository
→ the repository reads/writes Room and the engine → new data flows back up as a `StateFlow` → the screen
recomposes. Data never flows the other way.

**Rules enforced by the build:** features don't depend on features; data depends only on core; only
`core:llm` can see LiteRT-LM.

Full details: `docs/architecture.md`.

## 9. Glossary (every technical term used in the project)

| Term | Meaning |
|---|---|
| AAB | Android App Bundle: the upload format for Google Play |
| ABI | CPU instruction set of native code; we ship `arm64-v8a` (64-bit ARM) |
| Activity | A screen/window component; we have exactly one |
| adb | Command-line tool to talk to a phone over USB |
| AGP | Android Gradle Plugin |
| ANR | "App Not Responding" — main thread blocked too long |
| AOT | Ahead-of-time compilation of code at install (Baseline Profiles) |
| APK | Installable Android package |
| Application class | Created once per process before any screen (`PocketMindApp`) |
| ART | Android Runtime that executes DEX bytecode |
| AutoMigration | Room-generated database migration between schema versions |
| Baseline Profile | List of hot code compiled AOT for faster startup |
| BiometricPrompt | System UI for fingerprint/face/PIN authentication |
| BOM | "Bill of materials": picks matching versions for a library family (Compose) |
| Cascade delete | Deleting a parent row deletes its children automatically |
| Channel | Coroutine queue; used for one-off UI events |
| CI | Continuous integration (GitHub Actions) |
| Compose | Android's declarative UI toolkit |
| Context window | Max tokens an LLM sees at once |
| Convention plugin | Our own Gradle plugin with shared build config |
| Coroutine | Lightweight concurrent task in Kotlin |
| Cosine similarity | Angle-based similarity of two vectors |
| DAO | Data Access Object: Room interface with SQL queries |
| DataStore | Async key-value storage (settings) |
| Detekt | Static analysis for Kotlin code smells |
| DEX | Android bytecode format |
| DI | Dependency injection (Hilt) |
| Dispatcher | Decides which thread(s) a coroutine runs on |
| Embedding | Vector representing a text's meaning |
| Entity | Room class mapped to a table |
| Flow / StateFlow | Stream of values / stream that always holds a current value |
| Foreground service | Long-running work with a visible notification |
| Hilt | Google's DI library for Android (built on Dagger) |
| Inference | Running a trained model to get output |
| JNI | Java Native Interface: Kotlin ↔ C++ calls |
| KSP | Kotlin Symbol Processing: code generation at compile time |
| KV cache | Per-token LLM state that speeds up decoding |
| LazyColumn | Compose list that only renders visible items |
| Lint | Android's static checker |
| LiteRT-LM | Google's on-device LLM runtime |
| Macrobenchmark | Measures startup/frames of a release-like build on a device |
| Manifest | `AndroidManifest.xml`, the app's declaration |
| Migration | Code that upgrades an existing database schema |
| Module | Separately compiled part of the project |
| Mutex | Lock for coroutines (one at a time) |
| MVVM | Model–View–ViewModel architecture |
| Navigation Compose | Screen-to-screen navigation with type-safe routes |
| NPU | Neural processing unit (AI accelerator chip) |
| OkHttp | HTTP client library |
| PSS | Process memory measure incl. native memory |
| Quantization | Storing model weights with fewer bits |
| R8 | Code shrinker/optimizer/obfuscator for release builds |
| RAG | Retrieval-Augmented Generation |
| Recomposition | Compose re-running UI functions after state changes |
| Repository | Class that owns a type of data and hides its sources |
| Robolectric | Runs Android code in JVM unit tests |
| Room | SQLite library with compile-time-checked SQL |
| SAF | Storage Access Framework: the system file picker |
| SavedStateHandle | Small state that survives process death |
| SHA-256 | Cryptographic file fingerprint |
| Singleton | One instance per app process |
| Spotless / ktlint | Automatic code formatting |
| Token | Unit of text an LLM processes |
| TTFT | Time to first token |
| Turbine | Library for testing Flows |
| TypeConverter | Teaches Room to store custom types |
| UiState | Immutable data class describing a whole screen |
| ViewModel | Screen logic that survives rotation |
| WorkManager | Guaranteed background work scheduler |

## 10. Reading order

1. `00-start-here.md` (this file)
2. `phase-1.md` — project structure, Gradle, Hilt, Compose, CI
3. `on-device-llm.md` — how the model works (tokens, KV cache, quantization, GPU/CPU)
4. `phase-2.md` — the LLM engine
5. `phase-3.md` — chat, Room, ViewModels, Flow
6. `phase-4.md` — downloads, WorkManager, DataStore, onboarding
7. `phase-5.md` — settings, benchmark, migrations, Baseline Profiles
8. `phase-6.md` — PDFs, chunking, map-reduce, translation
9. `rag-explained.md` — RAG from zero
10. `phase-7.md` — Ask your PDF
11. `phase-8.md` — release, security, the final review
12. `interview-pitch.md` — how to present all of it
