# v2 Phase 7 — Smarter context: model-written summaries and fewer rebuilds

Addresses the plan's "smart context management" item and part of AUDIT **M-5** (context handling in long chats).

## What changed

| Where | Change |
|---|---|
| `core/database` | Schema **v4**: table `conversation_summaries` (chatId, summary, coveredTurns, updatedAt), AutoMigration 3→4, exported `4.json`; DAO `summary()` / `upsertSummary()` |
| `data/chat/ConversationSummarizer.kt` (new) | Interface + `LlmConversationSummarizer` (asks the model, precise sampling, falls back to null) + `ChatSummaryPrompt` |
| `data/chat/OfflineChatRepository.kt` | Cached summaries extended incrementally; 60 % headroom after a rebuild; tracks what the live session holds (`sessionTokens`) instead of rebuilding on every message |
| Tests | `ConversationSummarizerTest` (5), 4 new `OfflineChatRepositoryTest` cases, `MigrationTest` from v3 (+ v4 table check on every path) |

Coverage of app logic: **80.1 %** (213 tests): the plan's 80 % target, now enforced by the gate.

**Not changed (on purpose):** the context window stays 4,096 tokens on every phone. Raising it to 8,192 on
large-RAM phones needs a measured memory peak first (budget rule: no unmeasured numbers). That's listed as a manual
measurement.

## Why

Two problems in long chats:
1. **What the model forgot.** v1 replaced dropped turns with the first sentence of each. The useful part of an answer
   (a number, a name, a code detail) is rarely in its first sentence.
2. **Hidden cost on every message.** Once a chat was longer than the window, the condition "the whole history fits"
   was false on *every* message, so the native conversation was rebuilt and the whole kept history re-read
   ("prefill") each time.

## Concepts

**Context window.** The number of tokens the model can consider at once: system prompt + history + question +
answer. Ours is 4,096, with 1,536 reserved for the answer.

**Prefill vs decode.** Before writing the first token, the model processes all input tokens (prefill). Re-creating a
conversation means prefilling the whole history again. Fast on GPU, but not free. Avoiding needless rebuilds saves
that time on every message.

**Summarization memory (rolling summary).** Keep the recent turns verbatim, and replace older turns with a summary
that the model writes. When more turns fall out, give the model the *previous summary plus only the new turns*.
That's incremental and cheap, and old turns are never re-read.

**Caching expensive results.** A summary takes seconds to generate, so it's stored in Room per chat with
`coveredTurns`, the number of oldest turns it already covers. Cache hit: same count. Extend: more turns dropped.
Rebuild from scratch: the chat got shorter (an answer was regenerated).

**Headroom (hysteresis).** If you trim to exactly 100 % of the budget, the next message overflows again: rebuild,
summarize, rebuild… Trimming to 60 % leaves room for several messages before the next rebuild. Thermostats use the
same idea.

**Graceful fallback.** If the summary fails (native error, empty output), the cheap extractive note is used, so the
chat never breaks because of a summary.

## Code walkthrough

```kotlin
// prepareSessionLocked (simplified)
val upToDate = session == key && sessionInSync && ownedSessionId == engine.sessionId &&
    sessionTokens <= budget                       // v1: "whole history <= budget" → false forever in long chats
if (upToDate) return
if (historyTokens <= budget) { kept = turns; note = null }
else {
    val trimmed = HistoryTrimmer.trim(turns, budget * 60 / 100)          // headroom
    kept = trimmed.turns
    note = earlierContextNote(chatId, turns.take(turns.size - kept.size), trimmed.summaryNote)
}
ownedSessionId = engine.newSession(systemPrompt + note, kept, settings.sampling)
sessionTokens = tokens(kept) + tokens(note)
// …and saveAssistantMessage(COMPLETE): sessionTokens += question + answer
```

```kotlin
// earlierContextNote: cache, extend, or fall back
val cached = chatDao.summary(chatId)
val summary = if (cached?.coveredTurns == dropped.size) cached.summary
else {
    val extendFrom = cached?.takeIf { it.coveredTurns < dropped.size }
    summarizer.summarize(extendFrom?.summary, dropped.drop(extendFrom?.coveredTurns ?: 0))
        ?.also { chatDao.upsertSummary(ConversationSummaryEntity(chatId, it, dropped.size, now)) }
}
return summary?.let { "$SUMMARY_HEADER\n$it" } ?: fallback
```

```kotlin
// The summary prompt keeps the NEWEST lines when the transcript is too long and clips each
// message, so the summarizer's own prompt can't overflow the context window.
ChatSummaryPrompt.build(previousSummary, turns)
```

## Trade-offs

- **Latency at overflow.** The first message after an overflow waits a few seconds longer while the summary is written
  (the "thinking" bubble shows). After that, with headroom and caching, there's no extra cost for several messages.
- **Summaries can be wrong.** A small model may drop or distort details. The prompt asks to keep names, numbers, code
  and decisions and to add nothing. Low temperature reduces invention. Recent turns are always verbatim.
- **Token counts are estimates** (about 4 characters per token, rounded up). That's why the budget keeps a reserve,
  and the session is rebuilt when the estimate says it's full.
- **Edits and branches (phase 12)** will change earlier turns. Summaries must be invalidated then; noted for that
  phase.

## Interview questions

1. **How do you keep a long conversation within a small context window?**
   Keep the newest turns verbatim within a token budget, and replace older turns with a summary (rolling,
   incrementally updated). Reserve tokens for the answer. Rebuild with headroom so you don't trim on every message.

2. **What's the difference between extractive and abstractive summaries, and when would you use each?**
   Extractive copies parts of the text (cheap, deterministic, may miss the point). Abstractive is written by a model
   (better quality, costs inference, can be wrong). Use abstractive with caching, and extractive as a fallback.

3. **Why cache the summary, and what's the cache key?**
   Generation costs seconds. The key is the chat plus the number of oldest turns covered. Equal count = reuse. More
   turns = extend with only the new ones. Fewer (history shrank) = regenerate.

4. **What is prefill, and why does rebuilding a session cost time?**
   Prefill processes all input tokens to build the KV cache before generating. Recreating a conversation re-runs
   prefill over the whole kept history; doing it on every message wastes time and battery.

5. **What's hysteresis, and where did you use it?**
   Using different thresholds for entering and leaving a state, to avoid oscillation. Trimming to 60 % of the budget
   after overflowing at 100 % prevents a rebuild on every subsequent message.

6. **Why does the summarizer use a separate session with low temperature?**
   The summary should be faithful, not creative, and it must not pollute the chat's conversation state. The chat
   session is rebuilt right after with the summary in the system prompt.

7. **How did you make the feature safe to fail?**
   Any summarizer exception (except cancellation) returns null, and the extractive note is used. A failed summary
   isn't cached, so it's retried at the next rebuild.

8. **How do you test logic that depends on an LLM's output?**
   Put the LLM behind an interface. Test the repository with a deterministic fake (records calls, returns fixed
   text), test the real summarizer against a fake engine (prompt content, session, sampling, failure paths), and
   check output quality manually on the device.

## To verify on device

Have a 20+ message chat about one topic with specific numbers early on, then ask about an early detail: the
answer should use it (it comes from the summary).
