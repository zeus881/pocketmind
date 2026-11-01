# Phase 8 — Release: signing, R8, network security, app lock, licences, Hindi, docs, review

## 1. What we built

Everything needed to ship: a release build type minified by R8 (31 MB APK), signing from
environment variables (never from the repo), a tag-triggered GitHub Actions release that publishes
a signed AAB + APK, a network security config (HTTPS only, system CAs only), an optional biometric
app lock, an open-source licences screen generated at build time, a privacy policy, a full Hindi
translation, README/CHANGELOG/architecture docs — and a strict code review that fixed three real bugs.

## 2. Big picture

```
 git tag v1.0.0 ─push─▶ GitHub Actions release.yml
                          ├─ quality gate (spotless, detekt, lint, unit tests)
                          ├─ decode KEYSTORE_BASE64 secret → $RUNNER_TEMP/…jks (never in git)
                          ├─ ./gradlew bundleRelease assembleRelease   (R8 + shrinkResources + signing)
                          └─ GitHub Release: PocketMind-v1.0.0.aab (Play) + .apk (direct install)

 App at runtime:
   MainActivity (FragmentActivity) ─ settings.appLockEnabled? ─▶ LockScreen ─▶ BiometricPrompt ─▶ app
   network_security_config: cleartext off, system CAs only  +  OkHttp HostAllowlist (huggingface only)
   Settings ─▶ Licences (AboutLibraries JSON generated at build) · Privacy statement · App lock
   values-hi/strings.xml in every module (Lint MissingTranslation proves completeness)
```

## 3. Concepts you need

### App signing (upload key vs app signing key)
- Every APK/AAB must be signed; Android only installs updates signed with the same key.
- With **Play App Signing**, you sign uploads with your *upload key*; Google re-signs with the *app
  signing key* it keeps. Losing the upload key is recoverable; leaking it is still serious.
- **Never commit a keystore.** We read 4 env vars; CI decodes a base64 secret into a temp file.

### AAB vs APK
- **AAB** (Android App Bundle): what Play requires; Play generates device-specific APKs from it.
- **APK**: directly installable file (GitHub Releases, sideloading).

### R8 (shrink, optimize, obfuscate)
- Removes unused code and resources, inlines, renames classes. 54.5 MB debug → 31.2 MB release here
  (most of the remaining size is the native LiteRT-LM and PdfBox code).
- **Danger:** code used by reflection or JNI looks "unused". LiteRT-LM's AAR ships no keep rules (we
  checked), and its C++ calls Kotlin callbacks by name → we keep `com.google.ai.edge.litertlm.**`.
  Without that, the debug build works and the release build crashes — the classic R8 trap.

### Network security config
- XML that the OS enforces for the whole app: `cleartextTrafficPermitted="false"` blocks http://,
  `<certificates src="system"/>` ignores user-installed CAs (so a proxy certificate can't intercept).
- Combined with our OkHttp host allowlist and SHA-256 model checks = defence in depth.

### BiometricPrompt
- System UI for fingerprint/face/PIN. The app never sees biometric data; it only gets success/failure.
- `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`: works on more phones and nobody gets locked out (PIN fallback).
- Requires a `FragmentActivity`. We re-lock in `onStop()` unless it's a rotation (`isChangingConfigurations`).

### Licences at build time (AboutLibraries)
- The Gradle plugin reads each dependency's POM licence and writes `res/raw/aboutlibraries.json`;
  `offlineMode = true` keeps builds independent of the network. Models aren't Gradle dependencies,
  so their licences (Apache 2.0) are listed by hand above the generated list.

### Localization and plurals
- `values-hi/strings.xml` per module. Lint's `MissingTranslation` (an error with warnings-as-errors)
  guarantees no string is forgotten. Placeholders (`%1$s`) and plurals (`one`/`other`) carry over.

## 4. Code walkthrough

### `app/build.gradle.kts` — signing without secrets in git
```kotlin
val keystorePath: String? = System.getenv("POCKETMIND_KEYSTORE_PATH")
...
val hasReleaseKeystore = listOf(...).all { !it.isNullOrBlank() } && file(keystorePath!!).exists()
signingConfigs { if (hasReleaseKeystore) create("release") { ... } }
buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; if (hasReleaseKeystore) signingConfig = ... } }
```
No keystore → an unsigned release (`app-release-unsigned.apk`), so anyone can still build.

### `.github/workflows/release.yml`
Tag `v*` → quality gate → `base64 --decode` the secret into `$RUNNER_TEMP` → `bundleRelease assembleRelease`
→ `softprops/action-gh-release` uploads `PocketMind-v1.0.0.aab/.apk`.

### `MainActivity` + `MainActivityViewModel` — app lock
`MainUiState.Ready(settings, locked = appLockEnabled && !unlocked)`. `LockScreen` opens the prompt
automatically; `onAuthenticationSucceeded → viewModel.unlock()`; `onStop → lock()`.

### `proguard-rules.pro`
Keeps LiteRT-LM (JNI + Gson) and PdfBox (reflection), `-dontwarn` for PdfBox's optional JPEG-2000 codec.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| Env-var signing, unsigned fallback | keystore.properties file | Nothing secret on disk in the repo; CI and local work the same. |
| Keep whole LiteRT-LM package | Fine-grained rules | Native callbacks + Gson reflection are hard to enumerate; safety > a few KB. |
| System CAs only | Certificate pinning to Hugging Face | Pinning breaks when HF rotates certificates/CDNs; checksums already guarantee integrity. |
| `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` | `BIOMETRIC_STRONG` only | Works on budget phones; PIN fallback prevents lock-outs. The lock protects casual access, not keys. |
| AboutLibraries | Play Services OSS plugin | No Google Play Services dependency; Compose UI; offline. |
| Hindi only (v1) | More languages | One full, reviewed translation beats many partial ones. |

## 6. Common mistakes

1. Committing `*.jks` or passwords (our `.gitignore` blocks keystores).
2. Testing only debug builds → R8-only crashes ship. Always run the release build on a phone before publishing.
3. Missing keep rules for JNI/reflection libraries.
4. `usesCleartextTraffic="true"` "just to make it work".
5. App lock with BIOMETRIC_STRONG only → users without strong biometrics can't use it.
6. Hard-coded text → untranslatable UI. Every string is in `strings.xml`.
7. Re-locking on rotation → users asked for a fingerprint every time they turn the phone.

## 7. Try it yourself

1. Create your keystore and produce a signed APK locally (see README "Release").
2. Add a third language: copy `values-hi` folders to `values-bn` and translate; Lint will list every missing key.
3. Add an "Auto-lock after N minutes" option instead of locking immediately on `onStop`.

## 8. Interview questions

1. **How do you keep signing keys out of the repo and still build in CI?**
   The Gradle script reads keystore path and passwords from environment variables; CI stores the keystore
   as a base64 secret, decodes it into the runner's temp dir and passes passwords as secrets. Without them
   the build produces an unsigned release instead of failing.

2. **What does R8 do and what can go wrong?**
   Shrinks, optimizes and obfuscates code and removes unused resources. It can remove or rename code that's
   only reached via reflection or JNI — LiteRT-LM has no consumer rules, so I added keep rules; otherwise
   the release build would crash during generation while debug works.

3. **How do you secure the network layer of an "offline" app?**
   One OkHttp client with a host allowlist (HTTPS to Hugging Face only, checked per redirect), a network
   security config that disables cleartext and user CAs, URLs pinned to commits, and SHA-256 verification
   before a model is ever loaded.

4. **How does your app lock work?**
   A setting enables it; `MainActivity` shows a lock screen until BiometricPrompt succeeds (fingerprint, face
   or device PIN). It re-locks when the app goes to the background but not on rotation. The OS performs the
   authentication; the app never handles biometric data.

5. **How do you make sure translations are complete?**
   Every module has `values-hi/strings.xml`; Lint's MissingTranslation check fails the build if any string is
   missing; plurals use the `<plurals>` resource so grammar is correct per language.

6. **Describe the release pipeline.**
   Pushing a `v*` tag triggers a workflow: same quality gate as CI, decode keystore, build signed AAB and APK,
   attach them to a GitHub Release with generated notes. Play gets the AAB, users can sideload the APK.

7. **Which bugs did your final review find?** (see below) A session race in the shared engine, uncaught
   native exceptions in ViewModels, and a foreground-service restriction on Android 12+.

8. **What would block a Play Store release today?**
   The closed test: new personal developer accounts need 12+ testers opted in for 14 days; plus the store
   listing, Data safety form ("no data collected") and real-device testing of the release build.

## 9. Check your understanding

1. Why does the release build need keep rules for LiteRT-LM but the debug build doesn't?
2. What happens when you run `assembleRelease` without the 4 environment variables?
3. Why do we trust only system certificate authorities?
4. Why doesn't rotating the phone ask for the fingerprint again?
5. What makes Lint fail if a Hindi string is missing?

---

<details>
<summary>Answers</summary>

1. Debug builds don't run R8, so nothing is removed or renamed; release builds do.
2. An unsigned release APK is built (`app-release-unsigned.apk`); nothing fails.
3. A user-installed CA (e.g. from a proxy or malware) could otherwise intercept HTTPS traffic.
4. `onStop()` skips locking when `isChangingConfigurations` is true, and the ViewModel (unlock state) survives rotation.
5. The `MissingTranslation` check, which is an error because lint runs with warnings-as-errors.
</details>

---

## Review fixes (final senior review of the whole codebase)

After all features were in, I reviewed the code as a strict senior reviewer looking for crashes, leaks,
main-thread work, security issues, architecture violations and missing tests. Findings and fixes:

### 1. Race condition: a prompt could run in another feature's session (correctness — HIGH)
**Problem.** The engine has one native conversation shared by chat, translate, summarize, Ask-your-PDF and
the benchmark. Callers did `newSession()` and then `generate()` as two separate locked steps. If another
feature called `newSession()` in between (e.g. a translation running while the user sends a chat message),
the chat prompt would run inside the *translation* session — wrong system prompt, no chat history.
**Fix.** `newSession()` now returns its session id, and `generate(prompt, sessionId)` checks the id **while
holding the engine lock**; a mismatch throws `SessionReplacedException` instead of answering in the wrong
context. The chat repository uses the id *returned* by `newSession` (reading `engine.sessionId` afterwards
could already see someone else's session). Regression test:
`answerRefusesToRunIfTheSessionWasReplacedAfterPreparing`.
**Lesson.** A lock makes each call atomic, not a *sequence* of calls. Pass an ownership token through the
whole sequence and validate it at the point of use.

### 2. Crash: uncaught exceptions in ViewModels (crash risk — HIGH)
**Problem.** If Android unloaded the model under memory pressure in the middle of a summary,
`newSession()` threw `IllegalStateException` inside `SummarizeViewModel`'s collector, which had no
`catch` → an exception in `viewModelScope` crashes the app. The same applied to PdfBox runtime exceptions on
malformed PDFs, `AskViewModel.addPdf` (indexing), and `ChatViewModel.send/retry` (the call that prepares
the session).
**Fix.** These paths now convert exceptions into user-readable error states (`GENERATION_FAILED` /
`FILE_UNREADABLE`) and log them in debug builds, while still re-throwing `CancellationException` so
Stop/Cancel keep working.
**Lesson.** Any coroutine that touches native code or third-party parsers needs an error boundary at the
ViewModel level.

### 3. Crash: foreground service start not allowed (Android 12+ — MEDIUM)
**Problem.** When a download is retried by WorkManager while the app is in the background,
`setForeground()` throws `ForegroundServiceStartNotAllowedException` (an `IllegalStateException`) on
Android 12+, failing the worker.
**Fix.** The worker catches it and continues as normal background work; the `.part` file preserves progress
if the system stops it later, and the next attempt resumes.

### Checked and found OK
- **Main thread:** all native calls, file I/O, PDF parsing and similarity search run on IO/Default
  dispatchers; Room and DataStore are async.
- **Leaks:** native `Engine`, `Conversation` and `EmbeddingEngine` are closed on unload/reload; PDF streams and
  documents use `use {}`; ViewModels hold no Context.
- **Security:** single allowlisted HTTP client, network security config, no backups, no logs in release,
  no secrets in the repo, SHA-256 verification before loading.
- **Architecture:** the Gradle guard confirms feature↛feature and data→core only; LiteRT-LM appears only
  in `core:llm`.
- **Tests:** every ViewModel, repository, chunker, prompt builder and checksum utility has unit tests.

### Known limitations (documented, not fixed)
- `unload()` waits for a running generation to finish (or be cancelled) because both take the engine lock.
- Token counts are estimated (no tokenizer API in LiteRT-LM 0.19).
- The app has not been run on a physical phone from this environment; release-build R8 rules must be
  confirmed on a device before publishing.
