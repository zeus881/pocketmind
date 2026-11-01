# Phase 1 — Project skeleton, build system, theme, navigation, CI

## 1. What we built

We created an empty but *professionally structured* Android app. It has 12 Gradle modules
(app, 3 core modules, 8 feature modules), shared build recipes ("convention plugins"), one
file that pins every library version, a Material 3 theme with dark mode, bottom navigation
between empty screens, automatic code formatting (Spotless), static analysis (Detekt),
Android Lint with warnings as errors, and a GitHub Actions CI pipeline. Nothing "smart"
happens yet — but every later phase plugs into this frame without changing it.

## 2. Big picture

```
                         ┌──────────────── :app ────────────────┐
                         │ PocketMindApp (Hilt root)             │
                         │ MainActivity → PocketMindTheme        │
                         │   → PocketMindAppUi (Scaffold+NavHost)│
                         └───────┬───────────────────────────────┘
             navigation callbacks│ (only :app knows every feature)
   ┌────────────┬────────────────┼──────────────┬──────────────┐
   ▼            ▼                ▼              ▼              ▼
feature:chat  feature:history  feature:docs  feature:translate  feature:settings ... (8)
   │            │                │              │              │
   └────────────┴──────┬─────────┴──────────────┴──────────────┘
                       ▼
     core:designsystem (theme, Loading/Empty/Error views)
     core:common       (dispatchers, AppResult, throttleLatest)
     core:model        (pure Kotlin data types, e.g. ThemeMode)

 build-logic/  → convention plugins applied by every module
 gradle/libs.versions.toml → every version, in one place
 .github/workflows/ci.yml  → spotlessCheck, detekt, lint, tests, assembleDebug on each push
```

Later phases add `core:llm`, `core:database`, `core:datastore`, `data:*` and the
`benchmark` module into the empty slots of this picture.

## 3. Concepts you need

### Gradle and the Android Gradle Plugin (AGP)
- **What:** Gradle is the build tool. It reads `*.gradle.kts` files (Kotlin code) and runs
  *tasks*: compile Kotlin, merge resources, run tests, package the APK. AGP is the Gradle
  plugin that knows how to build Android apps.
- **Analogy:** Gradle is a factory manager; tasks are workstations; AGP is the manual for
  assembling Android phones.
- **Why here:** It is the only officially supported Android build system.
- **Alternative:** Bazel/Buck (used at Google/Meta for huge monorepos). Too heavy for one app.

### Gradle modules (multi-module project)
- **What:** Each folder with its own `build.gradle.kts` is a module, compiled separately,
  with explicit dependencies on other modules.
- **Analogy:** Rooms in a house with locked doors. You can only walk into a room you have a
  key (dependency) for.
- **Why here:** (1) Enforces architecture — `feature:chat` physically *cannot* import
  LiteRT-LM because it doesn't depend on it. (2) Faster builds: change one feature and only
  that module recompiles. (3) Teams can own modules.
- **Alternative:** One big `app` module. Simpler at first, but nothing stops a screen from
  calling the database directly, and every change recompiles everything.

### Version catalog (`gradle/libs.versions.toml`)
- **What:** A TOML file listing every library and plugin with its version; build files
  refer to `libs.okhttp` instead of a string.
- **Analogy:** One price list for the whole shop instead of prices written on each shelf.
- **Why:** A version changes in one line; no module can drift to a different version.
- **Alternative:** Hard-coded strings in each build file, or a `buildSrc` constants object.

### Convention plugins (`build-logic/`)
- **What:** Our own small Gradle plugins (`pocketmind.android.library`,
  `pocketmind.android.feature`, ...) that contain the shared configuration.
- **Analogy:** A recipe card. Each module says "make this like the feature recipe" instead of
  re-listing 40 ingredients.
- **Why:** 12+ modules would otherwise copy-paste minSdk, Java version, lint, test libs...
- **Alternative:** `subprojects {}` blocks in the root file. Works, but it is implicit
  "magic" and breaks Gradle's configuration caching and isolation.

### Kotlin 2 (K2) and AGP 9 "built-in Kotlin"
- **What:** In AGP 9 the Android plugin compiles Kotlin itself; we no longer apply
  `org.jetbrains.kotlin.android`. K2 is the new, much faster Kotlin compiler.
- **Why:** Current tooling (AGP 9.5.0, Kotlin 2.4.21). LiteRT-LM 0.19 itself is compiled with
  Kotlin 2.4, so an older Kotlin could not even read its metadata.

### Hilt (dependency injection)
- **What:** A class declares what it needs in its `@Inject constructor(...)`; Hilt builds
  those objects and passes them in. `@HiltAndroidApp` on the Application creates the
  container; `@AndroidEntryPoint` lets an Activity receive injections.
- **Analogy:** A restaurant kitchen where cooks shout "I need onions" and a runner brings
  them; cooks never go shopping themselves.
- **Why:** Testability — in tests we hand in fakes (fake engine, test dispatcher). Also one
  shared instance of heavy objects (the LLM engine) is trivial: `@Singleton`.
- **Alternative:** Manual DI (a hand-written `AppContainer`) — fine for tiny apps, painful at
  20 modules. Koin — runtime DI, errors appear at runtime instead of compile time.

### KSP (Kotlin Symbol Processing)
- **What:** Lets libraries (Hilt, Room) generate code at compile time from annotations.
- **Alternative:** KAPT, the old way via Java stubs — roughly 2x slower and deprecated.

### Coroutine dispatchers (injected)
- **What:** A dispatcher decides *which threads* a coroutine runs on. `IO` = a big pool for
  blocking work, `Default` = one thread per CPU core for computation, `Main` = the UI thread.
- **Why injected:** Tests swap them for a virtual-time test dispatcher (see `@Dispatcher`).

### Jetpack Compose and Material 3
- **What:** UI written as Kotlin functions (`@Composable`). When state changes, Compose
  re-runs ("recomposes") only the functions that read that state.
- **Analogy:** A spreadsheet: change a cell and only the formulas using it recalculate.
- **Why:** Google's modern UI toolkit; less code than XML views, easy previews and tests.
- **Alternative:** XML layouts + Views. Still everywhere in older apps, but more boilerplate.
- **Material 3 / dynamic colour:** On Android 12+ we take colours from the wallpaper
  (`dynamicLightColorScheme`). Older phones use our teal palette in `Color.kt`.

### Single-activity app + type-safe Navigation Compose
- **What:** One `MainActivity`; screens are composables swapped by a `NavHost`. Routes are
  `@Serializable` Kotlin objects (`ChatRoute`), not strings.
- **Why:** Typos in a route string compile fine and crash at runtime; a wrong object type
  does not compile. One Activity avoids re-creating windows and simplifies shared state.
- **Alternative:** One Activity per screen (old style), or string routes (Navigation <2.8).

### Spotless + ktlint, Detekt, Android Lint
- **Spotless/ktlint:** auto-formats code. Ends style debates and noisy diffs.
- **Detekt:** finds code smells (too-long functions, magic numbers, swallowed exceptions).
- **Lint:** Android-specific bugs (missing translations, wrong API levels, backup rules).
- **Why warnings as errors:** Warnings that are allowed to stay grow into hundreds and
  everyone stops reading them.

### CI with GitHub Actions
- **What:** A YAML file that tells GitHub to run our checks on a fresh Linux machine for every
  push/PR. A red check blocks merging.
- **Why:** "Works on my machine" is not proof. CI also proves the build needs no secrets.

## 4. Code walkthrough

### `settings.gradle.kts` — the list of modules
```kotlin
pluginManagement { includeBuild("build-logic") ... }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) ... }
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":app") ; include(":core:model") ...
```
- `includeBuild("build-logic")`: compile our convention plugins first so modules can apply them.
- `FAIL_ON_PROJECT_REPOS`: a module may not quietly add a random Maven repository
  (supply-chain safety: all sources are listed in one place).
- `TYPESAFE_PROJECT_ACCESSORS`: lets us write `projects.core.model` (autocomplete, no typos).

### `build.gradle.kts` (root) — quality tools and the architecture guard
```kotlin
gradle.projectsEvaluated {
    ... if (from.startsWith(":feature:")) broken = to.startsWith(":feature:") ...
    check(violations.isEmpty()) { "Module dependency rule violations: ..." }
}
```
After Gradle has read every module, we walk each module's `implementation`/`api`
dependencies and fail the build if a feature depends on a feature, or a data/core module
depends on something outside `core`. The architecture rules in CLAUDE.md are now
*enforced by the build*, not just written in a document. Detekt is configured once here
over all sources; we pin its embedded Kotlin compiler to 2.0.21 (the version Detekt was
built with) so Gradle doesn't "upgrade" it and break it.

### `build-logic/convention/.../AndroidLibraryConventionPlugin.kt`
```kotlin
pluginManager.apply("com.android.library")
extensions.configure<LibraryExtension> {
    compileSdk = libs.intVersion("compileSdk")
    defaultConfig { minSdk = libs.intVersion("minSdk") ... }
    testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
    configureLint(lint)
}
configureKotlin()
```
- Reads SDK levels from the catalog (compileSdk 37, minSdk 26).
- `isIncludeAndroidResources`: Robolectric tests can load strings/themes.
- `configureKotlin()` sets JVM 17 and `allWarningsAsErrors` for every Kotlin compile task,
  and allows test tasks with no tests (Gradle 9 otherwise fails modules like the design system).

`AndroidFeatureConventionPlugin` stacks library + Compose + Hilt + serialization and adds
the libraries every screen needs. Creating a new feature module is now a 7-line build file.

### `core/common/.../Dispatcher.kt` + `DispatchersModule.kt`
```kotlin
@Qualifier annotation class Dispatcher(val dispatcher: PmDispatchers)
@Provides @Dispatcher(PmDispatchers.IO) fun providesIoDispatcher() = Dispatchers.IO
```
Two objects of the same type (`CoroutineDispatcher`) need a *qualifier* so Hilt knows which
one you want. Later the engine asks for `@Dispatcher(IO)`; tests pass a test dispatcher.

### `core/common/.../ApplicationScope.kt`
An app-lifetime `CoroutineScope` with a `SupervisorJob`. Used later to save a half-finished
answer after the user leaves the chat — work that must outlive the screen.

### `core/common/.../FlowExtensions.kt` — `throttleLatest`
```kotlin
fun <T> Flow<T>.throttleLatest(periodMillis: Long) = conflate().transform { v -> emit(v); delay(periodMillis) }
```
Line by line: `conflate()` keeps only the newest value while downstream is busy;
`transform` emits it then sleeps 50 ms; during the sleep newer values overwrite each other;
the very last value is always delivered. Phase 3 uses this to update the chat UI at most
20 times per second while tokens arrive 50+ times per second.

### `core/common/.../AppResult.kt`
A sealed `Success`/`Failure(kind)` type. `runCatchingApp` converts exceptions into a typed
failure but **re-throws `CancellationException`** — swallowing it would keep a cancelled
coroutine running (a classic coroutine bug).

### `core/designsystem` — theme + state views
`PocketMindTheme(themeMode)` picks light/dark and dynamic colour. `LoadingState`,
`EmptyState`, `ErrorState` give every screen the four required states; `ErrorState` always
has a Retry button. `liveRegion` semantics make TalkBack announce loading/errors.

### `feature/*/navigation/XxxNavigation.kt`
```kotlin
@Serializable data object ChatRoute
fun NavGraphBuilder.chatScreen() { composable<ChatRoute> { ChatScreen() } }
```
Each feature *registers itself* into a NavHost through an extension function and exposes a
route type. The feature knows nothing about other features.

### `app/.../PocketMindAppUi.kt` — how a tap travels
```
User taps "History" tab
  → NavigationBarItem.onClick
  → navController.navigateToTopLevel(HISTORY)   (popUpTo start, saveState, restoreState)
  → NavHost finds composable<HistoryRoute>
  → HistoryScreen() is composed; the tab is highlighted because
    currentDestination.hierarchy.any { it.hasRoute(HistoryRoute::class) }
```
`saveState/restoreState` make each tab remember its state, like Instagram's bottom tabs.

### `AndroidManifest.xml` + `data_extraction_rules.xml`
`allowBackup="false"` stops old-style backups; on Android 12+ that flag no longer covers
device-to-device transfer, so `dataExtractionRules` excludes every domain. Chats never
leave the phone, even when the user moves to a new phone.

### `.github/workflows/ci.yml`
checkout → install JDK 17 → setup-gradle (caching) → `spotlessCheck detekt` → `lint` →
`testDebugUnitTest` → `assembleDebug` → upload the APK as a downloadable artifact.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why this one |
|---|---|---|
| Multi-module from day 1 | Single module, split later | Splitting later is painful; the module graph *is* the architecture enforcement. |
| Convention plugins | `subprojects {}` / copy-paste | Explicit, testable, cache-friendly; what Google's Now in Android does. |
| Detekt at the root only | Detekt plugin in each module | Independent from AGP 9 internals (Detekt 1.23 predates AGP 9). Trade-off: no type resolution, so a few advanced rules are off. |
| Lint warnings = errors, 5 checks disabled | Warnings allowed | Disabled checks depend on the *date*, not our code (e.g. "newer version available"). Each is explained in `lint.xml`. |
| compileSdk 37, targetSdk 36 | target 37 | We compile against the newest APIs, but target the level Google Play currently requires; raising target changes runtime behaviour and deserves its own tested change. |
| arm64-v8a only | all ABIs | LLM phones are 64-bit ARM; 32-bit phones lack RAM for a 2.6 GB model anyway. APK is ~half the size. |
| Pure-Kotlin `core:model` | Android library | Builds faster, tests run on plain JVM, proves models don't depend on Android. |
| Backups fully disabled | Selective backup | Privacy promise: nothing leaves the phone. Trade-off: chats are lost on phone change (we document this). |

## 6. Common mistakes

1. **Hard-coding `Dispatchers.IO`** — tests then run real threads and become flaky. We inject.
2. **Swallowing `CancellationException`** in `catch (e: Exception)` — coroutine keeps running
   after cancel. `runCatchingApp` re-throws it, and a test proves it.
3. **String routes** like `"chat/{id}"` with typos — we use `@Serializable` route types.
4. **Feature-to-feature dependencies** ("chat needs to open history, let me just import it") —
   the root guard fails the build; navigation goes through callbacks in `:app`.
5. **Dynamic versions (`1.+`)** — builds change without any commit. Every version is pinned.
6. **Unescaped Windows paths in `local.properties`** — we hit this one ourselves: Lint's
   `PropertyEscape` check failed until `C:\` was written as `C\:\\`.
7. **Forgetting `dataExtractionRules`** — `allowBackup=false` alone does not stop Android 12+
   device transfers.

## 7. Try it yourself

1. **Add a sixth tab "Models".** Hint: add an entry to `TopLevelDestination` using
   `ModelsRoute` and `Icons.Outlined.Memory`, plus a string `nav_models`. Then look at how
   crowded 6 tabs are on a small phone — that is why Models lives under Settings.
2. **Break the architecture on purpose.** Add `implementation(projects.feature.history)` to
   `feature/chat/build.gradle.kts` and run `./gradlew help`. Read the guard's error, then undo.
3. **Force dark mode.** Change `PocketMindTheme {` in `MainActivity` to
   `PocketMindTheme(themeMode = ThemeMode.DARK) {`. Hint: you need an import from `core.model`
   and `implementation(projects.core.model)` already exists in `app`.

## 8. Interview questions

1. **Why did you split the app into modules?**
   Three reasons: enforced boundaries (a feature module literally cannot import the database
   or LiteRT-LM), faster incremental builds (only changed modules recompile, and modules
   build in parallel), and clear ownership. I also added a Gradle check that fails the build
   if a feature depends on another feature, so the rule can't erode over time.

2. **What is a convention plugin and why not `subprojects {}`?**
   It's a small Gradle plugin in `build-logic` that applies shared config (SDK levels, Java 17,
   lint, test libraries). Modules opt in explicitly by plugin id, so the build is readable and
   cacheable. `subprojects {}` injects config into every module implicitly and couples them,
   which breaks project isolation and configuration cache.

3. **How do you manage dependency versions?**
   A version catalog, `libs.versions.toml`, with every version pinned to a verified release.
   No `+` or `latest.release`, so a build is reproducible from a commit. Upgrades are explicit
   commits that CI validates.

4. **Explain Hilt in one minute.**
   Classes declare dependencies in an `@Inject` constructor; modules with `@Provides`/`@Binds`
   teach Hilt how to build interfaces or third-party types; components define lifetimes
   (`SingletonComponent` = app process). It generates code at compile time with KSP, so a
   missing binding is a compile error, not a runtime crash. Main benefit: testability with fakes.

5. **Why inject coroutine dispatchers?**
   So tests can replace them with `StandardTestDispatcher`, which uses virtual time. Delays
   finish instantly and ordering is deterministic. With hard-coded `Dispatchers.IO` tests
   would be slow and flaky.

6. **What does `SupervisorJob` do in your application scope?**
   With a normal `Job`, one failing child cancels the parent and all siblings. With a
   `SupervisorJob`, children fail independently — one failed background save doesn't kill
   the scope that also handles memory-pressure unloading.

7. **How does dark mode work in your app?**
   All composables read colours from `MaterialTheme.colorScheme`. `PocketMindTheme` picks a
   light or dark scheme based on the user's setting or the system, and on Android 12+ uses
   dynamic colour from the wallpaper. Nothing hard-codes a colour, so every screen follows.

8. **What runs in your CI and why in that order?**
   Formatting and Detekt first (fastest, cheapest failures), then Lint, unit tests, and finally
   a debug build whose APK is uploaded as an artifact. Concurrency settings cancel outdated
   runs. Fail fast, fail cheap.

9. **Why lint warnings as errors, and how do you stop that being annoying?**
   Tolerated warnings accumulate until nobody reads them. I disabled only checks that depend
   on time rather than code — "newer library available", "old target API" — and documented
   each in `lint.xml`. Everything else blocks the build.

10. **Why a single Activity?**
    Navigation between composables is cheaper than Activity transitions, shared state
    (theme, ViewModels scoped to graphs) is simpler, and type-safe Navigation Compose gives
    compile-time-checked routes and arguments.

## 9. Check your understanding

1. Which file would you edit to upgrade OkHttp for the whole project?
2. What stops `feature:chat` from importing a class from `feature:history`?
3. Why does `throttleLatest` never lose the final value of a stream?
4. What is the difference between `Dispatchers.IO` and `Dispatchers.Default`?
5. Why do we need both `allowBackup` and `dataExtractionRules`?

---

<details>
<summary>Answers</summary>

1. `gradle/libs.versions.toml` — change `okhttp = "..."` once.
2. Two things: `feature:chat` has no Gradle dependency on it (so the import doesn't compile),
   and the root build's architecture guard fails if someone adds that dependency.
3. `conflate()` keeps the newest value in a buffer; when the upstream completes, the
   buffered value is still delivered after the current `delay` finishes.
4. IO is a large thread pool for *waiting* (disk, network, blocking native calls). Default has
   about one thread per CPU core for *computing*.
5. `allowBackup` controls backups on Android 11 and older; on Android 12+ device-to-device
   transfers ignore it, so `dataExtractionRules` must exclude data explicitly.
</details>
