# PocketMind — Build Progress

Read this file and CLAUDE.md first when a session restarts. Never redo finished phases.

## V3 status (branch `v3`, from tag `v2-final`; spec: CLAUDE.md; plan: V3_PLAN.md)

| Stage | State | Notes |
|---|---|---|
| 0 Setup | DONE | tag `v2-final` = af6890f; branch `v3`; spec committed (5473486) |
| 1 Audit | DONE | AUDIT_V3.md (baseline score 28 / 130 against the V3 spec) |
| 2 Plan | DONE | V3_PLAN.md: phases 1, 2a–2e, 3–16, Final |
| 3 Build | IN PROGRESS (approved 2026-11-10) | decisions A–G as proposed in V3_PLAN.md §1; name: **Sanjay Kumar** (owner's GitHub profile + earlier "use Sanjay everywhere"; one constant) |

### V3 phases

| Phase | State | Gate | Notes |
|---|---|---|---|
| 1 Monorepo, KMP skeleton, CI | DONE | 402 tests, 0 failures (Linux/WSL); logic coverage 84.17 % (gate 84) | see "Phase 1 notes" |
| 2a Shared data layer (Room KMP, DataStore KMP) | DONE | 402 tests, 0 failures (Linux/WSL) + 3 iOS tests (CI); logic coverage 84.09 % | see "Phase 2a notes"; iOS build green on CI |
| 2b Shared AI (tools, engine contract, documents/RAG logic) | DONE | 419 tests, 0 failures (Linux/WSL); logic coverage 84.15 % | see "Phase 2b notes"; iOS tests on CI |
| 2c UI port to Compose Multiplatform | IN PROGRESS | all 9 screens shared; a08ae36: 604 tests, 0 failures, coverage 84.22 %; CI green (Android + iOS); phone check open | see "Phase 2c notes" |

**GitHub (2026-11-10):** public repo https://github.com/zeus881/pocketmind; branches main, v2, v3 and tag v2-final pushed.
Commit email changed to sanjaykumarr99009@gmail.com in the whole history before the first push (old history kept in a
bundle outside the repo). First CI ever: the workflow file had an unterminated `if:` string and `gradlew` was not
executable (committed from Windows); both fixed on main, v2 and v3. Result on Linux: main fully green; v2 and v3 pass
formatting, detekt, Lint, all unit + E2E tests and the coverage gate; only the screenshot comparison fails because
the goldens are stale and Windows-rendered. "Record screenshots" now commits Linux images itself.
**Owner:** Actions → Record screenshots → Run workflow on v3 (and v2), then re-run CI.

### Phase 1 notes (2026-11-10)
- v2 moved into `mobile/` with `git mv` (history kept); `app` → `androidApp` (same app ID); stray `AskPane.kt`
  removed. Workflows run Gradle from `mobile/` (`defaults.run.working-directory`); `ci.yml` → `mobile-ci.yml`
  (path-filtered); new `ios-build.yml` (macOS runner: `:shared:core:iosSimulatorArm64Test`, XcodeGen, unsigned
  simulator `xcodebuild`). **iOS build: to verify on CI** (first run after this push).
- `shared:core` = v2 `core:model` in commonMain (KMP: jvm + iosArm64 + iosSimulatorArm64; Android modules use the
  JVM jar unchanged). Convention plugin `pocketmind.kmp.library`. 4 new shared tests (catalog JSON + benchmark maths).
- `composeApp` (Compose Multiplatform 1.12.2, AGP `com.android.kotlin.multiplatform.library`, target DSL
  `kotlin { android {} }` checked in the AGP 9.5.0 jars) builds the iOS framework `ComposeApp`; first shared screen
  = v2's onboarding texts (en/hi/pa). `iosApp/project.yml` (XcodeGen, iOS 16, bundle `com.sanjay.pocketmind`).
- **Deviation from V3_PLAN phase 1:** `core:tools` stays a JVM module until phase 2b. It uses `java.math.BigDecimal`,
  `Math` and `java.time`, which Kotlin/Native lacks; replacing them needs verified KMP libraries.
- Bugs found and fixed: release/version tags matched `v2-final` (`git describe --match v*` → AppVersion rejected
  it → every build with tags failed) → pattern `v[0-9]*.[0-9]*.[0-9]*`, workflows `v[0-9]+.[0-9]+.[0-9]+*`;
  the design-token guard scanned the old `app/` path; the architecture guard now knows `shared:*` (core/data may
  use it; shared → shared only) and reads KMP source-set configurations; detekt treats `*Test/kotlin` as tests.
- Verified for phase 2a (Room 2.8.5 Android artifact, javap): the builder has both `openHelperFactory(…)` (keeps
  v2's SQLCipher factory on Android) and `setDriver(…)` (iOS); `RoomOpenHelper` and `BaseRoomConnectionManager` both
  call `Migration.migrate(SQLiteConnection)`, so migrations written once in commonMain run on both (AUDIT_V3 D-3, D-7).
- Facts: Compose Multiplatform 1.12.2 supports iOS 14+ (official compatibility page, read 2026-11-10); built on
  AndroidX Compose 1.12.1, Kotlin stdlib ≥ 2.2.20.
- CI after phase 1: Mobile CI green except the stale screenshots; iOS build first failed at the Xcode step (a
  generic simulator destination builds x86_64 too, which the shared code doesn't target) → arm64-only build,
  framework linked in its own step, Xcode errors as annotations → **iOS build green** (run 42784456193).

### Phase 2a notes (2026-11-10)
- `shared:data` (KMP Android + iOS; plugins `pocketmind.kmp.android.library` + `pocketmind.kmp.room`) owns the Room
  entities, DAOs, `Migrations`, `PocketMindDatabase` (+ `@ConstructedBy`/`expect object`) and `SettingsDataSource`,
  moved with `git mv` (packages unchanged, so no consumer changed). **Schema still version 8**: Room's KMP export
  reproduced `8.json` exactly (no diff), so no v9 and no new migration were needed (V3_PLAN said v9: not needed).
- Migrations on `SQLiteConnection`; `NoteDao` suspend (`RoomNoteStore` waits with `runBlocking` on LiteRT-LM's thread).
- `core:database` = SQLCipher + Keystore + Hilt; `core:datastore` = the "user_settings" file + Hilt
  (`SettingsModule` separate so E2E tests keep the real settings class).
- `androidx.sqlite` **2.6.2** (= what Room 2.8.5 requires per its Gradle module metadata) for `sqlite-bundled`;
  `datastore-preferences-core` 1.3.0 has iOS variants (checked).
- **iOS:** `IosDatabaseTest` (bundled driver: FTS4 search incl. Hindi, notes) passed on the macOS runner →
  AUDIT_V3 D-6 resolved (FTS4 works in Room's bundled SQLite on iOS).
- Coverage: moving the DAO code out of the report dropped the merged figure to 83.57 % although every test passed;
  `shared:data` joined the merged report (Kover variant name "android", probed) → 84.09 %.
- **Deviation:** no Koin yet. Shared classes carry no DI annotations; Android provides them with Hilt `@Provides`.
  Koin arrives with the shared UI (phase 2c), so the Android app never runs two DI frameworks at once.
- Not yet done (planned for 2d/2e): creating the database file and the DataStore file on iOS (paths, Data
  Protection) — they are only needed once iOS screens exist.

### Phase 2b notes (2026-11-10)
- `shared:tools` (pure KMP): java.math → **bignum 0.3.10** (Apache-2.0, API read with javap from its JVM jar, sha1
  matched Maven Central), java.time → **kotlinx-datetime 0.8.0**, Math → kotlin.math. 12 tool tests unchanged in
  their expected values; they run on the JVM and the iOS simulator.
- `shared:ai` (KMP Android + iOS): LlmEngine/EngineState/SamplingParams/InferenceSettings, EngineMath (now public),
  TokenEstimator, HistoryTrimmer, LlmTask, Summarizer, Translator, PromptTemplates, TextChunker, PageText,
  VectorMath, VectorStore, GroundedPrompt. `AppResult`/`ErrorKind` + Flow helpers → `shared:core`.
- `VectorMath` without java.nio; `VectorMathNioTest` proves byte-identical output to v2 (stored embeddings stay valid).
- KMP Android modules now have host tests (`testAndroidHostTest`, part of the root `testDebugUnitTest`).
- Hilt providers outside the modules E2E tests replace: `LlmTaskModule`, `DocumentTasksModule`.
- **Kept for later:** LiteRT-LM stays 0.19.0 (verified on the phone); 0.19.1 exists — update only with a device
  check (manual step). PDF text extraction on iOS (PDFKit) comes with the iOS platform services (2d/2e).
- **To verify on device** (no behaviour change expected): chat with tools, PDF summary, Ask-your-PDF citations.

### Phase 2c notes (2026-11-10/11)
- **Step 1 done — prerequisites for shared screens:**
  - `shared:designsystem` (Compose Multiplatform; v2 core:designsystem moved; wallpaper colours expect/actual;
    "Retry" as a Compose resource). Convention plugin `pocketmind.kmp.compose`; KMP host tests include Android
    resources and run Robolectric on SDK 36 (`robolectric.properties`). Kover ignores generated `*.resources`.
  - Benchmark logic in `shared:ai` (UUID → kotlin.uuid, AtomicInteger → kotlin atomics, String.format →
    `formatFixed` in shared:core with tests); report text unchanged.
  - **Hilt → Koin 4.2.2** for the whole Android app (17 Hilt modules, 44 injected classes, 11 ViewModels,
    worker, widget, E2E tests). Scopes kept exactly (former @Singleton → `single`, unscoped → `factory`, from the
    diff). `KoinGraphTest` (`Module.verify`) replaces Hilt's compile-time check. E2E tests run on
    `TestPocketMindApp` with one module of fakes (Koin's `allowOverride` default = true, checked in bytecode).
    Hilt's KSP processing is gone; Room keeps the KSP safety rules.
  - Fixed on the way: a JVM-only `removeIf` in shared test code (iOS CI), Gradle's own `javax.inject` use in
    build-logic (restored after the mechanical strip), a broken secondary constructor (CrashLog).
- **Mistake recorded:** a "workflow only" commit (f2d2cfc) also captured staged `git mv` renames with their old
  contents; CI failed until 7d0cff2. Rule now: commit single files with `git commit -- <path>`.
- **Step 2 done — all 9 feature screens are shared code (2026-11-11):** benchmark e46f31b, history 636d770
  (+e870278), personas + Pro entitlement 72d61b2, onboarding + models de562b3 (+9827830), documents + translate
  0c286be, settings 735218a, chat a08ae36. Gate on a08ae36: 604 tests, 0 failures, coverage 84.22 %.
  - Each screen: `pocketmind.kmp.feature` convention (KMP Android library + Compose + Roborazzi; JetBrains
    lifecycle 2.11.0 / navigation 2.10.0 / material3-adaptive 1.3.0, koin-compose-viewmodel, Kermit). Strings are
    Compose resources (en/hi/pa); screenshot goldens unchanged except where the time zone was pinned (benchmark).
  - Data layer: data:chat is KMP; ModelRepository/ModelStatus/ModelRecommender, PdfTextExtractor (interface),
    DocumentRepository, SpeechInput/SpeechOutput/SpeechText moved to shared:ai; ProStatus + entitlement rules to
    core:billing commonMain (`expect PurchaseHost` = Activity / UIViewController); PmQualifiers, DeviceFacts,
    DiagnosticReport to shared:core (Android CrashLog implements the common `CrashReports`). Files picked by the
    user are passed as text (content URI / file URL), not `android.net.Uri`. Timber → Kermit in shared code;
    `TimberLogWriter` routes Kermit to Timber in every build, so release builds still log nothing.
  - Platform UI helpers (shared:designsystem `platform/`): share sheet (optional title/subject), date, relative
    time, file size, notification permission, PDF picker, plain-text clipboard entry, language names,
    `dynamicColorAvailable`. Screen-specific pieces are expect/actual inside the module (SettingsPlatform.kt,
    ChatPlatform.kt).
  - **iOS gaps, honest and visible (filled in 2d):** no model download, PDF reading, data export, chat export,
    photo attachments, voice input or App Store purchase on iOS yet. The iOS actuals return "not available"
    and the screens hide those controls instead of showing dead buttons.
  - Bugs caught by tests during the port: "41%%" in the download progress (Android escapes % as %%; screenshot
    test), the speed label's `%1$.1f` (Compose resources fill only %s/%d; ChatContentTest), a benchmark golden
    that depended on the recording machine's time zone. Android-only APIs that compiled on Android but not iOS
    (an unused `R` import, `popoverPresentationController` category import, `ExperimentalForeignApi` opt-in)
    were caught by the iOS CI job.
  - **Mistake recorded:** removing an old `src/test` folder also deleted history's 12 golden images (636d770);
    restored in e870278. Rule: goldens live in `src/test/screenshots`, never remove that folder.
  - **Mistake recorded:** a `git show ref:path` in Git Bash had its argument rewritten as a Windows path, which
    committed an EMPTY screenshots.yml to main (2ab4707, fixed in 2819370); with MSYS_NO_PATHCONV set, a worktree
    path then resolved elsewhere and the commands ran in the main checkout (switched to main briefly; no work
    lost). Rule: run git with repo-relative paths from the repo, never through a converted absolute path.
  - Deviations: features stay separate KMP modules under `mobile/feature/*` (not one composeApp module), which
    keeps v2's enforced dependency rules; ViewModel and screenshot tests stay in androidHostTest (they use the
    Robolectric test kit in core:testing) instead of commonTest. composeApp hosting the shared navigation for
    iOS moves to 2e, where the iOS app gets its Koin graph and engine.
- **Still open for 2c "done":** the Android parity check on the phone from the shared UI.


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
  items keep their fade but have no placement animation. Commit `a30ab97`. Owner confirmed on the phone: no
  flicker any more.
- **Reading while an answer is written (owner's report): fixed, to verify on device.** The text kept moving up,
  even after scrolling up to read. Cause: `reverseLayout` anchors the list to the bottom of the newest item.
  Now the list is laid out top-down and follows the newest text only while the user is at the end
  (`FollowLatestState`: a drag towards older messages stops following; reaching the end or "Jump to latest"
  resumes it). The finished answer replaces the live one in a single frame (ViewModel waits for Room's copy).
  Commit `6f61544`; owner confirmed: the text stays steady. Owner's follow-up wish: the answer should start at the
  TOP, not the bottom. Now (like current AI assistant apps) a new question moves to the top of the screen and its
  answer is written downwards below it; nothing scrolls on its own; the newest answer is at least one screen tall
  so this works for short answers too; "Jump to latest" shows while there is more below (`ChatScrollEffect`,
  3 Robolectric tests in `MessageListScrollTest`). **Verified on the phone** by the owner (long story and a short
  follow-up question both start at the top; "What is 2 + 2" answered with Calculator, 21.9 tok/s · GPU).
- **Voice input: works** ("What is the capital of France", microphone permission granted, answer correct).
  **Read aloud: works** (the owner heard the answer; the icon turned into Stop and back).
- **Export all my data: works.** ZIP with 17 chats (Markdown), JSON with 116 messages, 1 note, 2 photos
  (same as on the phone), settings, README.
- **Seen:** unneeded tool calls (Notes for a photo question, Date and time for "capital of France"); the
  temperature setting on the phone was 1.5, which may make this more likely.
- **App language: works** (checked on the phone: Hindi applies instantly, back to English, then restored to
  "Same as the phone" as the owner had it; a Punjabi option is listed too).
- **Unneeded tool calls: fixed and measured** (commit `cc5e804`). Tool descriptions and the system rule now say when
  NOT to use a tool. 5 fresh chats on the phone, temperature 1.5 (the owner's setting; default 0.4):
  capital of Japan: no tool ✓; why the sky is blue: no tool ✓; 123.4 × 56: Calculator, 6910.4 ✓; photosynthesis
  with a heading: no tool ✓; weekday today: Date and time, "Tuesday" ✓. 5 of 5 right, a small sample.
- **Headings: fixed** (same commit). The renderer used Material display styles (`#` = 57 sp); now h1 =
  headlineSmall … h6 = titleSmall. Checked on the phone: "Photosynthesis" heading at a normal title size.
- **Still open:** the model sometimes writes LaTeX (`$\text{H}2\text{O}$`), which is shown raw.
- **App logs:** this phone shows no app log lines in logcat (vivo/iQOO suppresses them unless enabled in developer
  options), so on-device checks read files instead.

## BLOCKERS

- **V3: Windows blocks Robolectric's native runtime (2026-11-10).** On this PC, Windows app control (likely Smart
  App Control) now refuses `libandroid_runtime.dll`, which Robolectric unpacks into `%TEMP%`: 26 tests (Room DAO
  tests on native SQLite, a Compose test) fail with "An Application Control policy has blocked this file". Not a
  code problem: the same tests pass on Linux. Safest working alternative (not switching off a Windows security
  feature, which can't be turned back on without reinstalling Windows): the gate runs on **Linux in WSL** with a
  private toolchain in `~/pm-toolchain` (Temurin 17.0.21 + 21.0.13, Android SDK platforms 36/37, build-tools
  36.0.0; every download checksum-verified) and a copy of `mobile/` in `~/pm-build/mobile`. WSL has 7.7 GB, so WSL
  builds use `--max-workers=2`, a 3 GB Gradle heap and a 1.5 GB Kotlin daemon (the default 4 GB daemon was
  OOM-killed, dmesg). `spotlessApply` still runs on Windows (it edits the real files).

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

### V3 (MANUAL STEPS / UNKNOWNS)
- **Screenshots:** Actions → "Record screenshots" → Run workflow on `v3` (it now commits the Linux images itself).
- **iOS on a Mac (optional now, needed in phase 2e):** `brew install xcodegen`; in `mobile/iosApp`: `xcodegen generate`,
  open `iosApp.xcodeproj`, pick a simulator, Run. On an iPhone: a free Apple ID in Xcode → Signing.
- Unverified until checked: Oracle A1 free limits, Supabase free limits/idle pause, free LLM provider limits and
  data-use terms, FTS4 in Room's bundled SQLite on iOS, LiteRT-LM Swift embeddings, pgvector inside pgserver,
  the free weather API.

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
