# PocketMind V3 — Audit of v2 against the V3 spec (2026-11-10)

Stage 1 of CLAUDE.md §9. No code was changed. Starting point: tag `v2-final` (commit `539803e`, branch `v2`).
Every version below was read from Maven Central, Google Maven, PyPI or GitHub on 2026-11-10; every count was
measured in this repo. Anything not verified is marked **UNVERIFIED**.

## 1. Summary

v2 is a solid, tested, **Android-only, fully offline** app: 25 Gradle modules, 209 main Kotlin files (17,427 lines),
398 passing tests, 84.2 % gated line coverage, 8 Room schema versions with migration tests, verified on a real
phone. V3 asks for three things v2 does not have at all:

1. **A second platform (iOS)** from one Kotlin Multiplatform + Compose Multiplatform codebase.
2. **A backend** ("PocketMind Brain", Python/FastAPI) with 8 production components. Nothing exists yet.
3. **Accounts, teams and cloud routing**, which change PocketMind's core promise ("nothing leaves the phone")
   into "nothing leaves the phone *in privacy mode*".

Of the 16,812 main lines in these modules, **49 % move to shared KMP code with small changes**, **40 % need an
`expect/actual` split** (same interface, Android code kept, iOS code new), and **11 % stay Android-only**
(widget, tile, Play services, Macrobenchmark). Counted by lines, per module
(table in §3). The biggest technical risks are listed in §7; the biggest *practical* blocker is that **iOS code
cannot be compiled on this Windows PC at all** (Kotlin/Native Apple targets need a macOS host), and the repo has
**no GitHub remote yet**, so no CI has ever run.

## 2. Build environment (measured)

| Item | Found | Consequence |
|---|---|---|
| PC | Windows 11, Intel i5-13420H, 16 GB RAM, 229 GB free | Android builds fine (v2 full check: 4–10 min) |
| JDK | Temurin 17 (Gradle) and 21 (tests) | unchanged |
| macOS / Xcode | **none** | iOS targets, the iOS app and the Swift bridge can only be built on a GitHub macOS runner |
| Git remote | **none** (58 commits exist only on this PC) | no CI, no backup, no macOS runner until the owner creates a repo |
| Python | none on Windows; **WSL Ubuntu 24.04 has Python 3.12.3**, pip, make | backend is developed and tested inside WSL |
| Docker | **none** (not on Windows, not in WSL) | local tests use pip-only stand-ins (§6); real Postgres/Redis run on CI and on the VM |
| sudo in WSL | needs the owner's password | can't `apt install` Postgres/Redis/Docker without the owner |
| WSL memory | 7 GB visible | enough for tests; NOT a stand-in for the Oracle VM's RAM budget |
| Phone | iQOO Neo 10R, Android 16, 8 GB (wireless adb when connected) | the only real device; no iPhone |

## 3. v2 inventory and what happens to each module

Lines = main Kotlin source only. "Platform imports" = the Android/JVM-only APIs the module uses (measured with grep).

| v2 module | Files / lines | Platform-only APIs used | V3 destination | Verdict |
|---|---|---|---|---|
| core:model | 8 / 263 | none | `shared/core` commonMain | **move as is** |
| core:common | 8 / 331 | Hilt, `java.io`, `android.app` (crash diagnostics) | `shared/core` (Result, dispatchers) + androidMain (diagnostics) | small split |
| core:tools | 5 / 664 | none (pure Kotlin) | `shared/tools` commonMain | **move as is**; becomes the *on-device* tool set |
| core:designsystem | 10 / 640 | Compose only, 1 `android.os` (haptics/dynamic colour) | `shared/designsystem` | port, then **replaced** by the V3 design system (phase 3) |
| core:llm | 16 / 1,174 | **LiteRT-LM Kotlin** (21 imports), `ApplicationExitInfo`, `ActivityManager` | `shared/ai`: `LlmEngine`, `EngineState`, prompt/context logic in commonMain; LiteRT-LM engine + LoadGuard in androidMain; iOS actual via Swift bridge | **expect/actual** |
| core:database | 23 / 1,303 | Room (Android), **SQLCipher**, **Android Keystore**, `SupportSQLite` migrations | `shared/data`: Room KMP entities/DAOs/migrations in commonMain; encryption in androidMain; iOS uses file protection (§5) | port + split |
| core:datastore | 2 / 180 | DataStore Preferences (Android artifact) | `shared/data` with DataStore KMP (`datastore-preferences-core` 1.3.0) | port |
| core:speech | 4 / 312 | `android.speech`, `TextToSpeech` | `shared/ai` interface; androidMain; iOS `SFSpeechRecognizer` (on-device) + `AVSpeechSynthesizer` | **expect/actual** |
| core:billing | 3 / 266 | Play Billing | androidApp only; iOS StoreKit needs the paid Apple account (§7) | Android-only |
| core:testing | 6 / 324 | DataStore, Robolectric helpers | `shared/*/commonTest` fakes + androidUnitTest helpers | port |
| data:chat | 14 / 1,580 | `android.graphics` (photo scaling, EXIF), `PdfDocument` (export), `Uri`, `ContentResolver` | `shared/data` repository in commonMain; image store + PDF export expect/actual | port + split |
| data:models | 14 / 976 | **WorkManager**, foreground service, `StatFs`, notifications, OkHttp | catalog, SHA-256, RAM rule in commonMain (Ktor download core); scheduling in androidMain (WorkManager) / iosMain (background `URLSession`) | **expect/actual** |
| data:benchmark | 6 / 299 | `Debug.getPss` | commonMain + memory reader actual | small split |
| data:documents | 11 / 746 | **PdfBox-Android**, `ContentResolver` | chunker, summariser, translator, vector search in commonMain; PDF text extraction actual (PdfBox / iOS PDFKit) | port + split |
| feature:* (9 modules) | 42 / 6,154 | Compose (portable), Hilt ViewModels, activity-result pickers, biometric, `android.text.format`, `androidx.window` | `composeApp` commonMain (Koin, KMP lifecycle/navigation); pickers and biometric via small platform interfaces | **port**, then redesign (phase 3) |
| app | 19 / 1,414 | Glance widget, quick-settings tile, Play update/review, biometric lock, share-in intents, `onTrimMemory`, LeakCanary, StrictMode | `androidApp` (stays Android) | Android-only, rewired to Koin |
| benchmark | 4 / 186 | Macrobenchmark | stays Android-only | keep |

Lines by verdict (16,812 lines in the modules above): move/port to common 8,225 (49 %); expect/actual split 6,721 (40 %); Android-only 1,866 (11 %). The split modules are counted whole, so the truly platform-specific share is smaller.

## 4. Library mapping (v2 → V3), versions verified 2026-11-10

| Concern | v2 | V3 choice | Latest stable found | Notes |
|---|---|---|---|---|
| Kotlin | 2.4.21 | same | 2.4.21 (2.5.0-Beta2 exists; stay stable) | |
| UI | Jetpack Compose (BOM 2026.09.00) | Compose Multiplatform | **1.12.2** (1.13.0-beta02 exists) | iOS minimum OS: **UNVERIFIED**, check in phase 1 |
| Navigation | Navigation Compose 2.10.2 | JetBrains `navigation-compose` (KMP) | 2.10.0 | type-safe routes as in v2 |
| ViewModel | AndroidX | JetBrains `lifecycle-viewmodel-compose` (KMP) | 2.11.0 | |
| DI | Hilt 2.60.1 + KSP | **Koin** | **4.2.2** (4.3.0-Beta1 exists) | removes the KSP/Hilt flakiness that cost 9 BLOCKER entries in v2 |
| Database | Room 2.8.5 (Android) | **Room KMP** (same artifact family) | 2.8.5, with iOS targets (`room-runtime-iosarm64`, `-iossimulatorarm64`, `-iosx64`) | chosen over SQLDelight: keeps v2's entities, 8 exported schemas and migration tests (§5). Bundled driver `sqlite-bundled` 2.7.1 |
| Settings | DataStore 1.3.0 | DataStore KMP | 1.3.0 (`datastore-preferences-core`) | same keys (15) → no data loss |
| HTTP | OkHttp 5.5.1 | **Ktor client** | 3.6.1 | SSE support for `/v1/chat`; OkHttp engine on Android |
| JSON | kotlinx.serialization | same | 1.12.0 | |
| Logging | Timber | **Kermit** | 2.2.0 | |
| Images | (none; photos decoded by hand) | Coil 3 | 3.6.3 | |
| Markdown | mikepenz renderer 0.46.0 | same (it is already multiplatform) | 0.46.0 | the streaming fix from v2 carries over |
| Screenshots | Roborazzi (Android/Robolectric) | Roborazzi on the Android target of shared UI | 1.78.0 | iOS screenshots only on the macOS runner |
| On-device LLM (Android) | LiteRT-LM 0.19.0 | same | **0.19.1** | |
| On-device LLM (iOS) | — | LiteRT-LM Swift package, product `LiteRTLM` | v0.19.1 binary (`Package.swift` checked: `.iOS(.v15)`, xcframework URL + checksum published) | early preview; embeddings API on iOS **UNVERIFIED** |
| DB encryption | SQLCipher 4.20.0 | Android: same; iOS: see §5 | 4.20.0 | |

Backend (PyPI, all support Python 3.12): FastAPI 0.162.2, Pydantic 2.14.3, pydantic-settings 2.16.2, Uvicorn
0.54.0, SQLAlchemy 2.1.8, asyncpg 0.32.0, Alembic 1.20.1, httpx 0.28.1, redis 8.1.0, sse-starlette 3.7.0,
PyJWT 2.15.1, OpenTelemetry SDK 1.45.2 (+ FastAPI instrumentation 0.66b2), prometheus-client 0.26.0, langfuse
5.2.0, **mcp 2.3.0** (official SDK), pgvector 0.5.1, pypdf 7.0.0, python-docx 1.2.0, trafilatura 2.3.1, fastembed
0.9.1, pytest 9.2.0, pytest-asyncio 1.4.1.post1, pytest-cov 7.1.0, ruff 0.17.4, mypy 2.4.1, pip-audit 2.10.1,
locust 2.47.0. Local stand-ins: pgserver 0.1.4 (bundled Postgres; pgvector inside it **UNVERIFIED**),
fakeredis 2.40.0.

## 5. Data migration risks (the owner's real phone has real chats)

| # | Risk | How V3 avoids it |
|---|---|---|
| D-1 | A new app ID would install V3 as a *second* app with no data | keep `applicationId = com.sanjay.pocketmind`, same signing key |
| D-2 | Moving Room to KMP changes the database | same file `pocketmind.db`, same tables; KMP schema becomes **v9** with a real migration test from **every** schema 1–8 (all 8 JSON schemas are committed) |
| D-3 | SQLCipher is Android-only; Room KMP's driver API differs from v2's `SupportOpenHelperFactory` | Android keeps the v2 encrypted open helper in androidMain (Room's Android builder still accepts an open-helper factory: **verify in phase 2a** in the resolved 2.8.5 artifact). Same key file in `noBackupFilesDir` |
| D-4 | iOS has no SQLCipher with Room's bundled driver | iOS stores the DB with Data Protection (`NSFileProtectionComplete`) instead of app-level encryption; documented as a platform difference. A SQLCipher build for iOS is possible but not free of effort; decide later |
| D-5 | Room KMP needs `suspend`/`Flow` DAOs | `NoteDao` has 4 blocking functions (used by the notes tool): convert to `suspend` |
| D-6 | FTS4 search table (`messages_fts`, `unicode61`) on iOS | **UNVERIFIED** whether the bundled SQLite build includes FTS4; test on the macOS runner in phase 2e; fallback: escaped `LIKE` (v2 already has it for titles) |
| D-7 | Migrations use `SupportSQLiteDatabase` | rewrite the 4 v2 migrations against Room KMP's `SQLiteConnection` API; keep the same SQL |
| D-8 | Settings, models, photos, notes keys and paths | DataStore keys (15) unchanged; models stay in `filesDir/models/<id>/`; photos in the same folder |
| D-9 | v2 data that later goes to the cloud | never automatic: uploading local chats or documents to the backend is an explicit user action |

## 6. Gaps against the V3 spec

**Missing entirely (new work):** iOS app and Swift bridge; KMP structure; the whole backend (LLM layer, router,
caches, RAG service, tools registry, MCP, auth/RBAC/RLS, guardrails, observability, evals); accounts and orgs;
admin console; infra (Compose file, Caddy, keep-alive, backups, Grafana); backend and iOS CI; the V3 design
system and 12 screens.

**Exists in v2 and is reused:** on-device chat engine with GPU→CPU fallback and crash guard; on-device RAG
(EmbeddingGemma, cosine search, page citations); on-device tools (calculator, date, units, notes); streaming UI
with the flicker and scroll fixes; voice in/out; personas, prompt library; export/delete my data; app lock;
EN/HI/PA; adaptive layouts; 72 screenshot goldens; the release pipeline (signing from env, Play publishing).

**Conflicts between v2 decisions and the V3 spec (need the owner):**

| # | v2 | V3 spec | Proposal |
|---|---|---|---|
| C-1 | Privacy promise: nothing ever leaves the phone; Data safety = "No data collected" | Cloud routes, accounts, Langfuse traces | Privacy mode **on by default** (guests are always on-device); new privacy policy and Data safety answers before any cloud release |
| C-2 | No third-party crash SDK; library telemetry blocked | "Mobile: opt-in crash reporting (free tier)" | keep v2's local crash log + "share report"; add a free crash service only behind an explicit opt-in, chosen in phase 12 |
| C-3 | Owner's name: the owner asked on 2026-11-02 to use **Sanjay** everywhere (app ID `com.sanjay.pocketmind`) | Identity text says "built by **Manjit Singh**"; manual-steps heading says "FOR MANJIT" | ask the owner which name the assistant should state |
| C-4 | Langfuse traces normally include prompt text | "no raw user content in logs by default" | mask inputs/outputs in Langfuse unless the user opts in |

## 7. Risks and unknowns

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R-1 | **No Mac**: Kotlin/Native iOS targets can't compile on Windows | iOS code is unverified until CI runs | iOS code is kept thin (Swift host + actuals); every iOS phase is "to verify on CI/iPhone"; shared logic is tested on the Android/JVM targets |
| R-2 | **No GitHub remote** | no CI, no macOS runner, no backup | owner creates a repo (spec says public for free Actions minutes; the owner must accept that the code becomes public) |
| R-3 | LiteRT-LM Swift is an early preview | iOS on-device chat may not work or may change | handle "not available" gracefully; iOS guests still get the UI and cloud mode |
| R-4 | No Docker/Postgres/Redis locally | backend integration tests | pgserver + fakeredis locally; real services on CI; the owner may install Docker in WSL later (needs sudo) |
| R-5 | Free-tier limits change (Oracle A1, Supabase, Groq, Gemini, Langfuse) | wrong assumptions | all limits read from config; 429 handling; owner verifies in each console (§8 of V3_PLAN) |
| R-6 | CPU-only llama.cpp on a ≤ 12 GB Arm VM | slow cloud answers | small GGUF Q4 model; router sends long or heavy jobs to free external providers when allowed; RAM budget measured on the VM |
| R-7 | Supabase Free projects pause when idle; 500 MB DB | outages, storage | keep-alive job; lean chunks; hard deletes |
| R-8 | Scale of the port (25 modules → ~8) while "nothing may break" | regressions | strangler approach: a v2 module is deleted only when its KMP replacement passes the same tests; the v2 E2E tests guard the Android app throughout |
| R-9 | Free external providers may train on data | privacy | never route privacy-mode, guest or PII-flagged requests there; PII redaction before any external call |
| R-10 | Sign in with Apple, App Store, iOS in-app purchase need the paid Apple Developer Program | conflicts with zero cost | build and test with a free Apple ID; production iOS release is an owner decision |
| R-11 | Model licences for the server model (Gemma terms vs Apache-2.0 Qwen) | legal | pick the model in phase 5 with the licence recorded |

## 8. Scores (baseline for AUDIT_V3_AFTER.md)

Scored against the **V3 spec**, so v2's strong Android work scores low where V3 asks for more.

| Area (V3 spec) | Now | Why |
|---|:-:|---|
| Cross-platform app (Android + iOS) | 2 | Android only |
| V3 GUI / design system | 4 | good v2 design system, but not the V3 screens |
| On-device AI (offline mode) | 8 | works and measured on a phone (Android) |
| LLM API layer (backend) | 0 | none |
| Tools (device + server) | 3 | 4 on-device tools only |
| RAG | 4 | on-device only, no hybrid search, no collections |
| MCP | 0 | none |
| Auth + RBAC | 0 | none (app lock only) |
| Guardrails | 1 | system-prompt rules only |
| Caches + router | 0 | none |
| Observability + evals | 1 | local crash log; no traces, metrics or evals |
| Infra / zero-cost deployment | 0 | none |
| Tests + CI | 5 | 398 Android tests, 84 % logic coverage; CI never ran |
| **Total** | **28 / 130** | |
