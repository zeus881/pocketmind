# v2 Phase 0 — Foundations: coverage gate, faster builds, dependency updates, performance budget

## What changed

| File | Change |
|---|---|
| `CLAUDE.md` | New section 0 "v2 — current work": workflow, approved decisions, quality gates, new modules |
| `gradle/libs.versions.toml` | Kover 0.9.11 (plugin + build-logic library) |
| `build-logic/.../KotlinAndroid.kt` | `configureKover(koverSource)` and `configureHostTestsForDebugOnly()` |
| 3 convention plugins | Apply Kover to every module; unit tests only for the debug build type |
| `build.gradle.kts` (root) | Merges all modules into one coverage report, filters out generated code and Composables, fails below 66 % |
| `gradle.properties` | `org.gradle.configuration-cache=true` |
| `.github/workflows/ci.yml` | Runs on `v2` too; coverage gate; coverage summary on the run page; coverage artifact |
| `.github/dependabot.yml` | Weekly grouped update PRs for Gradle dependencies and GitHub Actions |
| `docs/performance-budget.md` | The budget table: limits, measured values, how each is measured |

## Why

You can't improve what you don't measure. Before adding features, v2 needed three gauges:
1. **Coverage gate.** It proves that new code comes with tests, and stops coverage from silently sliding down.
2. **Performance budget.** It defines "fast enough" with numbers, so a redesign can't quietly make the app slower.
3. **Dependabot and the configuration cache.** These keep the project healthy and fast to build as it grows.

## Concepts

**Code coverage.** While the unit tests run, Kover instruments the bytecode and records which lines executed.
*Line coverage* = executed lines ÷ executable lines. It shows what was **not** tested. High coverage doesn't prove the
tests are good (a test with no assertions still "covers" lines), so we use it as a floor, not as a goal in itself.

**What we measure.** Generated code (Hilt factories, Room `_Impl`, Compose singletons) and `@Composable` functions are
excluded. Generated code isn't ours to test. UI is tested better by screenshot and UI tests (phase 10), which check
what the user sees rather than which lines ran. So the 66.8 % baseline means **66.8 % of the app's logic**. The v1
audit's 44.1 % counted everything, including UI.

**Kover variants.** An Android module has several build types (debug, release, plus the `benchmarkRelease` and
`nonMinifiedRelease` types that the Baseline Profile plugin adds). Kover's default "total" report includes all of
them, so it compiled every release variant. That made the run slow (13 min) and flaky (an intermittent KSP error
during the release compile). We define one custom variant named `app`: each Android module puts its **debug** build
in it, the pure-Kotlin module its **jvm** code, and the root merges them. The tasks are `koverVerifyApp`,
`koverXmlReportApp` and `koverHtmlReportApp`.

**Host tests for debug only.** AGP creates a unit-test task for every build type, and they all run the same tests.
`beforeVariants { hostTests[UNIT_TEST_TYPE].enable = false }` for every non-debug type removes the duplicates. The
API name was checked in the AGP 9.5 jar (`HasHostTestsBuilder`, `HostTestBuilder.UNIT_TEST_TYPE`).

**Configuration cache.** A Gradle build has three phases: *initialization* (which projects), *configuration* (run every
`build.gradle.kts` and build the task graph) and *execution* (run the tasks). With 21 modules, configuration takes
seconds every time. The configuration cache saves the task graph and reuses it when no build script changed. The
price is that build logic must not touch mutable global state at execution time. Our build passed:
`Configuration cache entry stored.`

**Dependabot.** A GitHub bot that opens a pull request when a dependency has a new version. Grouping (androidx,
kotlin, test) means one PR per family instead of twenty. Every PR runs CI, so a breaking update can't be merged
unnoticed.

**Performance budget.** A table of limits ("cold start < 500 ms"), each with a measured value and the method used to
measure it. Rule: numbers are measured on a real phone, never estimated, and a change that breaks the budget must
say so.

## Code walkthrough

```kotlin
// build-logic/.../KotlinAndroid.kt
internal fun Project.configureKover(koverSource: String) {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject {
            createVariant(COVERAGE_VARIANT) { add(koverSource) } // "debug" or "jvm"
        }
    }
}
```
Each module says "my contribution to the `app` variant is my debug build". In the root project:

```kotlin
dependencies { coveredModules.forEach { kover(project(it)) } } // merge these modules
kover {
    currentProject { createVariant("app") { } }   // the root itself has no code
    reports {
        filters { excludes { classes("*Hilt_*", "*_Impl", …); annotatedBy("androidx.compose.runtime.Composable") } }
        verify { rule("Line coverage of app logic") { minBound(koverMinLineCoverage) } }
    }
}
```
`kover(project(":x"))` is a dependency in Kover's own configuration: it means "include module x's coverage here".
`minBound(66)` makes `koverVerifyApp` fail if coverage drops below 66 %. The number only goes up as v2 adds tests.

The CI step parses the JaCoCo-format XML with a few lines of Python instead of a third-party action. That is one less
dependency to trust and keep updated.

## Trade-offs

- **Excluding Composables from the line gate.** This avoids pressure to write meaningless tests just to execute UI
  lines. The risk is that UI goes untested, which is why phase 10 adds screenshot tests as a separate gate.
- **A custom variant instead of the standard `koverVerify`.** It's faster and reliable, but it isn't the default name,
  so CLAUDE.md and CI document `koverVerifyApp`.
- **A 66 % floor, not 80 % right away.** A gate set above reality just gets switched off. We raise it phase by phase
  as the tests arrive.
- **Configuration cache.** Faster builds, but plugins must be compatible. Ours are today; a future plugin that isn't
  will fail loudly, and we fix it instead of turning the cache off.

## Interview questions

1. **What does line coverage tell you, and what doesn't it tell you?**
   It shows which lines ran during tests, so it reveals untested code. It does not show whether the tests *check*
   anything. A test without assertions raises coverage. Use it as a floor plus code review of the tests themselves.

2. **Why exclude generated code from coverage?**
   You don't own or change it (Hilt factories, Room `_Impl`), and it's tested by its library's authors. Including it
   distorts the number in both directions and rewards tests of glue code.

3. **How would you stop coverage from dropping over time?**
   A CI gate (`koverVerify` with `minBound`) that fails the build, set at the current real value and only ever raised.
   Plus PR review that asks for tests with new logic.

4. **What is the Gradle configuration cache, and what can break it?**
   It caches the task graph produced by the configuration phase. It breaks when tasks read `Project` or other
   configuration-time objects at execution time, or when build logic depends on undeclared inputs (environment
   variables, files) that Gradle can't track.

5. **Why run unit tests only for the debug build type?**
   JVM unit tests run the same Kotlin code for every build type. R8 minification only affects the APK, not unit
   tests. Running them per build type multiplies CI time without finding more bugs. Release-specific risks (R8 keep
   rules) are covered by building and running the release APK instead.

6. **What is a performance budget, and how do you keep it honest?**
   A list of measurable limits (startup, frame time, APK size). Honest means: measured on real devices with a written
   method (Macrobenchmark, file size), dated, and re-measured when relevant code changes. A breach must be called out
   in the PR.

7. **What does Dependabot do, and what's the risk of auto-merging its PRs?**
   It opens PRs for newer versions. Auto-merging can bring in breaking behaviour that tests don't cover (a different
   default, a security-relevant change). So we group the PRs, run full CI, and a human reads the changelog for majors.

8. **Why did the default Kover report take 13 minutes, and how did you fix it?**
   The total report includes every Android build variant, so it compiled release, benchmark and non-minified variants,
   including KSP for each. A custom `app` variant containing only debug (Android) and jvm (pure Kotlin) code,
   merged at the root, measures the same tested code without compiling the other variants.
