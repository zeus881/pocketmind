# Phase 5 — Settings, in-app benchmark, Room migration, Macrobenchmark & Baseline Profile

## 1. What we built

A Settings screen backed by DataStore (model, GPU/CPU, temperature, top-K, system prompt,
theme, mobile-data downloads). A Benchmark screen that cold-loads the model and runs 3 fixed
prompts, measuring load time, time to first token, decode tokens/s and peak memory, saves
each run in a new Room table (database v2, with a tested migration) and shares the results as
text. A Macrobenchmark module that measures cold startup and frame timing, and generates a
Baseline Profile that makes the app start faster. We also fixed a subtle shared-engine bug.

## 2. Big picture

```
 SettingsScreen ─▶ SettingsViewModel ─▶ SettingsDataSource (DataStore) ──▶ read before every request
                                                                         by SettingsInferenceSource
 BenchmarkScreen ─▶ BenchmarkViewModel ─▶ BenchmarkRunner (data:benchmark)
                                           │  unload → load (timed) → 3 × [newSession, generate]
                                           │  parallel: memory sampler (PSS every 250 ms)
                                           ▼
                                       LlmEngine.lastStats (TTFT, decode tok/s)
                                           ▼
                              BenchmarkRepository ─▶ BenchmarkDao ─▶ benchmark_runs (Room v2)
                                           ▼
                              BenchmarkReport.format() ─▶ Android share sheet

 :benchmark (com.android.test) ──UiAutomator drives──▶ release-like :app
     StartupBenchmark  → StartupTimingMetric / FrameTimingMetric
     BaselineProfileGenerator → baseline-prof.txt ─▶ packaged in APK ─▶ AOT-compiled at install
```

## 3. Concepts you need

### Room migrations and AutoMigration
- **What:** When a table changes, the database version goes up and a *migration* converts
  existing users' data. Room refuses to open a database whose schema doesn't match.
- **AutoMigration:** for simple changes (new table/column) Room compares `schemas/1.json` with
  `schemas/2.json` and generates the SQL. Complex changes (rename, split) need a manual `Migration`.
- **Analogy:** Renovating a house while people live in it — you must not lose their furniture.
- **Alternative:** `fallbackToDestructiveMigration()` — deletes everyone's chats. Never in production.

### TypeConverters
- SQLite stores INTEGER, REAL, TEXT, BLOB. A `@TypeConverter` maps other types (our list of
  prompt results) to one of those (JSON TEXT) and back.

### `channelFlow` vs `flow`
- `flow {}` may only emit from its own coroutine. The benchmark runs a *second* coroutine (the
  memory sampler) in parallel, so we use `channelFlow {}`, which allows `launch` inside and
  `send` from any child.

### Measuring performance honestly
- **Cold load:** unload first, then time `load()`.
- **TTFT vs decode speed:** TTFT is dominated by *prefill* (reading the prompt); decode speed is
  tokens per second after the first token. They stress different hardware paths.
- **Greedy decoding (top-K = 1, fixed seed):** every run produces the same tokens → comparable.
- **PSS memory:** includes native memory (model weights, KV cache). Java heap numbers would miss
  almost all of it.
- **Monotonic clock (`SystemClock.elapsedRealtime`)** for durations; wall clock can jump.

### Macrobenchmark
- **What:** Jetpack library that installs a release-like build, kills/restarts it and measures
  startup and frame timing from the outside (UiAutomator + system traces).
- **Why:** Debug builds are 2–10× slower; only release-like measurements mean anything.
- **Alternative:** Microbenchmark (measures small code blocks in-process) — not for startup.

### Baseline Profiles
- **What:** A text list of classes and methods used at startup/critical journeys. Android compiles
  them to machine code at install time (Ahead-Of-Time) instead of interpreting/JIT-ing them.
- **Analogy:** Pre-heating the oven for the dishes you know you'll cook first.
- **Gain:** commonly 20–40% faster cold start and fewer janky first frames.
- **`profileinstaller`:** applies the profile even when the app isn't installed by the Play Store.

### Shared mutable resource: the session-ownership bug
- The engine has ONE conversation. Chat, benchmark (and translate/summarize in phase 6) all call
  `newSession()`. The chat repository believed its conversation was still loaded after the
  benchmark had replaced it. **Fix:** `LlmEngine.sessionId` changes on every new session, load
  and unload; each caller remembers the id it created and rebuilds if it changed. This is the
  classic "optimistic ownership token" pattern.

### Settings UX details
- Sliders keep a local value while dragging and save on `onValueChangeFinished` (one disk write
  instead of dozens). Whole rows are `toggleable`/`selectable` with a `Role` → big touch targets
  and correct TalkBack announcements ("Switch, on", "Radio button, 1 of 3, selected").

## 4. Code walkthrough

### `PocketMindDatabase` v2
```kotlin
@Database(entities = [ChatEntity::class, MessageEntity::class, BenchmarkRunEntity::class],
          version = 2, exportSchema = true,
          autoMigrations = [AutoMigration(from = 1, to = 2)])
@TypeConverters(Converters::class)
```
`schemas/.../2.json` is generated and committed next to `1.json`.

### `MigrationTest` — proving existing users keep their chats
1. Read `1.json`, execute its exact `createSql`, index SQL and `setupQueries` (Room's identity
   hash) with plain `SQLiteDatabase` → a genuine version-1 file.
2. Insert a chat + message. 3. Open with today's Room DB → AutoMigration runs and Room validates
every table. 4. Assert the old chat is still there and `benchmark_runs` works.
(Room's `MigrationTestHelper` failed on Windows + Robolectric file paths; the manual version
also teaches what a migration really is.)

### `BenchmarkRunner.run()`
```kotlin
fun run(): Flow<BenchmarkProgress> = channelFlow {
    val settings = settingsSource.current() ?: return Failed(NO_MODEL)
    val sampler = launch(io) { while (isActive) { peak = max(peak, metrics.processMemoryMb()); delay(250) } }
    send(LoadingModel); engine.unload(); engine.load(...)           // cold load
    val results = BenchmarkPrompts.all.mapIndexed { i, prompt ->
        send(RunningPrompt(i + 1, 3, prompt.name))
        engine.newSession(SYSTEM_PROMPT, emptyList(), GREEDY)        // fresh, deterministic
        engine.generate(prompt.text).collect { pieces++ }
        BenchmarkPromptResult(ttft, decodeTps, tokens, totalMs, fromEngine)
    }
    sampler.cancel(); repository.save(result); send(Finished(result))
}
```

### `BenchmarkReport.format()`
Plain text with `Locale.US` numbers; estimated values (not from engine counters) get a `*`.

### `OfflineChatRepository` + `LlmEngine.sessionId`
```kotlin
val upToDate = session == key && sessionInSync && ownedSessionId == engine.sessionId && historyTokens <= budget
...
engine.newSession(...); ownedSessionId = engine.sessionId
```
New test: `sessionReplacedByAnotherFeatureIsRebuilt`.

### `:benchmark` module
- `StartupBenchmark`: cold start with `CompilationMode.None()` vs `Partial(BaselineProfileMode.Require)`
  (10 iterations each) and `FrameTimingMetric` while switching tabs.
- `BaselineProfileGenerator`: `BaselineProfileRule.collect { startActivityAndWait(); passOnboardingIfShown(); visitAllTabs() }`.
- `targetProjectPath = ":app"`, self-instrumenting, minSdk 28 (profile generation requirement).

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| AutoMigration 1→2 | Manual `Migration` | Pure table addition; less hand-written SQL to get wrong. Manual migrations still needed for renames. |
| Prompt results as JSON column | Child table | Never queried individually; one row per run is simpler. Trade-off: no SQL over per-prompt numbers. |
| Cold load each benchmark | Reuse loaded model | Load time is a key metric; warm load would hide it. Costs a few seconds. |
| Greedy decoding | User's settings | Reproducible output length → comparable tok/s across phones. |
| PSS sampling every 250 ms | Single reading at the end | Memory peaks during load/prefill; a final reading misses the peak. Sampling may miss sub-250 ms spikes. |
| `data:benchmark` module | Logic in the ViewModel | Keeps the repository pattern and makes the runner unit-testable. |
| `sessionId` token | Global "chat owns the engine" lock | Features stay independent; whoever finds a stale session simply rebuilds. |

## 6. Common mistakes

1. **Benchmarking a debug build** — numbers are meaningless. Macrobenchmark uses release-like builds.
2. **`fallbackToDestructiveMigration()`** to "fix" a migration crash — silently deletes user data.
3. **Forgetting to commit `schemas/*.json`** — AutoMigration and tests need the old schema.
4. **Measuring Java heap for an LLM app** — the model lives in native memory. Use PSS.
5. **Using wall-clock time for durations** — it jumps with NTP/timezone changes.
6. **Mixing test schedulers** — `UnconfinedTestDispatcher()` without `testScheduler` inside
   `runTest` → "Detected use of different schedulers". We pass the test's scheduler.
7. **Shared mutable state without ownership** — the chat/benchmark session bug above.

## 7. Try it yourself

1. **Add a "warm" benchmark** that skips the unload and compare load times. Hint: add a
   `cold: Boolean` parameter to `BenchmarkRunner.run()`.
2. **Export CSV** of all runs (one line per prompt) via the share sheet. Hint: build it in
   `BenchmarkReport`, test it like `BenchmarkReportTest`.
3. **Run the real Macrobenchmark** on your phone (see MANUAL STEPS) and paste the startup median
   with and without the Baseline Profile into `docs/benchmarks.md`.

## 8. Interview questions

1. **How do you measure on-device LLM performance?**
   Cold-load the model and time it; then for 3 fixed prompts with greedy decoding I record
   time-to-first-token and decode tokens/s from the runtime's own counters (falling back to
   streamed-piece estimates, flagged), and sample process PSS every 250 ms for peak memory. Results
   are stored in Room and shareable, so I can build a cross-device table.

2. **Why is TTFT different from tokens per second?**
   TTFT is dominated by prefill — processing the whole prompt, which is parallel and compute-bound.
   Decode generates one token at a time and is memory-bandwidth-bound. Long prompts hurt TTFT;
   decode speed barely changes.

3. **How do you handle database schema changes?**
   Bump the version, export schemas, use AutoMigration for additive changes or a manual Migration
   otherwise, and test it: I build a real v1 database from the committed schema JSON, insert data,
   open it with the new version and assert data survived and Room's validation passed.

4. **What is a Baseline Profile and how did you generate it?**
   A list of hot classes/methods compiled ahead of time at install. A Macrobenchmark
   `BaselineProfileRule` drives startup and tab navigation on a real device; the profile is
   packaged with the APK and applied by ProfileInstaller.

5. **How do you prove the profile helps?**
   `StartupBenchmark` measures cold startup with `CompilationMode.None` vs
   `Partial(BaselineProfileMode.Require)` over 10 iterations each and compares medians.

6. **Why `channelFlow` in the runner?**
   A memory sampler coroutine runs in parallel with the measurements. A plain `flow` can't launch
   children that emit; `channelFlow` provides a `ProducerScope` with structured concurrency, so
   cancelling the benchmark also cancels the sampler.

7. **Describe a concurrency bug you found.**
   Several features share one native conversation. After the benchmark replaced it, chat thought its
   session was still valid and would answer without the chat history. I added a session id that
   changes on every new session, load or unload; each owner checks it and rebuilds if stale. A
   regression test reproduces the scenario.

8. **Why save slider values only when the finger lifts?**
   Each DataStore write is a disk transaction; dragging would cause dozens. The UI keeps a local
   state while dragging and writes once in `onValueChangeFinished`.

9. **What does PSS measure and why use it?**
   Proportional set size: private pages plus a proportional share of shared pages, including native
   allocations. LiteRT-LM's weights and KV cache are native, so PSS is the honest footprint.

10. **How do you make benchmark results trustworthy?**
    Fixed prompts, greedy decoding, cold load, release build for startup, multiple iterations, exact
    engine counters when available, estimates explicitly marked, and the device/OS/backend recorded.

## 9. Check your understanding

1. What does Room do if the schema on disk doesn't match the code and no migration exists?
2. Why must the benchmark unload the model before loading it?
3. What problem does `LlmEngine.sessionId` solve?
4. Why does Baseline Profile generation need a physical device (or emulator) and not a unit test?
5. Why `Locale.US` in the shared report?

---

<details>
<summary>Answers</summary>

1. It throws an exception when opening the database (unless destructive fallback is enabled, which deletes data).
2. Otherwise load is a no-op and the measured "load time" would be zero / meaningless.
3. Detecting that another feature replaced the shared native conversation, so chat rebuilds its history.
4. The profile records which code the real app executes on the Android runtime during real journeys.
5. So numbers read the same everywhere ("52.1" not "52,1") when pasted into READMEs or issues.
</details>
