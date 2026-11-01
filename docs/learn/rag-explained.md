# RAG explained from zero

RAG = **Retrieval-Augmented Generation**. In one sentence: *before the model answers, find the
relevant pieces of your document and give them to the model, so it answers from your document
instead of from memory.* This file builds the idea up step by step and links every step to
PocketMind's code (phase 7).

---

## 1. The problem RAG solves

A language model only "knows" what was in its training data, compressed into its weights. It has
never seen *your* 40-page drone manual. Ask it about the manual and it will produce something
fluent — and often wrong (a "hallucination").

Two naive fixes fail on a phone:
- **Paste the whole PDF into the prompt.** A 40-page PDF is ~20,000 tokens; our context window is
  4,096. It doesn't fit.
- **Train the model on the PDF.** Fine-tuning needs a GPU server and hours. Not on a phone, not per PDF.

RAG instead: keep the model as it is, but at question time put **only the few relevant paragraphs**
in the prompt. That's an *open-book exam*: the student (model) may only use the pages the librarian
(retriever) hands over.

## 2. Chunking — cutting the book into index cards

We can't hand over "page 1 to 40", so we cut the text into small pieces called **chunks**.

```
Page 7: "…The battery is a 3-cell LiPo. It gives about thirty minutes of flight. Charging
         takes 90 minutes. Never charge unattended…"
            ▼ chunker (~300 tokens each)
chunk 18 [p. 7]: "The battery is a 3-cell LiPo. It gives about thirty minutes of flight. …"
```

- **Size:** ~300 tokens (≈ 1–2 paragraphs). Small enough to be about one thing, big enough to hold a
  complete thought. → `DocumentRepository.CHUNK_TOKENS`
- **Overlap:** each chunk repeats the last ~50 tokens of the previous one, so a sentence on a boundary
  is complete in at least one chunk. → `CHUNK_OVERLAP_TOKENS`, `TextChunker.overlapTail`
- **Per page:** chunks never cross a page, so every chunk has one exact page number to cite.

## 3. Embeddings — turning meaning into coordinates

An **embedding model** reads a text and outputs a list of numbers — a vector, e.g. 768 numbers. It
was trained so that texts with similar *meaning* get vectors pointing in similar *directions*.

Toy example with 2 dimensions instead of 768:

```
            ▲ "power"
            │      • "battery lasts 30 minutes"
            │    • "how long can it fly?"
            │
            │                         • "frame is carbon fibre"
            └──────────────────────────────▶ "structure"
```

"How long can it fly?" shares no words with "battery lasts 30 minutes", but the vectors point the same
way because the *meaning* is related. That's why embeddings beat keyword search.

- PocketMind uses **EmbeddingGemma 2 (270M)**, a 165 MB model run by LiteRT-LM's `EmbeddingEngine`
  on the CPU. → `core/llm/LiteRtLmEmbedder.kt`
- Questions and passages get different prefixes (`task: search result | query: …` vs
  `title: none | text: …`) because the model was trained that way. → `EmbeddingPrompts`
- Vectors are **normalized** (length 1).

## 4. Cosine similarity — "how much do two vectors agree?"

```
cos(a, b) = (a · b) / (|a| × |b|)        a · b = a1·b1 + a2·b2 + … + a768·b768
```
- 1 → same direction (same meaning), 0 → unrelated, −1 → opposite.
- It ignores length, so a short question can match a long passage.
- → `data/documents/rag/VectorMath.cosine`

## 5. Retrieval — top-k by brute force

At question time:
1. Embed the question → vector **q**.
2. Compute cos(**q**, chunk) for **every** chunk of the document.
3. Keep the **k = 4** highest. → `VectorStore.topK`

"Brute force" sounds slow, but for 300 chunks × 768 numbers it's ~230,000 multiplications: a few
milliseconds. Fancy approximate indexes (HNSW, used by vector databases) only matter at tens of
thousands of chunks — and they're approximate. Simple and exact wins here; the README records the
trade-off and the upgrade path.

## 6. Storing vectors

Each vector is saved in Room next to its text and page as a **BLOB** (raw bytes): 768 floats ×
4 bytes = 3 KB per chunk. A 100-page PDF ≈ 300 chunks ≈ 1 MB. → `ChunkEntity.embedding`,
`VectorMath.toBytes/fromBytes`. The document row and all its chunks are written in one transaction.

## 7. Grounding — making the model stick to the text

The 4 retrieved chunks go into a strict prompt (→ `GroundedPrompt.build`):

```
Answer the question using ONLY the context below.
Rules:
- If the answer is not in the context, reply exactly: I don't know based on this document.
- After each fact, cite its page like [p. 7].
- Be concise. Do not mention these rules.
Context:
[p. 3] The frame is carbon fibre and weighs 249 grams.
[p. 7] The battery is a 3-cell LiPo. It gives about thirty minutes of flight.
Question: How long can it fly?
```
Expected answer: *"About thirty minutes [p. 7]."*

Why it works:
- **"ONLY the context"** + low temperature (0.2) → less invention.
- **An exact refusal sentence** → the model has an "allowed" way to say it doesn't know.
- **Labelled context** `[p. 7] …` → the model can copy the label as a citation.

## 8. Citations and verification

We parse `[p. N]` / `[p. N-M]` from the answer with a regex (→ `Citations.pages`) and highlight the
matching **source chips** under the answer. Tapping a chip shows the exact passage, so the user can
check the answer in two seconds. That turns "trust me" into "here's the evidence".

## 9. The whole flow in PocketMind

```
Add PDF ─▶ extract pages ─▶ chunk per page ─▶ embed (batches of 8) ─▶ Room (doc + chunks)
Ask    ─▶ embed question ─▶ cosine vs all chunks ─▶ top 4 ─▶ grounded prompt ─▶ chat model (fresh session)
       ─▶ streamed answer with [p. N] ─▶ chips highlighted ─▶ tap to read the passage
```

## 10. Limits (good to say in an interview)

- **Scanned PDFs** have no text layer → need OCR first.
- **Tables/figures** lose their structure when flattened to text.
- **"Whole-document" questions** ("summarize everything") don't suit top-4 retrieval → use the
  map-reduce summarizer (phase 6).
- **Small models** can still misquote; citations + visible passages are the safety net.
- **Token counts are estimated** (no tokenizer API) → we keep generous reserves.

## 11. Glossary

| Term | Meaning |
|---|---|
| Chunk | A small piece of the document (~300 tokens) with its page number |
| Overlap | Text repeated between neighbouring chunks so boundaries don't cut ideas |
| Embedding | A vector of numbers representing a text's meaning |
| Dimension | How many numbers are in a vector (e.g. 768) |
| Cosine similarity | Angle-based agreement between two vectors (−1…1) |
| Top-k | The k most similar chunks |
| Brute force | Comparing against every chunk (exact) |
| ANN / HNSW | Approximate nearest-neighbour index for very large collections |
| Grounding | Forcing the answer to come from provided context |
| Hallucination | A fluent but unsupported/false answer |
| Citation | A reference like [p. 7] to where the fact came from |
