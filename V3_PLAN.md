# PocketMind V3 — Build plan

Stage 2 of CLAUDE.md §9. Based on [AUDIT_V3.md](AUDIT_V3.md). Branch `v3`, starting from tag `v2-final`.
**Status: waiting for the owner's "approved".** Nothing below is built yet.

## 0. How every phase runs

1. Implement → tests → run the gates below → fix until green → small conventional commits → update PROGRESS.md
   → write `docs/learn/v3-phase-N.md` (CLAUDE.md §11) → next phase, without stopping.
2. **Mobile gate** (from `mobile/`): `./gradlew spotlessApply detekt lint check koverVerify assembleDebug`
   (`check` runs the Android and JVM test targets of shared code; iOS tests run only on the macOS runner).
   Coverage: shared logic ≥ 80 % (starts at v2's 84 % for ported code and must not drop).
3. **Backend gate** (in WSL, from `backend/`): `ruff check && ruff format --check && mypy --strict app && pytest
   --cov=app --cov-fail-under=80`; from phase 13 also `make eval-fast`.
4. Anything that needs the phone, an iPhone, a Mac, CI, an account or the Oracle VM is marked **"to verify on
   …"** and listed for the owner; it never blocks the run.
5. Same error 3 times → BLOCKERS in PROGRESS.md, safest working alternative, continue.
6. **Safety rule for the owner's data:** from phase 2a on, every Android build must upgrade a copy of a v2
   database (schemas 1–8) without losing a row. The v2 E2E tests stay green until their screens are replaced.

## 1. Decisions the owner should confirm (with "approved" or a correction)

| # | Question | Proposal used if not corrected |
|---|---|---|
| A | Which name does the assistant give as its builder, and which name goes in docs and headings? The spec says "Manjit Singh"; on 2026-11-02 the owner asked for **Sanjay** everywhere. | **Owner to answer.** Until then the identity text is one constant + one eval file, so it changes in one place |
| B | Default of the privacy-mode switch | **On** (on-device only) for everyone; guests can't turn it off. Keeps v2's promise by default |
| C | Crash reporting | v2's local crash log + "share report" only; no crash SDK. An opt-in free service only if the owner asks |
| D | Langfuse traces | prompts and answers **masked** by default |
| E | GitHub repo | the owner creates it; the spec says **public** (free macOS minutes), which makes the code public |
| F | Local backend tests without Docker | pip-only: `pgserver` (Postgres) + `fakeredis`; real Postgres/pgvector/Redis on CI and the VM |
| G | iOS database encryption | iOS Data Protection (file encrypted while the phone is locked), not SQLCipher (AUDIT_V3 D-4) |

## 2. Phases

Each phase lists: **Goal · Files · Risks · Tests · Done when · Owner**.

### Phase 1 — Monorepo, KMP skeleton, CI
- **Goal:** move v2 under `mobile/` unchanged, add the KMP structure and the iOS host, CI for Android + iOS.
- **Files:** `git mv` v2 build → `mobile/`; `mobile/shared/core` (KMP: androidTarget, iosArm64, iosSimulatorArm64;
  `core:model` + `core:tools` code in commonMain); `mobile/composeApp` (empty shared app shell);
  `mobile/iosApp` (Xcode project, SwiftUI host); `.github/workflows/mobile-ci.yml`, `ios-build.yml` (macos runner).
- **Risks:** path moves break Gradle/CI/scripts; the iOS minimum OS for Compose Multiplatform 1.12.2 is unverified;
  KMP plugin vs AGP 9.5 compatibility (check the KMP Android library plugin).
- **Tests:** all v2 tests still pass from `mobile/`; `core` + `tools` tests run in commonTest on the JVM/Android target.
- **Done when:** `./gradlew check assembleDebug` green from `mobile/`; v2 app identical on the phone; iOS build
  **to verify on CI**.
- **Owner:** create the GitHub repo and push (decision E).

### Phase 2a — Shared data layer (Room KMP, DataStore KMP, Koin)
- **Goal:** v2's database, settings and repositories in `shared/data`; Hilt → Koin for these.
- **Files:** entities/DAOs/migrations → commonMain (schema **v9**, `NoteDao` made `suspend`); Android SQLCipher
  open helper + Keystore key in androidMain; iOS bundled driver + file protection; DataStore KMP with the same 15 keys.
- **Risks:** AUDIT_V3 D-2, D-3, D-5, D-7; Room's Android builder accepting the SQLCipher factory must be checked in the
  resolved artifact.
- **Tests:** migration tests from **every** schema 1–8 to 9; DAO tests in commonTest; encryption logic tests; a
  device test that upgrades a real v2 database copy.
- **Done when:** gates green; the phone upgrades **a copy** of the real database with every row intact (to verify on
  device, backup first).

### Phase 2b — Shared AI (engine interface, on-device RAG, documents)
- **Goal:** `shared/ai` with `LlmEngine`/`EngineState`, context trimming, prompt builders, chunker, summariser,
  translator, vector search, on-device tools wiring in commonMain; LiteRT-LM 0.19.1 + LoadGuard in androidMain.
- **Risks:** LiteRT-LM 0.19.0 → 0.19.1 API changes (javap check); PDF extraction split (PdfBox / PDFKit).
- **Tests:** all v2 engine-logic, chunker, prompt-builder, RAG and tool tests moved to commonTest; FakeLlmEngine in commonTest.
- **Done when:** gates green; the phone chats, summarises a PDF and answers with page citations offline.

### Phase 2c — UI port to Compose Multiplatform (Android parity)
- **Goal:** all 9 feature modules → `composeApp` (KMP ViewModels, JetBrains navigation, Koin); `androidApp` hosts it.
- **Risks:** Android-only bits in screens (pickers, biometric prompt, `android.text.format`) need small platform
  interfaces; the v2 streaming/scroll fixes must survive.
- **Tests:** v2 ViewModel tests in commonTest; Roborazzi goldens re-recorded on the Android target; the 3 v2 E2E tests
  ported.
- **Done when:** every v2 feature works on the phone from the shared UI; old feature modules deleted.

### Phase 2d — Platform services
- **Goal:** speech, app lock, model downloads, Play Billing, widget, tile, share-in, in-app update/review rewired to Koin.
- **Files:** interfaces in commonMain; Android actuals (WorkManager, biometric, `SpeechRecognizer`, Glance stays in
  androidApp); iOS actuals for speech (on-device `SFSpeechRecognizer`, `AVSpeechSynthesizer`), lock
  (`LocalAuthentication`), downloads (background `URLSession`).
- **Done when:** Android parity checklist from v2 passes on the phone; iOS actuals compile **on CI**.

### Phase 2e — iOS app and LiteRT-LM Swift bridge
- **Goal:** iOS runs the shared UI; on-device chat through a Swift class implementing a Kotlin `OnDeviceEngine`
  interface from `shared`, using the `LiteRTLM` Swift package v0.19.1.
- **Risks:** AUDIT_V3 R-1, R-3, D-6 (FTS4 on iOS).
- **Tests:** iOS simulator tests on the macOS runner (shared commonTest + bridge smoke test with a fake).
- **Done when:** `ios-build` green on CI; app launches on the simulator (CI screenshot); on-device chat **to verify on
  an iPhone**.
- **Owner:** Mac with Xcode or a free Apple ID for a real iPhone test.

### Phase 3 — V3 design system and all screens
- **Goal:** new tokens (colour light/dark + Android dynamic colour, type scale, 4/8 spacing, radius, elevation,
  motion, icons) and the 12 screens from CLAUDE.md §5 with loading/empty/error/content/offline states.
- **Risks:** large UI change on top of parity; iOS conventions (back-swipe, sheets, safe areas).
- **Tests:** Roborazzi per screen: light/dark × phone/tablet × font 200 %; accessibility labels checked in UI tests.
- **Done when:** every screen exists with all states and goldens; cloud-only screens show a clear "sign in / offline"
  state until their backend phases land.

### Phase 4 — Backend skeleton
- **Goal:** `backend/` FastAPI app: settings from env, `/v1/health`, error model, request IDs, JSON logs, OTel setup,
  Alembic migrations (users, orgs, roles, audit log), Dockerfile (non-root), `backend-ci.yml` with Postgres+pgvector
  and Redis services.
- **Tests:** API tests with httpx; migration up/down on pgserver.
- **Done when:** backend gate green in WSL; CI green **to verify on CI**.

### Phase 5 — LLM layer, SSE streaming, AiRouterClient
- **Goal:** `LLMProvider` (chat/embed/health/capabilities), `LlamaCppProvider`, `OpenAICompatibleProvider`,
  `MockProvider`; versioned prompt files; `/v1/chat` SSE with the 8 event types; Ktor SSE client and `AiRouterClient`
  in the app (on-device when privacy mode/offline, else Brain); route badge.
- **Risks:** server model choice and licence (R-11), CPU speed (R-6).
- **Tests:** provider contract tests with recorded responses; SSE parser tests on both sides; retry/backoff/timeout tests.
- **Done when:** the app streams from a local backend + mock provider; llama.cpp end-to-end **to verify on the VM**.

### Phase 6 — Auth (Supabase OAuth + PKCE), RBAC, RLS
- **Goal:** sign in with Google, GitHub, email link, Apple (iOS); PKCE in the app; tokens in Keystore/Keychain;
  backend JWT verification (JWKS, issuer, audience, expiry); roles and one permissions matrix (`docs/rbac.md` + code);
  RLS policies; audit log; logout everywhere; delete my account.
- **Tests:** JWT tests with locally generated keys; permission-matrix table tests; RLS tests on Postgres.
- **Done when:** guest mode unchanged; sign-in **to verify with the owner's Supabase project**.
- **Owner:** Supabase project, OAuth apps (Google, GitHub; Apple needs the paid program).

### Phase 7 — Router and caches
- **Goal:** explainable routing policy (privacy, online, role/quota, task classifier, length, health, latency),
  fallback chains, circuit breakers, 429 handling, concurrency limits; exact, semantic (per user/org), embedding and
  retrieval caches with TTLs and invalidation; never cache privacy-mode or side-effect turns.
- **Tests:** policy table tests, breaker state tests, cache isolation tests (no cross-user hits).

### Phase 8 — Guardrails (backend + on-device)
- **Goal:** `GuardrailResult` pipeline: input (size, PII redaction before external providers, injection/jailbreak
  heuristics + classifier), RAG (strip injected instructions, groundedness), tools (allowlist, schema, confirmation),
  output (safety, PII leak, system-prompt leak, **identity**, format); light on-device checks; events logged without
  raw text.
- **Risks:** a safety classifier model that fits the RAM budget (choose and record in phase).
- **Tests:** red-team fixtures per check; precision/recall measured in phase 13.

### Phase 9 — RAG (backend) and Knowledge screens
- **Goal:** ingestion (PDF/DOCX/TXT/HTML/URL), structure-aware chunking, embeddings, pgvector + full-text, RRF,
  cross-encoder rerank, context packing, `[doc, p. N]` citations, "I don't know"; personal and org collections with
  RBAC + RLS; background jobs with progress; dedupe; hard delete. On-device RAG stays.
- **Tests:** chunker and RRF unit tests; retrieval tests on a fixture corpus; ACL tests.

### Phase 10 — Tools (server + device protocol)
- **Goal:** registry (JSON Schema, `side_effect`, `location`, permission); server tools (safe calculator, unit/time,
  weather via a free no-key API **to verify**, notes/tasks); device tools via `tool_call` → confirmation card →
  `/v1/chat/{id}/tool_result`; Android and iOS actuals (calendar, reminders, alarms, contacts, clipboard, open link,
  share) with just-in-time permissions.
- **Tests:** schema validation, depth and size limits, confirmation required for side effects.

### Phase 11 — MCP
- **Goal:** `MCPManager` (stdio + streamable HTTP, namespacing, RBAC + guardrails, timeouts) with mcp 2.3.0;
  `pocketmind-mcp` server (notes, tasks, knowledge search); Tools & MCP screen; admin enable/disable per role.
- **Tests:** in-process MCP server/client tests.

### Phase 12 — Observability
- **Goal:** OTel spans for every lifecycle step; Langfuse (masked); Prometheus metrics from CLAUDE.md §6.8; Grafana
  dashboard JSON; mobile TTFT timing; crash reporting per decision C.
- **Tests:** span/metric presence tests; no raw user text in logs (test).

### Phase 13 — Evals
- **Goal:** versioned JSONL datasets (chat, RAG with gold citations, tools, router labels, red-team, identity),
  metrics, LLM-as-judge with a rubric, `make eval` reports, `make eval-fast` + `evals/thresholds.yaml` CI gate.
- **Risks:** a judge model at zero cost (local llama.cpp or a free tier) and run time on CI.

### Phase 14 — Admin console, orgs and teams
- **Goal:** role-gated admin screens (users & roles, usage, guardrail events, router stats, eval scores, feature
  flags), org membership and shared collections.

### Phase 15 — Infra for the Oracle VM
- **Goal:** `infra/docker-compose.yml` (api, llama.cpp, embed/rerank, redis, caddy, prometheus, grafana) for Arm,
  Caddy + DuckDNS HTTPS, keep-alive for Supabase, nightly `pg_dump` + restore test, `docs/runbook.md` with the RAM
  budget, step-by-step deployment guide.
- **Done when:** `docker compose config` valid; images build for arm64 on CI; deployment **to verify on the VM**.
- **Owner:** Oracle account + VM, DuckDNS, SSH keys, secrets.

### Phase 16 — Hardening and release
- **Goal:** security review (headers, CORS, limits, secrets, pip-audit, Dependabot), load test with Locust within free
  limits, accessibility and localisation (EN/HI/PA) pass, new privacy policy + Data safety answers, release builds
  (signed AAB/APK; iOS archive instructions).

### Final
Full gates, release builds, `AUDIT_V3_AFTER.md` (scorecard against AUDIT_V3 §8), README (architecture, features,
screenshots, **measured** benchmarks, zero-cost deployment guide), the Oracle deployment guide, final report with
resume bullets from measured numbers only.

## 3. Manual steps and unknowns for the owner (when each is needed)

| When | Step |
|---|---|
| Before phase 1 ends | Create the GitHub repo (public or private: decision E) and allow me to push |
| Phase 2a | Connect the phone; I back up the real database before the first V3 install |
| Phase 2e | A Mac with Xcode, or a free Apple ID + iPhone, to run the iOS app |
| Phase 5 | Choose whether free external providers (Groq, Gemini API) are used at all; create free keys if yes |
| Phase 6 | Supabase project; Google and GitHub OAuth apps; Apple sign-in needs the paid Apple Developer Program |
| Phase 12 | Langfuse Cloud Hobby keys (optional) |
| Phase 15 | Oracle Cloud Always Free VM (verify the current A1 limits in the console), DuckDNS name, SSH keys |
| Any time | Optional: `sudo apt install docker.io` in WSL to run the real stack locally |

Unverified until the owner or CI checks them: Oracle A1 free limits; Supabase free limits and idle pause;
free-provider rate limits and data-use terms; Compose Multiplatform's minimum iOS version; FTS4 in Room's bundled
SQLite on iOS; LiteRT-LM Swift embeddings; pgvector inside pgserver; the free weather API.
