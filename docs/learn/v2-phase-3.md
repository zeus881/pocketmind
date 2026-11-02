# v2 Phase 3 — Memory policy and download robustness

Fixes AUDIT **H-4** (the model was unloaded on every app switch on Android 14+) and **M-12** (Android 15's
6-hour limit for `dataSync` foreground services).

## What changed

| Where | Change |
|---|---|
| `app/ModelKeepAlive.kt` (new) | Observes the whole app's lifecycle (`ProcessLifecycleOwner`); unloads the model N minutes after the app goes to the background; returning in time cancels it |
| `app/PocketMindApp.kt` | Registers the keep-alive; `MemoryPressure` now unloads only on real pressure (`RUNNING_CRITICAL`, `MODERATE`, `COMPLETE`) |
| `core/model`, `core/datastore` | `keepModelLoadedMinutes` (0 / 5 default / 15), validated on write |
| `feature/settings` | "Keep the model loaded after leaving the app" chooser |
| `data/models/ModelDownloadWorker.kt` | Detects `STOP_REASON_FOREGROUND_SERVICE_TIMEOUT` and posts "Paused by Android … continues automatically"; one `post()` for all notifications (permission check + `SecurityException`) |
| Tests | `ModelKeepAliveTest` (4, virtual time), `MemoryPressureTest` (rewritten), `SettingsDataSourceTest` (+1) |

## Why

`onTrimMemory(level)` is Android's "please give memory back" call. Before Android 14 the levels meant real pressure.
Since Android 14 the system sends only `TRIM_MEMORY_UI_HIDDEN` (20) and `TRIM_MEMORY_BACKGROUND` (40), and it sends
`BACKGROUND` when the app simply goes into the background. v1 unloaded at `BACKGROUND`, so answering a WhatsApp
message cost a 5.5 s reload (13 s on a cold GPU cache) when coming back.

## Concepts

**`ProcessLifecycleOwner` vs an Activity's lifecycle.** An Activity stops on rotation, when another of our
activities opens, and when a system picker covers it. `ProcessLifecycleOwner` reports the **whole app**: `ON_START`
when the first activity starts, `ON_STOP` about 700 ms after the last one stops (the delay absorbs rotations). That
is the right signal for "the user left the app".

**Cancellable timer with coroutines.** `scope.launch { delay(...); engine.unload() }` is a timer; `job.cancel()`
stops it. Because `delay` is cancellable, coming back in time leaves no leftover work. The scope is the app-wide
`@ApplicationScope`, which outlives screens.

**Virtual time in tests.** `runTest` uses a test scheduler. `advanceTimeBy(5 * 60_000)` moves the clock five
minutes instantly, so a "5-minute timer" test runs in milliseconds.

**Two signals, two jobs.** Leaving the app → a gentle timer (UX and good citizenship). Memory pressure → unload
immediately (survival: a background app holding 1–2 GB is the first one Android kills).

**Foreground service types and Android 15 timeouts.** A long download runs as a foreground service of type
`dataSync`. Android 15 limits that type to 6 hours per 24 hours and then stops the service. WorkManager surfaces this
as a stopped worker with `stopReason == STOP_REASON_FOREGROUND_SERVICE_TIMEOUT`. Our downloader saves every byte to
`model.part` and resumes with an HTTP Range request, so nothing is lost. The work is rescheduled, and we only tell
the user why the progress paused.

**Runtime permissions can disappear at any moment.** `POST_NOTIFICATIONS` (Android 13+) can be revoked between our
check and the call. Lint requires either a check in the same function or explicit handling of `SecurityException`;
`post()` does both, in one place.

## Code walkthrough

```kotlin
@Singleton
class ModelKeepAlive @Inject constructor(engine: LlmEngine, settings: SettingsDataSource,
                                         @ApplicationScope scope: CoroutineScope) : DefaultLifecycleObserver {
    private var pendingUnload: Job? = null

    override fun onStop(owner: LifecycleOwner) {
        pendingUnload?.cancel()                              // never two timers
        pendingUnload = scope.launch {
            delay(settings.current().keepModelLoadedMinutes * 60_000L)
            engine.unload()                                  // waits for a running answer (engine Mutex)
        }
    }
    override fun onStart(owner: LifecycleOwner) { pendingUnload?.cancel(); pendingUnload = null }
}
// PocketMindApp.onCreate:
ProcessLifecycleOwner.get().lifecycle.addObserver(modelKeepAlive)
```
`engine.unload()` takes the engine's Mutex. If an answer is still being generated when the timer fires, the unload
waits until the answer is finished, so it never cuts off native generation.

```kotlin
// MemoryPressure: only real pressure
fun shouldUnloadModel(level: Int) = level == RUNNING_CRITICAL /*15*/ || level >= MODERATE /*60*/
```

## Trade-offs

- **Memory held for 5 minutes in the background.** That costs other apps RAM for a while. The user can choose
  "Unload right away", and real pressure still unloads immediately.
- **The app may be frozen in the background.** Android's cached-app freezer can pause our process, so the timer may
  fire late. The process can still be killed under pressure, which frees everything, so the system stays healthy.
- **A timeout pause instead of a different job type.** Android 14+ offers "user-initiated data transfer" jobs
  without the 6-hour cap, but they aren't available through WorkManager the same way and need a different
  permission flow. Resuming from `.part` is simpler and loses nothing.

## Interview questions

1. **What changed about `onTrimMemory` in Android 14, and how did it affect this app?**
   Android 14 only delivers `UI_HIDDEN` and `BACKGROUND`, and `BACKGROUND` arrives on normal backgrounding. An app
   that frees caches at `BACKGROUND` now frees them on every app switch. For us that meant a multi-second model
   reload after each switch.

2. **Why `ProcessLifecycleOwner` instead of `Activity.onStop`?**
   It represents the app as a whole and debounces transitions between activities and rotations (about 700 ms
   delay). Activity callbacks also fire for in-app navigation and pickers.

3. **How do you implement "do X after N minutes unless Y happens" with coroutines?**
   Launch a coroutine that `delay`s, then acts, and keep its `Job`. Cancel the job on Y. `delay` is cancellable, so
   cancellation is immediate and clean. Use a scope that outlives the UI (application scope).

4. **How do you test a 5-minute timer quickly?**
   Inject the scope and use `runTest`'s virtual time (`advanceTimeBy`, `runCurrent`). No real waiting, deterministic
   results.

5. **What happens to a `dataSync` foreground service on Android 15 after 6 hours, and how should you design for it?**
   The system stops it (`Service.onTimeout`, surfaced by WorkManager as `STOP_REASON_FOREGROUND_SERVICE_TIMEOUT`).
   Design the work to be resumable (persist progress, HTTP Range), let WorkManager reschedule, and tell the user.

6. **Why catch `SecurityException` after already checking `POST_NOTIFICATIONS`?**
   The user can revoke a runtime permission at any time, so a check-then-act race is possible. Notifications are
   optional for us, so failing silently with a log is correct.

7. **Why doesn't the delayed unload interrupt an answer that is still being generated?**
   `unload()` and `generate()` share the engine's Mutex. The unload waits until the generation finishes, then frees
   the native memory.

8. **How would you choose the default keep-alive time?**
   Measure: the reload cost (5.5–13 s here), typical app-switch durations, and the memory the model holds. Pick a
   default that covers most short switches (5 min), let users change it, and still unload on real pressure.

## To verify on device

- Load a chat, switch to another app for 30 s, return: no "Loading the model…" bar (check logcat: no new
  `Model loaded on` line).
- Set "Unload right away", leave and return: the model reloads.
