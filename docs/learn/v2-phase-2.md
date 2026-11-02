# v2 Phase 2 — The app lock, done right

Fixes AUDIT **H-2** (the lock threw away the user's place and any running work) and **H-3** (picking a PDF with the
lock on re-locked the app and lost the file). It also fixes a build reliability problem found on the way (KSP
incremental processing).

## What changed

| Where | Change |
|---|---|
| `app/MainActivity.kt` | The app UI is **always** composed; the lock screen is an overlay on top. The content is hidden from TalkBack while locked, and the keyboard is closed. Recents thumbnail hidden when the lock is on (Android 13+). `startActivityForResult` override marks "our own picker". |
| `app/LockTimer.kt` (new) | Pure rules for *when* to lock: timeout, picker grace period, no re-lock from the unlock dialog's PIN screen |
| `app/MainActivityViewModel.kt` | Lock state from settings: cold start locked; turning the lock on doesn't lock you out; turning it off unlocks |
| `core/model/UserSettings.kt`, `core/datastore` | `appLockTimeoutSeconds` (0, 30 s default, 1 min, 5 min), validated on write |
| `feature/settings` | "Lock again after leaving the app" radio group (shown when the lock is on); plurals in English and Hindi |
| `gradle.properties` | `ksp.incremental=false` (see "Build fix" below) |
| Tests | `LockTimerTest` (9), `MainActivityViewModelTest` (4), `SettingsDataSourceTest` (+1) |

## Why

In v1, `MainActivity` showed **either** `LockScreen` **or** `PocketMindAppUi`. In Compose, a composable that leaves the
composition loses its `remember` state. `PocketMindAppUi` owns the `NavController` (via `rememberNavController()`),
and each screen's ViewModel is scoped to a navigation back-stack entry. So locking the app:
- dropped the navigation back stack (you returned to the start screen), and
- abandoned the screens' ViewModels and the coroutines in them: a streaming answer, a PDF summary, a translation.

On top of that, `onStop()` locked the app **immediately**, and the system file picker is a separate activity, so
picking a PDF triggered `onStop()`. The picker's result came back to a UI that had been torn down.

## Concepts

**Composition and state lifetime.** `remember { }` lives exactly as long as its call site stays in the composition.
Conditionally swapping two big subtrees (`if (locked) A() else B()`) destroys B's state. Drawing A *over* B
(`Box { B(); if (locked) A() }`) keeps B alive.

**Overlay, done safely.** An overlay must also:
1. **block touches.** `LockScreen` uses a Material 3 `Surface`, which consumes pointer events so nothing reaches the
   content underneath;
2. **block accessibility.** `Modifier.clearAndSetSemantics { }` on the content removes it from the semantics tree,
   so TalkBack can't read chats through the lock;
3. **close the keyboard and clear focus**, so typed text and the IME aren't left visible.

**Lock timeout instead of "lock on every `onStop`".** Real banking and password apps use a grace period. Short trips
(answering a message, copying a code) don't demand a fingerprint; a phone left on a table locks soon.

**Our own pickers are not "leaving".** Every `ActivityResultLauncher.launch()` in a `ComponentActivity` goes through
`Activity.startActivityForResult(intent, requestCode, options)`. Overriding it is a single choke point that tells the
lock timer "the next trip to the background is ours". It still has a cap (10 minutes), so leaving through a picker
doesn't disable the lock forever.

**Monotonic time.** Elapsed times come from `SystemClock.elapsedRealtime()`: it keeps counting in deep sleep and the
user can't change it. With `System.currentTimeMillis()`, setting the phone's clock back an hour would postpone the
lock by an hour.

**Recents thumbnails.** Android takes a screenshot of the app for the Recents screen when you leave it. With the lock
on, that picture would show the last chat to anyone who opens Recents. Android 13 added
`Activity.setRecentsScreenshotEnabled(false)`, which hides just that thumbnail. `FLAG_SECURE` would also block the
user's own screenshots, which is too heavy-handed for a chat app.

**State machine in a ViewModel, rules in a pure class.** `LockTimer` has no Android types and takes timestamps as
parameters. Every rule (timeout, grace, exemption used up, locked-while-leaving) is a one-line JVM unit test.

## Code walkthrough

```kotlin
// MainActivity
LockedAppFrame(locked = current.locked) { PocketMindAppUi(startDestination = start) }

@Composable
private fun LockedAppFrame(locked: Boolean, content: @Composable () -> Unit) {
    LaunchedEffect(locked) { if (locked) { keyboard?.hide(); focus.clearFocus(force = true) } }
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.clearAndSetSemantics { } else Modifier) { content() }  // stays composed
        if (locked) LockScreen(onUnlock = ::authenticate)                              // drawn on top
    }
}

override fun onStart() { super.onStart(); viewModel.onAppForeground(SystemClock.elapsedRealtime()) }
override fun onStop()  { super.onStop();  if (!isChangingConfigurations) viewModel.onAppBackground(SystemClock.elapsedRealtime()) }

@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
    viewModel.onLaunchingForResult()          // "the next onStop is our picker"
    super.startActivityForResult(intent, requestCode, options)
}
```

```kotlin
// LockTimer.shouldLockOnForeground
val leftAt = backgroundAtMs ?: return false      // first start: never "came back"
val away = nowMs - leftAt
val graceMs = if (leftForResult) maxOf(timeoutMs, EXTERNAL_GRACE_MS) else timeoutMs
return enabled && away >= graceMs
```

```kotlin
// MainActivityViewModel: lock state from the settings stream
val justSwitchedOn = wasEnabled == false && s.appLockEnabled
if (wasEnabled == null) locked.value = s.appLockEnabled      // cold start
if (!s.appLockEnabled || justSwitchedOn) locked.value = false
```

## Build fix: KSP incremental processing

Two full builds failed with `[Dagger/MissingBinding]` in `:app`. The generated Hilt files of one module
(`data:models`, later `core:llm`) were missing from `build/generated/ksp/debug`, although the same module's
**release** output had them. Run on its own, the same KSP task generated them correctly. Together with the earlier
intermittent `"… could not be resolved"` KSP errors, this points to **KSP incremental processing** losing outputs
after source changes, especially when a processor (Hilt) aggregates across files. The fix is
`ksp.incremental=false` plus deleting the stale `kspCaches` once. KSP then reprocesses a changed module completely,
which is a few seconds per module and always correct.

Lesson: when a build fails in a way the code can't explain, compare the generated outputs (debug vs release, alone vs
in the full build) before changing code.

## Trade-offs

- **The content stays in memory while locked.** That is the point (no lost work), but the data is in RAM behind the
  overlay. The lock is a privacy screen, not encryption. Encryption at rest comes in phase 18 (SQLCipher).
- **Grace period.** 30 s by default is a usability/security balance. Users who want the strictest setting choose
  "Immediately".
- **The `startActivityForResult` override uses a deprecated API.** It's the one method every launcher reaches. The
  alternative (each feature telling the app before launching a picker) would couple feature modules to the app
  shell. The suppression is documented in place.
- **Turning the lock on doesn't lock immediately.** Locking someone out the moment they enable the setting is
  surprising; the lock applies from the next time they leave.

## Interview questions

1. **Why did the v1 lock lose the user's navigation and running work?**
   The lock *replaced* the app composable. Leaving composition dropped the `rememberNavController()` state, so the
   back stack was gone and the screens' ViewModels and their coroutines were abandoned. Drawing the lock as an
   overlay keeps the app UI composed.

2. **How do you make an overlay secure in Compose?**
   Block input (a consuming `Surface` or `pointerInput`), remove the underlying content from accessibility with
   `clearAndSetSemantics {}`, close the IME and clear focus. For Recents, hide the thumbnail
   (`setRecentsScreenshotEnabled(false)`, API 33+) or use `FLAG_SECURE` if screenshots must also be blocked.

3. **Why `SystemClock.elapsedRealtime()` and not `System.currentTimeMillis()` for the timeout?**
   Elapsed realtime is monotonic, counts during sleep, and the user can't change it. Wall-clock time can jump (manual
   change, network time sync, time zones), which would break or bypass a timeout.

4. **How did you stop the file picker from triggering the lock?**
   Every Activity Result launcher ends in `Activity.startActivityForResult`. Overriding it sets a one-shot "expecting
   result" flag; the next background trip gets a capped grace period. The exemption is consumed by one trip and
   cleared if the app was already locked.

5. **What's the difference between `FLAG_SECURE` and `setRecentsScreenshotEnabled(false)`?**
   `FLAG_SECURE` blocks screenshots, screen recording and casting, and blanks the Recents thumbnail.
   `setRecentsScreenshotEnabled(false)` (Android 13+) only blanks the Recents thumbnail; the user can still screenshot
   their own chat.

6. **How do you unit-test lock timing without an emulator?**
   Put the rules in a pure class (`LockTimer`) that takes timestamps as arguments and returns decisions. Test it with
   plain numbers. The Activity only feeds it `elapsedRealtime()` at `onStart`/`onStop`.

7. **Why might the unlock itself re-lock the app, and how did you prevent it?**
   The system PIN/pattern screen sends the app to the background (`onStop`) while it's locked. With a 0 s timeout,
   coming back would lock again. Rule: leaving while already locked records nothing and clears any picker flag.

8. **What caused the intermittent `MissingBinding` errors, and how did you find it?**
   KSP incremental processing left a module's generated Hilt files out of the debug output. Debug output was missing
   them while release had them, and the same task generated them when run alone. Disabling `ksp.incremental` and
   clearing `kspCaches` made builds deterministic.

## To verify on device

- Settings → App lock on, "After 30 seconds". Ask a long question, switch to the home screen for 5 s, come back:
  no lock and the answer is still streaming. Wait 40 s away: lock; unlock; the same chat and answer are there.
- Docs → pick a PDF with the lock on: no lock, the PDF opens.
- Recents with the lock on (Android 13+): the PocketMind thumbnail is blank.
