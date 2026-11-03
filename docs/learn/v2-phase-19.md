# v2 Phase 19 — The Play pipeline: notes, updates, reviews, publishing

Part H of the plan. Phases 0–18 made the app good. This one makes shipping it routine and safe. Approved
decision 5 allows Google Play features that run through the Play Store app.

## What changed

| Where | Change |
|---|---|
| `libs.versions.toml` | Play In-App Update **2.1.0**, In-App Review **2.0.2** (Google Maven), Gradle Play Publisher **4.1.1** (Gradle Plugin Portal); APIs checked with `javap`, including the ktx coroutine helpers and the testing fakes |
| `build-logic/ReleaseNotes.kt` | `ReleaseNotes.fromCommits` (feat → New, fix → Fixed, perf → Faster; ≤ 500 characters, whole lines) and `GitCommitSubjectsValueSource` (commits since the PREVIOUS tag); 4 tests |
| `app/build.gradle.kts` | `generateReleaseNotes` task → `src/main/play/release-notes/en-US/default.txt`; `play { track = internal; defaultToAppBundles = true }` |
| `app/play/` (new) | `InAppUpdates` (flexible flow, state NONE/DOWNLOADING/READY_TO_INSTALL, every Play error ignored); `InAppReview` + `ReviewPolicy` (5th completed answer, at most every 90 days); `PlayModule` |
| `data:chat/AnswerEvents` | "an answer was completed" as a hot `SharedFlow`, emitted by the repository |
| `MainActivity` | update check once per start (the lock timer ignores Play's dialog); the review listener only while the app is on screen; an "update ready" bar (Restart / Later) |
| `core:datastore` | completed-answer counter and the "last asked for a review" time |
| `.github/workflows/play.yml` (new) | tag → quality gate → signed AAB + notes → **internal** track |
| Docs | `docs/release-guide.md` (setup, every release, staged rollout 5 → 20 → 50 → 100 %, halt criteria, rollback); privacy policy section "Google Play features" |

## Concepts

**Release notes as a by-product of good commits.** The repo uses conventional commits (`feat:`, `fix:`, …), so the
Play notes can be generated: user-facing types become plain labels, and developer-only types (chore, docs, test,
ci) are dropped. Play allows 500 characters per language, so only whole lines are kept, plus "…and more". One
subtle bug was caught while writing it: on a tag push, `git describe` at HEAD returns the tag being released, so
"commits since the last tag" was empty. The fix is to describe from `HEAD^`.

**Flexible vs immediate updates.** *Immediate* takes over the screen until the update is installed (for critical
fixes). *Flexible* downloads in the background while the app keeps working, and the app chooses when to restart.
PocketMind uses flexible: restarting in the middle of an answer would lose it, so only the user triggers it.

**Ask for a review at a moment of value, rarely.** Play limits how often the card can appear and may show nothing.
The app must not ask "Do you like us?" first or reward ratings (Play policy). The rule: after the 5th completed
answer, at most once every 90 days. It is one pure function, `ReviewPolicy.shouldAsk(count, lastAsked, now)`, so
it is tested with plain numbers.

**Decoupling with events.** The review logic lives in the app module (it needs an Activity); answers complete in
the data layer. `AnswerEvents` (a `SharedFlow` with a one-element buffer) connects them without either side knowing
the other. The listener uses `repeatOnLifecycle(STARTED)`, so a review card never pops up over another app.

**Fail quietly when the Play Store isn't there.** A GitHub APK or an Android Studio install has no Play entry, so
every Play call fails. The two wrappers catch the error, log it at info level, and carry on. No feature depends on
Play.

**Publishing from CI, but promoting by hand.** `play.yml` uploads to the *internal* track only. Moving a build to
production, and through the staged rollout percentages, is a deliberate step in Play Console. Automation does the
repetitive part; a person makes the risky decision.

## Code walkthrough

```kotlin
// InAppUpdates.check: ask, then show Play's own dialog (flexible)
val info = manager.requestAppUpdateInfo()
if (info.installStatus() == InstallStatus.DOWNLOADED) { _state.value = READY_TO_INSTALL; return }
if (info.updateAvailability() == UPDATE_AVAILABLE && info.isFlexibleUpdateAllowed) {
    manager.registerListener(listener)        // DOWNLOADING → READY_TO_INSTALL
    beforeDialog()                            // tell the lock timer this trip away is ours
    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
}
```

```kotlin
// ReviewPolicy: value first, then rarely
fun shouldAsk(completedAnswers: Int, lastAskedAt: Long?, now: Long) =
    completedAnswers >= 5 && (lastAskedAt == null || now - lastAskedAt >= 90 * DAY)
```

```kotlin
// MainActivity: listen only while visible
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        answers.completed.collect { review.onAnswerCompleted(this@MainActivity) }
    }
}
```

## Trade-offs

- **English notes only, generated.** Other languages fall back to English unless a file is added by hand. Machine
  translation of commit messages would read badly.
- **"Completed answer" stands in for "good answer".** The app can't know whether an answer was good. A completed
  (not stopped or failed) answer is a reasonable, private signal.
- **Asked counts even if Play shows nothing.** This prevents asking Play again and again when it has decided not to
  show the card.
- **Internal track only.** Slower than auto-promoting, but every production release passes a person and a staged
  rollout.

## Interview questions

1. **Flexible or immediate in-app update: when would you use each?**
   Immediate for critical fixes (security, data loss) when old versions must not be used. Flexible otherwise: the
   download happens in the background and the user restarts when convenient.

2. **How do you test Play in-app updates without publishing?**
   `FakeAppUpdateManager` simulates availability, acceptance, download and install. Then use Play's internal app
   sharing or an internal track on a real device.

3. **What are the rules for asking for an in-app review?**
   Ask after the user got value, not at start or after an error. Don't ask a "do you like it" question first, don't
   reward ratings, and accept that Play's quota may show nothing. Never block the UI waiting for it.

4. **How do you generate release notes automatically, and what can go wrong?**
   Parse conventional commit subjects since the previous tag, map types to user-facing labels, and keep the store's
   length limit. Pitfalls: the current tag at HEAD (empty range), merge commits, duplicates, and developer-only noise.

5. **Why upload from CI to the internal track instead of production?**
   CI makes builds reproducible and removes manual steps. A person, a test track and a staged rollout limit the
   damage of a bad build. Automatic production releases remove that safety net.

6. **What is a staged rollout, and how do you roll back on Play?**
   Releasing to a growing percentage of users while watching crash, ANR and review signals. Play can't downgrade
   a versionCode: halt the rollout, then ship a fixed higher versionCode.

7. **How do you connect a data-layer event to an Activity-only API cleanly?**
   Emit a domain event (a SharedFlow) from the data layer and collect it in the Activity with `repeatOnLifecycle`.
   Neither layer depends on the other's types, and collection stops when the UI isn't visible.

8. **What should an app do when Google Play services or the Play Store are missing?**
   Treat Play features as optional: catch failures, log them, and keep every core feature independent of them
   (sideloaded and F-Droid-style installs).

## To verify on device (needs the Play setup in docs/release-guide.md)

1. Tag `v2.0.0-beta.1`. The `play.yml` run uploads it to the internal track. Install it from the internal testing
   link.
2. Tag a higher version, wait until it is on the internal track, and open the older install. Play's update dialog
   appears; accept, keep chatting; the "ready" bar offers Restart.
3. Complete 5 answers: the review card may appear (Play decides). It never appears twice within 90 days.
4. A debug or GitHub APK: no update dialog, no review card, no errors.
