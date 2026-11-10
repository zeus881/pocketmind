# V3 Phase 2b — The on-device AI logic, shared with iOS

## What we built

- **`shared:tools`** — the calculator, date & time, unit converter and notes tools, now Kotlin Multiplatform.
  `java.math.BigDecimal` became the KMP library **bignum** 0.3.10, `java.time` became **kotlinx-datetime** 0.8.0,
  `Math.*` became `kotlin.math`. The 12 existing tests pass with the *same expected strings* (0.1 + 0.2 = 0.3,
  123.4 × 56 = 6910.4, 15 % = 0.15, Tuesday 14:05 in Kolkata, …) and now also run on the iOS simulator.
- **`shared:ai`** — everything about on-device AI that isn't the engine itself: the `LlmEngine` contract and
  `EngineState`, sampling settings, context-window maths (`EngineMath`, `TokenEstimator`, `HistoryTrimmer`),
  `LlmTask`, the summarizer and translator with their prompt templates, the PDF `TextChunker`, and the local RAG
  pieces (`VectorMath`, `VectorStore`, `GroundedPrompt`).
- `AppResult`/`ErrorKind` and the Flow helpers (`throttleLatest`) moved to `shared:core`.
- Still Android-only, by design: the LiteRT-LM engine and embedder, the crash-loop guard, model file paths, PdfBox
  extraction and the document repository. iOS gets its own engine in phase 2e.

## Big picture

```
              shared:ai (commonMain)                         platform engines
   LlmEngine ◄──────── interface ────────────────────── LiteRtLmEngine (core:llm, Android, LiteRT-LM Kotlin)
   LlmTask ─ Summarizer ─ Translator                     iOS engine bridge (phase 2e, LiteRT-LM Swift)
   TextChunker ─ VectorMath ─ VectorStore ─ GroundedPrompt
   HistoryTrimmer ─ TokenEstimator ─ EngineMath
        │
   shared:tools (calculator, dates, units, notes)   shared:data (ChunkEntity, NoteDao…)   shared:core (models, AppResult)
```

## Concepts

### Replacing JVM-only libraries
- **What:** `java.*` exists only on the JVM. Kotlin/Native (iOS) needs pure-Kotlin or multiplatform libraries.
- **How we chose:** the replacement must give the *same results* — so the existing tests are the contract.
  bignum keeps exact decimal arithmetic (a significand and an exponent, explicit precision per division:
  `DecimalMode(34, ROUND_HALF_TO_EVEN)` = Java's `MathContext.DECIMAL128`). kotlinx-datetime is JetBrains' own
  date library. Both APIs were read from the real artifacts (`javap` on bignum's JVM jar; its checksum matched
  Maven Central's).
- **Alternative:** `expect`/`actual` with `java.math` on Android and `NSDecimalNumber` on iOS — two
  implementations to keep identical, which is exactly what tests are bad at catching.

### Binary formats are contracts
v2 stored each document embedding as a BLOB: 4 bytes per float, little-endian, written with `java.nio.ByteBuffer`.
iOS has no `java.nio`, so the code now uses `Float.toRawBits()` and shifts. A format that is already on users'
phones must not change by a single byte: `VectorMathTest` pins known bytes (1.0f = `00 00 80 3F`) on every
platform, and `VectorMathNioTest` (JVM only) runs v2's exact ByteBuffer code side by side on 20 random 768-float
vectors.

### Host tests for KMP Android modules
A KMP module with an Android target runs `commonTest` on the JVM only when the target has host tests
(`withHostTest {}`); the task is `testAndroidHostTest`. Without it, the common tests of `shared:ai` would only run
on the iOS simulator — no local runs, no Linux CI, no coverage.

### kotlin.test is not JUnit
Converting tests showed three real differences: the message is the *last* argument (`assertTrue(cond, "msg")`),
`assertTrue`/`assertNotNull` smart-cast (so old casts and `!!` become "redundant" warnings — errors here), and
there is no `@Test(expected = …)`: use `assertFailsWith<T> { … }`.

## Code walkthrough: "What is 15% of 240?" on an iPhone

1. The model calls `calculator` with `{"expression": "15% * 240"}`; `ToolRunner` parses the JSON.
2. `Expression.evaluate` → `Parser.sum()` → `product()` → `postfix()` reads `15`, sees `%` and calls
   `moveDecimalPoint(PERCENT_SHIFT)` (−2): 0.15.
3. `* 240` → `BigDecimal.multiply` → 36.00 (exact).
4. `Expression.format` → `roundSignificand(DecimalMode(15, ROUND_HALF_TO_EVEN))` → `toPlainString()` → `"36"`.
   The same bytes of Kotlin run on Android (JVM) and iOS (Kotlin/Native).

## Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| bignum instead of expect/actual decimals | one implementation, same tests everywhere | a third-party dependency (Apache-2.0, small) |
| `shared:ai` as a KMP *Android* module (not pure JVM) | it uses `ChunkEntity` from `shared:data`, which has an Android target | Android host tests instead of plain JVM tests |
| Keep `RagTest` and `TasksTest` on the Android side | they need Room/Robolectric and v2's `FakeLlmEngine` from core:testing | those tests don't run on iOS yet |
| LiteRT-LM stays at 0.19.0 | 0.19.0 is verified on the owner's phone; 0.19.1 must be checked on a device first | a newer release not yet used |
| Providers in modules E2E tests don't replace (`LlmTaskModule`, `DocumentTasksModule`) | E2E tests swap the engine, keep the real logic | two more tiny Hilt modules |

## Common mistakes

- **Converting test imports and nothing else.** JUnit's `assertTrue("message", condition)` compiles to nonsense or
  fails to compile in kotlin.test; read every assertion.
- **Leaving `internal` on code that moved modules.** `EngineMath` was internal to `core:llm`; after the move the
  Android engine couldn't see it. Visibility is per module.
- **Trusting a filtered "latest version".** The newest kotlinx-datetime tag was a `-0.6.x-compat` build; the real
  release line is 0.8.0.

## Try it yourself

1. Add `"cbrt"` (cube root) to the calculator's `FUNCTIONS` with `kotlin.math.cbrt`, and a test in commonTest.
2. Break `VectorMath.toBytes` by writing big-endian and run `:shared:ai:testAndroidHostTest`. Which test catches it?
3. Change `SHOWN_DIGITS` to 6 and predict what `1/3` prints before running the test.

## Interview questions

1. **How do you port JVM-only code to Kotlin Multiplatform safely?** Find every platform API, choose multiplatform
   replacements that preserve behaviour, and keep the existing tests unchanged as the contract; run them on every target.
2. **Why is floating-point wrong for a calculator tool?** Binary floats can't represent 0.1 exactly
   (0.1 + 0.2 = 0.30000000000000004); decimal arithmetic with explicit precision gives exact results for decimal input.
3. **What is half-even rounding and why use it?** Ties round to the even neighbour (2.5 → 2, 3.5 → 4); it avoids
   the upward bias of half-up over many operations. It's Java's DECIMAL128 default.
4. **How do you change code that writes a persisted binary format?** Treat the format as a contract: golden
   bytes in tests, and a side-by-side test against the old implementation before replacing it.
5. **What are KMP host tests?** JVM tests of a KMP module's Android target (`testAndroidHostTest`), running
   `commonTest` + `androidHostTest` without a device.
6. **Why keep the engine out of shared code?** It wraps native, platform-specific SDKs (LiteRT-LM Kotlin vs Swift);
   the shared layer depends only on the `LlmEngine` interface, so logic is testable with a fake.
7. **What changes when moving from JUnit to kotlin.test?** Message argument order, smart-casting assertions, no
   expected-exception attribute (`assertFailsWith`), `@BeforeTest`/`@AfterTest`.
8. **Why must visibility be revisited after a module split?** `internal` is per module; moved helpers used by the
   old module must become public (or the user must move too).

## Self-check

1. Which library replaced `java.time`, and which version?
2. What are the 4 bytes of 1.0f in PocketMind's embedding format?
3. Which Gradle task runs `shared:ai`'s common tests on the JVM?
4. Why does `shared:ai` depend on `shared:data`?
5. Name one test that still runs only on Android, and why.
