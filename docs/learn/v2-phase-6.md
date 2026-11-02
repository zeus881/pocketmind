# v2 Phase 6 — StrictMode, LeakCanary, ANR safety, measuring performance

Addresses AUDIT **M-2** (Baseline Profile never generated; generation is a device step), **M-10** (no StrictMode or
LeakCanary), and the plan's ANR-safety and performance-budget items.

## What changed

| Where | Change |
|---|---|
| `app/StrictModePolicies.kt` (new) | Debug-only thread and VM policies, logging violations |
| `app/PocketMindApp.kt` | Enables StrictMode in debug builds |
| `app/build.gradle.kts`, version catalog | LeakCanary **2.14** (latest stable; 3.0 is still alpha) as `debugImplementation` |
| `app/ui/LicensesScreen.kt` | Licence JSON read and parsed on `Dispatchers.IO` (was on the main thread during composition) |
| `data/models/WorkManagerModelRepository.kt` | Model list built on the IO dispatcher (`flowOn(io)`): file checks and the catalog asset read left the main thread |
| `benchmark/ChatStreamingBenchmark.kt` (new) | Frame timing while an answer streams (needs a model on the phone) |
| `benchmark/Journeys.kt` | `sendPromptAndWaitForAnswer()` journey (waits for "Answer ready") |
| `docs/performance-budget.md` | Startup budget 500 ms; all numbers still measured-only |

## Concepts

**ANR.** If the main thread is blocked for about 5 seconds while handling input, Android shows "App isn't
responding". Even 16 ms blocks drop a frame (jank). Disk and network I/O on the main thread are the classic causes:
usually fast, sometimes very slow (busy storage, a cold cache, a slow SD card).

**StrictMode.** A developer tool built into Android. The *thread policy* watches the main thread for disk, network
and slow calls. The *VM policy* watches the whole process for leaked `Closeable`s, cursors, SQLite objects,
activities and receivers, for `file://` URIs given to other apps, and for cleartext HTTP. We only *log* violations:
crashing would also fire on violations inside libraries we can't change.

**LeakCanary.** A debug-only library that watches objects that should be garbage soon (destroyed Activities,
Fragments, ViewModels). If one survives 5 s and two GCs, it dumps the heap, finds the shortest reference chain that
keeps it alive, and shows a notification ("MainActivity leaked via LockTimer…"). `debugImplementation` guarantees it
can never reach a release build.

**Moving work off the main thread in Compose.** `remember { readFile() }` runs on the main thread during
composition. `produceState(initial) { value = withContext(Dispatchers.IO) { readFile() } }` runs it in a coroutine on
IO and recomposes when the value arrives. Show a loading state meanwhile.

**`flowOn` for repository flows.** A `combine { … }` lambda runs in the collector's context, which is the
ViewModel's main-thread scope. `flowOn(io)` moves everything *upstream* of it (including the lambda) to the IO
dispatcher.

**Macrobenchmark metrics.** `StartupTimingMetric` measures time to the first frame (cold, warm, hot).
`FrameTimingMetric` reports frame durations (P50/P90/P95/P99) and frame overrun during a journey. Run release-like
builds (`benchmarkRelease`), compiled the way users get them (`CompilationMode.Partial(BaselineProfileMode.Require)`).

**Baseline Profiles.** A list of classes and methods used in startup and key journeys, generated on a device by
`BaselineProfileGenerator`. Play (or ProfileInstaller) uses it to AOT-compile those paths at install, so the first
launches don't run interpreted or JIT code. It's typically 20–40 % faster for the first run (Google's figures). Ours
must be generated on your phone; that's a manual step.

## Code walkthrough

```kotlin
// PocketMindApp.onCreate
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
    StrictModePolicies.enable()          // R8 removes this branch entirely in release
}
```

```kotlin
// LicensesScreen: from main-thread read to produceState + IO
val libs by produceState<Libs?>(initialValue = null) {
    value = withContext(Dispatchers.IO) {
        val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        Libs.Builder().withJson(json).build()
    }
}
if (libs == null) LoadingState() else LibrariesContainer(libs, Modifier.fillMaxSize())
```

```kotlin
// ChatStreamingBenchmark: frames while a real answer streams
rule.measureRepeated(packageName = TARGET_PACKAGE, metrics = listOf(FrameTimingMetric()),
    compilationMode = CompilationMode.Partial(BaselineProfileMode.Require), iterations = 3,
    setupBlock = { pressHome(); startActivityAndWait(); passOnboardingIfShown() },
) { sendPromptAndWaitForAnswer("Write a short list of five healthy breakfast ideas…") }
```

## ANR audit (what was checked)

- Searched main source sets for `runBlocking`, `Thread.sleep`, blocking `.get()`, `readText`, `execute()`,
  SharedPreferences. Results: OkHttp `execute()` runs inside the worker on IO ✓; `LoadGuard` reads its marker inside
  the engine on IO ✓; the licence JSON and the model list were on the main thread → fixed.
- Engine calls (`initialize`, generation, close) run on the injected IO dispatcher under a Mutex ✓.
- Room DAOs are `suspend` or `Flow` ✓; DataStore is asynchronous ✓.

## Trade-offs

- **Log-only StrictMode** can be ignored. The alternative (crash on violation) blocks development whenever a library
  misbehaves. We review the log during the on-device checks.
- **LeakCanary in debug** adds a launcher icon ("Leaks") and heap dumps that pause the debug app for a few seconds.
  That's acceptable for development, and never in release.
- **Benchmarks need the phone and a model.** They are written and compiled in every build, but the numbers can only
  come from a device run (manual step). The budget table says "Not measured yet" until then.

## Interview questions

1. **What causes an ANR, and how do you prevent one?**
   The main thread blocked for about 5 s (input) or a BroadcastReceiver/Service exceeding its limit. Prevent it by
   keeping disk, network, heavy CPU and locks off the main thread (coroutines with IO/Default dispatchers), and find
   offenders with StrictMode and Android vitals.

2. **What does StrictMode's thread policy detect vs its VM policy?**
   Thread policy: per-thread operations (disk read/write, network, custom slow calls, unbuffered I/O). VM policy:
   process-wide resource problems (leaked closeables, SQLite objects, activities, registrations, file URI exposure,
   cleartext traffic, unsafe intent launches).

3. **How does LeakCanary decide something leaked?**
   It watches objects that should be unreachable after a lifecycle event (an Activity's `onDestroy`). If one is still
   reachable after a few seconds and a GC, it dumps the heap, analyzes it with Shark, and reports the shortest strong
   reference path from a GC root.

4. **Why `produceState` instead of `remember { }` for reading a file in Compose?**
   `remember`'s block runs synchronously during composition on the main thread. `produceState` launches a coroutine,
   so the work can move to IO and the UI can show a loading state.

5. **Where does `flowOn` apply in a flow chain?**
   It changes the context of everything upstream of it (operators and the producer). It doesn't affect downstream
   operators or the collector.

6. **What's the difference between StartupTimingMetric and FrameTimingMetric?**
   Startup measures time to the first frame (and to fully drawn if reported). Frame timing measures each frame's
   CPU/GPU duration and overrun during an interaction, which is how you quantify jank.

7. **What is a Baseline Profile, and how does it reach users?**
   A text list of hot classes and methods. It's packaged in the APK/AAB; Play's cloud profiles or ProfileInstaller
   compile those methods ahead of time at install, so cold start and first interactions skip interpretation and JIT.

8. **Why measure on release-like builds?**
   Debug builds disable R8, include debugging hooks (and here LeakCanary and StrictMode), and run 2–5× slower.
   Their numbers don't describe what users experience.

## Manual steps (phone)

```
./gradlew :app:generateBaselineProfile                       # writes app/src/release/generated/baselineProfiles/
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest    # startup, tab switching, streaming frames
```
Then copy the medians into `docs/performance-budget.md`.
