# PocketMind

**A private AI assistant that runs entirely on your Android phone — chat, summarize PDFs, ask your PDFs
questions with page citations, and translate. No account. No cloud. Works in airplane mode.**

[![CI](https://github.com/YOUR_GITHUB_USER/pocketmind/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_GITHUB_USER/pocketmind/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/YOUR_GITHUB_USER/pocketmind)](https://github.com/YOUR_GITHUB_USER/pocketmind/releases/latest)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue) ![License](https://img.shields.io/badge/license-Apache%202.0-green)

> Demo GIF: `docs/screenshots/demo.gif` (recorded on a real phone in airplane mode).
> Google Play: _closed testing_ · APK: [GitHub Releases](https://github.com/YOUR_GITHUB_USER/pocketmind/releases/latest)

---

## Features

| | |
|---|---|
| 💬 **Offline chat** | Answers stream token by token from a model on your phone. Stop, regenerate, copy, Markdown, and a live "12.4 tok/s · GPU" label. |
| 🗂️ **Chat history** | Search titles and messages, rename, delete with undo, automatic titles. |
| 📄 **Summarize PDFs** | Any length: token-aware chunking + map-reduce, with "part X of Y" progress and cancel. |
| ❓ **Ask your PDF** | On-device RAG: answers cite pages like `[p. 7]`, say "I don't know" when the PDF doesn't say, and show the source passages. |
| 🌐 **Translate** | 21 languages, swap, streaming output. |
| ⬇️ **Model manager** | Recommends a model for your RAM; resumable, SHA-256-verified downloads; Wi-Fi only by default. |
| 📊 **Benchmark** | Load time, time to first token, tokens/s and peak memory on your phone — shareable. |
| 🔒 **Privacy** | No analytics, no backups of your data, optional fingerprint lock. English + Hindi UI. |

Screenshots: `docs/screenshots/` (chat, history, model manager, ask-your-PDF, benchmark).

## How it works

```
 Compose screens ─▶ ViewModels (StateFlow<UiState>) ─▶ Repositories ─▶ Room · DataStore
                                                          │
                                                          ▼
                              LlmEngine / TextEmbedder (core:llm) ─▶ LiteRT-LM ─▶ GPU, else CPU
                                                          ▲
                         one-time download: WorkManager + OkHttp ─▶ huggingface.co (the ONLY network call)
```

Module graph and flows: [docs/architecture.md](docs/architecture.md).

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, type-safe Navigation |
| Architecture | Multi-module clean architecture, MVVM, unidirectional data flow (21 Gradle modules) |
| LLM runtime | [LiteRT-LM](https://ai.google.dev/edge/litert-lm) 0.19 |
| Models | Gemma 4 E2B (default), Qwen3.5 0.8B, Qwen3 0.6B, EmbeddingGemma 2 270M — all Apache 2.0 |
| DI / async | Hilt, Coroutines + Flow |
| Storage | Room (schema-versioned, tested migrations), DataStore |
| Background | WorkManager foreground worker + OkHttp |
| PDF | PdfBox-Android |
| Quality | Spotless/ktlint, Detekt, Android Lint (warnings = errors), JUnit, MockK, Turbine, Robolectric, Compose UI tests, Macrobenchmark + Baseline Profile |

## Benchmarks

Measured on real phones with the in-app benchmark (3 fixed prompts, greedy decoding, cold load).
Full table and method: [docs/benchmarks.md](docs/benchmarks.md).

| Phone | Model | Backend | Load | First token | Decode | Peak memory |
|---|---|---|---|---|---|---|
| _measure on device_ | Gemma 4 E2B | | | | | |

## Design decisions and trade-offs

- **Why LiteRT-LM?** Google moved the MediaPipe LLM Inference API to maintenance-only; LiteRT-LM is its
  successor, with a Kotlin API, GPU/NPU backends and first-party `.litertlm` Gemma models. It's hidden behind an
  `LlmEngine` interface, so the runtime can be swapped without touching any screen.
- **Why brute-force vector search first?** A PDF has hundreds to a few thousand passages; exact cosine
  similarity over all of them takes milliseconds on a phone. An approximate index (e.g. HNSW in ObjectBox)
  only pays off at tens of thousands of chunks and adds a dependency and approximation error — it's the
  upgrade path, not the starting point.
- **Why no analytics?** The product promise is "nothing leaves your phone". Analytics would break it and
  require a Data-safety disclosure. Quality comes from tests, CI and the built-in benchmark instead.
- **Why a single shared engine?** A 2.6 GB model can only be in memory once. A Mutex serializes native calls,
  and a session ownership token stops one feature from answering inside another feature's conversation.
- **Why models outside the APK?** Keeps the install at ~31 MB and lets users choose a model that fits their RAM.
- **Why estimate tokens?** LiteRT-LM doesn't expose its tokenizer; a conservative estimate plus a 1,024-token
  answer reserve keeps prompts inside the context window.

## Build in 3 commands

Requirements: JDK 17 (runs Gradle) and JDK 21 (runs unit tests), Android SDK (platform 37).

```bash
git clone https://github.com/YOUR_GITHUB_USER/pocketmind.git && cd pocketmind
./gradlew assembleDebug                 # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # phone connected with USB debugging
```

Full checks: `./gradlew spotlessApply detekt lint testDebugUnitTest assembleDebug`.

### Release

Set `POCKETMIND_KEYSTORE_PATH`, `POCKETMIND_KEYSTORE_PASSWORD`, `POCKETMIND_KEY_ALIAS`, `POCKETMIND_KEY_PASSWORD`,
then `./gradlew bundleRelease assembleRelease`. Without them you get an unsigned release build. Pushing a tag
`v*` builds, signs and publishes the AAB + APK via GitHub Actions (`.github/workflows/release.yml`).

## Learn how it's built

`docs/learn/` explains every part of the app for Android newcomers — start with
[00-start-here.md](docs/learn/00-start-here.md).

## Roadmap

- NPU-compiled models per chipset (Tensor, Snapdragon, Dimensity)
- Voice input and image input (Gemma 4 is multimodal)
- Hybrid (keyword + vector) search, HNSW index, multi-document questions
- OCR for scanned PDFs
- Tool calling (calculator, calendar) — on device

## Privacy

See [docs/privacy-policy.md](docs/privacy-policy.md). In short: nothing is collected; the only network use
is downloading model files from Hugging Face over HTTPS.

## License

Code: Apache License 2.0. Models are downloaded separately under their own licences (all Apache 2.0 for the
models in the catalog) — see the in-app "Open-source licences" screen.

> AI answers can be wrong. Don't rely on PocketMind for medical, legal or financial decisions.
