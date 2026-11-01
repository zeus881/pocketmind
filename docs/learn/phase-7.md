# Phase 7 — Ask your PDF: on-device RAG with page citations

> Read `docs/learn/rag-explained.md` first if embeddings and cosine similarity are new to you.

## 1. What we built

"Ask your PDF" in the Documents tab. The user adds a PDF; the app splits each page into
~300-token passages, turns each passage into a vector with a small on-device embedding model
(EmbeddingGemma 2, 165 MB, run by LiteRT-LM's `EmbeddingEngine`), and stores text + vector in
Room (database v3). A question is embedded the same way, compared with every passage by cosine
similarity, and the best 4 passages go into a strict "grounded" prompt. The chat model answers
only from them, cites pages like `[p. 7]`, or says "I don't know". Tappable source chips show
the exact passages.

## 2. Big picture

```
INDEX (once per PDF)                                        ASK (per question)
────────────────────                                        ──────────────────
PDF ─▶ PdfTextExtractor ─▶ pages                            question
        │                                                      │ TextEmbedder.embedQuery
        ▼ TextChunker per page (300 tok, overlap 50)           ▼  ("task: search result | query: …")
   passages ──▶ TextEmbedder.embedDocuments ──▶ vectors     q-vector
        │       ("title: none | text: …")                      │
        ▼                                                      ▼ VectorStore.topK (cosine, brute force)
   Room v3: documents ◀─1:N─ chunks(text, page, BLOB) ───▶ top-4 passages (+ pages)
                                                               │ GroundedPrompt.build
                                                               ▼
                                         LlmTask.stream (fresh session, temp 0.2)
                                                               ▼
                                     "Thirty minutes [p. 2]."  + source chips [p. 1] [p. 2 ✓]
```

Two different models run on the phone: the **embedding model** (finds the right pages) and the
**chat model** (writes the answer). Only `core:llm` touches LiteRT-LM for both.

## 3. Concepts you need

### Retrieval-Augmented Generation (RAG)
- **What:** Before generating, *retrieve* relevant text and put it in the prompt; the model answers from
  that text instead of from its (possibly wrong) memory.
- **Analogy:** An open-book exam: the student (LLM) may only use the pages the librarian (retriever) hands over.
- **Why:** Small models know little about *your* document and hallucinate; grounding + citations make
  answers checkable.
- **Alternatives:** Put the whole PDF in context (doesn't fit), fine-tune the model on the PDF (impossible on a phone).

### Embeddings
- A vector (e.g. 768 numbers) where similar meaning ⇒ similar direction. Produced by a separate model trained
  for this (EmbeddingGemma). Queries and passages use different prefixes ("asymmetric retrieval").

### Cosine similarity and brute-force search
- `cos(a, b) = a·b / (|a||b|)` — 1 same meaning, 0 unrelated. Brute force = compare with every chunk:
  O(n·d). For a 100-page PDF (~300 chunks × 768 dims) that's ~0.2 M multiply-adds — milliseconds.
- **Alternative:** approximate nearest-neighbour indexes (HNSW in ObjectBox, FAISS) — needed only at
  tens of thousands of chunks; they add a dependency and approximate results.

### Storing vectors in SQLite
- A float array becomes a BLOB of little-endian float32 bytes (`ByteBuffer`). 768 floats = 3 KB per chunk.

### Grounded prompting and citations
- Rules in the prompt: only use context; exact refusal sentence; cite `[p. N]`. Context lines are labelled
  `[p. N] passage…` so the model can copy the label. We parse citations with a regex to highlight chips.

### Two-model memory budget
- The embedding model runs on CPU (165 MB) while the chat model keeps the GPU — they coexist on 6–8 GB phones.

## 4. Code walkthrough

### `core:llm/TextEmbedder.kt` + `LiteRtLmEmbedder.kt`
```kotlin
EmbeddingEngine(EmbeddingEngineConfig(modelPath = path, backend = Backend.CPU(), cacheDir = cacheDir)).initialize()
loaded.computeEmbeddingBatch(batch.map { listOf(InputData.Text(it)) }, EmbeddingOptions(normalize = true))
```
API verified in the 0.19.0 AAR (`EmbeddingEngine`, `EmbeddingOptions`, `InputData.Text`). Batches of 8, IO
dispatcher, Mutex, `ensureActive()` between batches (cancel indexing). `EmbeddingPrompts` adds the
EmbeddingGemma task prefixes.

### `core:database` v3
`DocumentEntity(id, name, pageCount, chunkCount, createdAt, embeddingModelId, dimensions)` and
`ChunkEntity(documentId FK CASCADE, chunkIndex, startPage, endPage, text, embedding BLOB)`.
`insertIndexed()` is a `@Transaction` — a half-indexed document is never visible. AutoMigration 2→3;
`MigrationTest` now checks 1→3 and 2→3 from the committed schemas.

### `DocumentRepository.index(uri)` (Flow<IndexProgress>)
1. Embedding model present? else `Failed(NO_MODEL)`. 2. Extract pages. 3. **Chunk page by page** (never
across pages, so each passage has one exact page to cite — we found this while writing the test: the
chunker had merged three short pages into one chunk). 4. Embed in batches, emitting `Embedding(done, total)`.
5. Store document + chunks atomically.

### `DocumentRepository.ask(documentId, question)`
```kotlin
val q = embedder.embedQuery(question)
val sources = withContext(cpu) { VectorStore.topK(q, dao.chunks(documentId), TOP_K = 4) }
Answer(sources, task.stream(GroundedPrompt.SYSTEM, GroundedPrompt.build(question, sources), Precise))
```

### `GroundedPrompt` and `Citations`
```
Answer the question using ONLY the context below.
Rules:
- If the answer is not in the context, reply exactly: I don't know based on this document.
- After each fact, cite its page like [p. 7].
Context:
[p. 2] The lithium polymer battery gives a flight time of thirty minutes.
…
Question: What flight time does the battery give?
```
`Citations.pages("… [p. 3-4] … [p.7]")` → `[3, 4, 7]`.

### `AskViewModel` / `AskPane`
Documents list (radio selection, delete), indexing card with determinate progress, question field,
Ask/Cancel, streamed Markdown answer, `FilterChip` per source (✓ when cited), tap → dialog with the passage.
If the embedding model is missing: a clear empty state with "Get the search model" → Models screen; the
state refreshes `ON_RESUME` when the user comes back.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| EmbeddingGemma 2 270M via LiteRT-LM | MediaPipe Text Embedder + USE | Same runtime as the chat model (one native stack, only `core:llm` imports it), Apache 2.0, strong multilingual quality. |
| ~300-token chunks, 50 overlap | Bigger chunks | Specific passages → precise citations and less irrelevant text in a small context window. |
| Chunk per page | Across pages | Exact page citations. Cost: a sentence crossing a page break is split. |
| Top-4 | Top-10 | 4 × 300 = 1,200 tokens of context fits comfortably in 4k with room for the answer. |
| Brute-force cosine | HNSW index | Exact, simple, fast enough below ~10k chunks. README lists it as a known trade-off. |
| Vectors as BLOB in Room | Separate vector DB | One storage system, transactional with metadata, cascades on delete. |
| No similarity threshold | Refuse below score X | Thresholds are model-specific and brittle; the prompt's "I don't know" rule handles irrelevant context. |
| Store `embeddingModelId` | — | Vectors from different models aren't comparable; enables re-index detection in v2. |

## 6. Common mistakes

1. **Embedding queries and passages identically** with asymmetric models → worse retrieval. Use the prefixes.
2. **Mixing vectors from two embedding models** → meaningless similarities. We store the model id.
3. **Chunks spanning pages** → citations that point to the wrong page.
4. **Unnormalized vectors with dot product** → long passages always win. We normalize and use cosine.
5. **No "I don't know" rule** → the model invents an answer from irrelevant context.
6. **Similarity search on the main thread** → jank on big documents. `withContext(Default)`.
7. **Saving the document row before its chunks without a transaction** → a crash leaves a broken index.
8. **`ByteArray` in a data class** → `equals` compares references; we override `equals/hashCode`.

## 7. Try it yourself

1. **Show similarity scores** on the source chips (e.g. "p. 7 · 0.82"). Hint: `SourceChunk.score`.
2. **Search across all documents** instead of one: load chunks of every document and include the
   document name in the citation label.
3. **Hybrid search:** add a keyword score (how many question words appear in the passage) to the cosine
   score and compare answers on a technical PDF.

## 8. Interview questions

1. **Explain your RAG pipeline end to end.**
   Index: extract PDF text page by page, chunk each page into ~300-token passages with 50-token overlap,
   embed them with EmbeddingGemma on-device, store text, pages and float32 vectors in Room. Ask: embed the
   question with the query prefix, brute-force cosine against the document's chunks, take the top 4, build a
   grounded prompt with `[p. N]` labels and strict rules, stream the chat model's answer, parse citations to
   highlight source chips.

2. **Why brute force instead of a vector database?**
   For a few thousand chunks it's exact and takes milliseconds; an ANN index adds a dependency, memory and
   approximation error. I documented the threshold where I'd switch (~10k+ chunks) and the upgrade path (HNSW).

3. **How do you prevent hallucinations?**
   Grounding: only retrieved context, explicit "I don't know" sentence, low temperature, mandatory citations,
   and the UI shows the exact passages so the user can verify.

4. **How do you store embeddings in SQLite?**
   As BLOBs of little-endian float32 (4 bytes per dimension) via `ByteBuffer`, in a chunks table with a
   foreign key to documents and cascade delete; inserted in one transaction with the document row.

5. **Why two models?**
   Embedding models are trained to place similar meanings close together and are tiny and fast; generative
   models are trained to write. Using the chat model for embeddings would be slower and worse.

6. **How did you choose chunk size and top-k?**
   Small chunks give precise citations; 300 tokens hold a complete thought. Top-4 × 300 = 1,200 tokens fits a
   4k window with room for instructions and the answer.

7. **What happens if the answer isn't in the PDF?**
   The prompt requires the exact sentence "I don't know based on this document." The answer then has no
   citations, so no chip is highlighted.

8. **How did you test RAG without models?**
   A deterministic bag-of-words `FakeTextEmbedder` (texts sharing words get similar vectors) and
   `FakeLlmEngine`, with real in-memory Room. Tests check the right page ranks first, the prompt contains the
   cited context and question, fresh sessions, cascade delete, plus unit tests for cosine, byte round-trip,
   top-k ordering and citation parsing.

9. **What are RAG's limits here?**
   Tables and figures lose structure in text extraction, scanned PDFs need OCR, questions needing the whole
   document (e.g. "summarize") don't suit top-k retrieval (we have map-reduce for that), and the token estimate
   is approximate.

10. **How would you scale it?**
    HNSW index (ObjectBox/sqlite-vec), int8-quantized vectors (4× smaller), hybrid BM25 + vector search,
    re-ranking with a cross-encoder, and incremental indexing in a WorkManager job.

## 9. Check your understanding

1. Why do queries and passages get different prefixes?
2. What is stored in the `embedding` column and how big is it for 768 dimensions?
3. Why do we chunk per page for Q&A but across pages for summaries?
4. Where does the "I don't know" behaviour come from?
5. What does a highlighted source chip mean?

---

<details>
<summary>Answers</summary>

1. EmbeddingGemma was trained for asymmetric search: short questions and long passages are encoded with task-specific prefixes.
2. The vector as little-endian float32 bytes: 768 × 4 = 3,072 bytes.
3. Q&A needs exact page citations; summaries don't cite, so merging pages gives better context.
4. From the grounded prompt rule that requires that exact sentence when the context lacks the answer.
5. The answer text cited that page (`[p. N]` parsed by `Citations.pages`).
</details>
