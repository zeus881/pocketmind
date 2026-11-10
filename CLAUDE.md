# PocketMind V3 — Project Instructions for Claude Code

PocketMind V3 turns the existing Android-only offline assistant (v1/v2, in this repo) into a
**full AI platform**: one cross-platform app for **Android + iOS** with a redesigned GUI, plus
**our own AI backend ("PocketMind Brain")** with 8 production components:

1. LLM API layer  2. System/device tool calls  3. RAG  4. MCP tools  5. RBAC + OAuth
6. Guardrails  7. Caches + router  8. Observability + evals

Read this file fully at the start of every session. It is the single source of truth.
Also read `PROGRESS.md` (create it if missing) before doing anything.

---

## 0. Hard constraints

- **Zero running cost.** Use only free tiers, free open-source software, and the owner's own
  free cloud VM. Never add a paid service, paid API key, or anything that needs a paid plan
  to work. If a feature truly needs money, stop and ask.
- **Free tiers change.** Never hard-code a provider's rate limit as fact. Read limits from
  config, handle HTTP 429 gracefully, and fall back to another route.
- **Keep the offline on-device mode** from v1/v2. The app must still work in airplane mode.
- **Nothing that works today may break.** Migrate v2 features into V3; don't drop them.
- **Verify every library API** in the real resolved artifact / package before using it.
  Never invent APIs, versions, limits, prices, model names, URLs or checksums. Mark unknowns
  and list them under "MANUAL STEPS / UNKNOWNS FOR MANJIT" in PROGRESS.md.
- **Honest AI identity** (see section 8.6).

---

## 1. Product scope

### End-user features
- Chat with streaming, markdown, code blocks, edit/regenerate/branch, stop, copy, share.
- A visible **route badge** on every answer: `On-device`, `PocketMind Cloud` (our self-hosted
  model) or `Partner model` (free external provider), plus latency and a "why this route" tip.
- **Privacy mode** switch: forces on-device only; nothing leaves the phone.
- **Knowledge (RAG):** upload PDF/DOCX/TXT/URL into personal or team collections; answers cite
  sources with tappable chips (document + page). On-device RAG still works offline.
- **Tools:** device tools (calendar, reminders, alarms, contacts lookup, clipboard, open link,
  share) and cloud tools (calculator, unit/time conversion, weather via a free no-key API,
  notes/tasks) — every tool call shown as a card; side-effect tools need user confirmation.
- **MCP:** connected MCP servers listed in a Tools screen; admin can enable/disable per role.
- Accounts: sign in with Google, GitHub, email magic link, and Sign in with Apple (iOS);
  guest mode = on-device only.
- Teams/orgs: shared knowledge collections, roles.
- Admin console (in-app, admin role only): users & roles, usage, guardrail events, router
  stats, eval scores, feature flags.
- Everything from v2 that still makes sense (voice, personas, prompt library, widgets on
  Android, export/delete my data, app lock, localization EN/HI/PA, accessibility).

### Platforms
- Android (min SDK 26) and iOS (iOS 16+, verify the minimum Compose Multiplatform supports).
- One shared codebase with **Kotlin Multiplatform + Compose Multiplatform** for UI and logic.

---

## 2. Repository layout (monorepo)

```
pocketmind/
├── CLAUDE.md  PROGRESS.md  README.md  AUDIT_V3.md  V3_PLAN.md
├── mobile/                         # Kotlin Multiplatform
│   ├── composeApp/                 # Shared Compose Multiplatform UI (all screens)
│   ├── shared/
│   │   ├── core/                   # models, result types, dispatchers, utils
│   │   ├── designsystem/           # V3 design system (tokens, components)
│   │   ├── data/                   # repositories, SQL/Room-KMP DB, DataStore, Ktor client
│   │   ├── ai/                     # AiRouterClient, OnDeviceEngine (expect/actual), local RAG
│   │   ├── tools/                  # DeviceTool interface + expect/actual implementations
│   │   └── auth/                   # OAuth PKCE flow, token storage (Keystore / Keychain)
│   ├── androidApp/                 # Android entry, LiteRT-LM Kotlin engine, widgets
│   └── iosApp/                     # Xcode project, Swift host, LiteRT-LM Swift engine bridge
├── backend/                        # PocketMind Brain (Python 3.12, FastAPI)
│   ├── app/
│   │   ├── api/                    # REST + SSE endpoints (versioned /v1)
│   │   ├── llm/                    # provider adapters + unified interface
│   │   ├── router/                 # routing policy, fallbacks, circuit breakers, quotas
│   │   ├── cache/                  # exact, semantic, embedding, retrieval caches
│   │   ├── rag/                    # ingestion, chunking, embeddings, hybrid search, rerank
│   │   ├── tools/                  # tool registry, cloud tools, device-tool protocol
│   │   ├── mcp/                    # MCP client manager + PocketMind MCP server
│   │   ├── auth/                   # JWT verification, RBAC policies, org membership
│   │   ├── guardrails/             # input/output/tool guardrails pipeline
│   │   ├── observability/          # OpenTelemetry, Langfuse, metrics, logging
│   │   └── core/                   # config, settings, DB, errors, rate limiting
│   ├── evals/                      # datasets, runners, judges, reports, CI gate
│   ├── migrations/                 # SQL migrations (incl. RLS policies, pgvector)
│   ├── tests/
│   └── pyproject.toml
├── infra/
│   ├── docker-compose.yml          # api, llama.cpp server, embed/rerank, redis, caddy, monitoring
│   ├── caddy/  prometheus/  grafana/
│   └── scripts/                    # deploy, backup, keep-alive, model download
├── docs/
│   ├── architecture.md  api.md  rbac.md  guardrails.md  router.md  evals.md  runbook.md
│   └── learn/                      # teaching docs (see section 11)
└── .github/workflows/              # mobile-ci, backend-ci, evals, ios-build, release
```

---

## 3. Architecture

```
 ┌───────────────────────── PocketMind app (Android + iOS, Compose Multiplatform) ────────────────────────┐
 │  UI (V3 design system) → ViewModels → Repositories                                                       │
 │  ├── AiRouterClient ── decides: on-device? (privacy mode / offline / simple) else → Brain API (SSE)     │
 │  ├── OnDeviceEngine (expect/actual): Android = LiteRT-LM Kotlin, iOS = LiteRT-LM Swift via bridge       │
 │  ├── Local RAG (on-device embeddings + vector search)    ├── DeviceTools (calendar, reminders, …)        │
 │  └── Auth (OAuth PKCE, tokens in Keystore/Keychain)      └── Local DB + DataStore                       │
 └───────────────────────────────────────────────┬──────────────────────────────────────────────────────────┘
                                                 │ HTTPS (JWT)  /v1/chat (SSE), /v1/rag, /v1/tools, /v1/admin
 ┌───────────────────────────────────── PocketMind Brain (FastAPI) ────────────────────────────────────────┐
 │ Auth+RBAC → Rate limit/quota → Input guardrails → Cache lookup → Router → [RAG] → LLM call (+tools/MCP)  │
 │   → Output guardrails → Cache store → Stream to app          (every step traced: OTel + Langfuse)       │
 │ Providers: self-hosted llama.cpp (our model) | free external providers (OpenAI-compatible) as fallback  │
 │ Data: Supabase Postgres (+pgvector, RLS) | Redis | Langfuse | Prometheus/Grafana                         │
 └──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Request lifecycle for `/v1/chat` (must be implemented in this order, each step a traced span):
1. Verify JWT, load user + roles + org → 2. RBAC check (model, tools, collections) →
3. Per-user/role rate limit + daily quota → 4. Input guardrails → 5. Exact cache → semantic cache →
6. Router picks route + fallback chain → 7. RAG retrieval (if enabled) → 8. LLM call with tools
(server tools / MCP tools run server-side; device tools are returned to the app to execute and
the result is posted back) → 9. Output guardrails → 10. Cache store → 11. SSE stream with
events: `route`, `token`, `tool_call`, `tool_result`, `citation`, `guardrail`, `done`, `error`.

---

## 4. Zero-cost deployment target

| Need | Free choice | Notes |
|---|---|---|
| Server | Oracle Cloud Always Free Ampere A1 VM | Arm; allowance was cut to about 2 OCPU / 12 GB in 2026 — design for ≤ 12 GB RAM total. Verify current limits in the OCI console. |
| Our own model | `llama.cpp` server (OpenAI-compatible API) with a small open model in GGUF Q4 (1–4B params, e.g. Gemma / Qwen family; verify license) | CPU-only, so slow-ish: route heavy jobs elsewhere. |
| Embeddings + rerank | Small open models on the same VM (CPU) | Keep total RAM in budget. |
| DB + vectors + auth | Supabase Free (Postgres, pgvector, Auth with OAuth providers) | Free projects pause after ~7 days idle → add a keep-alive job. 500 MB DB limit → keep chunks lean. |
| Cache | Redis in docker on the VM | |
| External LLM fallback | Free tiers of OpenAI-compatible providers (e.g. Groq, Gemini API free tier) | Keys in env vars; optional; app must work if they're absent. Note in docs: some free tiers may use data to improve their products → never send privacy-mode or sensitive requests there. |
| LLM tracing | Langfuse Cloud Hobby (free) | Or self-host later if RAM allows. |
| Metrics | Prometheus + Grafana in docker (lightweight) | |
| HTTPS | Caddy + free DuckDNS subdomain (automatic Let's Encrypt) | |
| CI | GitHub Actions (public repo) | Includes a macOS job for iOS build/tests. |

Total RAM budget on the VM must be documented in `docs/runbook.md` with measured numbers.

---

## 5. Mobile app (KMP + Compose Multiplatform)

- Kotlin 2.x, Compose Multiplatform (iOS target is stable since 1.8.0 — use the current stable),
  Ktor client (SSE streaming), kotlinx.serialization, Koin (DI that works in KMP), Room KMP or
  SQLDelight (pick one after checking current KMP support; justify in docs), DataStore KMP,
  Coil 3 (KMP), Navigation Compose multiplatform, Kermit logging.
- **On-device engine** behind `expect/actual` or an injected interface:
  - Android: LiteRT-LM Kotlin (`com.google.ai.edge.litertlm:litertlm-android`).
  - iOS: LiteRT-LM Swift package (`https://github.com/google-ai-edge/LiteRT-LM`, product
    `LiteRTLM`). It is an **early preview** — wrap it in a Swift class implementing a Kotlin
    interface exported from `shared`, and handle "not available" gracefully.
- **Device tools** via `expect/actual`: Android (CalendarContract, AlarmClock intents, ContactsContract
  read, Clipboard, Intents) and iOS (EventKit, Contacts, UIPasteboard, UIApplication.open).
  Ask runtime permission just-in-time; never run a side-effect tool without user confirmation.
- Tokens: Android Keystore-backed encrypted storage; iOS Keychain.
- Migrate v2 local data with a tested migration; keep v2 behaviour for guests/offline.

### V3 GUI — complete redesign
- New design system in `shared/designsystem`: color tokens (light/dark + dynamic color on
  Android), typography scale, spacing (4/8 grid), radius, elevation, motion specs, icons.
- Platform feel: shared Compose UI, but respect iOS conventions (back-swipe, sheet styles,
  haptics, safe areas) and Android conventions (predictive back, edge-to-edge).
- Screens: Onboarding (3 steps), Sign in, Home (recent chats + quick actions + privacy toggle),
  Chat (streaming, route badge, tool cards, citation chips, guardrail notices), Knowledge
  (collections, upload, indexing progress), Tools & MCP, Models (on-device downloads + cloud
  status), Settings, Profile/Org, Admin console (role-gated), Benchmarks.
- Every screen: loading / empty / error / content / offline states; TalkBack + VoiceOver labels;
  font scaling to 200%; tablets and foldables (adaptive layouts).
- Screenshot tests for every screen (light/dark, phone/tablet, large font).

---

## 6. Backend (PocketMind Brain)

Python 3.12, FastAPI, Pydantic v2, SQLAlchemy 2 (async) or asyncpg, httpx, Redis client,
OpenTelemetry SDK, Langfuse SDK, official MCP Python SDK, pytest. Fully typed (mypy strict),
ruff for lint/format. API versioned under `/v1`, documented with OpenAPI.

### 6.1 LLM API layer
- One `LLMProvider` interface: `chat(messages, tools, params) -> AsyncIterator[Event]`,
  `embed(texts)`, `health()`, `capabilities()`.
- Adapters: `LlamaCppProvider` (our self-hosted model, OpenAI-compatible endpoint),
  `OpenAICompatibleProvider` (generic, for free external providers), `MockProvider` (tests).
- Timeouts, retries with jittered backoff, streaming, token counting, cost = 0 but still track
  tokens.
- Prompt templates versioned in files (`app/llm/prompts/`), never inline strings.

### 6.2 System / device tool calls
- Tool registry with JSON-Schema arguments, `side_effect: bool`, `location: server|device|mcp`,
  and required permission.
- Server tools: calculator (safe evaluator, no `eval`), unit/time conversion, weather (free
  no-key API, verify), notes/tasks (DB-backed, per user).
- Device tools: the backend emits a `tool_call` SSE event with `location=device`; the app shows
  a confirmation card, executes it locally, and POSTs the result to `/v1/chat/{id}/tool_result`.
- Max tool-call depth per turn (config), argument validation, results size limits.

### 6.3 RAG
- Ingestion: PDF/DOCX/TXT/HTML/URL → clean text → structure-aware chunking (headings, pages)
  with overlap → embeddings → store in pgvector with metadata (doc, page, collection, org, ACL).
- Retrieval: **hybrid** (Postgres full-text BM25-style + vector), merged with Reciprocal Rank
  Fusion → cross-encoder rerank → top-k → context packing within token budget.
- Answers must cite `[doc, p. N]`; "I don't know" when not grounded.
- Collections: personal, org-shared; access enforced by RBAC **and** Postgres Row-Level Security.
- Background ingestion jobs with progress; dedupe by content hash; delete = hard delete vectors.

### 6.4 MCP tools
- `MCPManager`: connects to configured MCP servers (stdio and streamable HTTP), discovers tools,
  namespaces them (`server.tool`), applies RBAC + guardrails before every call, timeouts.
- `pocketmind-mcp`: our own MCP server exposing notes, tasks and knowledge-search tools, so
  PocketMind's knowledge can also be used from other MCP clients.
- Admin can enable/disable servers and tools per role.

### 6.5 RBAC + OAuth
- Identity: Supabase Auth (OAuth/OIDC: Google, GitHub, Apple; email magic link). Mobile uses
  Authorization Code + PKCE with system browser / ASWebAuthenticationSession.
- iOS: if any third-party social login is offered, also offer **Sign in with Apple** (App Store
  rule) — needs the owner's Apple Developer account; list as manual step.
- Backend verifies JWTs (JWKS, issuer, audience, expiry), maps user → roles.
- Roles: `guest` (on-device only), `user`, `pro`, `org_member`, `org_admin`, `admin`.
- Permissions matrix in `docs/rbac.md` and code (single source): allowed routes/models, tools,
  MCP servers, collections, rate limits, daily quotas, admin endpoints.
- Enforce at API layer (dependency) + DB layer (RLS). Audit log of admin and permission changes.
- Refresh tokens rotation, logout everywhere, account deletion (GDPR/DPDP-style "delete my data").

### 6.6 Guardrails
Pipeline of small, testable checks with a shared `GuardrailResult(action=allow|redact|block|warn)`:
- **Input:** size limits, PII detection + redaction before sending to any *external* provider,
  prompt-injection / jailbreak detection (heuristics + classifier), disallowed-content check
  with a safety classifier model (open model; verify which runs within RAM or on a free tier).
- **RAG:** strip instructions found inside retrieved documents (indirect prompt injection),
  groundedness check on answers.
- **Tools:** allowlist per role, JSON-Schema validation, confirmation for side effects, no tool
  can read secrets.
- **Output:** safety check, PII leak check, system-prompt leak check, **identity check**
  (section 8.6), JSON/format validation.
- On-device lightweight guardrails for offline mode.
- Every guardrail event logged (without storing raw sensitive text) and shown in admin console.

### 6.7 Caches + router
- Caches: exact-match (normalized prompt + params + user scope) in Redis; **semantic cache**
  (embedding similarity ≥ threshold, scoped per user/org, never across users); embedding cache;
  retrieval cache. TTLs and invalidation on document changes. Never cache privacy-mode or
  tool-side-effect turns.
- Router (explainable, configurable policy):
  - Inputs: privacy mode, online status, role/quota, task type (classifier: chat / reasoning /
    code / RAG / tools / long-context), prompt length, provider health, recent latency.
  - Routes: `on_device` (decided in the app), `self_hosted` (default cloud), `external_fast`,
    `external_long_context`.
  - Fallback chains, circuit breaker per provider, 429 handling, per-provider concurrency limits.
  - Emits a `route` event with the reason. Router decisions are logged and evaluated.

### 6.8 Observability + evals
- OpenTelemetry traces for every request step; Langfuse traces for LLM calls (prompt version,
  route, tokens, latency, cache hit, guardrail results, user feedback score).
- Prometheus metrics: latency p50/p95 per route, time-to-first-token, cache hit rate, router
  distribution, guardrail blocks, error rates, provider 429s, queue depth. Grafana dashboard JSON
  committed in `infra/grafana/`.
- Structured JSON logs with request IDs; no raw user content in logs by default.
- Mobile: opt-in crash reporting (free tier) + client-side timing for TTFT.
- **Evals** (`backend/evals/`):
  - Datasets (versioned JSONL): general chat, RAG Q&A with gold citations, tool-calling tasks,
    router labels, red-team prompts (injection, jailbreak, PII), identity questions.
  - Metrics: answer quality (LLM-as-judge with a rubric), RAG faithfulness / context precision /
    recall, citation accuracy, tool-call accuracy, router accuracy, guardrail precision/recall,
    identity-correctness, latency.
  - `make eval` produces `evals/reports/<date>.md`; CI runs a fast subset on every PR and
    **fails if any metric drops below the threshold** in `evals/thresholds.yaml`.
  - Online: thumbs up/down in the app → Langfuse scores → weekly report.

---

## 7. Security baseline
- Secrets only in env vars / GitHub secrets; `.env.example` committed, `.env` never.
- HTTPS only; CORS locked; security headers; request size limits; per-IP and per-user rate limits.
- Dependency scanning (Dependabot / pip-audit), container runs as non-root.
- Backups: nightly `pg_dump` of app tables to the VM's block storage, documented restore test.

---

## 8. Rules for writing code

8.1 Production quality: no TODOs, no placeholders, no fake data in release code; complete files
with paths. 8.2 Tests for every module (backend ≥ 80% coverage; shared KMP logic ≥ 80%).
8.3 Small conventional commits. 8.4 Docs updated with every change. 8.5 Teaching KDoc /
docstrings explaining *why* on every class and tricky line.

### 8.6 AI identity (honest)
The assistant's system prompt and identity guardrail must make it answer questions like
"who built you / which company made you / are you Gemini/ChatGPT" with:
> "I'm PocketMind, an AI assistant built by Manjit Singh. I run on open-source and partner AI
> models — for example Google's open Gemma models on your phone — orchestrated by PocketMind's
> own backend for routing, knowledge search, tools and safety."

Never claim a model was trained from scratch by PocketMind, and never claim to be Google,
OpenAI or any other company's product. Add these questions to the identity eval set.
(Optional later phase: a LoRA fine-tune of a small open model on free GPU notebooks to bake in
PocketMind's identity and style, served by llama.cpp — only if it fits the zero-cost rule.)

---

## 9. Process

### Stage 1 — Audit (no code changes)
Read the whole v2 codebase. Write `AUDIT_V3.md`: what can be reused in KMP, what must be
rewritten, Android-only APIs that need `expect/actual`, data migration risks, and gaps against
this spec.

### Stage 2 — Plan, then STOP
Write `V3_PLAN.md`: small phases, each with goal, files, risks, tests, "done when", and the
manual steps for Manjit. Suggested order:
1. Monorepo + KMP skeleton, design system, CI (Android + iOS build on macOS runner)
2. Port v2 features to KMP (Android parity), on-device engine Android, then iOS bridge
3. V3 GUI: all screens with the new design system + screenshot tests
4. Backend skeleton: FastAPI, config, DB migrations, health, OTel, Docker, CI
5. LLM layer + SSE streaming + app integration (AiRouterClient)
6. Auth (Supabase OAuth + PKCE in app) + RBAC + RLS
7. Router + caches
8. Guardrails (backend + on-device)
9. RAG (backend hybrid search + app Knowledge screens; keep on-device RAG)
10. Tools: server tools + device tools protocol + confirmation UI
11. MCP manager + pocketmind-mcp server + Tools & MCP screen
12. Observability: Langfuse, Prometheus, Grafana dashboards, mobile timing
13. Evals: datasets, judges, reports, CI gate; identity evals
14. Admin console, org/team features
15. Infra: docker-compose for the Oracle VM, Caddy HTTPS, keep-alive, backups, runbook
16. Hardening: security review, load test (k6 or Locust) within free limits, accessibility,
    localization, final review, release builds (Android AAB/APK, iOS archive instructions)

Then **STOP and wait for "approved"**.

### Stage 3 — Build (after approval, run all phases without stopping)
For each phase: implement → tests → run all checks
(`./gradlew check` for mobile incl. iOS-compatible shared tests where possible;
`ruff check && mypy && pytest` for backend; `make eval-fast` once evals exist) → fix until
green → commit → update PROGRESS.md → write the lesson (section 11) → next phase.
- Work on branch `v3`. Tag the v2 state as `v2-final` before starting.
- Things only Manjit can do: don't stop; list them under "MANUAL STEPS / UNKNOWNS FOR MANJIT".
- Same error 3 times: record under BLOCKERS, choose the safest working alternative, continue.
- If the session restarts: read CLAUDE.md, V3_PLAN.md, PROGRESS.md and continue.

### Final
Full checks, release builds, re-audit → `AUDIT_V3_AFTER.md` with before/after scorecard, final
README (architecture diagram, features, screenshots, benchmarks with **measured** numbers,
zero-cost deployment guide), and a final report with resume bullets using only measured numbers.

---

## 10. Things Manjit must do manually (remind at the right phase)
- Mac with Xcode for running the iOS app on a simulator/iPhone (CI can build without it).
- Apple Developer Program membership (paid, yearly) is needed for App Store release and Sign
  in with Apple in production. Without it: test on own iPhone via Xcode with a free Apple ID.
- Oracle Cloud account + Always Free VM, DuckDNS subdomain, SSH keys.
- Supabase project, OAuth apps (Google, GitHub, Apple) and their client IDs/secrets.
- Optional free API keys for external providers; Langfuse Cloud Hobby keys.
- Real-device testing on Android and iPhone; measured benchmarks.

---

## 11. Teaching docs (Manjit is learning while building)
For every phase write `docs/learn/v3-phase-N.md`: what we built; big picture with ASCII diagram;
concepts (what, analogy, why, alternatives); code walkthrough following a real request;
design decisions and trade-offs; common mistakes; 2–3 "try it yourself" exercises; 8–10
interview questions with model answers; 5 self-check questions.
Also: `docs/learn/v3-system-design.md` — the full platform explained as a system-design
interview answer (requirements, API, data model, scaling, failure modes, trade-offs), and
`docs/learn/v3-interview-pitch.md` — 2-minute and 30-second pitches and 25 likely questions.
