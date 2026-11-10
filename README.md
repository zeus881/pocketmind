# PocketMind

**A private AI assistant that runs entirely on your Android phone: chat, ask about photos, summarize and question
PDFs with page citations, translate, and speak. No account. No cloud. Works in airplane mode.**

[![Mobile CI](https://github.com/zeus881/pocketmind/actions/workflows/mobile-ci.yml/badge.svg)](https://github.com/zeus881/pocketmind/actions/workflows/mobile-ci.yml)
[![iOS build](https://github.com/zeus881/pocketmind/actions/workflows/ios-build.yml/badge.svg)](https://github.com/zeus881/pocketmind/actions/workflows/ios-build.yml)
[![Latest release](https://img.shields.io/github/v/release/zeus881/pocketmind)](https://github.com/zeus881/pocketmind/releases/latest)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green)

> Demo GIF: `docs/screenshots/demo.gif` (recorded on a real phone in airplane mode).
> Google Play: _internal testing_ · APK: [GitHub Releases](https://github.com/zeus881/pocketmind/releases/latest)

---

## Features

| | |
|---|---|
| 💬 **Offline chat** | Answers stream token by token from a model on your phone. Stop, regenerate (old answers kept as versions), edit and resend, copy, Markdown, "12.4 tok/s · GPU" under each answer. |
| 📷 **Ask about a photo** | Attach a photo with the system photo picker (no permission); Gemma 4 E2B's image encoder reads it on the phone. |
| 🧮 **Tools** | Exact calculator, today's date, unit converter and notes. The model calls them through LiteRT-LM's tool calling, and the answer shows "Used: Calculator". |
| 🎙️ **Voice** | Speak your question (Android's on-device recognizer only) and have answers read aloud with offline voices. Free for everyone. |
| 🎭 **Personas and prompts** | Built-in Coder, Tutor and Writer; your own personas (Pro); a library of saved prompts. |
| 🗂️ **History** | Full-text search (FTS4) with highlights, folders, pins, rename, delete with undo, automatic titles; export a chat as Markdown or PDF. |
| 📄 **Summarize PDFs** | Token-aware chunking and map-reduce with "part X of Y" progress; up to 50 pages free, any length with Pro. |
| ❓ **Ask your PDF** | On-device RAG: answers cite pages like `[p. 7]`, say "I don't know" when the PDF doesn't say, and show the source passages. |
| 🌐 **Translate** | 21 languages, swap, streaming output. |
| ⬇️ **Model manager** | Recommends a model for your RAM; resumable, SHA-256-verified downloads; Wi-Fi only by default; a crash-loop guard around native model loading. |
| 🔒 **Privacy** | Chats encrypted with SQLCipher (key in Android Keystore), app lock, no backups of your data, export or delete all your data in one tap. |
| 🌍 **Languages** | English, हिन्दी, ਪੰਜਾਬੀ, with an in-app language picker (Android 13+). |
| 📱 **Everywhere on Android** | Phones, tablets and foldables (navigation rail, list-detail), home-screen widget, quick-settings tile, "Share → PocketMind". |
| 📊 **Benchmark** | Load time, time to first token, tokens/s and peak memory on your phone, shareable. |

**PocketMind Pro**: a one-time purchase through Google Play unlocks bigger models, PDFs of any length and your own
personas. Everything else, including voice, stays free.

Screenshots: `docs/screenshots/` (chat, history, model manager, ask-your-PDF, benchmark).

## How it works

```
 Compose screens ─▶ ViewModels (StateFlow<UiState>) ─▶ Repositories ─▶ Room (SQLCipher) · DataStore
                                                          │
                                                          ▼
          LlmEngine / TextEmbedder (core:llm) ─▶ LiteRT-LM ─▶ GPU, else CPU   ◀─ tools (core:tools)
                                                          ▲
                    one-time download: WorkManager + OkHttp ─▶ huggingface.co (PocketMind's ONLY network call)
     Play Store app: in-app update, review, billing (no user content; decision 5 in CLAUDE.md)
```

Module graph and flows: [docs/architecture.md](docs/architecture.md).

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, Material 3 with design tokens, adaptive navigation suite, type-safe Navigation |
| Architecture | Multi-module clean architecture, MVVM, unidirectional data flow (**25 Gradle modules**; feature → data → core only) |
| LLM runtime | [LiteRT-LM](https://ai.google.dev/edge/litert-lm) 0.19: text, vision, tool calling |
| Models | Gemma 4 E2B (default, vision + tools), Qwen3.5 0.8B, Qwen3 0.6B (tools), EmbeddingGemma 2 270M, all Apache 2.0 |
| DI / async | Hilt, Coroutines + Flow |
| Storage | Room, schema v8 with tested migrations from every version, FTS4 search, SQLCipher 4.20; DataStore |
| Background | WorkManager foreground worker + OkHttp |
| Google Play | Billing 9.1 (Pro), In-App Update 2.1, In-App Review 2.0, Gradle Play Publisher 4.1 |
| Quality | Spotless/ktlint, Detekt, Android Lint (warnings = errors), Kover gate, JUnit, MockK, Turbine, Robolectric, Roborazzi screenshots, Compose end-to-end tests, Macrobenchmark + Baseline Profile |

## Quality, measured

| | |
|---|---|
| JVM tests (unit, Robolectric, end-to-end) | **392 JVM tests**, 0 failures (plus 3 instrumented test classes for the phone) |
| Line coverage of logic (data, domain, ViewModels) | **84.2 %**, enforced in CI by `koverVerifyApp` (gate 84 %) |
| Screenshot tests | 72 golden images: light, dark, tablet and 200 % font |
| End-to-end | onboarding → download → chat → history → delete/undo; shared PDF → summary; app lock (real app, Hilt test modules) |
| Release size | APK 30.59 MB (arm64; LiteRT-LM native library 21.64 MB), AAB 23.40 MB |

## Benchmarks

Measured on real phones with the in-app benchmark (3 fixed prompts, greedy decoding, cold load).
Full table and method: [docs/benchmarks.md](docs/benchmarks.md).

| Phone | Model | Backend | Load | First token | Decode | Peak memory* |
|---|---|---|---|---|---|---|
| iQOO Neo 10R (Snapdragon 8s Gen 3, 8 GB) | Gemma 4 E2B | GPU | 5.5 s (13.3 s first ever) | 0.39 s | 26.3 tok/s | 960 MB |

\* Process PSS; excludes most of the memory-mapped model and GPU driver buffers. See docs/benchmarks.md.
Measured with v1; v2 adds encryption and features whose speed cost is to be measured on the phone
(`docs/performance-budget.md`).

## Design decisions and trade-offs

- **Why LiteRT-LM?** Google moved the MediaPipe LLM Inference API to maintenance-only; LiteRT-LM is its
  successor, with a Kotlin API, GPU/NPU backends, vision and tool calling, and first-party `.litertlm` Gemma models.
  It's hidden behind an `LlmEngine` interface, so the runtime can be swapped without touching any screen.
- **Why brute-force vector search first?** A PDF has hundreds to a few thousand passages; exact cosine
  similarity over all of them takes milliseconds on a phone. An approximate index (e.g. HNSW) only pays off at
  tens of thousands of chunks and adds a dependency and approximation error. It's the upgrade path, not the start.
- **Why no analytics or crash SDK?** The product promise is "nothing leaves your phone". Crashes are recorded on
  the phone, and the user can share a diagnostic report through the share sheet if they choose to.
- **Why tools?** Small models predict digits instead of calculating. With an exact BigDecimal calculator, the
  model only has to write the expression.
- **Why SQLCipher?** Android encrypts storage while the phone is locked; app-level encryption also protects a
  copied or leaked app database. The key never leaves the phone (Keystore-wrapped). It costs 2.01 MB of APK.
- **Why no receipt server for Pro?** No server is the privacy design. For a cheap one-time unlock, Play's on-device
  library is enough (a patched APK can bypass it; subscriptions would need a server). See `docs/billing.md`.
- **Why a single shared engine?** A 2.6 GB model can only be in memory once. A Mutex serializes native calls,
  and a session ownership token stops one feature from answering inside another feature's conversation.
- **Why models outside the APK?** Keeps the install near 31 MB and lets users choose a model that fits their RAM.
- **Why estimate tokens?** LiteRT-LM doesn't expose its tokenizer; a conservative estimate plus a 1,536-token
  answer reserve keeps prompts inside the context window, and long chats are summarized.

## Build in 3 commands

Requirements: JDK 17 (runs Gradle) and JDK 21 (runs unit tests), Android SDK (platform 37).

```bash
git clone https://github.com/zeus881/pocketmind.git && cd pocketmind/mobile
./gradlew assembleDebug                 # → androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew installDebug                  # phone connected with USB debugging
```

The Android/iOS app lives in `mobile/` (V3 monorepo). Full checks, from `mobile/`:
`./gradlew spotlessApply detekt lint testDebugUnitTest koverVerifyApp assembleDebug`.

iOS (needs a Mac with Xcode): `brew install xcodegen`, then in `mobile/iosApp`: `xcodegen generate` and open
`iosApp.xcodeproj`. CI builds the iOS app on every push (`ios-build.yml`).

### Release

Set `POCKETMIND_KEYSTORE_PATH`, `POCKETMIND_KEYSTORE_PASSWORD`, `POCKETMIND_KEY_ALIAS`, `POCKETMIND_KEY_PASSWORD`,
then `./gradlew bundleRelease assembleRelease` (in `mobile/`). Without them you get an unsigned release build. Pushing a version tag
(`v1.2.3`) builds and signs the AAB + APK for GitHub Releases (`release.yml`) and uploads the AAB with generated release
notes to Play's internal track (`play.yml`). See [docs/release-guide.md](docs/release-guide.md).

## Learn how it's built

`docs/learn/` explains every part of the app for Android newcomers. Start with
[00-start-here.md](docs/learn/00-start-here.md); `v2-phase-0.md` … `v2-phase-22.md` cover each v2 step, with
interview questions.

## Roadmap

- A bigger Pro model (Gemma 4 E4B) once its file, size and SHA-256 are verified and it runs on 8 GB phones
- NPU-compiled models per chipset (Tensor, Snapdragon, Dimensity)
- Hybrid (keyword + vector) search, HNSW index, multi-document questions
- OCR for scanned PDFs
- Extra themes (Pro)

## Privacy

See [docs/privacy-policy.md](docs/privacy-policy.md) and [docs/data-safety.md](docs/data-safety.md). In short:
nothing is collected; PocketMind's own code only downloads model files from Hugging Face over HTTPS.

## License

Code: Apache License 2.0. Models are downloaded separately under their own licences (all Apache 2.0 for the
models in the catalog). See the in-app "Open-source licences" screen.

> AI answers can be wrong. Don't rely on PocketMind for medical, legal or financial decisions.
