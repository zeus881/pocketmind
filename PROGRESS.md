# PocketMind — Build Progress

Read this file and CLAUDE.md first when a session restarts. Never redo finished phases.

## v2 status (branch `v2`, plan: V2_PLAN.md, approved 2026-11-02)

| Phase | State | Coverage gate (logic) | Notes |
|---|---|---|---|
| 0 Foundations: Kover, config cache, Dependabot, CI coverage, perf budget | DONE | 66 % (measured 66.8 %) | `koverVerifyApp` = debug-only merged variant |
| 1 Crash-loop guard + model integrity | DONE | 67 % (measured 67.0 %, 158 tests) | to verify on device: damaged-file message, "Check file" time |
| 2 App lock overlay + grace period | DONE | 67 % (measured 67.9 %, 172 tests) | KSP incremental off (lost Hilt outputs); to verify on device: lock + PDF picker, Recents thumbnail |
| 3 Memory policy + download robustness | DONE | 67 % (measured 67.7 %, 177 tests) | to verify on device: no reload after a 30 s app switch |
| 4 Tests for engine, worker, model repository | DONE | 78 % (measured 78.0 %, 201 tests) | engine device test written; run manually (see manual steps) |
| 5 Versioning + quick wins | DONE | 78 % (measured 78.3 %, 203 tests) | release APK 31.2 → 27.27 MB; version from git tag |
| 6 StrictMode, LeakCanary, perf budget, Baseline Profile | DONE | 79 % (measured 79.6 %, 204 tests) | 2 main-thread disk reads fixed; benchmarks + Baseline Profile need the phone |
| 7 Smarter context (summaries, context size) | DONE | 80 % (measured 80.1 %, 213 tests) | Room v4; 8k context deferred until memory is measured on a phone |
| 8 Design system v2 | DONE | 80 % (measured 80.5 %, 217 tests) | tokens everywhere (92 raw dp replaced), checkDesignTokens in lint, dynamic-colour switch |
| 9 Adaptive layouts, edge-to-edge, predictive back | DONE | 80 % (measured 80.6 %, 218 tests) | rail on wide windows, History list-detail at 840 dp+; build memory capped (workers 6) after 2 stalls |
| 10 Screen redesign + Roborazzi screenshot tests | DONE | 81 % (measured 81.1 %, 234 tests) | 64 goldens (16 states x phone light/dark, tablet, font 200 %); CI verifies |
| 11 Database v5: pin, folders, branches, FTS | DONE | 81 % (measured 81.7 %, 245 tests) | manual migration 4->5 tested from v1-v4; to verify on device: upgrade with a copy of real chats |
| 12 Edit/resend, versions, branches, pin, folders, search | DONE | 81 % (measured 81.5 %, 265 tests) | regenerate keeps old answers as versions; screenshots updated |
| 13 Export, share-in, widget, quick-settings tile | DONE | 81 % (measured 81.3 %, 285 tests) | to verify on device: share text/PDF, widget (no titles when locked), tile, PDF export |
| 14 Personas + prompt library | DONE | 81 % (measured 81.2 %, 294 tests) | Room v6; new module feature:personas; Pro gate comes in phase 20 |
| 15 Voice in (on-device) and spoken answers | DONE | 81 % (measured 81.4 %, 308 tests) | new core:speech; Android 12+ on-device recognizer only; offline TTS voices only |
| 16 Image questions (if the model supports vision) | DONE | 81 % (measured 81.1 %, 329 tests) | vision verified in the file (Gemma 4 E2B only); Room v7; photo picker, no permission; to verify on device |
| 17 Tool calling (calculator, date, units, notes) | DONE | 82 % (measured 82.5 %, 349 tests) | new core:tools; Room v8; tools for Gemma 4 E2B + Qwen3 0.6B (template checked); KSP cache fix; gate to verify on device |
| 18 Encryption (SQLCipher) and data control | DONE | 82 % (measured 82.1 %, 362 tests) | SQLCipher 4.20.0, key wrapped by Keystore, verified one-time migration; export/delete all data; APK 30.22 MB (SQLCipher 2.01 MB); to verify on device |
| 19 Release notes, In-App Updates/Review, Play publishing | DONE | 82 % (measured 82.0 %, 373 tests) | Play app-update 2.1.0, review 2.0.2, Play Publisher 4.1.1; play.yml → internal track; needs the service-account secret |
| 20 PocketMind Pro (Play Billing) + local diagnostics | DONE | 82 % (measured 82.6 %, 388 tests) | Billing 9.1.0 in new core:billing; gates: Pro models, PDFs > 50 pages (my choice, one constant), new custom personas; extra themes not built (none exist); crash log + share report; builds now serial (BLOCKERS) |
| 21 Punjabi, Hindi review, per-app language, accessibility | DONE | 82 % (measured 82.5 %, 388 tests) | values-pa in all 12 modules (keys checked against en and hi); localeConfig + LocaleManager picker (Android 13+); accessibility audited in code; native review and TalkBack walk to do on device |
| 22 End-to-end Compose UI tests + CI gates | DONE | 84 % (measured 84.2 %, 392 tests) | 3 E2E tests on the real app (Robolectric + Hilt test modules) found and fixed the "No model yet after first download" bug; KSP serialized (KspLock), parallel builds back on; PR coverage comment |
| Final: release build, AUDIT_AFTER.md, README, report | DONE | 84 % (measured 84.2 %, 392 tests) | assembleRelease + bundleRelease green (unsigned: APK 30.59 MB, AAB 23.40 MB; one R8 -dontwarn for a Play annotation); AUDIT_AFTER.md scores 62 → 79; README rewritten |

Check command for v2: `./gradlew spotlessApply detekt lint testDebugUnitTest koverVerifyApp assembleDebug`.

## v1 status: ALL 8 PHASES DONE

| Phase | State | Commit |
|---|---|---|
| 1 Skeleton, build, theme, nav, CI | DONE | feat(phase-1) |
| 2 core:llm engine + debug screen | DONE | feat(phase-2) |
| 3 Room, chat, history | DONE | feat(phase-3) |
| 4 Model manager, onboarding | DONE | feat(phase-4) |
| 5 Settings, benchmark, macrobenchmark | DONE | feat(phase-5) |
| 6 PDF summarize, translate | DONE | feat(phase-6) |
| 7 RAG (ask your PDF) | DONE | feat(phase-7) |
| 8 Release, a11y, Hindi, README, review | DONE | feat(phase-8) + fix(review) |

## Environment (set up 2026-11-01)

- JDK 17: Temurin 17.0.20 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` (JAVA_HOME, runs Gradle).
- JDK 21: Temurin 21.0.13 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (RUNS unit tests via toolchain).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools, platform-tools, platforms 36 + 37, build-tools 36) — ANDROID_HOME set.
- Git 2.56 (winget). Gradle 9.8.2 via wrapper. No Android Studio installed (not needed to build).
- Open a NEW terminal so the JAVA_HOME / ANDROID_HOME / PATH changes are visible.

## Verified versions (Google Maven / Maven Central, 2026-11-01)

AGP 9.5.0 · Kotlin 2.4.21 · KSP 2.3.13 · Hilt 2.60.1 · Compose BOM 2026.09.00 · Navigation 2.10.2 ·
Room 2.8.5 · DataStore 1.3.0 · WorkManager 2.12.0 · LiteRT-LM 0.19.0 · OkHttp 5.5.1 · PdfBox-Android 2.0.27.0 ·
Benchmark/Baseline Profile 1.5.0 · AboutLibraries 15.3.0 · Biometric 1.1.0 · Spotless 8.10.4 / ktlint 1.8.0 ·
Detekt 1.23.8. Full list: `gradle/libs.versions.toml`.

## Differences from CLAUDE.md (decisions taken)

1. **Model repo name**: the real HF repo is `litert-community/gemma-4-E2B-it-litert-lm` (not `-litertlm`).
2. **Gemma 3 1B is gated** on Hugging Face (login + licence) → can't be downloaded anonymously. **Qwen2.5 0.5B has
   no `.litertlm` file**. Lite models used instead: **Qwen3 0.6B** and **Qwen3.5 0.8B** (Apache 2.0).
3. **Real sizes + SHA-256** from Hugging Face LFS metadata (the LFS oid *is* the SHA-256), URLs pinned to commits:
   - gemma-4-E2B-it.litertlm — 2,588,147,712 B — 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c — commit 1caa527c1e88b55f046fcd8d2b4265606680663d
   - Qwen3.5-0.8B_int8.litertlm — 964,566,992 B — 25447e2550082aed6145bbc755ec4da5d535f98f6a0c386865fdae2ccb88aed1 — commit 0edf7e8cac6bbd035b4516c4303e2c0518391e4b
   - Qwen3-0.6B.litertlm — 613,722,264 B — c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108 — commit 2670e142a0d16a1b685be6604e5b4f2b31a826d7
   - embeddinggemma-2-text-270m.litertlm (RAG) — 164,626,432 B — 2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb — commit 9be6e8b90982095dc05c2bd162e4b954ee4dbac7
4. **LiteRT-LM 0.19.0 API (inspected with javap)** vs CLAUDE.md:
   - `EngineConfig(modelPath, backend, …, maxNumTokens, maxNumImages, cacheDir, …)`; `SamplerConfig(topK, topP, temperature, seed)`.
   - `ConversationConfig(systemInstruction, initialMessages, …, samplerConfig, …, thinkingConfig, …)`.
   - **Cancelling `sendMessageAsync`'s Flow does NOT stop native generation** (empty `awaitClose`) → engine calls `cancelProcess()`.
   - Extras used: `getBenchmarkInfo()` (+`ExperimentalFlags.enableBenchmark`), `ThinkingConfig(enableThinking=false)`, `EmbeddingEngine` for RAG.
   - The AAR ships **no R8 consumer rules** → `app/proguard-rules.pro` keeps `com.google.ai.edge.litertlm.**`.
5. AGP 9 built-in Kotlin; Detekt runs once at the root. `targetSdk 36`, `compileSdk 37`.
6. `LlmEngine` API: `newSession(...)` is `suspend` and returns a session id; `generate(prompt, sessionId)` validates it
   (`SessionReplacedException`); plus `lastStats`, `contextWindowTokens`, `sessionId`.
7. Fakes live in test-only `core:testing` (not in `core:llm`). Extra module `data:benchmark`.
8. Embedder interface lives in `core:llm` (only module allowed to import LiteRT-LM), not in `data:documents`.
9. Unit tests RUN on JDK 21 (Robolectric Android 36 + markdown-renderer Java-21 bytecode); code compiles to Java 17.

## Test results (final)

- **139 unit tests in 37 suites, 0 failures** (JVM + Robolectric + Compose UI tests).
- Full pipeline `spotlessApply detekt lint testDebugUnitTest assembleDebug assembleRelease`: **GREEN**.
- Outputs: `app/build/outputs/apk/debug/app-debug.apk` (54.7 MB), `app/build/outputs/apk/release/app-release-unsigned.apk` (31.2 MB).
- Line coverage: **not measured** (no Kover/JaCoCo configured). Every ViewModel, repository, chunker, prompt builder and
  checksum utility has tests.
- **NOT run on a phone** from this PC (no device attached). The LiteRT-LM engine, GPU fallback, downloads, PDF parsing,
  biometric lock, Macrobenchmark and the R8 release build must be verified on a real device.

## Phase notes

- Phase 3: Room v1; OfflineChatRepository keeps the native conversation in sync with Room.
- Phase 4: core:datastore, data:models; debug screen removed; HostAllowlist; InMemoryPreferencesDataStore for tests.
- Phase 5: Room v2 (AutoMigration); data:benchmark; :benchmark (Macrobenchmark + Baseline Profile); session ownership id.
- Phase 6: data:documents (PdfBox, TextChunker, Summarizer, Translator); LlmTask; lint ignore for Bouncy Castle jar only.
- Phase 7: Room v3 (documents, chunks), tested migrations 1→3 and 2→3; LiteRtLmEmbedder; RAG; page-exact chunks.
- Phase 8: env-var signing, release.yml, network security config, biometric lock, AboutLibraries licences, Hindi,
  privacy policy, README, CHANGELOG, final review fixes (see docs/learn/phase-8.md → Review fixes).

## Real-device verification (2026-11-02)

- iQOO Neo 10R (I2221, SM8635 Snapdragon 8s Gen 3, 8 GB, Android 16), debug APK installed over wireless adb.
- Onboarding → model screen recommended Gemma 4 E2B (8 GB) ✓. Gemma 4 E2B + EmbeddingGemma downloaded,
  exact byte sizes, SHA-256 verified (renamed to model.litertlm) ✓.
- Chat: model loaded on **GPU in 13.3 s**; streamed Markdown answer; auto-title ✓; **21.7 tok/s · GPU** ✓. No crashes.
- User confirmed on device: answers correct, airplane mode, history, summarize, Ask-your-PDF, translate ✓.
- **Benchmark screen (official numbers):** GPU · load 5.5 s (log: 5454 ms; first-ever load 13.3 s before the
  GPU program cache existed) · TTFT 0.39 s · 26.3 tok/s · peak PSS 960 MB. Recorded in README + docs/benchmarks.md.
- Still to check on device: app lock and the R8 release build (signed) before publishing.

## Fixes after first device test (2026-11-02)

Reported by Sanjay: (1) code answers poor, (2) answer bubble flickers while streaming, (3) rename Manjit → Sanjay.
- **Rename**: package/applicationId `com.sanjay.pocketmind`, all folders, Room schema folder, docs, LICENSE, git user.name.
  New applicationId = new app on the phone (old `com.manjit.pocketmind` must be uninstalled; models re-copied or re-downloaded).
- **Flicker**: `Markdown(content = text)` re-parsed asynchronously on every 50 ms update and showed an empty frame,
  plus an animated height change on every token. Now `rememberMarkdownState(text, retainState = true)`, size animation
  off, finished answers parsed `immediate`, assistant bubbles full width (chat, PDF summary, Ask-your-PDF).
- **Code answers**: new default system prompt (one complete runnable program in a fenced block with language, explain,
  expected output, fix-code rules, answer only the latest message); default temperature 0.7 → 0.4; answer reserve
  1024 → 1536 tokens so long code is not cut off. Users who edited temperature/system prompt keep their own values.
- Check: `spotlessApply detekt lint testDebugUnitTest assembleDebug assembleRelease` → BUILD SUCCESSFUL. Not yet re-tested on phone.

## Open issues

- Remaining real-device checks listed above, plus re-test flicker and code answers on the phone.
- A 2 B model will still make mistakes in harder code; Gemma 4 E2B is the best that fits 8 GB comfortably.
- `docs/benchmarks.md` and README benchmark table are empty until measured on phones (by design: no invented numbers).

## On-device findings (2026-11-03, iQOO I2221, Android 16)

- **Backup first:** the v1 database (schema 3, integrity ok, 11 chats, 98 messages) and settings were copied to
  `C:\Users\CHIFE\Desktop\pocketmind-backup-2026-11-03\` before installing v2 (outside the repo).
- **Play Billing telemetry (privacy):** after the first v2 start, `databases/com.google.android.datatransport.events`
  appeared. Play Billing 9.1.0 depends on Google's `datatransport` + CCT backend, which queued one
  `PLAY_BILLING_LIBRARY` event (library version, package, device model, build fingerprint, carrier code 40554,
  country, locale, time zone; no user content) for upload to Google. It contradicted the privacy policy.
  **Owner's decision: block it, keep Pro.** The queued event was deleted on the phone before its first upload
  attempt (0 attempts). `AndroidManifest.xml` now removes the CCT backend registration (the uploader then deletes
  events: "Unknown backend … deleting event batch", checked in transport-runtime 3.1.8); `NoTelemetryBackendTest`
  guards it. To verify on device: Pro buy / restore still works (docs/billing.md).
- **Room migration 3 → 8 on the real chats: verified.** After installing v2 over v1, the database was schema 8,
  `PRAGMA integrity_check` = ok, 11 chats and 98 messages (same as the v1 backup). Second backup:
  `…\pocketmind-backup-2026-11-03\after-v8-upgrade\`.
- **Encryption migration failed on the first try, safely, and is now fixed.** The first start created
  `database.key` but kept the plain file (outcome FAILED → plain fallback, retried next start: the safety net
  worked, no data lost). `EncryptionDeviceTest` reproduced it; a diagnostic run on the phone found the cause:
  the ATTACH of the new encrypted file failed with "unable to open database (code 14)", because an attached
  database is opened with the main connection's flags and the plain file was opened without
  `CREATE_IF_NECESSARY`. SQLCipher's `rawExecSQL` hid that error. Fix in `SqlCipherOperations`: open with
  `CREATE_IF_NECESSARY`, `execSQL` for ATTACH/DETACH (errors surface), `rawQuery` for the export, single
  connection (WAL off), and the schema version set on the copy itself. The migrator now also records why a
  verification failed. **Result on the phone:** `EncryptionDeviceTest` 2/2 passed; the real app migrated the real
  database: header no longer "SQLite format 3", History shows all chats.
- **Telemetry queue after the fix:** `com.google.android.datatransport.events` holds 0 events and no transport
  context; with no backend registered the CCT uploader is never set up.
- **Tool calling (phase 17 gate) on the phone, Gemma 4 E2B, GPU:**
  - First new chat: "What is 123.4 * 56?" was answered from memory, **wrong** (6810.4), without a tool. Asked
    explicitly to use the calculator, the model even claimed it had none.
  - A debug log (`files/debug.log`, debug builds only, `FileLogTree`) proved the wiring is right. The repository
    passed all 4 tools, and LiteRT-LM's ToolManager held their declarations in the correct format.
  - Next new chat: "123.4 multiplied by 56 is 6910.4" with **"Used: Calculator"**, 35.1 tok/s · GPU.
  - So tool use works end to end, but a 2B model doesn't call tools every time. Measured so far: the calculator
    was used in **2 of 3** fresh chats (123.4 × 56 twice, 25 + 26 once); each time it was used, the answer was
    exact. Too few runs for a reliable rate. Possible improvement: a stronger system rule, or lower temperature
    for arithmetic questions.
  - Note: a scripted test run left the app by mistake (Back closed PocketMind) and tapped the home screen. Rule
    for future device scripts: take a screenshot and check the foreground app before every tap. Also seen: the model sometimes writes
    LaTeX (`$… \times …$`), which the Markdown view shows raw.
- **Photo question (vision): works.** A photo of a restaurant menu was read correctly: sections, items, prices and
  the shop's address (28.7 tok/s · GPU). Oddity: the model also called the Notes tool, which it didn't need.
- **Streaming flicker (owner's report): fixed, to verify on device.** The answer flickered while it was written.
  Two causes: the whole Markdown text was re-parsed and re-composed on every 50 ms update, and `animateItem()`
  sprang every message above into place each time the answer grew. Now `StreamingMarkdownText` uses the
  renderer's append-only `StreamingMarkdownState` (0.46.0, checked with javap; finished blocks stay put), and list
  items keep their fade but have no placement animation. Commit `b77222b`. Owner confirmed on the phone: no
  flicker any more.
- **Reading while an answer is written (owner's report): fixed, to verify on device.** The text kept moving up,
  even after scrolling up to read. Cause: `reverseLayout` anchors the list to the bottom of the newest item.
  Now the list is laid out top-down and follows the newest text only while the user is at the end
  (`FollowLatestState`: a drag towards older messages stops following; reaching the end or "Jump to latest"
  resumes it). The finished answer replaces the live one in a single frame (ViewModel waits for Room's copy).
  3 Robolectric tests (`MessageListFollowTest`). Commit `221d652`.
- **Voice input: works** ("What is the capital of France", microphone permission granted, answer correct).
  **Read aloud: works** (the owner heard the answer; the icon turned into Stop and back).
- **Export all my data: works.** ZIP with 17 chats (Markdown), JSON with 116 messages, 1 note, 2 photos
  (same as on the phone), settings, README.
- **Seen:** unneeded tool calls (Notes for a photo question, Date and time for "capital of France"); the
  temperature setting on the phone was 1.5, which may make this more likely.
- **App logs:** this phone shows no app log lines in logcat (vivo/iQOO suppresses them unless enabled in developer
  options), so on-device checks read files instead.

## BLOCKERS

- **Intermittent KSP2 errors in parallel builds** (seen 5 times, phases 0–5): `InjectProcessingStep was unable to
  process X because Y could not be resolved`, and once a module's KSP output was empty (→ `MissingBinding`). Task
  order was verified correct; the same task succeeds alone. Mitigations applied (phase 2: `ksp.incremental=false`;
  phase 5: `ksp.classloader.cache.processors=false`). If it recurs: re-run the build (it is not a code error); the
  long-term fix is a KSP release that addresses it — watch Dependabot PRs for KSP.
  **Phase 17 diagnosis (seen 4 more times in phases 16–17):** the errors kept coming back because the Gradle
  build cache had stored a KSP result with NO files and restored it ("FROM-CACHE") in later builds; deleting
  `build/generated/ksp`, `--no-parallel` and restarting the daemon did not help. `--rerun --no-build-cache` produced
  the files at once. Fix: the Hilt convention plugin now marks every `ksp*` task as not cacheable, so KSP always runs
  (a few seconds per module). If it ever recurs: `./gradlew :<module>:kspDebugKotlin --rerun --no-build-cache`.
  **Phase 18:** `assembleRelease` failed once with the same "ChatRepository could not be resolved" in the RELEASE
  KSP tasks of feature:chat and feature:history (not cache-related), and passed straight away with `--no-parallel`.
  Safest choice: release builds (and `release.yml`) use `--no-parallel`; debug builds stay parallel.
  **Phase 20:** it came back in a parallel DEBUG build (`data:chat` KSP: even `Context` "could not be resolved",
  so KSP saw an incomplete classpath). That is the third kind of recurrence, so per the workflow rule the safest
  working alternative was chosen: `org.gradle.parallel=false` in `gradle.properties` for every build. Cost: the
  full check takes longer; benefit: no more false failures. Re-enable it when a KSP release fixes the race.
  The next serial run then failed with `Cannot find required type element java.lang.Class`: the real culprit for
  that one was the phase 5 setting `ksp.classloader.cache.processors=false` (a fresh processor classloader per KSP
  task fills the daemon's 1 GB metaspace over ~25 modules). With serial builds the cache is safe again, so it is
  back ON and the metaspace limit is 1.5 GB.
  **Phase 22, root cause and fix:** KSP runs as Gradle *worker actions*, which overlap even in a serial build, so
  two KSP tasks still ran at the same time (seen as two simultaneous failures). A build service with one allowed
  user (`KspLock` in `HiltConventionPlugin`) now runs KSP tasks strictly one at a time; parallel builds are back
  ON. Since then every full check passed without a KSP error. Also found: a `kspDebugKotlin` output folder deleted
  in an earlier clean-up was never regenerated (the debug app lacked ChatViewModel's Hilt registration until
  `kspDebugKotlin --rerun`). Status: **resolved**, keep watching.

## MANUAL STEPS FOR SANJAY

### v2 (added during the v2 build)
- **Engine test on the phone (phase 4):** `adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/model.litertlm`, then
  `./gradlew :core:llm:connectedDebugAndroidTest` (3 tests: load/stream/cancel/unload, damaged size, CPU-only).
- **Baseline Profile + benchmarks (phase 6):** `./gradlew :app:generateBaselineProfile` (commit the generated
  `app/src/release/generated/baselineProfiles/`), then `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`;
  copy the medians into `docs/performance-budget.md`. Use the debug app once and read `adb logcat -s StrictMode` and
  LeakCanary notifications; report anything found.
- **Context size (phase 7):** to consider an 8k context on 8 GB+ phones, measure peak memory with 8192 tokens first
  (Benchmark screen after temporarily changing `CONTEXT_WINDOW_TOKENS`). Until then it stays 4096.
- **Screenshots on Linux (phase 10):** push the repo, run the GitHub Actions workflow **Record screenshots**, download
  the `golden-screenshots` artifact and commit the PNGs (fonts render slightly differently on Windows than on CI).
- **Photo questions (phase 16):** with Gemma 4 E2B selected, attach a photo and ask about it (the first one reloads
  the model with its image encoder). Engine check: `./gradlew :core:llm:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.vision=true` (a solid red picture must be called "red"). Note the
  RAM with and without the encoder (Android Studio profiler, or the Benchmark screen's peak memory) for
  `docs/performance-budget.md`, and measure an image's real token cost (`Conversation.getTokenCount()` before and
  after); the repository reserves 512 tokens until then.
- **Tool calling gate (phase 17):** `./gradlew :core:llm:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.tools=true` (Gemma 4 E2B must call the calculator and answer 6910.4).
  Then in the app, with Tools on: "What is 123.4 × 56?", "What day is it today?", "Convert 10 km to miles",
  "Remember my locker code is 4312" and later "What is my locker code?". Each answer should show "Used: …".
  Also try Qwen3 0.6B: its template has tool calls, but a 0.6B model may call tools badly; if so, set its
  `supportsTools` to false in `models.json`.
- **Encryption (phase 18), most important:** install the new build OVER the current app, which has your real chats.
  All chats must still be there. Then check that the file is encrypted:
  `adb shell run-as com.sanjay.pocketmind head -c 16 databases/pocketmind.db | xxd` must NOT show
  "SQLite format 3". `adb logcat -s PocketMind` (or the Timber tag) shows `Database encryption: MIGRATED` once.
  Before installing, keep a copy of your chats from the CURRENT (debug) app:
  `adb exec-out run-as com.sanjay.pocketmind cat databases/pocketmind.db > chats-backup.db` (backups are off by
  design, so this is the way).
  Then run `./gradlew :core:database:connectedDebugAndroidTest` (real SQLCipher + Keystore) and try
  Settings → Your data → Export all my data, and Delete all my data (after the export).
- **Play publishing (phase 19):** follow `docs/release-guide.md` → "One-time setup": upload the FIRST signed AAB by
  hand (Play's API can't create an app), create the service account, give it "Release to testing tracks", and add
  the GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`. Then a tag runs `play.yml`. Check In-App Update and Review with two
  internal-track versions (steps in `docs/learn/v2-phase-19.md`). Optional: `app/src/main/play/release-notes/hi-IN/
  default.txt` for Hindi release notes.
- **PocketMind Pro (phase 20):** in Play Console create the in-app product `pocketmind_pro` and set its price
  (decision 4: only there), add yourself under License testing, then test buy / restore / refund on an
  internal-track build (steps in `docs/billing.md`). Decide whether 50 free PDF pages is right
  (`PdfBoxTextExtractor.FREE_PDF_PAGES`). "Extra themes" from decision 2 don't exist yet: say if you want them
  built (they would be gated with `ProStatus.isPro`).
- **Languages and accessibility (phase 21):** ask a native Punjabi speaker to read the app in ਪੰਜਾਬੀ and a native
  Hindi speaker in हिन्दी (Settings → App language on Android 13+), and fix the `values-pa` / `values-hi` files they
  mark. Walk through `docs/accessibility-checklist.md` with TalkBack and at the largest font size. Then re-record
  the screenshot goldens (Record screenshots workflow): phases 16–21 changed Settings, the chat input and the answer
  footer, so `verifyRoborazziDebug` reports differences until then.
- **CI gate (phase 22):** on GitHub, Settings → Branches → add a branch protection rule for `main` (and `v2`) with
  "Require status checks to pass" → select the CI job `build`. Then a red CI really blocks a merge.

### v1

1. **GitHub**: create repo `pocketmind`; `git remote add origin https://github.com/<you>/pocketmind.git`;
   `git push -u origin main`. Replace `YOUR_GITHUB_USER` in README.md (badges/links).
2. **Phone test** (64-bit, 6 GB+ RAM, USB debugging on): `./gradlew installDebug`; onboarding → download the
   recommended model on Wi-Fi → chat → History → Docs (Summarize, then "Get the search model" → Ask) → Translate →
   Settings → Benchmark. Toggle airplane mode to prove offline use.
3. **Release keystore**: `keytool -genkeypair -v -keystore pocketmind-release.jks -alias pocketmind -keyalg RSA -keysize 4096 -validity 10000`.
   Back it up in two safe places. Never commit it.
4. **GitHub secrets** (Settings → Secrets → Actions): `KEYSTORE_BASE64` (base64 of the .jks), `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`. Then `git tag v1.0.0 && git push origin v1.0.0` → signed AAB + APK on GitHub Releases.
5. **Verify the release build on a phone** before publishing (R8 rules): install the release APK, chat, summarize, ask.
6. **Real numbers**: in-app benchmark on 3 phones (flagship, mid, budget) → `docs/benchmarks.md` + README table;
   `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`; `./gradlew :app:generateBaselineProfile` and commit
   `app/src/release/generated/baselineProfiles/`.
7. **Media**: record `docs/screenshots/demo.gif` + 4–8 screenshots; 2-minute demo video.
8. **Privacy policy URL**: publish `docs/privacy-policy.md` with GitHub Pages; use the URL in Play Console.
9. **Play Console**: US$25 account; listing (512 px icon, 1024×500 feature graphic, screenshots); Data safety form
   ("No data collected"); content rating; target audience 18+; upload AAB to Internal → Closed test with **12+ testers
   for 14 days** (new personal accounts); then production with staged rollout.
10. **Hindi review**: ask a native speaker to proofread the `values-hi/strings.xml` files.
