# PocketMind v1 — Audit

Date: 2026-11-02 · Commit audited: `9589ca3` (branch `main`) · Auditor roles: principal Android engineer,
senior product designer, Play Store release manager.

Every number below was **measured** on this codebase or on the real test phone (iQOO Neo 10R, Snapdragon 8s Gen 3,
8 GB, Android 16) unless it is marked *not measured*. No code was changed for this audit. Coverage was measured with
Kover 0.9.11 applied through a temporary Gradle init script outside the repository.

---

## 1. Scorecard

| Area | Score | Evidence (short) |
|---|:-:|---|
| Architecture | **8** | 21 modules, dependency rules enforced by the root build; LiteRT-LM isolated behind `LlmEngine`; Room as source of truth. Weak spots: app lock is wired at the wrong level (§2 H-3), no crash-loop protection around native code. |
| Code quality | **8** | 136 main Kotlin files / 8,195 lines; Spotless + ktlint, Detekt and Lint (warnings as errors) all green; KDoc on every class. Some composables are over 200 lines (`SettingsScreen.kt` 262, `HistoryScreen.kt` 223). |
| Test coverage | **5** | 139 JVM tests (28 test files, 2,249 lines). **Kover: 44.1 %** of hand-written lines. Logic is well covered (repositories and ViewModels 79–100 %), but `LiteRtLmEngine`, `ModelDownloadWorker` and `WorkManagerModelRepository` are at 0 %, screens at 0–25 %, and there are no UI, screenshot or instrumented tests. |
| Performance | **7** | Measured: 26.3 tok/s decode, 0.39 s time to first token, 5.5 s warm model load (13.3 s first ever), peak PSS 960 MB, GPU. Release APK 31.2 MB, of which 4.0 MB is unused BouncyCastle data. App startup and jank *not measured*; the Baseline Profile was never generated, so it isn't in the APK. |
| Reliability | **6** | Strong: engine `Mutex`, native `cancelProcess()`, resumable download with streaming SHA-256 and atomic rename, AutoMigrations tested. Weak: a native crash during model load crash-loops the app, no model integrity check at load time, app lock throws away navigation and running work, model unloaded on every trip to the background (API 34+), no PDF size limits. |
| Security & privacy | **7** | No analytics or accounts; HTTPS only; system CAs only; OkHttp host allowlist; backups and device transfer fully excluded; logs only in debug builds. Weak: the database is not encrypted at app level; the lock is a UI-only `BIOMETRIC_WEAK` gate; there is no "export my data" or "delete my data". |
| Accessibility | **6** | Content descriptions on actions, 48 dp `IconButton`s, polite live regions. Weak: the streaming bubble is a live region that changes every 50 ms (TalkBack chatter), no TalkBack walkthrough recorded, no testing at 200 % font size, no screenshot tests at large font. |
| Localization | **6** | English + Hindi complete (188/188 strings; only the brand name is untranslated), `supportsRtl`. Weak: the Hindi was written by the AI and never reviewed by a native speaker; no per-app language setting (`localeConfig`); no Punjabi. |
| UX / visual design | **5** | Material 3, dynamic color, dark mode, every screen has loading, empty and error states. But it looks stock: no brand tokens, illustrations, motion or haptics; no tablet or foldable layout; the input is disabled while the model loads; no edit/branch/pin; no "scroll to latest" button. |
| Play Store readiness | **4** | Privacy policy, OSS licences screen, env-var signing, GitHub release workflow. Missing: versionCode hard-coded to 1, no Play upload, no Data safety mapping, no store listing assets, Baseline Profile not generated, no in-app update/review, 4 MB of dead weight in the APK. |

**Overall: 6.2 / 10.** It's a solid engineering base, not yet a polished store product.

---

## 2. Problems found, ranked

Format: **ID — title** · file:line · why it matters · fix.

### Critical

**C-1 — A native crash while loading the model crash-loops the app.**
[LiteRtLmEngine.kt:104-115](core/llm/src/main/kotlin/com/sanjay/pocketmind/core/llm/LiteRtLmEngine.kt#L104-L115),
[ChatViewModel.kt:104-108](feature/chat/src/main/kotlin/com/sanjay/pocketmind/feature/chat/ChatViewModel.kt#L104-L108).
GPU fallback only catches *Java exceptions*. A buggy GPU driver or a damaged model file can kill the process
in native code (SIGSEGV), which no `try/catch` can catch. Chat is the start screen and loads the model on open, so the
app would crash on every launch until the user clears data. This is the most common 1-star failure mode for on-device-AI
apps.
*Fix:* write a "load in progress (model, backend)" marker before `initialize()` and clear it afterwards. On the next start,
if the marker is still there, skip that backend (GPU → CPU), don't auto-load, and show "Last load crashed — try CPU /
re-verify the model".

### High

**H-1 — No model integrity check at load time.**
[LiteRtLmEngine.kt:97](core/llm/src/main/kotlin/com/sanjay/pocketmind/core/llm/LiteRtLmEngine.kt#L97) only checks `canRead()`.
A truncated or changed file (storage error, a manual copy, a restore) goes straight into native code, which leads to C-1.
*Fix:* check the exact size against the catalog on every load (cheap); do a full SHA-256 check in the background after
install/update and on demand ("Verify model" in Models).

**H-2 — The app lock throws away the user's place and the running work.**
[MainActivity.kt:56-60](app/src/main/kotlin/com/sanjay/pocketmind/MainActivity.kt#L56-L60). When locked, the whole
`PocketMindAppUi` (including its `NavController` and every screen's ViewModel) leaves composition. Unlocking starts from
an empty navigation state: an answer being generated, a PDF summary or a translation is lost.
*Fix:* keep the app UI composed and draw the lock as an opaque overlay on top (hide the content from screenshots and
accessibility while locked).

**H-3 — With the lock on, picking a PDF re-locks the app and the picked file is lost.**
[MainActivity.kt:66-70](app/src/main/kotlin/com/sanjay/pocketmind/MainActivity.kt#L66-L70). The system file picker is
a separate activity, so `onStop` fires and locks the app. Combined with H-2, the activity-result launcher is recreated
and the result is dropped. *Confirmed by reading the code; not yet reproduced on the phone.*
*Fix:* lock after a short grace period (for example 30 s in the background) and not while one of our own pickers or
dialogs is open.

**H-4 — The model is unloaded every time the app goes to the background (Android 14+).**
[PocketMindApp.kt:70](app/src/main/kotlin/com/sanjay/pocketmind/PocketMindApp.kt#L70) unloads at
`TRIM_MEMORY_BACKGROUND`. On API 34+ that is sent for ordinary backgrounding, not real memory pressure. The cost is a
5.5–13 s reload every time the user switches apps, and the GPU warm-up is lost. *To be confirmed on the phone with
logcat.*
*Fix:* unload only on `RUNNING_CRITICAL`, or after N minutes in the background (configurable), and always when the
process is about to be killed.

**H-5 — Untested critical code: engine, download worker, download repository (0 % coverage).**
`LiteRtLmEngine` (60 lines), `ModelDownloadWorker` (54), `WorkManagerModelRepository` (61). These are the parts where
bugs cost users gigabytes or crashes.
*Fix:* `work-testing` for the worker and repository; an instrumented test on a real phone for the engine (load, generate,
cancel, unload, GPU→CPU fallback).

**H-6 — Release versioning can't ship updates.**
[app/build.gradle.kts:31-32](app/build.gradle.kts#L31-L32): `versionCode = 1`, `versionName = "1.0.0"` are hard-coded. Play
rejects a second upload with the same versionCode.
*Fix:* derive both from the git tag in CI (for example `v1.2.3` → versionCode `10203xx`).

### Medium

| ID | Problem | Where | Why it matters | Fix |
|---|---|---|---|---|
| M-1 | 4.0 MB of BouncyCastle post-quantum data in the APK (13 %) | PdfBox → bcpkix; release APK | Bigger download, slower install | `packaging.resources.excludes += "org/bouncycastle/pqc/**"` (encrypted-PDF support is unaffected) |
| M-2 | Baseline Profile never generated | `benchmark/` has the generator; no `baseline-prof.txt` in `app/src` | Slower first launch and scrolling until ART warms up | Run the generator on the phone, commit the profile |
| M-3 | Streaming bubble is a live region updated every 50 ms | [MessageList.kt:159](feature/chat/src/main/kotlin/com/sanjay/pocketmind/feature/chat/component/MessageList.kt#L159) | TalkBack interrupts itself constantly | Announce "Answer ready" once at the end; no live region while streaming |
| M-4 | Can't type while the model loads | [ChatScreen.kt:175](feature/chat/src/main/kotlin/com/sanjay/pocketmind/feature/chat/ChatScreen.kt#L175) | 5–13 s of a dead input box on every cold open | Allow typing; queue the send until Ready |
| M-5 | Context window fixed at 4,096 tokens for every model and phone | [LiteRtLmEngine.kt:280](core/llm/src/main/kotlin/com/sanjay/pocketmind/core/llm/LiteRtLmEngine.kt#L280) | Long chats get trimmed early on 8–12 GB phones | Choose from RAM and model (for example 4k / 8k), measured with the benchmark |
| M-6 | Chat database not encrypted at app level | `core/database` | It relies only on Android's file-based encryption; a rooted or forensic copy can read chats | SQLCipher with the key in Android Keystore (v2 plan G) |
| M-7 | No limits on PDF size or pages | [PdfTextExtractor.kt:55](data/documents/src/main/kotlin/com/sanjay/pocketmind/data/documents/PdfTextExtractor.kt#L55) | A 1,000-page PDF holds all text in memory and summarizes for a very long time | Page and size caps with a clear message (raise them for Pro) |
| M-8 | Message search uses `LIKE '%q%'` over every message, no index | [ChatDao.kt:43](core/database/src/main/kotlin/com/sanjay/pocketmind/core/database/dao/ChatDao.kt#L43) | Slows down with large histories; `%` and `_` typed by the user act as wildcards | Room FTS4 table for messages |
| M-9 | No local crash or ANR record | app | Without telemetry (by design) you can't learn why the app failed on a user's phone | Local crash log plus a user-initiated "Share diagnostic report" (no SDK, no network) |
| M-10 | No StrictMode or LeakCanary in debug | app | Disk or network on the main thread and leaks go unnoticed | Add both, debug only |
| M-11 | Hindi translation not reviewed by a native speaker | `values-hi/` | Awkward wording looks unprofessional | Native review; add Punjabi the same way |
| M-12 | Model download type `dataSync` | [ModelDownloadWorker.kt:109](data/models/src/main/kotlin/com/sanjay/pocketmind/data/models/work/ModelDownloadWorker.kt#L109) | Android 15+ limits `dataSync` foreground services to 6 h per 24 h; a very slow 2.6 GB download can be stopped (it resumes later from `.part`) | Handle `onStopped`/timeout with a "paused — will resume" message; consider the `USER_INITIATED` data transfer job on API 34+ |
| M-13 | No screenshot, UI or end-to-end tests | — | Visual regressions and broken flows reach users | Roborazzi + Compose UI tests (plan F) |
| M-14 | CI: no Dependabot, no coverage, no configuration cache | `.github/`, `gradle.properties` | Outdated dependencies, slower builds, coverage invisible | Plan F |
| M-15 | No Data safety mapping document | `docs/` | The Play Console form must exactly match the app's behaviour | `docs/data-safety.md` mapping every field |

### Low

| ID | Problem | Where | Fix |
|---|---|---|---|
| L-1 | Failed first send leaves an empty "untitled" chat in History | `ChatViewModel.send` creates the chat before sending | Create the chat on first successful send, or hide chats with no messages |
| L-2 | Snackbar "Retry" also shows for "no model" errors, where retry can't help | [ChatScreen.kt:64-68](feature/chat/src/main/kotlin/com/sanjay/pocketmind/feature/chat/ChatScreen.kt#L64-L68) | Action depends on the error ("Get a model") |
| L-3 | `ExperimentalFlags.enableBenchmark = true` always on | [LiteRtLmEngine.kt:88](core/llm/src/main/kotlin/com/sanjay/pocketmind/core/llm/LiteRtLmEngine.kt#L88) | Measure its cost; keep only if negligible |
| L-4 | No per-app language picker | manifest | `android:localeConfig` + an in-app language setting |
| L-5 | Predictive back not enabled | manifest | `android:enableOnBackInvokedCallback="true"` and test |
| L-6 | Same `StableMarkdown` helper in two feature modules | feature/chat, feature/documents | Move to `core:designsystem` (or a `core:ui` module) |
| L-7 | No "scroll to latest" button in long chats | `MessageList` | Floating button when not at the bottom |

---

## 3. Compared with the best offline-AI apps on Android

The apps most often recommended in 2026 are **PocketPal AI** (open source, llama.cpp, any GGUF model), **Google AI
Edge Gallery** (Google's LiteRT-LM showcase, Gemma 4) and **MLC Chat** (fast GPU inference). Sources: TokForge's
comparison of 9 offline AI apps, the PocketPal AI GitHub README, and Google AI Edge Gallery release notes (links below).
Feature lists come from public pages, not from my own testing.

| Capability | PocketMind v1 | PocketPal AI | AI Edge Gallery | MLC Chat |
|---|---|---|---|---|
| Fully offline, no account | ✔ | ✔ | ✔ | ✔ |
| GPU acceleration | ✔ (auto-fallback to CPU) | ✔ (also NPU) | ✔ | ✔ |
| Choose any model from Hugging Face | ✘ (curated catalog) | ✔ | ✔ (LiteRT-LM imports) | partial |
| Image questions | ✘ | — | ✔ "Ask Image" | — |
| Audio / voice | ✘ | ✔ | ✔ "Audio Scribe" | — |
| Custom assistants / personas | ✘ | ✔ "Pals" | Prompt Lab | — |
| Edit messages | ✘ | ✔ | — | — |
| Tool calling / agent skills | ✘ | ✔ | ✔ | — |
| PDF summarize + RAG with page citations | **✔ (unique)** | — | — | — |
| Translation screen | **✔** | — | — | — |
| Built-in benchmark | ✔ | ✔ (+ public leaderboard) | — | — |
| Verified downloads (SHA-256, resume) | **✔** | — | — | — |
| Tablet / iPad layout | ✘ | ✔ (iPad) | — | — |
| Localization | en, hi | en, ja, zh | en | en |

**What they do better:** multimodal input (image and audio), personas, message editing, tool use, model choice, NPU
support, tablet layouts, and a visible community (public benchmark leaderboard).

**Where PocketMind is already ahead:** grounded PDF Q&A with page citations, a translator, verified resumable downloads,
a strict no-network privacy model, and Indian-language support.

**Positioning for v2:** "the private document and productivity assistant", not just another chat wrapper. Close the
multimodal, persona and editing gaps, and keep the document features as the differentiator.

Sources:
- [9 Offline AI Apps for Android and iPhone, Compared (2026) — TokForge](https://tokforge.ai/guides/best-offline-ai-apps-android/)
- [PocketPal AI — GitHub](https://github.com/a-ghorbani/pocketpal-ai)
- [Google AI Edge Gallery 1.0.16 release notes — APKMirror](https://www.apkmirror.com/apk/research-at-google/google-ai-edge-gallery/google-ai-edge-gallery-1-0-16-release/)
- [Best Offline AI Apps for Android 2026 — MeetAITools](https://meetaitools.com/best-offline-ai-apps-for-android/)

---

## 4. Verified library facts used by the v2 plan

Checked by inspecting the resolved `litertlm-android-0.19.0` classes (javap), not documentation:

- **Images:** `Content.ImageBytes(byte[])`, `Content.ImageFile`, `EngineConfig.visionBackend`, `EngineConfig.maxNumImages`.
- **Audio:** `Content.AudioBytes`, `Content.AudioFile`, `EngineConfig.audioBackend`.
- **Tools:** `@Tool`, `@ToolParam`, `ToolSet`, `ToolProvider`, `ConversationConfig.tools`, `ConversationConfig.automaticToolCalling`.
- **Also present:** `Backend.NPU`, `Backend.GOOGLE_TENSOR`, `SpeculativeDecodingConfig`, `RepetitionPenaltyConfig`, `ResponseFormat` (structured output), `ModelInfo`/`SupportedModalities`.
- **Still unknown:** whether the Gemma 4 E2B `.litertlm` file we download includes the vision and audio encoders. This must be checked on the phone through `ModelInfo` before building image or audio features.

## 5. Not measured yet (will be measured in v2, not guessed)

App cold and warm startup time (release build), frame timing and jank while streaming, battery cost per 1,000 tokens,
memory with 8k context, and whether H-3 and H-4 reproduce on the device.
