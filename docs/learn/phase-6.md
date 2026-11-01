# Phase 6 — PDF summarize (map-reduce) and Translate

## 1. What we built

The Documents tab lets the user pick a PDF with the system file picker (no storage
permission). PdfBox-Android extracts text page by page; scanned PDFs are detected. A
token-aware chunker splits long documents with 10% overlap, and a map-reduce summarizer turns
a 30-page PDF into one summary while showing "Summarizing part 4 of 12" with a Cancel button.
The Translate tab translates between 21 languages with a strict prompt template, streaming output,
swap, stop and copy — every request in a fresh model session.

## 2. Big picture

```
 DocumentsScreen ── OpenDocument("application/pdf") ──▶ content:// Uri (one file, no permission)
        │
 SummarizeViewModel ─▶ PdfTextExtractor (PdfBox, IO thread) ─▶ [PageText(1..N)]
        │
        └─▶ Summarizer ─▶ TextChunker ─▶ [chunk1 … chunkK]  (≈1,500 tokens, 10% overlap)
                 │ MAP:    LlmTask.complete(mapPrompt(chunk i))      × K   (fresh session each)
                 │ REDUCE: group summaries by budget → reducePrompt   × rounds
                 │ FINAL:  reducePrompt(isFinal = true)
                 ▼
            Flow<SummaryProgress>: Preparing → Mapping(i, K) → Reducing(r) → Done(summary)

 TranslateScreen ─▶ TranslateViewModel ─▶ Translator ─▶ split into parts ─▶ LlmTask.stream(translatePrompt)
                                                                 ▼
                                    output streamed (runningFold + throttleLatest 50 ms)

 LlmTask (core:llm) = ensureLoaded() + fresh newSession() + generate()  → changes engine.sessionId
```

## 3. Concepts you need

### Storage Access Framework (SAF)
- **What:** The system file picker. `ActivityResultContracts.OpenDocument()` returns a
  `content://` Uri for the single file the user chose; the app reads it through `ContentResolver`.
- **Why:** No `READ_EXTERNAL_STORAGE` permission (Google Play restricts broad storage access), and
  privacy: the app can't see any other file.
- **Alternative:** Storage permissions + file paths — deprecated, intrusive, rejected by Play policy.

### Context window and chunking
- **Context window:** the maximum tokens the model sees at once (prompt + answer). Ours is 4,096.
  A 30-page PDF is ~15,000 tokens — it simply doesn't fit.
- **Chunking:** cut text into pieces that fit; **overlap** repeats a little text across boundaries
  so a sentence split in two still appears whole in one chunk.
- **Token-aware:** we size chunks in (estimated) tokens, not characters, because the model's limit
  is in tokens.

### Map-reduce summarization
- **Map:** summarize each chunk independently. **Reduce:** combine the partial summaries; if they
  are still too long, combine in groups and repeat (a tree reduction).
- **Analogy:** 12 people each summarize one chapter; an editor merges their notes; if the notes are
  too long, sub-editors merge them first.
- **Alternatives:** "refine" (sequentially update one running summary — order-dependent and slower to
  recover from a bad step), "stuff" (whole document in one prompt — impossible here).

### Prompt templates for small models
- Short, explicit rules ("output only the translation, keep names and numbers unchanged") work far
  better than open-ended requests. Templates are pure functions → unit-testable.
- Pitfall we hit: `"""...$text...""".trimIndent()` doesn't trim when the inserted text has no
  indentation. We build prompts by joining lines; a regression test checks no line is indented.

### Fresh session per task
- `LlmTask.stream()` always calls `newSession()` first: no chat history leaks into a translation or a
  summary chunk, and each call has the full context window. It also bumps `engine.sessionId`, so chat
  knows to rebuild its conversation afterwards (phase 5 ownership token).

### Cancellation through layers
- The whole pipeline (extract → map → reduce) is one coroutine `Job` in the ViewModel. `job.cancel()`
  cancels the current `collect`, which cancels the engine's flow, which calls `cancelProcess()` on
  the native conversation. PDF extraction checks `ensureActive()` between pages.

### PdfBox memory settings
- `MemoryUsageSetting.setupTempFileOnly()` buffers the PDF in a temp file, not RAM — important when a
  2.6 GB model is already in memory.

## 4. Code walkthrough

### `TextChunker.chunk(pages, maxTokens, overlapTokens)`
```kotlin
val units = pages.flatMap { toUnits(it, maxTokens) }   // sentences, each tagged with its page
for (unit in units) {
    if (currentTokens + unit.tokens > maxTokens && current.isNotEmpty()) {
        chunks += build(chunks.size, current)          // startPage/endPage from first/last unit
        current = overlapTail(current, overlapTokens)  // last sentences ≈ overlap
        currentTokens = current.sumOf { it.tokens }
    }
    current += unit; currentTokens += unit.tokens
}
```
Sentences are never cut unless one sentence alone exceeds the limit (then split by words). Page
numbers travel with each unit → every chunk knows its page range (used for citations in phase 7).

### `Summarizer.summarize(pages)`
```kotlin
emit(Preparing); task.ensureLoaded() ?: emit(Failed(...))
val budget = chunkBudget(contextWindow)            // min(1500, window − 1024 answer − 200 prompt)
val chunks = TextChunker.chunk(pages, budget, budget / 10)
if (chunks.size == 1) → one-pass summary
var summaries = chunks.mapIndexed { i, c -> emit(Mapping(i+1, n)); ask(mapPrompt(c)) }
while (tooLong(summaries, budget) && round <= 5) { emit(Reducing(round++)); summaries = groupByBudget(...).map { ask(reducePrompt(it, false)) } }
emit(Done(ask(reducePrompt(summaries, isFinal = true)), n))
```

### `PdfBoxTextExtractor.extract(uri)`
`PDFBoxResourceLoader.init` once → `openInputStream(uri)` → `PDDocument.load(input, tempFileOnly)`
→ for each page: `stripper.startPage = p; stripper.endPage = p; getText()` → if every page is
blank → `NO_TEXT_IN_DOCUMENT` (scanned); `IOException` (corrupt / password) → `FILE_UNREADABLE`.

### `Translator.translate(text, from, to)`
Splits long input at blank lines into parts ≤ min(800, window/3) tokens (no overlap — overlapping
text would be translated twice), then for each part streams
`task.stream(TRANSLATE_SYSTEM, translatePrompt(part, from.englishName, to.englishName))`.

### `TranslateViewModel`
Languages saved in `SavedStateHandle` (survive process death). `swap()` exchanges languages AND moves
the translation into the input. Output: `runningFold("") { acc, p -> acc + p }.throttleLatest(50)`.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| SAF picker | Storage permission | Zero permissions, Play-policy compliant, privacy. |
| Token estimate (chars/4) | Real tokenizer | LiteRT-LM doesn't expose the tokenizer; we round up and keep a reserve. Risk: some chunks slightly larger than estimated → handled by the 1,024-token reserve. |
| ~1,500-token chunks | Bigger chunks | Small models summarize short inputs much better; also leaves room in a 4k window. Cost: more LLM calls. |
| 10% overlap | No overlap | Avoids losing sentences on boundaries; costs ~10% extra compute. |
| Map-reduce | Refine | Each chunk independent (simple progress, no error propagation); refine needs sequential context. |
| Extractive greedy decoding? No — `SamplingParams.Precise` (temp 0.2) | Chat settings | Faithfulness over creativity for summaries/translation. |
| One VM Job for the pipeline | Separate jobs | One `cancel()` stops everything consistently. |
| PDF text in memory only (phase 6) | Store in Room | Summaries are one-shot; phase 7 stores chunks for Q&A. |

## 6. Common mistakes

1. **Pasting the whole PDF into one prompt** → context overflow / truncated, garbage output.
2. **Character-based chunking** → chunk sizes vary wildly in tokens (especially non-Latin scripts).
3. **No overlap** → facts split across chunk boundaries disappear from summaries.
4. **Parsing PDFs on the main thread** → ANR. We use the IO dispatcher.
5. **Loading the whole PDF into RAM** next to a 2.6 GB model → OOM. Temp-file buffering.
6. **Reusing the chat session for translation** → the model "continues the chat" instead of translating.
7. **`trimIndent()` with interpolated multi-line text** → prompts silently keep their indentation.
8. **Not handling scanned PDFs** → an empty summary with no explanation. We detect and explain.

## 7. Try it yourself

1. **Summary length option.** Add "Short / Detailed" chips that change the final prompt (3 bullets vs
   10). Hint: add a parameter to `PromptTemplates.reducePrompt` and a test for both variants.
2. **Page range.** Let the user summarize only pages 5–12. Hint: filter `doc.pages` before
   `summarize()`; show the range in the result.
3. **Detect source language automatically.** Add "Auto" to the From picker and change the prompt to
   "Translate the text to $to". Compare quality on Hindi ↔ English.

## 8. Interview questions

1. **How do you summarize a document larger than the model's context window?**
   Map-reduce: token-aware chunking (~1,500 tokens, 10% overlap), summarize each chunk to 3–5 bullets in
   a fresh session, then combine; if the combined bullets still exceed the budget, reduce in groups
   repeatedly, then produce a final overview. Progress is a Flow; cancel stops the native generation.

2. **Why overlap chunks?**
   A key sentence on a boundary would otherwise be split and lost. Overlap repeats the last sentences of
   the previous chunk so every sentence appears complete in at least one chunk; the cost is ~10% more tokens.

3. **How do you count tokens without the model's tokenizer?**
   A conservative estimate: ~4 chars per token for ASCII, ~2 for other scripts, rounded up, plus a
   1,024-token reserve for the answer. Not exact, but errs on the safe side.

4. **How do you read PDFs without storage permissions?**
   The Storage Access Framework picker returns a content Uri to exactly the chosen file; I open it via
   ContentResolver. No permission, nothing else visible to the app.

5. **How do you handle scanned PDFs?**
   If every page's extracted text is blank, the PDF has no text layer; I show "No text found". OCR with
   an on-device model would be the v2 solution.

6. **Why a fresh LLM session per translation?**
   So no chat history influences the result and the full context window is available; it also keeps
   translations deterministic and independent.

7. **How does cancel work across extraction, map and reduce?**
   It's all one coroutine job; cancelling it cancels the current flow collection, which reaches the
   engine and calls `cancelProcess()`. Extraction checks `ensureActive()` between pages.

8. **How did you test the summarizer without a model?**
   `FakeLlmEngine` returns a fixed bullet; tests assert the number of map calls equals the number of
   chunks, progress parts are sequential, a reduce step happens, and every call used a fresh session
   with empty history. The chunker has its own tests for limits, overlap, page ranges and no text loss.

9. **What makes prompts work with small models?**
   Short imperative rules, explicit output format, explicit prohibitions ("no introduction", "do not
   add facts"), and low temperature. Templates are pure functions with unit tests.

10. **What would you improve?**
    Use the real tokenizer if exposed, parallel map on devices with enough memory (not possible with one
    engine instance), caching summaries in Room, and OCR for scanned documents.

## 9. Check your understanding

1. Why doesn't the app need a storage permission to read PDFs?
2. What do MAP and REDUCE do in our summarizer?
3. Why does the translator split text without overlap, but the summarizer with overlap?
4. What happens on the native side when the user taps Cancel during summarizing?
5. What was the `trimIndent()` bug?

---

<details>
<summary>Answers</summary>

1. The system picker (SAF) grants access to the single chosen file through a content Uri.
2. MAP summarizes each chunk separately; REDUCE combines the partial summaries (in rounds if needed) into one.
3. Overlapping text would be translated twice and appear duplicated; for summaries duplication is harmless and overlap prevents losing ideas.
4. The coroutine is cancelled → the engine flow catches the cancellation and calls `conversation.cancelProcess()`.
5. With interpolated text that has no indentation, the common indent is 0, so nothing is trimmed and instruction lines keep leading spaces.
</details>
