# PocketMind v2 — Audit after (2026-11-03)

The same ten areas as [AUDIT.md](AUDIT.md) (v1, 2026-11-02), re-scored against the code on branch `v2` after phases
0–22. Rule used: **a score only rises on evidence.** Anything that has not been run on a phone yet is marked "to
verify on device", and that caps the score.

## 1. Scorecard: v1 → v2

| Area | v1 | v2 | Evidence |
|---|:-:|:-:|---|
| Architecture | 8 | **9** | 25 modules (new: `core:speech`, `core:tools`, `core:billing`, `feature:personas`), dependency rules still enforced. The lock is an overlay (H-2). `ChatRepository` is split by concern (`ChatPhotos`, `ChatModel`). The DI graph has small, replaceable modules (`DatabaseFileModule`, `ModelSelectionModule`). Only `core:llm` imports LiteRT-LM; only `core:billing` imports Play Billing. |
| Code quality | 8 | **9** | 206 main Kotlin files / 15,433 lines. Spotless, Detekt and Lint (warnings as errors) are green on every phase, and design tokens are enforced (`checkDesignTokens`). Remaining: some long composables (`ChatScreen.kt`, `SettingsScreen.kt`). |
| Test coverage | 5 | **8** | **392 JVM tests**, 0 failures (82 test files, 6,833 lines). **84.2 %** line coverage of data, domain and ViewModel classes, enforced by `koverVerifyApp` (gate 84 %). 72 Roborazzi goldens (light, dark, tablet, 200 % font). 3 end-to-end tests on the real app. The engine, worker and repository from H-5 are now tested. Caps: the 3 instrumented test classes (engine, encryption, PDF export) are written but not yet run on a phone, and the goldens need re-recording after phases 16–21. v1's 44.1 % measured *all* hand-written code; v2's 84.2 % is the gated logic metric, so the two are not the same measure. |
| Performance | 7 | **7** | Unchanged until measured. v1's on-phone numbers stand (26.3 tok/s, 0.39 s first token, 5.5 s warm load). v2 removed 4.0 MB of dead BouncyCastle data (M-1), stopped the reload on every app switch (H-4) and made long chats cheaper (context summaries). It also added SQLCipher (2.01 MB) and features: release APK 30.59 MB, AAB 23.40 MB. Not yet done: Baseline Profile generation, Macrobenchmark runs and the 8k-context memory test (all need the phone). |
| Reliability | 6 | **8** | Crash-loop guard around native loading (C-1), exact size check and on-demand SHA-256 "Check file" (H-1), lock without losing work (H-2, H-3), a keep-alive policy (H-4), and an explanation when Android stops a long download (M-12). The plain → encrypted database migration is verified row by row and survives interruptions. KSP build flakiness was found and fixed at its root (serialized KSP). The E2E tests found and fixed a first-run bug ("No model yet" after the first download). Cap: the real-phone checks are still to do. |
| Security & privacy | 7 | **9** | Chats, notes and the PDF index are encrypted with SQLCipher 4.20 (AES-256); the key is random and wrapped by an Android Keystore AES-GCM key in `noBackupFilesDir` (M-6). Export all data (ZIP) and delete all data. Voice uses the on-device recognizer only; read-aloud uses offline voices only. A local crash log replaces any SDK. `docs/data-safety.md` maps the Play form. The privacy policy covers photos, tools, voice, Play features, Pro and diagnostics. |
| Accessibility | 6 | **7** | No live region while streaming, one "Answer ready" announcement (M-3); headings, roles and states; decorative icons audited; 48 dp targets; screenshots at 200 % font. Cap: the TalkBack walkthrough in `docs/accessibility-checklist.md` hasn't been done on a phone. |
| Localization | 6 | **7** | English, Hindi and **Punjabi (Gurmukhi)** for every user-facing string (324 strings per language, keys verified identical). `localeConfig` plus an in-app picker on Android 13+ (L-4). Cap: no native-speaker review yet for Hindi or Punjabi (M-11). |
| UX / visual design | 5 | **8** | Design tokens, illustrations, motion and haptics; adaptive layouts (navigation rail, list-detail on 840 dp+), predictive back. Typing is possible while the model loads (M-4). New: edit and resend, versions and branches, pins, folders, highlighted search, export, share-in, widget, quick-settings tile, personas, prompt library, voice, photos, tools, Pro. Cap: v2's screens haven't been seen on a real phone yet. |
| Play Store readiness | 4 | **7** | versionCode and versionName from the git tag (H-6); a release workflow plus `play.yml` → internal track with generated release notes; In-App Update and Review; Play Billing; the data-safety mapping; a release guide with staged rollout, halt and rollback. Missing (owner steps): Play Console product, first upload, service-account secret, store listing assets, Baseline Profile, and signed builds from the release keystore. |

**Total: 62 → 79 of 100.**

## 2. What happened to every v1 finding

| ID | v1 finding | Status | Where |
|---|---|---|---|
| C-1 | Native crash while loading crash-loops the app | **Fixed** (to verify on device) | phase 1: `LoadGuard`, `ExitReasonSource`, `CRASHED_LAST_TIME` |
| H-1 | No model integrity check at load | **Fixed** | phase 1: exact size check before every load; "Check file" (SHA-256) |
| H-2 | App lock throws away navigation and running work | **Fixed** | phase 2: lock overlay above the still-composed app |
| H-3 | Picking a PDF re-locks the app and loses the file | **Fixed** (to verify on device) | phase 2: `LockTimer` grace for our own pickers |
| H-4 | Model unloaded on every app switch (API 34+) | **Fixed** (to verify on device) | phase 3: `ModelKeepAlive`, `MemoryPressure` |
| H-5 | Engine, worker, download repository untested | **Fixed** for worker and repository; engine device test **written, to run** | phase 4 |
| H-6 | versionCode hard-coded | **Fixed** | phase 5: `AppVersion.fromTag`, `GitTagValueSource` |
| M-1 | 4.0 MB of unused BouncyCastle data | **Fixed** | phase 5 (APK 31.2 → 27.27 MB then) |
| M-2 | Baseline Profile never generated | **Open** (needs the phone) | `./gradlew :app:generateBaselineProfile` |
| M-3 | Streaming bubble is a chattering live region | **Fixed** | "PocketMind is writing…" / "Answer ready" |
| M-4 | Can't type while the model loads | **Fixed** | `ChatInput` stays usable; the send waits for the load |
| M-5 | Context fixed at 4,096 tokens | **Open by decision** | phase 7: summaries of long chats; 8k waits for a memory measurement |
| M-6 | Database not encrypted at app level | **Fixed** (migration to verify on device) | phase 18: SQLCipher + Keystore |
| M-7 | No PDF size limits | **Fixed** | phase 20: 50 pages free, any length with Pro |
| M-8 | Search uses `LIKE '%q%'` | **Fixed** | phase 11: FTS4 (`unicode61`), escaped LIKE for titles |
| M-9 | No local crash record | **Fixed** | phase 20: `CrashLog` + "Share diagnostic report" |
| M-10 | No StrictMode or LeakCanary | **Fixed** | phase 6 (debug only) |
| M-11 | Hindi not reviewed by a native speaker | **Open** (owner) | plus Punjabi, added in phase 21 |
| M-12 | `dataSync` download time limit | **Fixed** | phase 3: timeout explained, resumes from `.part` |
| M-13 | No screenshot, UI or E2E tests | **Fixed** | phases 10 and 22 |
| M-14 | No Dependabot, coverage or configuration cache | **Fixed** | phase 0 (+ PR coverage comment in phase 22) |
| M-15 | No Data safety mapping | **Fixed** | phase 18: `docs/data-safety.md` |
| L-1 | Failed first send leaves an empty chat | **Fixed** | `ChatViewModel.discardIfEmpty` |
| L-2 | "Retry" shown where it can't help | **Fixed** | snackbar action depends on the error |
| L-3 | `enableBenchmark` always on | **Open** (cost not measured) | measure on the phone, then decide |
| L-4 | No per-app language picker | **Fixed** | phase 21 |
| L-5 | Predictive back not enabled | **Fixed** | phase 9 |
| L-6 | Duplicate Markdown helper | **Fixed** | `MarkdownText` in `core:designsystem` |
| L-7 | No "scroll to latest" button | **Fixed** | phase 12 |

Of the 29 findings, 24 are fixed (4 of those still need a phone check: C-1, H-3, H-4, M-6), 4 are open and need the owner or the phone
(M-2, M-11, L-3, and the engine device run in H-5), and 1 is open by decision (M-5).

## 3. Compared with the best offline-AI apps on Android (now)

| Capability | PocketMind v1 | PocketMind v2 | PocketPal AI | AI Edge Gallery | MLC Chat |
|---|---|---|---|---|---|
| Fully offline, no account | ✔ | ✔ | ✔ | ✔ | ✔ |
| GPU acceleration | ✔ | ✔ (auto-fallback, crash guard) | ✔ (also NPU) | ✔ | ✔ |
| Image questions | ✘ | **✔** | — | ✔ | — |
| Voice in / out | ✘ | **✔ (on-device only)** | ✔ | ✔ | — |
| Personas | ✘ | **✔** | ✔ | Prompt Lab | — |
| Edit, versions, branches | ✘ | **✔** | ✔ (edit) | — | — |
| Tool calling | ✘ | **✔ (calculator, date, units, notes)** | ✔ | ✔ | — |
| PDF summarize + RAG with page citations | ✔ | ✔ | — | — | — |
| Encrypted chats + export/delete all | ✘ | **✔** | — | — | — |
| Tablet / foldable layout | ✘ | **✔** | ✔ (iPad) | — | — |
| Localization | en, hi | **en, hi, pa** | en, ja, zh | en | en |

The competitor columns are carried over from AUDIT.md (not re-checked in this audit).

## 4. The most important open items

1. **Run everything that needs the phone** (PROGRESS.md → MANUAL STEPS): the encryption migration over the real chats
   first, then the engine, vision and tool device tests, the benchmarks and the Baseline Profile.
2. Native-speaker review of Hindi and Punjabi; the TalkBack walkthrough.
3. Play Console: the `pocketmind_pro` product and price, the first upload, the service account, listing assets.
4. Re-record the screenshot goldens on Linux (phases 16–21 changed screens).
