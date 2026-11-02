# v2 Phase 5 — Release versioning and quick wins

Fixes AUDIT **H-6** (hard-coded versionCode), **M-1** (4 MB of dead weight in the APK), **M-3** (TalkBack chatter
while streaming), **M-4** (dead input while the model loads), **L-1** (empty chats after a failed first send) and
**L-2** (a "Retry" that can't help).

## What changed

| Where | Change |
|---|---|
| `build-logic/.../AppVersion.kt` (new) + `AppVersionTest` | versionName and versionCode computed from a git tag (`v2.1.0`, `v2.0.0-beta3`) |
| `app/build.gradle.kts` | Version from `POCKETMIND_VERSION_TAG` (CI) or `git describe` (local) or `0.0.0-dev`; excludes `org/bouncycastle/pqc/**` |
| `build.gradle.kts` (root) | `testDebugUnitTest` at the root also runs the build-logic tests |
| `.github/workflows/release.yml` | Passes the tag to the build |
| `feature/chat` | Input usable while loading; one status line ("PocketMind is writing…" / "Answer ready") replaces the live region on the streaming bubble; empty new chats removed after a failed first send; snackbar action "Get a model" for NO_MODEL |
| `gradle.properties` | `ksp.classloader.cache.processors=false` (see "Build stability") |
| Tests | `AppVersionTest` (5), two new `ChatViewModelTest` cases |

**Measured:** release APK **31.2 MB → 27.27 MB**. 203 tests, coverage 78.3 %.

## Concepts

**versionCode vs versionName.** Google Play orders uploads by `versionCode` (an integer that must strictly
increase); users see `versionName`. Deriving both from the release tag removes the "forgot to bump the version"
failure. The packing `major·10⁶ + minor·10⁴ + patch·10² + stage` keeps the order:
`2.0.0-alpha1 (2000001) < 2.0.0-beta1 (2000031) < 2.0.0-rc1 (2000061) < 2.0.0 (2000099) < 2.0.1-alpha1 (2000101)`.
Tags outside the scheme (minor 100, alpha30) **fail the build** rather than produce a code that sorts wrongly.

**Build logic is code, so test it.** `AppVersion` is a pure function in the included build `build-logic`. Its JUnit
test runs as part of `./gradlew testDebugUnitTest` through a root task with the same name that depends on
`gradle.includedBuild("build-logic").task(":convention:test")`.

**Configuration-cache-safe external commands.** `providers.exec { commandLine("git", "describe", …) }` instead of
`Runtime.exec`. Gradle tracks the command's output as a configuration input, so the cache is invalidated when the
tag changes.

**APK resources from dependencies.** Java libraries can ship non-class files (`.properties`, fonts) that end up in
the APK. R8 removes unused *classes*, not these *resources*. `packaging.resources.excludes` drops them. Here,
Bouncy Castle's post-quantum tables (picnic, SIKE) came with PdfBox's crypto dependency. PdfBox only needs classic
AES/RC4/RSA for encrypted PDFs.

**Live regions, used sparingly.** A live region makes TalkBack announce changes to a node. On text that changes 20
times a second, TalkBack interrupts itself constantly. Better: announce *state transitions* (started writing →
answer ready) on a small status node, and let the user move to the answer.

**Don't block input during background work.** The repository's `sendMessage` already waits for the model load
(engine Mutex). So the input can stay enabled; the "thinking" bubble appears instantly and the answer starts when
the model is ready.

## Code walkthrough

```kotlin
// build-logic: AppVersion.fromTag("v2.0.0-beta3")
val (major, minor, patch) = match.destructured.toList().take(3).map(String::toInt)
val stage = if (stageName.isEmpty()) 99 else STAGE_BASE.getValue(stageName) + number   // beta → 30 + 3
AppVersion(name = "2.0.0-beta3", code = major * 1_000_000 + minor * 10_000 + patch * 100 + stage)
```

```kotlin
// app/build.gradle.kts
val versionTag = providers.environmentVariable("POCKETMIND_VERSION_TAG").orNull
    ?: runCatching { providers.exec { commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
                                       isIgnoreExitValue = true }.standardOutput.asText.get().trim() }.getOrNull()
val appVersion = AppVersion.fromTag(versionTag)     // no tag → 0.0.0-dev / 1
```

```kotlin
// ChatViewModel.send — no empty chats
val isNewChat = chatId.value == null
…
is AppResult.Failure -> { discardIfEmpty(id, isNewChat); fail(result.kind) }
```

## Build stability

The intermittent KSP2 error `"InjectProcessingStep was unable to process X because Y could not be resolved"`
appeared again, this time in a release variant. The build log showed the correct task order (the dependency's jar
was built first), and the same task succeeds on its own. That rules out a dependency race and points to shared
state between KSP tasks running in parallel. KSP's Gradle plugin has a switch, `ksp.classloader.cache.processors`,
which reuses one processor classloader (Hilt/Dagger and their static caches) across tasks. Turning it off gives each
task a clean classloader. The full build, including release, has been green since. It's recorded in PROGRESS.md →
BLOCKERS in case it ever returns.

## Trade-offs

- **The versionCode scheme limits minor/patch to 99.** That's fine for an app; violations fail loudly at build time.
- **Local builds use the newest tag reachable from HEAD.** A local build of an untagged commit after `v2.0.0` is also
  "2.0.0". CI release builds always use the exact tag, and only those go to Play.
- **No PQC data.** If a future PdfBox used a post-quantum algorithm, it would fail at runtime. PdfBox 2.0.x
  doesn't; the exclusion is documented next to the rule.
- **Processor classloader cache off.** Slightly slower KSP start per task; deterministic builds are worth more.

## Interview questions

1. **What is the difference between versionCode and versionName, and what breaks if versionCode doesn't increase?**
   versionCode is the integer Play uses to order builds and decide updates; it must strictly increase for every
   upload. versionName is display text. Re-using a versionCode makes Play reject the upload, and devices won't
   update.

2. **How do you derive versions from git tags without breaking the configuration cache?**
   Use `providers.environmentVariable` and `providers.exec` so Gradle records them as configuration inputs. Parse the
   tag with a pure, tested function. Fail the build for malformed tags.

3. **How would you map pre-releases (alpha, beta, rc) into an increasing integer?**
   Reserve the last two digits for the stage: alpha 1–29, beta 31–59, rc 61–98, final 99. Then every pre-release of
   x.y.z sorts below x.y.z, and x.y.z sorts below x.y.(z+1)-alpha1.

4. **Why didn't R8 remove the Bouncy Castle tables?**
   R8 shrinks code (classes, methods, fields). Java resources from dependency jars are copied into the APK unless
   excluded via `packaging.resources.excludes`. Resource shrinking (`isShrinkResources`) applies to Android `res/`, not
   Java resources.

5. **Why is a live region on streaming text bad for accessibility?**
   Every change triggers an announcement; at 20 updates a second TalkBack interrupts itself and becomes unusable.
   Announce state changes (start, done) instead, and let users navigate to the content.

6. **How do you test build logic?**
   Keep it in plain Kotlin functions in `build-logic`, add JUnit tests to that included build, and wire them into the
   main test task (a root task depending on `includedBuild(...).task(":convention:test")`), so CI runs them.

7. **Why remove an empty chat after a failed first send?**
   Users see an untitled, empty entry in History they never meant to create. It's state the user didn't ask for, and
   the cleanup is a local decision in the ViewModel (only for chats created by that send).

8. **How did you track down the intermittent KSP failure?**
   Read the build log for task ordering (dependency jar built first), reproduce the task in isolation (it passes), and
   look at the tool's real configuration options in its jar instead of guessing. Then change one variable at a time
   and record the result in BLOCKERS.
