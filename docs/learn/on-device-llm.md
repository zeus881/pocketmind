# How on-device LLMs work (in simple terms)

This explains what actually happens when PocketMind answers a question on your phone, and
links every idea to the code. No maths beyond multiplication.

---

## 1. Tokens — the model's alphabet

A model doesn't read letters or words; it reads **tokens**: frequent chunks of text from a fixed
vocabulary (~250,000 for Gemma). "PocketMind runs offline" might become
`["Pocket", "Mind", " runs", " off", "line"]`.

- English ≈ 4 characters per token; Hindi and other scripts use more tokens per character.
- Everything is counted in tokens: prompt length, answer length, speed (tokens/second).
- In code: `TokenEstimator` (core:llm) estimates tokens (LiteRT-LM doesn't expose its tokenizer), and
  rounds up so we stay under limits.

## 2. What the model does: predict the next token, again and again

An LLM is a function: *given all tokens so far, give a probability for every possible next token.*
Generation is a loop:

```
"The capital of France is" → model → {" Paris": 0.92, " a": 0.02, …} → pick " Paris"
"The capital of France is Paris" → model → {".": 0.85, …} → pick "." → … until an end token
```

**Sampling** decides which token to pick:
- **temperature**: low = always the likeliest token (precise); high = more random (creative).
- **top-K**: only consider the K likeliest tokens. **top-P**: only the smallest set whose probabilities sum to P.
- **seed**: makes randomness repeatable.
- In code: `SamplingParams` → LiteRT-LM `SamplerConfig`. Chat uses the user's settings; summaries and
  translation use `SamplingParams.Precise` (temp 0.2); the benchmark uses greedy (top-K 1).

## 3. The context window — the model's short-term memory

The model can only look at a fixed number of tokens at once: the **context window**. Prompt +
chat history + answer must all fit. PocketMind reserves **4,096 tokens** (`EngineConfig.maxNumTokens`
in `LiteRtLmEngine`).

Consequences you see in the code:
- Long chats are trimmed and older turns summarized (`HistoryTrimmer`).
- PDFs are chunked and summarized with map-reduce (`TextChunker`, `Summarizer`).
- RAG puts only the 4 best passages in the prompt (`DocumentRepository`).

Why not 32k? Memory: see the KV cache below.

## 4. Prefill vs decode — the two phases of answering

1. **Prefill**: the model reads your whole prompt. All prompt tokens are processed *in parallel* — it's
   compute-heavy but fast per token. Its duration is most of the **time to first token (TTFT)**.
2. **Decode**: the model generates the answer *one token at a time*, each step depending on the last.
   This is limited mainly by **memory bandwidth** (reading all the weights for every token).

That's why the benchmark measures them separately: TTFT grows with prompt length; decode tokens/s
barely changes. (`BenchmarkRunner`, `GenerationStats.timeToFirstTokenMs` / `decodeTokensPerSecond`.)

## 5. The KV cache — why longer context costs memory

During generation the model stores, for every token already processed, intermediate "key" and
"value" vectors for each layer — the **KV cache** — so it doesn't recompute them for every new token.

- It grows **linearly with the number of tokens** in context. A larger `maxNumTokens` reserves more RAM.
- Rebuilding a chat session means re-running prefill to refill the cache — that's why
  `OfflineChatRepository` only rebuilds when necessary.

## 6. Why the model file is 2.6 GB — parameters and quantization

A model is mostly **weights** (parameters): billions of numbers. Gemma 4 E2B has about 2 billion
"effective" parameters (plus large embedding tables).

| Precision | Bytes per weight | 2 B weights |
|---|---|---|
| float32 | 4 | 8 GB |
| float16 / bf16 | 2 | 4 GB |
| int8 | 1 | 2 GB |
| int4 | 0.5 | 1 GB |

**Quantization** stores weights with fewer bits (int8/int4) plus scale factors. Files get 2–8× smaller
and decode gets faster (less memory to read per token), at a small quality cost. The `.litertlm` files we
download are already quantized (mixed int4/int8 + higher-precision parts and the tokenizer), which is why
Gemma 4 E2B is ~2.6 GB, Qwen3.5 0.8B int8 ~0.96 GB and Qwen3 0.6B ~0.6 GB.

The file is **memory-mapped** by the runtime, and the model's working memory (weights in use, KV cache,
activations) shows up as native memory — which is why the benchmark measures **PSS**, not the Java heap.

## 7. CPU vs GPU vs NPU

| | Good at | In PocketMind |
|---|---|---|
| **CPU** | Works everywhere; uses SIMD (ARM NEON) via XNNPACK | Always-available fallback; also runs the embedding model |
| **GPU** | Massive parallel math; great for prefill and decent decode | Preferred backend (`Backend.GPU()`), needs the OpenCL driver (`uses-native-library libOpenCL.so`) |
| **NPU** | Very efficient int8/int4 matrix math | Needs chip-specific compiled models (e.g. `_Qualcomm_SM8650.litertlm`); not used in v1 |

The engine tries **GPU first, then CPU** (`EngineMath.backendOrder`) and records which one actually
loaded — shown in the chat ("12.4 tok/s · GPU") and in the benchmark. GPU init fails on some phones
because the vendor OpenCL driver is missing or blocked; the fallback keeps the app working.

## 8. Why tokens/second differs between phones

- **Memory bandwidth** (LPDDR5X vs LPDDR4X): decode reads the weights for every token → bandwidth-bound.
- **GPU generation and driver quality**: some drivers are much faster, some unusable (→ CPU).
- **Thermal throttling**: phones slow down after a minute of full load (run the benchmark twice to see it).
- **Big.LITTLE CPU cores and background load**.
- **Model size and quantization**: a 0.6B model can be 3–4× faster than a 2B model on the same phone.
- **Prompt length**: affects TTFT (prefill) far more than decode speed.

So never quote one number for "the app": quote *phone + model + backend + metric*. That's what
`BenchmarkResult` stores and `BenchmarkReport` prints.

## 9. "Thinking" models

Some models (Qwen3) can write hidden reasoning before answering. On a phone that doubles latency and
would put raw reasoning in chat bubbles, so we disable it: `ThinkingConfig(enableThinking = false)` in
`LiteRtLmEngine.newSession`.

## 10. Embedding models (for RAG)

A different, small model (EmbeddingGemma, 165 MB) that outputs a vector instead of text. Similar meanings
→ similar vectors. Used to find relevant PDF passages. See `rag-explained.md`.

## 11. Putting it together: one chat message

```
"How do drones hover?"
  ▼ ChatRepository: make sure the native conversation holds this chat's history (prefill if rebuilt)
  ▼ LiteRtLmEngine.generate: prefill the new prompt (TTFT ≈ 0.3–1.5 s)
  ▼ decode loop on GPU: one token at a time (e.g. 12–50 tokens/s depending on phone)
  ▼ each token → Flow → throttled UI → Markdown bubble
  ▼ end → getBenchmarkInfo() → "23.1 tok/s · GPU" saved with the message in Room
```

## Glossary

| Term | Meaning |
|---|---|
| Token | Unit of text the model reads/writes |
| Context window | Max tokens the model sees at once |
| Prefill | Processing the prompt (parallel) |
| Decode | Generating answer tokens one by one |
| TTFT | Time to first token (mostly prefill) |
| KV cache | Stored per-token state that makes decoding fast; grows with context |
| Parameters / weights | The numbers that make up the model |
| Quantization | Storing weights with fewer bits (int8/int4) |
| Temperature / top-K / top-P | Sampling controls for randomness |
| Backend | Hardware used: CPU, GPU, NPU |
| PSS | Process memory incl. native allocations |
