# Interview pitch — PocketMind

> Replace every `[ ]` with numbers YOU measured on real phones (Settings → Benchmark, Macrobenchmark).
> Never quote a number you haven't measured — interviewers ask how you got it.

---

## The 2-minute version (spoken)

"PocketMind is a private AI assistant for Android where the language model runs entirely on the phone.
After a one-time model download it works in airplane mode — no account, no server, no analytics.

It does four things: streaming chat with history; PDF summarization; 'Ask your PDF', which is retrieval-
augmented generation with page citations; and translation into 21 languages. The default model is Google's
Gemma 4 E2B — 2.6 GB — running on LiteRT-LM, Google's on-device runtime. On my [phone] it generates
[ ] tokens per second on the GPU with [ ] seconds to the first token, and it automatically falls back to
the CPU on phones whose GPU driver doesn't work.

Technically it's a multi-module Kotlin app — Compose, Hilt, Room, DataStore, WorkManager — with clean
architecture enforced by the build: feature modules can't depend on each other, and only one module can
even see the LLM runtime, behind an interface. That interface is what let me unit-test everything with a fake
engine: there are [ ] unit tests running in CI on every push.

A few things I'm proud of. The 2.6 GB model downloads in a WorkManager foreground job that resumes with HTTP
Range requests, verifies a SHA-256 while streaming, and only renames the file once it's verified. For long
PDFs I use token-aware chunking and map-reduce summarization, because the model only sees 4,000 tokens.
'Ask your PDF' embeds passages with a second, 165 MB embedding model, stores the vectors in Room, retrieves by
cosine similarity and forces the model to cite pages or say 'I don't know'. And in my final review I found and
fixed a real race condition: several features share one native conversation, so I added a session ownership
token that the engine validates under its lock.

It's published as a signed release from a tag-triggered GitHub Actions pipeline, with a benchmark screen so
anyone can reproduce the numbers."

## The 30-second version

"PocketMind is an offline AI assistant for Android: Gemma 4 runs on the phone with LiteRT-LM — chat,
PDF summaries, PDF question-answering with page citations, and translation, with zero data leaving the
device. It's a multi-module Compose app with Hilt, Room and WorkManager, resumable checksum-verified model
downloads, on-device RAG, and a built-in benchmark — about [ ] tokens/second on my [phone]."

## STAR story — the hardest problem

**Situation.** One 2-billion-parameter model fits in a phone's memory only once, so chat, translation,
summarization, PDF Q&A and the benchmark all share a single native conversation inside one engine.

**Task.** Guarantee that every feature's prompt runs with *its* context — the right system prompt and chat
history — even when features are used one after another or at the same time, without loading the model twice.

**Action.**
1. Made the engine a Hilt singleton guarded by a coroutine `Mutex`, so native calls never overlap.
2. Found that a Mutex makes each call atomic but not the *sequence* "new session, then generate": another
   feature could slip in between. Chat would then answer inside a translation session.
3. Introduced an ownership token: `newSession()` returns a session id; `generate(prompt, id)` validates it
   under the lock and throws `SessionReplacedException` on mismatch. The chat repository remembers the id it
   created and rebuilds the conversation from Room (trimmed to the context window) when it's stale.
4. Discovered by inspecting LiteRT-LM's bytecode that cancelling its Flow does *not* stop native generation
   (empty `awaitClose`), so the engine calls `cancelProcess()` on cancellation — otherwise "Stop" would
   only stop the UI.
5. Wrote regression tests with a fake engine that enforces the same rules.

**Result.** No cross-feature context leaks, a Stop button that really stops the model, and a pattern I can
explain clearly: "locks protect calls, tokens protect sequences". [Optional: add a measured number, e.g.
session rebuild time on your phone.]

## The 20 most likely questions (with answers)

1. **Why on-device instead of a cloud API?**
   Privacy (nothing leaves the phone), works offline, zero server cost, no per-token bill, low latency for the
   first token. Trade-offs: smaller model quality, 0.6–2.6 GB download, battery and RAM use, device variance.

2. **Why LiteRT-LM and not MediaPipe or llama.cpp?**
   MediaPipe's LLM Inference API is maintenance-only and LiteRT-LM is its successor, with a Kotlin API, GPU/NPU
   support and first-party Gemma models. llama.cpp is excellent but I'd maintain JNI bindings myself.

3. **How do you stream tokens to Compose without jank?**
   The engine exposes a cold Flow on the IO dispatcher; the ViewModel folds pieces into the growing text,
   throttles to one update per 50 ms with a conflating operator, and exposes a StateFlow; Compose collects it
   lifecycle-aware. The answer is written to Room once at the end.

4. **What happens when the chat exceeds the context window?**
   Before each message I estimate history tokens; if they exceed the budget (window minus answer reserve,
   system prompt and new prompt) I rebuild the session with the newest turns that fit plus a short note
   summarizing the dropped turns in the system prompt.

5. **How do you handle a 2.6 GB download?**
   WorkManager foreground job (dataSync), unique work per model, Wi-Fi by default, storage check (size × 1.2),
   HTTP Range resume, SHA-256 computed while streaming, atomic rename, exponential backoff, pause = cancel job
   and keep the partial file.

6. **Why does the GPU fail on some phones and what do you do?**
   The GPU backend needs the vendor's OpenCL driver (declared as optional native libraries); some phones lack
   or block it. The engine tries GPU then CPU, closes the half-initialized engine, and records which backend
   actually loaded.

7. **Explain your RAG pipeline.**
   Chunk each PDF page into ~300-token passages with overlap, embed with EmbeddingGemma on-device, store
   vectors as BLOBs in Room; at question time embed the query, brute-force cosine top-4, build a grounded
   prompt with [p. N] labels and strict rules, stream the answer and highlight cited pages.

8. **Why brute-force vector search?**
   A document has hundreds to a few thousand chunks — exact search takes milliseconds. An ANN index adds a
   dependency, memory and approximation; I'd add HNSW past roughly ten thousand chunks.

9. **How do you prevent hallucinations?**
   Grounded prompts (only the context, exact "I don't know" sentence, mandatory citations), low temperature,
   and the UI shows the source passages so users can verify. It reduces, not eliminates, errors — hence the
   onboarding disclaimer.

10. **How do you summarize a 30-page PDF with a 4k context?**
    Token-aware chunks of ~1,500 tokens with 10% overlap; map each to bullets in a fresh session; reduce the
    bullets in rounds until they fit; final overview. Progress "part X of Y" and cancel at any point.

11. **What is quantization?**
    Storing weights in int8/int4 instead of 16/32-bit floats: 2–8× smaller and faster to decode, since decode
    is memory-bandwidth-bound, with a small quality loss. That's why a ~2B model is a 2.6 GB file.

12. **Prefill vs decode?**
    Prefill processes the prompt in parallel and dominates time-to-first-token; decode generates one token at a
    time and is bandwidth-bound. I measure them separately in the benchmark.

13. **How did you test code that needs a 2.6 GB model?**
    Everything depends on interfaces; unit tests inject FakeLlmEngine and FakeTextEmbedder (deterministic,
    enforcing the same session rules), with in-memory Room under Robolectric and virtual time. Only real model
    loading is tested manually on a phone.

14. **How is the architecture enforced?**
    Gradle modules plus a build check: features can't depend on features, data only on core, and LiteRT-LM is an
    `implementation` dependency of core:llm so no other module can compile against it.

15. **How do you handle low memory?**
    `onTrimMemory` at critical/background levels unloads the model; screens lazily reload; the benchmark
    records peak PSS; downloads check free storage; PdfBox buffers to temp files.

16. **How do you handle the user leaving mid-answer?**
    The generation coroutine is cancelled; the engine calls `cancelProcess()`; the partial answer is saved with
    status STOPPED inside `NonCancellable`, and the chat session is marked out of sync so it rebuilds next time.

17. **How do you migrate the database?**
    Versioned Room schemas exported to JSON and committed; AutoMigrations for additive changes; a test builds a
    real v1 and v2 database from the committed schemas and opens it with the current version.

18. **What's your security model?**
    One allowlisted HTTPS client, network security config without cleartext or user CAs, commit-pinned URLs and
    SHA-256 verification, no backups, no logs or analytics in release, R8, optional biometric lock, keystore
    only in CI secrets.

19. **How do you measure performance?**
    In-app benchmark: cold load, TTFT, decode tokens/s from the runtime's counters, peak PSS, backend, device —
    3 fixed prompts with greedy decoding. Macrobenchmark for cold startup with/without Baseline Profile and
    frame timing.

20. **What would you do for v2?**
    NPU-compiled models per chip, voice input, image input (Gemma 4 is multimodal), tool calling, an HNSW index
    and hybrid search for many documents, OCR for scanned PDFs, and per-chat model selection.

## Resume bullets (fill in the brackets)

- Built PocketMind, an offline Android AI assistant running Gemma 4 E2B fully on-device with LiteRT-LM:
  [ ] tok/s and [ ] s time-to-first-token on [phone], automatic GPU→CPU fallback across [ ] tested devices.
- Designed a 21-module clean-architecture app (Kotlin, Compose, Hilt, Room, WorkManager) with streaming
  Flow-based generation, resumable SHA-256-verified 2.6 GB downloads, and [ ] unit tests in GitHub Actions CI.
- Implemented on-device RAG over PDFs (EmbeddingGemma, Room vector store, cosine retrieval) with page-cited,
  grounded answers; [published on Google Play with [ ] installs].
