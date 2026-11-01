# PocketMind — Build Progress

Read this file and CLAUDE.md first when a session restarts. Never redo finished phases.

## Status

| Phase | State | Commit |
|---|---|---|
| 1 Skeleton, build, theme, nav, CI | DONE | feat(phase-1) |
| 2 core:llm engine + debug screen | DONE | feat(phase-2) |
| 3 Room, chat, history | DONE | feat(phase-3) |
| 4 Model manager, onboarding | pending | |
| 5 Settings, benchmark, macrobenchmark | pending | |
| 6 PDF summarize, translate | pending | |
| 7 RAG (ask your PDF) | pending | |
| 8 Release, a11y, Hindi, README | pending | |

## Environment (set up 2026-11-01)

- JDK: Temurin 17.0.20 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` (JAVA_HOME set for the user)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools, platform-tools, platforms 36 + 37, build-tools 36) — ANDROID_HOME set
- Git 2.56 (winget). Gradle 9.8.2 via wrapper. No Android Studio installed (not needed to build).
- Open a NEW terminal so the JAVA_HOME / ANDROID_HOME / PATH changes are visible.

## Verified versions (Google Maven / Maven Central, 2026-11-01)

AGP 9.5.0 · Kotlin 2.4.21 · KSP 2.3.13 · Hilt 2.60.1 · Compose BOM 2026.09.00 · Navigation 2.10.2 ·
Room 2.8.5 · DataStore 1.3.0 · WorkManager 2.12.0 · LiteRT-LM 0.19.0 · OkHttp 5.5.1 · PdfBox-Android 2.0.27.0 ·
Spotless 8.10.4 / ktlint 1.8.0 · Detekt 1.23.8. Full list: `gradle/libs.versions.toml`.

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
   - `EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, ...)` — extra params we use: `maxNumTokens`, `cacheDir`.
   - `SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int)` — has a `seed`.
   - `ConversationConfig(systemInstruction, initialMessages, tools, samplerConfig, ..., thinkingConfig, ...)` — `initialMessages: List<Message>` restores history (`Message.user()/model()`).
   - `Conversation.sendMessageAsync(String): Flow<Message>` — **cancelling the Flow does NOT stop native generation** (its `awaitClose {}` is empty). We must call `conversation.cancelProcess()` ourselves.
   - Extras: `getBenchmarkInfo()` (TTFT, prefill/decode tok/s — needs `ExperimentalFlags.enableBenchmark`), `getTokenCount()`, `ThinkingConfig(enableThinking)` (turn off Qwen3 "thinking").
   - `EmbeddingEngine` exists → embeddings for RAG also run in `core:llm` (only module allowed to import LiteRT-LM).
   - Library minSdk 24; built with Kotlin 2.4 → project must use Kotlin ≥ 2.4.
5. **AGP 9 built-in Kotlin**: no `org.jetbrains.kotlin.android` plugin. **Detekt** runs once at the root
   (Detekt 1.23.8 predates AGP 9; root-level avoids its AGP integration).
6. `targetSdk 36` (Google Play requirement), `compileSdk 37`.
7. Backup rules (`data_extraction_rules.xml`) added in phase 1 because Lint requires them.

## Test results

- Phase 1: 5 unit tests, all passing (`core:common`: throttleLatest, AppResult).
- Phase 2: +17 tests (core:llm EngineMath/TokenEstimator/HistoryTrimmer, app DebugLlmViewModel with
  FakeLlmEngine, MemoryPressure). LiteRtLmEngine compiles against the real LiteRT-LM 0.19.0 API.
  NOT verified on a phone yet (no device attached to this PC).
- Phase 3: 58 unit tests total, 0 failures (ChatDao, OfflineChatRepository, ChatTitles, ChatViewModel,
  ChatContent Compose UI, HistoryViewModel). Full pipeline GREEN.
- Full pipeline `spotlessApply detekt lint testDebugUnitTest assembleDebug`: GREEN.

## Phase 3 notes

- New modules: `core:database` (Room v1: chats, messages; FK cascade; schemas in `core/database/schemas`),
  `data:chat` (OfflineChatRepository keeps the native conversation in sync with Room).
- `InferenceSettingsSource` (core:llm) decouples "which model / prompt / sampling" from storage.
  Phase 3 binds `AdbModelSettingsSource` (app/di) → `/data/local/tmp/llm/model.litertlm`.
- Debug "Engine test" screen is still registered but no longer the start screen (removed in phase 4).
- **JDK 21 added (2026-11-01)** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`: unit tests RUN on
  JDK 21 via a Gradle toolchain (Robolectric's Android 36 image + markdown-renderer 0.46 Java-21 bytecode need it).
  Code still compiles to Java 17 bytecode; Gradle itself still runs on JDK 17. CI installs both.
- Robolectric test JVM flags: `--add-opens java.base/java.io|java.lang|java.util`, `--add-exports java.base/jdk.internal.access`.

## Open issues

- None.

## BLOCKERS

- None.

## MANUAL STEPS FOR MANJIT

1. Create a GitHub repo `pocketmind`, then: `git remote add origin <url>` and `git push -u origin main`.
   CI (`.github/workflows/ci.yml`) runs automatically.
2. (Phase 2/3 testing, optional — phase 4 adds in-app download) Push a model for the engine test screen:
   - `pip install -U huggingface_hub` then
     `huggingface-cli download litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --local-dir .`
     (or Qwen3-0.6B.litertlm from litert-community/Qwen3-0.6B for a quick 600 MB test)
   - `adb shell mkdir -p /data/local/tmp/llm`
   - `adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/model.litertlm`
   - Install the debug APK, open the app → "Engine test" → Load model → Send. Note tok/s + backend.

## Next step

Phase 4: model catalog (assets/models.json with the verified HF values above), core:datastore (selected model,
onboarding done, mobile data), data:models (ModelRepository, ModelDownloadWorker, Sha256, RamChecker, storage
check), feature:models picker, feature:onboarding; replace AdbModelSettingsSource; remove debug screen.
