# PocketMind — Build Progress

Read this file and CLAUDE.md first when a session restarts. Never redo finished phases.

## Status

| Phase | State | Commit |
|---|---|---|
| 1 Skeleton, build, theme, nav, CI | DONE | feat(phase-1) |
| 2 core:llm engine + debug screen | DONE | feat(phase-2) |
| 3 Room, chat, history | DONE | feat(phase-3) |
| 4 Model manager, onboarding | DONE | feat(phase-4) |
| 5 Settings, benchmark, macrobenchmark | DONE | feat(phase-5) |
| 6 PDF summarize, translate | next | |
| 7 RAG (ask your PDF) | pending | |
| 8 Release, a11y, Hindi, README | pending | |

## Environment (set up 2026-11-01)

- JDK 17: Temurin 17.0.20 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` (JAVA_HOME, runs Gradle).
- JDK 21: Temurin 21.0.13 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (RUNS unit tests via toolchain).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools, platform-tools, platforms 36 + 37, build-tools 36) — ANDROID_HOME set.
- Git 2.56 (winget). Gradle 9.8.2 via wrapper. No Android Studio installed (not needed to build).
- Open a NEW terminal so the JAVA_HOME / ANDROID_HOME / PATH changes are visible.

## Verified versions (Google Maven / Maven Central, 2026-11-01)

AGP 9.5.0 · Kotlin 2.4.21 · KSP 2.3.13 · Hilt 2.60.1 · Compose BOM 2026.09.00 · Navigation 2.10.2 ·
Room 2.8.5 · DataStore 1.3.0 · WorkManager 2.12.0 · LiteRT-LM 0.19.0 · OkHttp 5.5.1 · PdfBox-Android 2.0.27.0 ·
Benchmark/Baseline Profile 1.5.0 · Spotless 8.10.4 / ktlint 1.8.0 · Detekt 1.23.8. Full list: `gradle/libs.versions.toml`.

## Differences from CLAUDE.md (decisions taken)

1. **Model repo name**: the real HF repo is `litert-community/gemma-4-E2B-it-litert-lm` (not `-litertlm`).
2. **Gemma 3 1B is gated** on Hugging Face (needs login + licence acceptance) → cannot be downloaded
   anonymously by the app. **Qwen2.5 0.5B has no `.litertlm` file** (only `.task`/`.tflite`).
   Lite models used instead: **Qwen3 0.6B** and **Qwen3.5 0.8B** (Apache 2.0, real `.litertlm`).
3. **Real sizes + SHA-256** were read from Hugging Face LFS metadata (the LFS oid *is* the SHA-256):
   - gemma-4-E2B-it.litertlm — 2,588,147,712 B — 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c — commit 1caa527c1e88b55f046fcd8d2b4265606680663d
   - Qwen3.5-0.8B_int8.litertlm — 964,566,992 B — 25447e2550082aed6145bbc755ec4da5d535f98f6a0c386865fdae2ccb88aed1 — commit 0edf7e8cac6bbd035b4516c4303e2c0518391e4b
   - Qwen3-0.6B.litertlm — 613,722,264 B — c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108 — commit 2670e142a0d16a1b685be6604e5b4f2b31a826d7
   - embeddinggemma-2-text-270m.litertlm (RAG) — 164,626,432 B — 2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb — commit 9be6e8b90982095dc05c2bd162e4b954ee4dbac7
4. **LiteRT-LM 0.19.0 API (inspected with javap)** vs CLAUDE.md:
   - `EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, ...)` — we use `maxNumTokens`, `cacheDir`.
   - `SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int)` — has a `seed`.
   - `ConversationConfig(systemInstruction, initialMessages, tools, samplerConfig, ..., thinkingConfig, ...)` — `initialMessages` restores history.
   - `Conversation.sendMessageAsync(String): Flow<Message>` — **cancelling the Flow does NOT stop native generation**
     (its `awaitClose {}` is empty). Our engine calls `conversation.cancelProcess()`.
   - Extras used: `getBenchmarkInfo()` (needs `ExperimentalFlags.enableBenchmark`, `@OptIn(ExperimentalApi)`),
     `ThinkingConfig(enableThinking = false)`.
   - `EmbeddingEngine` exists → embeddings for RAG also run in `core:llm` (only module allowed to import LiteRT-LM).
   - Library minSdk 24; built with Kotlin 2.4 → project must use Kotlin ≥ 2.4.
5. **AGP 9 built-in Kotlin**: no `org.jetbrains.kotlin.android` plugin. **Detekt** runs once at the root.
6. `targetSdk 36` (Google Play requirement), `compileSdk 37`.
7. `LlmEngine` additions: `newSession` is `suspend` + takes `SamplingParams`; `lastStats`, `contextWindowTokens`,
   `sessionId` (ownership token so chat/benchmark/translate can share one conversation safely).
8. `FakeLlmEngine` lives in `core:testing` (test-only) instead of `core:llm`, so it can never ship in the APK.
9. Extra module `data:benchmark` (runner + repository) to keep the repository pattern for the benchmark screen.

## Test results

- Phase 1: 5 unit tests. Phase 2: 22. Phase 3: 58. Phase 4: 83. **Phase 5: 98 unit tests, 0 failures.**
- Full pipeline `spotlessApply detekt lint testDebugUnitTest assembleDebug`: GREEN after every phase.
- NOT verified on a phone yet (no device attached to this PC). Macrobenchmark/Baseline Profile need a phone.

## Phase notes

- Phase 3: `core:database` (Room v1), `data:chat` (OfflineChatRepository keeps the native conversation in sync with Room).
  JDK 21 added for the unit-test runtime; Robolectric test JVM flags: `--add-opens java.base/java.io|java.lang|java.util`,
  `--add-exports java.base/jdk.internal.access`.
- Phase 4: `core:datastore`, `data:models` (catalog, downloader, worker, repository, SettingsInferenceSource).
  Debug screen removed. Only network code: `data:models` OkHttpClient with `HostAllowlist`.
  Unit tests use `InMemoryPreferencesDataStore` (Windows JVM can't replace DataStore files).
- Phase 5: Room v2 (AutoMigration 1→2, `benchmark_runs`), tested by building a real v1 DB from `schemas/1.json`.
  `data:benchmark`, `feature:benchmark`, `feature:settings`, `:benchmark` (Macrobenchmark + Baseline Profile).

## Open issues

- None.

## BLOCKERS

- None.

## MANUAL STEPS FOR MANJIT

1. Create a GitHub repo `pocketmind`, then: `git remote add origin <url>` and `git push -u origin main`.
   CI (`.github/workflows/ci.yml`) runs automatically.
2. Test on a real 64-bit phone with 6 GB+ RAM (USB debugging on): `./gradlew installDebug`, open the app,
   finish onboarding, download the recommended model on Wi-Fi, chat. Emulators are too slow for LLMs.
3. (Optional) Instead of the in-app download you can push a model for quick tests — no longer needed since phase 4.
4. Phase 5 — real numbers:
   - In the app: Settings → Benchmark → Run. Share the result text. Repeat on 3 phones (flagship, mid, budget).
   - Startup: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` (phone connected).
   - Baseline Profile: `./gradlew :app:generateBaselineProfile`, then commit
     `app/src/release/generated/baselineProfiles/`.

## Next step

Phase 6: PDF text extraction (PdfBox-Android), token-aware chunker, map-reduce summarizer with progress/cancel,
documents screen (summarize), translate screen with fresh session per request.
