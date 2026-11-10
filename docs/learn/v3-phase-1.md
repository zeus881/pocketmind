# V3 Phase 1 — One repository, two platforms: the Kotlin Multiplatform skeleton

## What we built

- The whole v2 Android project moved into `mobile/` (git history kept: every file was `git mv`'d). The repo root
  now has room for `backend/` and `infra/` (V3 monorepo, CLAUDE.md §2).
- `app` was renamed `androidApp`, as in the V3 layout. Same app ID (`com.sanjay.pocketmind`), so a V3 build still
  installs over the v2 app on the phone and keeps its data.
- **`shared:core`**: the first Kotlin Multiplatform module. v2's `core:model` (Chat, Message, LlmModelInfo, …)
  moved into `src/commonMain` unchanged; it now compiles for the JVM (what Android uses) and for iOS.
- **`composeApp`**: the shared Compose Multiplatform UI. It is built as the iOS framework `ComposeApp`; its first
  screen explains PocketMind with v2's real onboarding text in English, Hindi and Punjabi.
- **`iosApp`**: a thin SwiftUI app that shows the shared UI. Its Xcode project is generated from `project.yml`
  by XcodeGen.
- **CI**: `mobile-ci.yml` (Linux: formatting, detekt, Lint, tests, coverage, screenshots, build) and the new
  `ios-build.yml` (macOS: shared tests on the iOS simulator target, Xcode build of the app).
- Three bugs found on the way, all invisible until CI ran for the first time: a broken workflow expression,
  a `gradlew` without the executable bit, and a tag (`v2-final`) that broke version parsing.

## Big picture

```
                         pocketmind/  (one git repo)
   ┌─────────────────────────── mobile/ (Gradle) ───────────────────────────┐   backend/  infra/
   │                                                                         │   (phases 4, 15)
   │   shared:core  (commonMain)  ──► jvm jar ──► Android modules (v2 code)   │
   │        │                     └─► iOS klib                                │
   │        ▼                                                                 │
   │   composeApp (commonMain UI) ──► Android library (phase 2c: used by app) │
   │                              └─► ComposeApp.framework ──► iosApp (Swift) │
   │                                                                         │
   │   androidApp  core:*  data:*  feature:*   (v2, unchanged, still the      │
   │                                            Android app until phase 2c)   │
   └─────────────────────────────────────────────────────────────────────────┘
        Windows PC: Android + JVM targets        macOS CI runner: iOS targets
```

## Concepts

### Kotlin Multiplatform (KMP)
- **What:** one Kotlin source set (`commonMain`) compiled to several targets: JVM bytecode, Android, and native
  machine code for iOS (Kotlin/Native).
- **Analogy:** one recipe, several kitchens. The recipe can't say "use the gas stove" (a platform API); where it
  must, it says "heat it" (`expect`) and each kitchen says how (`actual`).
- **Why here:** PocketMind's logic (models, prompts, chunking, RAG maths, the router client) is the same on both
  phones. Writing it once means one set of tests and one place to fix bugs.
- **Alternatives:** two native apps (Kotlin + Swift: twice the work, drift between them), Flutter or React Native
  (rewrite everything, lose the Kotlin code and LiteRT-LM Kotlin API).

### Compose Multiplatform
- **What:** JetBrains' port of Jetpack Compose. The same `@Composable` functions render on Android and, on iOS,
  inside a `UIViewController` (Skia drawing).
- **Why:** v2 already has ~6,000 lines of Compose UI. Most of it moves with small changes (phase 2c).
- **Trade-off:** iOS users get a UI that is drawn, not built from UIKit views. We must add iOS manners ourselves
  (back-swipe, sheets, haptics), which phase 3 does.

### Targets and source sets
- `commonMain` → every target. `jvmMain`, `iosMain` → one platform. `commonTest` runs on every target.
- `shared:core` has a **jvm** target, not an Android one: it uses no Android API, and the existing Android modules
  can depend on a plain JVM jar exactly as they did on v2's `core:model`.

### Why iOS can't be built on this PC
Apple targets need Apple's toolchain (Xcode's SDKs and linker), which only runs on macOS. Kotlin/Native therefore
skips iOS targets on Windows and Linux (`kotlin.native.ignoreDisabledTargets=true` silences the warning). The
`ios-build` job runs on GitHub's free macOS runners (free for public repos).

### XcodeGen
An `.xcodeproj` is a large generated file that is easy to break by hand and causes merge conflicts. XcodeGen
creates it from a short YAML file (`iosApp/project.yml`). The generated project is in `.gitignore`.

### AGP 9 and KMP
AGP 9 compiles Kotlin itself ("built-in Kotlin") and does not support the KMP plugin on `com.android.library`.
A KMP module with an Android target uses `com.android.kotlin.multiplatform.library` and configures its Android
target inside `kotlin { android { … } }`. We checked this in the AGP 9.5.0 jars before writing any code: the
target DSL interface `KotlinMultiplatformAndroidLibraryTarget`, `withHostTest {}`, and the two names `android`
(current) and `androidLibrary` (deprecated).

## Code walkthrough: from Kotlin to an iPhone screen

1. `composeApp/src/commonMain/.../App.kt` — `App()` sets a Material theme and shows `AboutScreen()`. Texts come
   from `composeResources/values*/strings.xml` through the generated `Res.string.*` accessors.
2. `composeApp/src/iosMain/.../MainViewController.kt` — `ComposeUIViewController { App() }` wraps the Compose UI
   in a UIKit view controller. Swift sees it as `MainViewControllerKt.MainViewController()`.
3. `composeApp/build.gradle.kts` — `binaries.framework { baseName = "ComposeApp"; isStatic = true }` turns the
   iOS targets into an Apple framework.
4. `iosApp/project.yml` — before compiling Swift, Xcode runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`,
   which builds the framework for exactly the architecture and configuration Xcode is building.
5. `iosApp/iosApp/ContentView.swift` — `UIViewControllerRepresentable` puts that view controller into SwiftUI.
6. `.github/workflows/ios-build.yml` — runs `:shared:core:iosSimulatorArm64Test`, generates the Xcode project and
   builds the app for the simulator with signing switched off.

## Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| Move v2 into `mobile/` in one commit series with `git mv` | history and blame survive; the spec's layout | every path in CI and docs changed (all updated) |
| Port `core:model` now, `core:tools` in phase 2b | `core:tools` uses `java.math.BigDecimal` and `java.time`, which iOS doesn't have; replacing them needs verified KMP libraries | the calculator stays Android-only a little longer |
| JVM target for pure modules | Android modules keep working unchanged; tests run fast | none found |
| Static iOS framework | one binary, faster start | the framework must be rebuilt on every Kotlin change (Xcode's build phase does it) |
| XcodeGen instead of a committed `.xcodeproj` | readable, reviewable, no merge conflicts | the owner installs one free tool on the Mac |
| Run the Android test suite in WSL (Linux) | Windows app control now blocks Robolectric's native runtime DLL; CI is Linux too | a copy of `mobile/` in WSL, synced before each run |

## Common mistakes

- **Using `java.*` in `commonMain`.** It compiles on the JVM target and fails only on iOS — that is why the iOS job
  runs the common tests on the simulator target, not just a build.
- **Matching tags too loosely.** `git describe --match "v*"` happily returned `v2-final`, and the version parser
  rejected it. Release patterns must describe the shape: `v[0-9]*.[0-9]*.[0-9]*`.
- **Committing scripts from Windows without the executable bit.** `git update-index --chmod=+x gradlew`.
- **Renaming a module and forgetting a guard that scans paths.** The design-token check scanned `app/src/main`;
  after the rename it would have scanned nothing and passed silently.

## Try it yourself

1. Add a function `fun Chat.isEmpty() = …` to `shared:core`, write a test in `commonTest`, and run
   `./gradlew :shared:core:jvmTest`. Then read `ios-build.yml` and find the step that would run your test on iOS.
2. Put `import java.io.File` in a `commonMain` file and run the build. Read the error, then remove it.
3. Add a fourth section to `AboutScreen` with a new string in all three `composeResources/values*` folders.

## Interview questions

1. **What is the difference between Kotlin Multiplatform and Compose Multiplatform?**
   KMP shares Kotlin code (logic) across targets; Compose Multiplatform is a UI framework on top of it that shares
   `@Composable` UI. You can use KMP for logic with native UIs, or both.
2. **How do you call platform APIs from shared code?**
   Declare `expect fun`/`expect class` in `commonMain` and provide `actual` implementations per target, or define an
   interface in common code and inject platform implementations (often easier to test).
3. **Why can't you build an iOS app on Windows?**
   Apple's SDKs, linker and code signing only run on macOS; Kotlin/Native needs them for Apple targets. CI on a
   macOS runner (or a Mac) does those builds.
4. **How does the iOS app get the Kotlin code?**
   Kotlin/Native compiles the iOS targets into an Apple framework; an Xcode build phase runs Gradle's
   `embedAndSignAppleFrameworkForXcode`, and Swift imports the framework like any other.
5. **Why keep a JVM target in a module whose users are Android apps?**
   A pure module needs no Android API; the JVM jar is consumed by Android modules directly, tests run on a plain
   JVM, and it proves the code has no Android dependency.
6. **How did you migrate a large codebase without breaking it?**
   Strangler approach: move files with `git mv`, change one module at a time, run the full test suite after every
   step, and delete the old module only when its replacement passes the same tests.
7. **What does AGP 9 change for KMP projects?**
   Built-in Kotlin: KMP modules with Android targets must use `com.android.kotlin.multiplatform.library`, and the
   app module stays a plain Android application that depends on them.
8. **Why generate the Xcode project?**
   The `.pbxproj` format is huge and machine-oriented; generating it from YAML makes changes reviewable and avoids
   merge conflicts.
9. **What is a static vs dynamic framework, and why static here?**
   A static framework is linked into the app executable; a dynamic one is a separate binary loaded at launch.
   Static means one binary and faster start; the cost is relinking the app when Kotlin changes.
10. **CI passed locally but failed on GitHub the first time. What kinds of problems does that reveal?**
    Environment differences: file permissions (executable bit), OS-specific paths, tags present only in some
    clones, and workflow syntax errors that only GitHub parses.

## Self-check

1. Which source set may use `platform.UIKit`? (`iosMain`)
2. Why did `shared:core` not get an Android target?
3. Which Gradle task does Xcode run, and when?
4. What would happen if a tag `v3-beta` were pushed after this phase? (It doesn't match `v[0-9]*.[0-9]*.[0-9]*`:
   no version change, no release workflow.)
5. Where do iOS tests run, and which target do they use?
