# v2 Phase 1 — Crash-loop guard and model integrity

Fixes AUDIT **C-1** (a native crash while loading the model crash-loops the app) and **H-1** (no integrity
check at load time).

## What changed

| Where | Change |
|---|---|
| `core/llm/LoadGuard.kt` (new) | "Black box recorder" for native loads: marker file before `initialize()`, crash records per model + backend |
| `core/llm/ExitReasonSource.kt` (new) | Asks Android why the previous process died (`getHistoricalProcessExitReasons`, API 30+) |
| `core/llm/EngineState.kt` | New `FailureReason.FILE_DAMAGED` and `CRASHED_LAST_TIME` |
| `core/llm/EngineMath.kt` | `backendOrder(preferGpu, blocked)` skips crashed backends; `modelFileProblem(...)` size check |
| `core/llm/LlmEngine.kt` | `load(..., expectedSizeBytes)`; `resetCrashProtection()` |
| `core/llm/LiteRtLmEngine.kt` | Recover → size check → skip blocked backends → marker around each native init |
| `data/models/ModelVerifier.kt` (new) | Full size + SHA-256 re-check of a downloaded file |
| `data/models` repository + worker | `verify()`; a damaged file is deleted; crash records cleared on delete and on a fresh download |
| `feature/chat` | Error message per reason; "Open models" as a second action; "Try again" clears crash records only after a crash |
| `feature/models` | "Check file" button with a progress state; snackbar result |
| `core/designsystem/ErrorState` | Optional secondary action |
| Tests | `LoadGuardTest` (8), `ModelVerifierTest` (4), new cases in `EngineMathTest`, `ChatViewModelTest`, `ModelsViewModelTest` |

## Why

`Engine.initialize()` runs C++ code: LiteRT-LM and the phone's GPU driver. If that code crashes with a
segmentation fault (SIGSEGV), Linux kills the process instantly. No Kotlin `catch` and no `finally` block runs.
Our chat screen loads the model as soon as it opens, and chat is the start screen, so the next launch would crash
again in exactly the same place. That is a **crash loop**: the app becomes unusable until the user clears its data.
Among on-device AI apps that's the classic 1-star review ("crashes on open").

## Concepts

**Why `try/catch` can't help.** Java exceptions are objects thrown inside the JVM. A native crash is a *signal*
from the operating system to the process. The default action for SIGSEGV is to terminate the process. The JVM
never gets a chance to unwind the stack.

**The flight-recorder pattern.** If you can't catch the crash, record your *intention* before the risky call and
erase it afterwards:

```
begin():  write "GPU\n/path"   ─┐
initialize()   ←── crash here? ─┤ the file survives the dead process
end():    delete the file      ─┘
```
At the next start, a surviving marker means "the last process died inside `initialize()`".

**Telling crashes from other deaths.** A marker also survives if the user swipes the app away mid-load, or if
Android's low-memory killer stops it. Since Android 11, `ActivityManager.getHistoricalProcessExitReasons()`
returns `ApplicationExitInfo` records with a `reason`: `REASON_CRASH_NATIVE`, `REASON_CRASH`,
`REASON_LOW_MEMORY`, `REASON_USER_REQUESTED`, and others. We block a backend only for crash reasons. On Android
8–10 the reason is unknown, so we treat the marker as a crash. Wrongly falling back to CPU once costs some speed;
wrongly retrying the GPU costs the whole app.

**Synchronous file writes on purpose.** DataStore writes asynchronously. A write that is still queued when the
process dies is lost, so the record of the crash would itself be lost in the crash. `File.writeText` has finished
before `initialize()` starts.

**Graceful degradation.** GPU crashed → load on CPU (slower, but works). Both crashed → stop trying automatically
and say so. The user can press "Try again" on purpose, check the file, or pick a smaller model.

**Cheap check every time, expensive check on demand.** Comparing the file size with the catalog takes
microseconds and catches truncated copies. A full SHA-256 over 2.6 GB takes seconds, so it runs only when the user
taps "Check file". Defence in depth: the download already verified the hash, the size check guards every load,
and the full re-check is available whenever something looks wrong.

## Code walkthrough

```kotlin
// LiteRtLmEngine.load (inside the engine Mutex, on the IO dispatcher)
closeAllLocked()
loadGuard.recoverFromPreviousRun()                       // marker from a dead process → block
EngineMath.modelFileProblem(file.canRead(), file.length(), expectedSizeBytes)
    ?.let { _state.value = EngineState.Failed(modelPath, it); return@withLock }
val backends = EngineMath.backendOrder(preferGpu, loadGuard.blockedBackends(modelPath))
if (backends.isEmpty()) { _state.value = EngineState.Failed(modelPath, CRASHED_LAST_TIME); return@withLock }
for (backend in backends) {
    loadGuard.begin(modelPath, backend)
    val created = try { tryCreateEngine(modelPath, backend) } finally { loadGuard.end() } ?: continue
    …
}
```
- `try/finally` around `tryCreateEngine` clears the marker for **every** outcome the JVM can see: success, a
  Java exception (which `tryCreateEngine` turns into null → next backend), even cancellation. Only a process death
  leaves it behind, and that's exactly the case we want to detect.
- `recoverFromPreviousRun()` runs once per process (`checkedPreviousRun`). Otherwise a second `load()` in the same
  process would mistake our own in-progress marker for a crash. `LoadGuardTest` has a test for that case.
- The decisions live in `EngineMath` and `LoadGuard`, plain classes that run in JVM unit tests. The engine itself
  needs a phone.

```kotlin
// ChatViewModel
fun retryModel() {
    val model = uiState.value.model
    if (model is ModelUi.Failed && model.reason == FailureReason.CRASHED_LAST_TIME) {
        repository.resetModelCrashProtection()  // the user asks for another attempt on purpose
    }
    loadModel()
}
```
The screen keeps one "Try again" button. The ViewModel decides what it means, so `ChatContent`'s signature didn't
change.

## Trade-offs

- **False positive on Android 8–10.** Swiping the app away during a load makes the next load use CPU. It's
  recoverable ("Try again" or re-checking the file clears it). The alternative was a possible crash loop.
- **Blocks are per file path, not per file content.** A re-download writes the same path, so we clear the records
  on delete, on download success, and when a check passes.
- **Size check vs hash on every load.** Hashing 2.6 GB on every chat open would add seconds to each start. Size
  catches the common failure (truncation); the hash is one tap away.
- **"Try again" after a crash may crash again.** True, and that's why it's a deliberate user action, never automatic.
  If it crashes, the guard records it again and the app returns to the safe state.

## Interview questions

1. **Why can't you catch a native crash in Kotlin?**
   A native crash is a POSIX signal (SIGSEGV, SIGABRT) delivered to the process. The default handler terminates the
   process. The JVM's exception machinery never runs, so `catch` and `finally` don't execute.

2. **How do you detect, on the next launch, that the previous launch crashed in a specific place?**
   Write a marker (synchronously) before the risky call and delete it afterwards. If the marker exists at the next
   start, the process died inside that call. Confirm with `ApplicationExitInfo` (API 30+) to separate crashes from
   kills.

3. **What is `getHistoricalProcessExitReasons`, and what can it tell you?**
   An `ActivityManager` API (Android 11+) that returns recent process exits with a reason (crash, native crash, ANR,
   low memory, user request, signalled, and so on), a timestamp and, for ANRs and native crashes, a trace input
   stream. It's useful for crash-loop protection and local diagnostics without any SDK.

4. **Why did you write the marker with `File.writeText` instead of DataStore?**
   DataStore writes are asynchronous and could still be queued when the process dies. The marker has to be on
   disk before the native call starts.

5. **How do you unit-test crash recovery without crashing anything?**
   Simulate a process death by creating a new `LoadGuard` over the same folder without calling `end()`. Inject the
   exit reason through the `ExitReasonSource` interface (`crashed = true/false/null`).

6. **What's the difference between the size check and the SHA-256 check, and why have both?**
   Size is O(1) and catches truncation, so it runs on every load. SHA-256 reads every byte (seconds for gigabytes) and
   catches any change, so it runs at download time and on demand. Together they give cheap always-on protection
   plus a thorough check when needed.

7. **Why is "Try again" after a crash a user action and not automatic?**
   An automatic retry recreates the crash loop. Making it explicit means the user can always reach the app's UI,
   and a repeat crash is just recorded again.

8. **How would you take this further in production?**
   Store the crash's backend, model hash and device model locally for a diagnostic report the user can share
   (phase 20). Detect GPU driver versions known to be bad and skip GPU up front. Add an instrumented test on real
   devices that loads every catalog model on every backend.

## To verify on device

- Truncate a copy of the model (`run-as … truncate`) → chat shows "The model file is missing or damaged".
- "Check file" on the intact Gemma 4 E2B → "is intact" snackbar after a few seconds; note the time.
