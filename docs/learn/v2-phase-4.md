# v2 Phase 4 — Tests for the riskiest code

Fixes AUDIT **H-5**: the model download worker, the model repository and the LiteRT-LM engine had **0 %** coverage.
They are the parts where a bug costs users gigabytes of data or a crash.

## What changed

| Test | What it proves |
|---|---|
| `data/models/work/ModelDownloadWorkerTest` (9) | Success path (verify, select first chat model, clear crash records); embedding models never become the chat model; an existing selection is kept; storage check happens before any request; retry vs fail on network errors by attempt count; checksum mismatch leaves no file; unknown id; foreground info |
| `data/models/WorkManagerModelRepositoryTest` (11) | Catalog state mapping; one unique job per model (double tap); fail-fast storage check; pause keeps `.part`, cancel deletes it; delete unloads a loaded model, frees the file, falls back to another chat model, clears crash records; select rules; verify deletes damaged files; storage maths |
| `data/models/ModelSourcesTest` (4) | Which chat model and settings reach the engine; custom prompt; expected size; embedding model path only after download |
| `core/llm/src/androidTest/LiteRtLmEngineDeviceTest` (3) | On a real phone: load on GPU, stream, cancel mid-answer, reuse after cancel, stats, unload; wrong size refused before native code; CPU-only load |
| Build | `work-testing`, Robolectric, `androidx.test` for data:models tests; androidTest dependencies for core:llm; design tokens excluded from the line gate |

Coverage of app logic: **67.7 % → 78.0 %** (201 JVM tests). The gate is now 78 %.

## Concepts

**Testing a `CoroutineWorker`.** `TestListenableWorkerBuilder<MyWorker>(context)` builds a worker with test-friendly
progress and foreground updaters. A custom `WorkerFactory` passes our dependencies (in the app, Hilt's
`HiltWorkerFactory` does this). Then `worker.doWork()` is just a suspend function you call and assert on.

**A real WorkManager in tests.** `WorkManagerTestInitHelper.initializeTestWorkManager(context, config)` installs a
WorkManager backed by an in-memory database, with a **test scheduler**: work with constraints does **not** run until
a `TestDriver` says the constraints are met. That's exactly what we want for the repository: `download()` must
*enqueue* a Wi-Fi-only job, and we observe it sitting in `ENQUEUED`.

**Robolectric.** A JVM implementation of the Android framework. Context, NotificationManager, assets and
WorkManager's database run on a laptop or CI machine in milliseconds, without an emulator.

**MockWebServer.** A real HTTP server on localhost. The downloader talks to it with real sockets, real headers and
real status codes (`503` for "network error"), and nothing touches the internet.

**Instrumented tests and `Assume`.** `src/androidTest` runs *on a device* (`connectedDebugAndroidTest`). The engine
test needs a 2.6 GB model file, so it uses `assumeTrue(...)`. Without a model it is *skipped* (reported as ignored),
not failed. CI has no phone, so it never blocks CI.

**Choosing what to test where.**

| Layer | Test type | Why |
|---|---|---|
| Pure rules (`LockTimer`, `EngineMath`, `LoadGuard`) | JVM unit | Fast, exhaustive |
| Android-backed logic (worker, repository) | Robolectric | Needs Context/WorkManager, still fast |
| Native code (LiteRT-LM) | Instrumented, real phone | Only the device has the GPU and the `.so` |

## Code walkthrough

```kotlin
private fun worker(catalogModel: LlmModelInfo?, inputId: String? = catalogModel?.id, runAttempt: Int = 0) =
    TestListenableWorkerBuilder<ModelDownloadWorker>(context)
        .setInputData(inputId?.let { workDataOf(KEY_MODEL_ID to it) } ?: Data.EMPTY)
        .setRunAttemptCount(runAttempt)           // "is this the 6th try?" → retry vs failure
        .setWorkerFactory(factory(catalogModel))  // our dependencies instead of Hilt's
        .build()

@Test fun `a network error after the last attempt fails`() = runTest {
    server.enqueue(MockResponse().setResponseCode(503))
    assertEquals(failure(ErrorKind.NETWORK), worker(model(), runAttempt = 5).doWork())
}
```

```kotlin
// Repository: the job is enqueued with the right tags and waits for Wi-Fi.
repository.download(chat.id); repository.download(chat.id)          // double tap
val active = jobs(chat.id).filter { !it.state.isFinished }
assertEquals(1, active.size)                                         // unique work
assertEquals(WorkInfo.State.ENQUEUED, active.single().state)         // constraint not met
```

```kotlin
// Engine on the phone: take(3) cancels the stream after 3 pieces.
val first = engine.generate("Count from 1 to 50…", session).take(3).toList()
// …the engine must have stopped native generation and released its lock:
val answer = engine.generate("Reply with the single word: ready", again).toList().joinToString("")
```

## Trade-offs

- **Robolectric is not a real phone.** It doesn't check vendor behaviour (OEM battery killers, real notifications).
  The instrumented test and manual checks cover the native and device-specific parts.
- **Excluding design tokens from the line gate.** Colors and type scales have no branches to test. Line coverage of
  them would only measure that a file was loaded. They get screenshot tests in phase 10.
- **The engine test needs a manual run.** It can't be automated without a device farm. It's written, compiles in
  every build (`compileDebugAndroidTestKotlin`), and is listed as a manual step.

## Interview questions

1. **How do you unit-test a `CoroutineWorker` with injected dependencies?**
   Build it with `TestListenableWorkerBuilder`, provide a custom `WorkerFactory` that passes fakes and test
   doubles, call `doWork()` in `runTest`, and assert on the returned `Result` and side effects.

2. **How do you test code that enqueues WorkManager jobs?**
   Initialize a test WorkManager (`WorkManagerTestInitHelper`) with a `SynchronousExecutor`. Enqueue through the real
   code, then query `getWorkInfosForUniqueWork(...)`. Constraints stay unmet until you use the `TestDriver`, so you
   can assert on the enqueued state, tags and uniqueness.

3. **When do you choose Robolectric over an instrumented test?**
   When the code needs Android framework classes but not real hardware: Context, resources, notifications,
   WorkManager, Room. Use instrumented tests for native code, GPU, sensors, OEM behaviour and real performance.

4. **Why use MockWebServer instead of mocking OkHttp?**
   It exercises the real HTTP stack: headers, status codes, Range requests, streaming bodies, connection errors. Mocks
   of `Call`/`Response` test your assumptions about OkHttp, not your code's real behaviour.

5. **What's the difference between a skipped test and a failed one, and when do you use `Assume`?**
   A failed assumption marks the test skipped (ignored). Use it when the environment can't run the test (no model
   file, no GPU). The suite stays green and honest about what didn't run.

6. **How did you verify that cancelling a stream really stops native generation?**
   On the device: `take(3)` cancels the flow after three pieces. If `cancelProcess()` hadn't stopped the native loop,
   the engine lock would still be held and the next `generate` would hang or fail. The test asserts a full second
   answer arrives.

7. **Why test "an existing selection is kept" for a download?**
   It's a user-visible rule: downloading a second model shouldn't silently switch the user's chat model. Rules like
   this break easily in refactors and are cheap to pin with a test.

8. **Coverage went from 67.7 % to 78 %. Is that the goal?**
   No. The goal was tests for the riskiest code paths: data loss, wasted downloads, crashes. Coverage rose as a side
   effect. The gate stops it from falling back.

## To run on your phone (manual)

```
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/model.litertlm
./gradlew :core:llm:connectedDebugAndroidTest
```
