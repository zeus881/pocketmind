# v2 Phase 22 — End-to-end tests and the CI gate

The last build phase. Unit tests prove the parts; end-to-end (E2E) tests prove that the parts work *together* the
way a user meets them. On its first run, the first E2E test found a real bug that no unit test could have caught.

## What changed

| Where | Change |
|---|---|
| `app/src/test/.../e2e/EndToEndTest.kt` (new) | 3 tests on the REAL app (MainActivity, NavHost, screens, ViewModels, repositories, Room): ① onboarding → download → chat → History → delete → Undo; ② a PDF shared from another app → summary; ③ app lock covers the app at start |
| `app/src/test/.../e2e/TestModules.kt` (new) | Hilt `@TestInstallIn` modules replacing only what needs a phone or the network: LLM engine (FakeLlmEngine), database file (in-memory Room), DataStore (in memory), Play Billing, Play update/review (Google's fakes), model downloads (`FakeModels`) |
| Production DI | Split so tests can replace exactly one thing: `DatabaseFileModule` (the encrypted file) apart from the DAO bindings; `ModelSelectionModule` (repository + inference settings) apart from WorkManager/OkHttp; `LlmModule`, `BillingModule` public |
| **Bug fix** | `ChatViewModel.onScreenResumed()` + `LifecycleResumeEffect`: after the first download in onboarding, the chat behind the model manager kept saying "No model yet" |
| Build | KSP tasks run **one at a time** (`KspLock`, a Gradle build service with one allowed user); parallel builds back on |
| CI (`ci.yml`) | E2E tests run inside `testDebugUnitTest` (no emulator); a coverage comment on every pull request (`actions/github-script@v9`, first-party, updated in place); test results uploaded as an artifact |

## Concepts

**What to fake in an E2E test.** Replace the edges of the system, not its middle. Native libraries (LiteRT-LM,
SQLCipher), Google Play, the network and the file system quirks of the JVM are edges. Every class a bug could hide
in (screens, navigation, ViewModels, repositories, DAOs and their SQL) stays real. Hilt's `@TestInstallIn(replaces =
[X::class])` swaps one module for the whole test source set.

**Design for testability shows up in the DI graph.** To replace "the database file" without re-declaring eight DAO
bindings, the production module had to be split in two. Making modules small and focused helps tests, and it also
documents what each module is for.

**Robolectric E2E vs emulator E2E.** Robolectric runs the real Android framework code on the JVM: no emulator boot,
seconds per test, the same result on any laptop and in CI. It can't run native code or real system dialogs, and it
has quirks:
- The default screen is tiny (320 × 470 dp), so buttons can be off-screen: the test uses a phone-sized qualifier and
  scrolls before clicking.
- System dialogs never answer, so the notification permission is granted up front.

A small set of instrumented tests on a real phone (`EncryptionDeviceTest`, `LiteRtLmEngineDeviceTest`) covers the
native parts.

**The bug.** Onboarding opens the chat and immediately the model manager on top of it. The chat's ViewModel loads,
finds no model and shows "No model yet". When the user downloads and goes back, nothing tells the chat to look
again. Fix: when the chat screen resumes without a model, it loads again. Unit tests of each screen passed. Only the
sequence onboarding → models → back exposed the problem.

**The KSP race, finally.** Earlier phases fought intermittent "X could not be resolved" errors with partial fixes: no
caching (phase 17), serial builds (phase 20). The serial build still failed, which pointed at the real cause. KSP
runs as Gradle *worker actions*, and those overlap even when tasks don't. A build service with
`maxParallelUsages = 1`, used by every KSP task, serializes exactly KSP and lets everything else run in parallel.

## Code walkthrough

```kotlin
// Replace one production module for all tests in this source set
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseFileModule::class])
object TestDatabaseModule {
    @Provides @Singleton
    fun providesDatabase(@ApplicationContext context: Context): PocketMindDatabase =
        Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
}
```

```kotlin
// A user's path, step by step, through the real UI
compose.clickText(text(ModelsR.string.feature_models_download))
compose.waitForText(text(ModelsR.string.feature_models_in_use))
compose.clickDescription(text(ModelsR.string.feature_models_back))
compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("How do drones fly?")
compose.clickDescription(text(ChatR.string.feature_chat_send))
compose.waitForText("Drones fly with four rotors.", substring = true)
```

```kotlin
// KSP one at a time (HiltConventionPlugin)
val oneKspAtATime = gradle.sharedServices.registerIfAbsent("oneKspAtATime", KspLock::class.java) {
    maxParallelUsages.set(1)
}
tasks.matching { it.name.startsWith("ksp") }.configureEach { usesService(oneKspAtATime) }
```

## Trade-offs

- **No emulator job.** Robolectric E2E gives fast, deterministic coverage of the app's own code on every push. Native
  and hardware behaviour is covered by the device tests, which need the phone (manual step). An emulator job could be
  added later for those, at the cost of slow and flaky CI.
- **Waiting by condition, not by time.** `waitUntil` with a 15 s ceiling and a screen dump on failure; no
  `Thread.sleep`. A failure shows what was on screen instead of only "timeout".
- **Some modules made public for tests.** Their bindings stay `internal`; only the module classes are visible.
- **KSP serialized.** Slightly slower KSP phases, in exchange for builds that don't fail for no reason.

## Interview questions

1. **What belongs in an end-to-end test, and what should be faked?**
   A real user journey through the real UI and logic. Fake only the boundaries: network, native code, platform
   services and payment, so tests are deterministic and fast.

2. **How do you replace dependencies in Hilt tests?**
   `@HiltAndroidTest` + `HiltAndroidRule`, and `@TestInstallIn(replaces = [...])` modules (for all tests) or
   `@UninstallModules` + `@BindValue` (per test).

3. **Robolectric or instrumented tests for UI?**
   Robolectric is fast, runs on the JVM and in CI without devices, but has no native code or real system UI.
   Instrumented tests are slower and need a device, but are fully real. Use both: many Robolectric tests, a few
   device tests for what only a device can do.

4. **How do you make UI tests stable?**
   Wait for conditions (`waitUntil`, idling), not fixed sleeps. Find elements by text from resources and semantics,
   not positions. Use a realistic screen size. Grant permissions up front. Print the semantics tree on failure.

5. **Why did unit tests miss the "No model yet" bug?**
   Each screen was correct on its own; the bug was in the sequence (onboarding opens two screens, state changes
   while one is in the background). Only a test that walks the user's path sees it.

6. **What is a Gradle build service, and how did it fix flaky builds?**
   A shared object with a managed lifetime that tasks declare they use. `maxParallelUsages = 1` makes Gradle run the
   tasks that use it one at a time, including their worker actions. That serialized the KSP tasks that raced.

7. **What makes a CI gate useful rather than annoying?**
   It is fast, deterministic, and has clear failure output (screen dumps, test reports as artifacts). It measures
   what matters: the coverage of logic classes, lint and formatting. Required checks on the main branch enforce it.

8. **How do you report coverage on pull requests without third-party services?**
   Parse the coverage XML in the workflow, write a summary, and post or update one PR comment with a first-party
   action (`actions/github-script`) and a marker, so there is one comment per PR, not one per push.
