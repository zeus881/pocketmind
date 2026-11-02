# PocketMind v2 — Plan

Status: **waiting for Sanjay's approval.** Nothing below has been built yet.
Inputs: [AUDIT.md](AUDIT.md) (scores, ranked issues) and the v2 brief (sections A–J).

## Ground rules for every phase

- Branch `v2`. One phase = one or more conventional commits, each green on
  `./gradlew spotlessApply detekt lint testDebugUnitTest koverVerify assembleDebug`.
- **Nothing that works today may break.** Every phase keeps all 139 existing tests green, and Room changes ship with
  migration tests from every older schema (v1, v2, v3).
- Every new library version is checked against Google Maven or Maven Central, and every API against the resolved artifact
  (javap or sources) before use. Unknown values (prices, model sizes, checksums) are marked and listed, never invented.
- Kover gate (`koverVerify`): starts at the measured baseline (44 %) in phase 0 and rises with each phase, reaching
  **≥ 80 % on data, domain and ViewModel classes** by phase 20. Compose UI code is covered by screenshot and UI tests
  instead of the line-coverage gate.
- Each phase ends with `docs/learn/v2-phase-N.md` (what changed, why, concepts, code walkthrough, trade-offs, 8 interview
  Q&As) and a PROGRESS.md update. Manual work goes under **MANUAL STEPS FOR SANJAY**.
- A phase is "done" only when its *done when* is met. Anything that needs the phone is recorded as "to verify on device"
  until you've run it.

---

## Part A — Fix Critical and High issues first

### Phase 0 — Foundations for v2
- **Goal:** branch, CLAUDE.md v2, measurement and quality gates in place before any feature work.
- **Files:** `CLAUDE.md` (v2 scope, new modules, rules), `build-logic` (Kover convention), `gradle/libs.versions.toml`,
  `gradle.properties` (configuration cache), `.github/dependabot.yml`, `.github/workflows/ci.yml` (koverXmlReport, upload
  coverage, run on PRs to `main` and `v2`), `docs/performance-budget.md`.
- **Risks:** configuration cache may fail with the AboutLibraries or Baseline Profile plugins → mark incompatible tasks,
  don't disable globally.
- **Tests:** CI green; `koverVerify` passes at the baseline.
- **Done when:** a PR build shows a coverage report, the configuration cache is reused on the second build, and Dependabot
  config is valid.

### Phase 1 — Crash-loop guard and model integrity (C-1, H-1)
- **Goal:** a bad driver or a damaged model can never brick the app.
- **Files:** `core/llm` (`LoadGuard` marker file, size check on every load, `ModelVerifier` with background SHA-256),
  `LiteRtLmEngine`, `data/models` (`verifyModel()`), `feature/models` ("Verify model" action), `feature/chat` (recovery
  card: "Last load crashed — use CPU / verify / choose another model").
- **Risks:** a false positive marker after a normal process kill during load → also record time and only trust it if the
  process died during `initialize()`.
- **Tests:** unit tests for the guard state machine and size check; instrumented test that kills the process during
  load (where possible).
- **Done when:** a deliberately truncated model shows a clear error instead of a crash, and a simulated crash during GPU
  load makes the next start use CPU.

### Phase 2 — App lock done right (H-2, H-3)
- **Goal:** locking never loses your place, running work or a picked file.
- **Files:** `MainActivity`, `MainActivityViewModel`, `ui/LockScreen` (overlay), `core/datastore` (lock timeout setting),
  `feature/settings`.
- **Risks:** content must stay hidden while locked → overlay plus `FLAG_SECURE` while locked, and clear the accessibility
  focus.
- **Tests:** ViewModel tests for the grace-period timer; Compose UI test that state survives lock and unlock; manual test
  of the PDF picker with the lock on.
- **Done when:** with the lock on, picking a PDF works and an answer keeps streaming through lock and unlock.

### Phase 3 — Memory policy and download robustness (H-4, M-12)
- **Goal:** don't reload the model after every app switch; downloads survive Android 15 limits.
- **Files:** `PocketMindApp` (`MemoryPressure`), new `ModelKeepAlivePolicy` (unload after N minutes in the background or
  on critical pressure), `ModelDownloadWorker` (handle stop or timeout, "paused — will resume" notification).
- **Risks:** keeping 1–2 GB loaded in the background raises the chance of being killed → default 5 minutes, configurable,
  and always unload on `RUNNING_CRITICAL`.
- **Tests:** unit tests for the policy; logcat check on the phone that the model survives a 30 s app switch.
- **Done when:** switching apps for under 5 minutes needs no reload (measured), and a stopped download resumes.

### Phase 4 — Tests for the untested core (H-5)
- **Goal:** the riskiest code gets tests.
- **Files:** `data/models` tests with `androidx.work:work-testing` (worker: success, retry, storage full, checksum fail,
  foreground denied); `WorkManagerModelRepository` tests; `core/llm/src/androidTest` instrumented engine test (load,
  stream, cancel, unload, fallback) that runs only when a model file is present on the device.
- **Risks:** instrumented tests need the phone → they skip cleanly in CI and are listed as a manual run.
- **Done when:** worker and repository coverage ≥ 80 %, and the engine test passes on your phone.

### Phase 5 — Release versioning (H-6) and quick wins (M-1, M-3, M-4, L-1, L-2, L-3)
- **Goal:** shippable versioning and the cheap fixes.
- **Files:** `app/build.gradle.kts` (versionCode and versionName from `git describe` or CI tag, fallback for local
  builds), packaging excludes for BouncyCastle `pqc`, `MessageList` (no live region while streaming; announce once),
  `ChatInput`/`ChatViewModel` (type while loading, queued send), empty-chat cleanup, error-specific snackbar action, measure
  then decide on `enableBenchmark`.
- **Tests:** unit tests for the version-code function, queued send and empty-chat cleanup; APK size compared before and
  after.
- **Done when:** the release APK is about 4 MB smaller (measured), and tag `v2.0.0-alpha1` produces a unique versionCode.

---

## Part E — Reliability and performance (before the redesign, so it has a budget to respect)

### Phase 6 — StrictMode, LeakCanary, ANR safety, performance budget
- **Goal:** catch main-thread disk access, leaks and slow frames during development; written budgets.
- **Files:** `app/src/debug` (StrictMode in a debug-only initializer), LeakCanary `debugImplementation`,
  `docs/performance-budget.md` (cold start < 500 ms release on the test phone, TTFT < 1 s, ≥ 20 tok/s on GPU, no frame
  > 16 ms while streaming, APK < 30 MB), `benchmark/` (startup and streaming-jank macrobenchmarks), Baseline Profile
  generated on the phone and committed (M-2).
- **Risks:** < 500 ms cold start may not be reachable with Hilt plus Compose; we report the real number, then optimise
  (lazy Hilt components, App Startup, profile).
- **Done when:** the budget doc has measured numbers from your phone, and the Baseline Profile is in the APK.

### Phase 7 — Smarter context (M-5, D: smart context)
- **Goal:** longer useful chats.
- **Files:** `core/llm` (context size chosen from RAM and model: 4k or 8k, measured), `HistoryTrimmer` → summarize older
  turns with the model itself in the background (a cached summary stored in Room).
- **Risks:** an 8k KV cache costs RAM → only on phones with ≥ 8 GB, after measuring the peak on your phone.
- **Done when:** a 40-turn chat keeps context from turn 1 (via the summary) without errors; memory is measured.

---

## Part B — Design and UX

### Phase 8 — Design system v2
- **Goal:** a real brand, not stock Material.
- **Files:** `core/designsystem`: color tokens (brand light/dark schemes + dynamic color toggle), type scale, spacing
  scale (4/8 dp grid), shapes, elevation, motion tokens (durations, easings), haptics helper, illustration set (vector
  drawables made for PocketMind: empty chat, no model, offline, error, no results), shared components (`PmButton`,
  `PmCard`, `PmTopBar`, `PmEmptyState`, `MarkdownText` replacing L-6's duplicate).
- **Risks:** visual regressions everywhere → phase 10 screenshot tests lock them down.
- **Done when:** every screen uses tokens only (Detekt rule or grep check: no raw `Color(0x…)` or `.dp` literals outside
  the design system, with documented exceptions).

### Phase 9 — Adaptive layouts, edge-to-edge, predictive back (L-5)
- **Goal:** good on tablets, foldables and landscape.
- **Files:** `app` (`NavigationSuiteScaffold`: bottom bar, rail or drawer by window size), list-detail for History ↔
  Chat on wide screens (`material3-adaptive`), manifest predictive back, inset audit.
- **Risks:** the navigation structure changes → keep the routes, change only the frame.
- **Done when:** the resizable emulator shows correct layouts at compact, medium and expanded widths.

### Phase 10 — Screen redesign and screenshot tests (F: Roborazzi)
- **Goal:** every screen redesigned with motion, haptics and illustrated empty and error states; locked by screenshots.
- **Files:** all `feature/*` screens; Roborazzi config in `build-logic`; `src/test/.../screenshots` per feature (light,
  dark, phone, tablet, font 2.0).
- **Risks:** Roborazzi on Windows uses Robolectric native graphics → verify it runs locally; CI on Linux is the
  reference.
- **Done when:** screenshot tests exist for every screen in all 5 configurations and are recorded in the repo.

---

## Part C — Chat features

### Phase 11 — Database v4: pin, folders, branches, edits, full-text search (M-8)
- **Goal:** the data model for the chat features.
- **Files:** `core/database` (`chats.pinned`, `folders` table, `chats.folderId`, `messages.parentId` + `branchId` for
  branching, `messages_fts` FTS4), schema v4 JSON, migrations 3→4 (manual where needed), migration tests from v1, v2 and v3.
- **Risks:** data loss on upgrade → migration tests run against the real v1–v3 schemas plus a backup-copy-before-migrate
  safety net.
- **Done when:** a v3 database with real chats from your phone upgrades with nothing lost (tested with a copy of your DB).

### Phase 12 — Edit and resend, regenerate with versions, branches, pin, folders, in-chat search
- **Files:** `data/chat`, `feature/chat` (long-press message menu, version switcher "< 2/3 >"), `feature/history` (pins,
  folders, FTS search with highlighted matches).
- **Done when:** UI tests cover edit → new branch → switch branch → search finds the message.

### Phase 13 — Export, share-in, widget, quick-settings tile
- **Files:** export to Markdown and PDF (Android `PdfDocument`, through the system "Save as" picker — no storage
  permission); `ACTION_SEND` intent filter for text and PDFs ("Ask PocketMind", "Summarize with PocketMind"); Glance
  home-screen widget (new chat, last chats); `TileService` (new chat or voice).
- **Risks:** Glance version and API → verify in the artifact.
- **Done when:** sharing text from Chrome opens a prefilled chat, and the widget and tile work on your phone.

---

## Part D — AI features

### Phase 14 — Personas and prompt library
- **Files:** `core/database` (personas, prompts), `feature/personas` (new), persona picker in chat; built-in personas
  (Coder, Tutor, Translator, Writer) with tuned prompts and temperatures.
- **Done when:** a chat started with "Coder" uses its prompt (unit-tested prompt building).

### Phase 15 — Voice in and voice out
- **Files:** new `core/speech` (`SpeechRecognizer.createOnDeviceSpeechRecognizer` on API 31+; on 26–30
  `EXTRA_PREFER_OFFLINE`, which only works if the offline language pack is installed — we detect and explain),
  `TextToSpeech` for spoken answers, mic button, `RECORD_AUDIO` runtime permission.
- **Risks:** on-device recognition depends on the phone's speech service → graceful "not available on this phone"
  state. **Privacy:** the online recogniser is never used.
- **Done when:** on your phone in airplane mode, speaking a question sends it and the answer can be read aloud.

### Phase 16 — Image questions (only if the model supports it)
- **Gate:** first check `ModelInfo` / `SupportedModalities` of our Gemma 4 E2B file on your phone. If vision isn't in
  the file, we list the right `.litertlm` (with its real size and checksum) for your approval, or skip this phase.
- **Files:** `LlmEngine` (multimodal message), `LiteRtLmEngine` (`visionBackend`, `Content.ImageBytes`), photo picker
  (`PickVisualMedia`, no permission), image down-scaling, image chips in the chat.
- **Done when:** a photo question is answered offline on your phone, or the phase is documented as skipped with the
  evidence.

### Phase 17 — Tool calling
- **Gate:** verify with a small on-device test that Gemma 4 E2B produces tool calls through LiteRT-LM's
  `automaticToolCalling`.
- **Files:** `core/tools` (new): `CalculatorTool` (exact arithmetic — this also fixes "25 + 26" style mistakes),
  `DateTimeTool`, `UnitConverterTool`, `NotesTool` (Room); a "Tools" toggle per chat; a visible "used calculator" chip.
- **Done when:** "what is 123.4 × 56" uses the calculator and is exact; tools are unit-tested without the model.

---

## Part G — Security and privacy

### Phase 18 — Encryption and data control
- **Files:** SQLCipher for Room (`net.zetetic:sqlcipher-android`, version verified), a key generated once and wrapped
  by Android Keystore, a **one-time migration** of the existing plaintext DB (`sqlcipher_export`) with a backup-and-verify
  step; Settings → "Export all my data" (ZIP: chats as Markdown + JSON, settings; no models) and "Delete all my data"
  (with confirmation); backup rules re-checked; `docs/data-safety.md` mapping every Play Data safety field;
  privacy-policy update.
- **Risks:** encryption migration is the riskiest change in v2 → copy, encrypt, verify row counts, then swap; on any
  failure keep the original. APK size grows (measured and reported).
- **Done when:** your real chats survive the upgrade (tested on a copy first), and the DB file is unreadable without the
  key.

---

## Part H — Release engineering

### Phase 19 — Play pipeline
- **Files:** release notes from conventional commits (`whatsnew/` per language); Play In-App Updates (flexible) and
  In-App Review (after the 5th good answer, at most once per 90 days); Gradle Play Publisher in `app` and a `play.yml`
  workflow (tag → signed AAB → internal track); `docs/release-guide.md` (staged rollout 5 → 20 → 50 → 100 %, halt
  criteria, rollback).
- **Risks:** In-App Update and Review talk to the Play Store app (not our server) → the privacy policy and CLAUDE.md
  rule "no network except model download" must be reworded to "PocketMind itself makes no network calls except model
  downloads; Google Play features run through the Play Store app". **Needs your OK.**
- **Done when:** a tag uploads to the internal track (after you add the service-account secret).

## Part I — Business

### Phase 20 — PocketMind Pro and diagnostics
- **Files:** `core/billing` (new): Play Billing Library (version verified), one-time product `pocketmind_pro`,
  purchase flow, acknowledgement, entitlement cached in DataStore, refreshed through `queryPurchasesAsync` (local, no
  server); `docs/billing.md` explaining the trade-off (no server receipt check, so a determined attacker can patch the APK
  — acceptable for a one-time low-price unlock, not for subscriptions). Pro gates: bigger models, unlimited PDF size,
  personas (see decision 2 about voice). **Diagnostics:** a local crash log (uncaught-exception handler + last native
  load marker) and a user-initiated "Share diagnostic report" through the share sheet — no SDK, nothing sent
  automatically (see decision 1).
- **Done when:** a license-tester account can buy and restore Pro on your phone (Play Console test purchase).

## Part J — Reach

### Phase 21 — Punjabi, Hindi review, per-app language, accessibility pass
- **Files:** `values-pa/` (Gurmukhi), Hindi revisions, `localeConfig` + in-app language picker (L-4), TalkBack
  walkthrough of every screen (written checklist), 48 dp audit, layouts checked at 200 % font (screenshot tests at
  font 2.0 from phase 10).
- **Risks:** I write the Punjabi and Hindi, but quality needs a native speaker → listed as a manual step.
- **Done when:** the app is fully usable with TalkBack and at 200 % font in all three languages.

### Phase 22 — End-to-end tests and CI gate
- **Files:** Compose UI tests with Hilt test modules + `FakeLlmEngine` for: onboarding → download (fake) → chat → stop
  → history → delete/undo; PDF summarize; lock/unlock. CI: required checks, coverage report artifact and PR comment.
- **Done when:** the E2E suite runs green in CI (emulator job), and `koverVerify` enforces ≥ 80 % on data, domain and
  ViewModel classes.

### Final
`assembleRelease bundleRelease`, re-audit → `AUDIT_AFTER.md` (old vs new scorecard), README update (features,
screenshots, architecture, measured benchmarks, scorecard), final report with three resume bullets using only
measured numbers.

---

## Decisions I need from you before building

1. **Crash reporting.** "Opt-in crash reporting" through a third-party SDK (Crashlytics or Sentry) breaks the "no data
   leaves the phone" promise and the CLAUDE.md network rule. **I recommend** a local crash log plus "Share diagnostic
   report" that you or the user sends manually. Approve, or name an SDK.
2. **What Pro unlocks.** Your brief puts *voice* behind Pro. Voice input is an accessibility feature, so paywalling it can
   draw criticism and hurt reviews. **I recommend** Pro = bigger models + unlimited PDF + personas + extra themes, and keep
   voice free.
3. **The "bigger model" for Pro.** Likely Gemma 4 E4B, but I'll only add it after verifying the real Hugging Face file,
   size and SHA-256, and that it runs on 8 GB phones.
4. **Pro price.** You set it in Play Console; I won't hard-code or suggest a number as fact.
5. **Network rule wording** for In-App Update, Review and Billing (phase 19): approve the new wording.
6. **SQLCipher** increases APK size (measured in phase 18). OK?
7. **Scope.** This is about 23 phases, a multi-session effort. You may cut or reorder; the order above fixes risk first,
   then looks, then features, then money.

## Things only you can do (also copied into PROGRESS.md during the build)

- Keep the phone available for: model-capability check (phases 16–17), engine instrumented test (phase 4), Baseline
  Profile and startup benchmark (phase 6), lock and PDF test (phase 2), voice in airplane mode (phase 15), real-DB
  migration test (phases 11, 18).
- **Google Play Console**: developer account (US$25), app created with package `com.sanjay.pocketmind`, merchant/payments
  profile for Pro, in-app product `pocketmind_pro` and its price, license-tester accounts, Data safety form (from
  `docs/data-safety.md`), store listing (icon, feature graphic, screenshots, descriptions), closed test with 12+ testers
  for 14 days.
- **Google Cloud service account** with Play Developer API access → JSON key as a GitHub secret (for Gradle Play Publisher).
- **Release keystore** + 4 GitHub secrets (from v1), and **Play App Signing** enrolment.
- **GitHub**: push the repo, enable branch protection on `main` (require CI and coverage checks).
- **Native-speaker review** of Hindi and Punjabi text.
- Benchmarks on 2 more phones for the README and resume.
