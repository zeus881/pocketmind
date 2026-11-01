# Changelog

All notable changes to PocketMind are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] — unreleased

First public release. Everything runs on the phone; the only network use is the model download.

### Added
- **Offline chat** with streaming answers, Stop, Retry/Regenerate, Copy, Markdown rendering and a
  per-answer speed label (e.g. "12.4 tok/s · GPU"). Long chats are trimmed to the context window with a
  short note about earlier turns.
- **Chat history**: search (titles and message text), rename, delete with Undo, automatic titles.
- **Model manager**: catalog of Gemma 4 E2B, Qwen3.5 0.8B, Qwen3 0.6B and EmbeddingGemma 2; RAM-based
  recommendation; background download with progress notification, pause/resume/cancel, HTTP Range
  resume, SHA-256 verification, Wi-Fi only by default.
- **Settings**: model, GPU/CPU preference (automatic CPU fallback), temperature, top-K, system prompt,
  light/dark/system theme, mobile-data downloads, optional biometric app lock.
- **Benchmark**: cold model load time, time to first token, decode tokens/s and peak memory over 3 fixed
  prompts; results saved and shareable as text.
- **Onboarding**: what the app does, privacy promise, recommended model and size, "answers can be wrong".
- **PDF summarize**: system file picker (no storage permission), page-by-page extraction, token-aware
  chunking with overlap, map-reduce summary with "part X of Y" progress and Cancel.
- **Translate**: 21 languages, swap, streaming output, fresh model session per request.
- **Ask your PDF** (on-device RAG): EmbeddingGemma vectors stored in Room, cosine top-4 retrieval,
  grounded answers with `[p. N]` citations, "I don't know" when the answer isn't in the document, and
  tappable source chips.
- Hindi translation of the whole UI. Open-source licences screen. Privacy policy.
- Macrobenchmark module (cold startup, frame timing) and Baseline Profile generator.
- CI (format, Detekt, Lint, unit tests, debug APK) and a tag-triggered signed release workflow.

### Security
- HTTPS only; system certificate authorities only; network access limited to Hugging Face hosts.
- No backups of chats, documents, settings or models (cloud and device-to-device).
- Release builds are minified with R8 and log nothing.
