# Phase 4 — Model manager, resumable verified downloads, onboarding

## 1. What we built

A fresh install now works end to end: onboarding explains the app, the privacy promise, the
recommended model for this phone's RAM and the "answers can be wrong" disclaimer. The model
screen downloads a 0.6–2.6 GB model in the background with WorkManager, shows progress in
the app and in a notification, supports pause / resume / cancel / delete, resumes interrupted
downloads with HTTP Range requests, verifies the SHA-256 while streaming, and only then
atomically renames the file into place. Settings live in DataStore. The adb debug screen is gone.

## 2. Big picture

```
 Onboarding ──"Get started"──▶ SettingsDataSource.onboardingDone = true ──▶ Models screen
                                                                              │ Download
 ModelsViewModel ──▶ ModelRepository (WorkManagerModelRepository)              ▼
      ▲                 │ enqueueUniqueWork("download-<id>", Wi-Fi constraint, backoff)
      │                 ▼
      │          WorkManager ──runs──▶ ModelDownloadWorker (foreground service + notification)
      │                                   │ storage check (size × 1.2)
      │                                   ▼
      │                            ModelDownloader ──OkHttp──▶ HostAllowlist ──HTTPS──▶ huggingface.co
      │                                   │ Range resume · SHA-256 while streaming
      │                                   ▼
      │              filesDir/models/<id>/model.part ──verified──▶ model.litertlm (atomic rename)
      │
      └── observeModels() = combine( WorkInfo flow , settings flow , file checks )

 Chat ──▶ ChatRepository ──▶ InferenceSettingsSource = SettingsInferenceSource
                               (selected + downloaded model + prompt/sampling from DataStore)
```

## 3. Concepts you need

### WorkManager (background work that must finish)
- **What:** Jetpack's scheduler for deferrable but guaranteed work. Jobs are stored in a
  database, survive app kill and reboot, respect constraints (Wi-Fi, storage) and retry with backoff.
- **Analogy:** A courier company: you hand over the parcel with rules ("only deliver on
  weekdays") and they guarantee delivery even if you go home.
- **Why here:** A 2.6 GB download takes minutes; the user will leave the app.
- **Alternatives:** A plain coroutine (dies with the process), a hand-written Service (lots of
  lifecycle code, no persistence), Android's `DownloadManager` (no streaming checksum, no
  custom verification, awkward resume semantics). WorkManager + OkHttp gives full control.

### Foreground service & notification
- **What:** Work Android must not kill because the user can see it (a notification). Android 14+
  requires a declared *type* — ours is `dataSync`.
- **Why:** Without it, Android stops long background work after ~10 minutes.

### Constraints, unique work, backoff
- `NetworkType.UNMETERED` = Wi-Fi only (default); `CONNECTED` if the user allows mobile data.
- `enqueueUniqueWork(name, REPLACE)` = one job per model; tapping twice never downloads twice.
- `Result.retry()` + exponential backoff (30 s, 60 s, 120 s…) for flaky networks.

### Hilt workers (`@HiltWorker`, `@AssistedInject`)
- WorkManager constructs workers itself; "assisted injection" lets WorkManager pass `Context` and
  `WorkerParameters` while Hilt supplies the rest. The app provides `HiltWorkerFactory` through
  `Configuration.Provider`, and the manifest disables WorkManager's default auto-start.

### HTTP Range requests (resume)
- **What:** `Range: bytes=1048576-` asks the server for the file from byte 1,048,576 onward.
  The server answers `206 Partial Content`. If it ignores the header it answers `200` with the
  whole file — then we must start over.
- **Analogy:** Asking a library for "page 300 to the end" instead of the whole book again.

### SHA-256 integrity checks
- **What:** A 256-bit fingerprint of the file. Hugging Face stores large files with Git LFS, whose
  object id *is* the SHA-256 — that's where our catalog values came from (no invented numbers).
- **Streaming hash:** `MessageDigest.update()` per 1 MiB chunk while writing; on resume we first
  hash the bytes already on disk. No second full read at the end.

### Atomic rename
- `Files.move(part, final, ATOMIC_MOVE)` — the final name appears in one step. Any code that
  sees `model.litertlm` sees a complete, verified file. A crash can never leave a half file
  with the "good" name.

### Pinned URLs
- `.../resolve/<40-char commit>/file` instead of `.../resolve/main/file`: the bytes can never
  change under us, so the checksum stays valid forever.

### DataStore (Preferences)
- **What:** Async, transactional key-value storage with Flow updates (replaces SharedPreferences).
- **Why:** No main-thread disk I/O, no lost writes, observable (theme changes apply instantly).
- **Alternative:** Proto DataStore — typed schema, better for complex data; overkill here.

### Runtime permissions
- `POST_NOTIFICATIONS` (Android 13+) is asked right before the first download, with
  `rememberLauncherForActivityResult`. If denied, the download still works silently.

### Network security (enforced in code)
- `HostAllowlist` is an OkHttp *network* interceptor: every request and every redirect hop
  must be HTTPS to `huggingface.co`, `*.huggingface.co` or `*.hf.co`. Everything else fails.

## 4. Code walkthrough

### `assets/models.json` — real, verifiable data
```json
{ "id": "gemma-4-e2b",
  "url": "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/1caa527c…/gemma-4-E2B-it.litertlm",
  "sizeBytes": 2588147712,
  "sha256": "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
  "minRamGb": 8, "license": "Apache-2.0" }
```
`ModelCatalogTest` checks every entry: 64-hex SHA, URL pinned to a 40-hex commit, allowed host.

### `ModelDownloader.download()` — the heart
```kotlin
val part = files.partFile(model.id)
if (part.length() > model.sizeBytes) part.delete()          // impossible size → start over
val digest = Sha256.newDigest()
var downloaded = part.length()
if (downloaded > 0) Sha256.update(digest, part, downloaded) // hash what we already have
downloaded = transfer(...)                                  // Range request + stream
if (downloaded != model.sizeBytes) return Failure(NETWORK)  // keep .part → resume later
if (!Sha256.matches(hex(digest.digest()), model.sha256)) { part.delete(); return Failure(CHECKSUM_MISMATCH) }
Files.move(part, final, ATOMIC_MOVE)                        // only now is it "installed"
```
`startOffset()` handles the server reply: `206` → append; `200` → `digest.reset()` and
truncate; anything else → `IOException`. The loop calls `currentCoroutineContext().ensureActive()`
per chunk so Pause/Cancel stop within one chunk.

### `ModelDownloadWorker.doWork()`
1. Look up the model. 2. Storage check (`requiredFreeBytes` = remaining × 1.2) → fail with
`NOT_ENOUGH_STORAGE` before writing a byte. 3. `setForeground(...)` with a progress notification
that has a **Pause** action (`createCancelPendingIntent`). 4. Download, reporting
`setProgress(downloaded, total)` every 500 ms. 5. Success → auto-select the first chat model;
network failure → `Result.retry()` (up to 5 attempts); other failures → `Result.failure(error)`.

### `ModelStatusResolver` — status is derived, never stored
```
final file exists        → Downloaded       (checksum already passed)
job RUNNING              → Downloading(bytes)
job ENQUEUED             → Queued ("Waiting for Wi-Fi…")
job FAILED               → Failed(reason)
.part exists, no job     → Paused(bytes)    (pause = cancel the job, keep the file)
otherwise                → NotDownloaded
```
A pure function with its own tests. No "status" column that could disagree with reality after
the app is killed mid-download.

### `WorkManagerModelRepository.observeModels()`
`combine(workManager.getWorkInfosByTagFlow(TAG), settings.settings, fileChanges)` → for each
catalog model: resolved status, `isSelected`, `fitsInRam`. Delete unloads the engine first if
that model is loaded, then picks a fallback selection.

### `SettingsInferenceSource` — replaces the adb debug path
Returns `null` unless the selected model is a downloaded chat model, so the chat screen shows
"Get a model" instead of failing. Converts settings into `InferenceSettings` (path, GPU
preference, custom or default system prompt, temperature, top-K).

### Start screen and theme (`MainActivityViewModel`)
Settings load in milliseconds; until then we draw a plain background (no onboarding flash).
`remember { if (onboardingDone) ChatRoute() else OnboardingRoute }` decides the start ONCE.
After onboarding: navigate to Chat (popping onboarding) and then Models, so Back lands in Chat.

## 5. Design decisions and trade-offs

| Decision | Alternatives | Why |
|---|---|---|
| WorkManager + OkHttp | DownloadManager | Full control: streaming SHA-256, custom resume logic, host allowlist, testable with MockWebServer. |
| Hash while streaming | Hash after download | Saves a full 2.6 GB re-read (~10–20 s). On resume we re-hash the partial file once. |
| Pause = cancel job, keep `.part` | Keep job paused | WorkManager has no "pause"; the file on disk *is* the progress. |
| Status derived | Status table in Room | One truth (WorkManager + files). Can't drift after process death. |
| Wi-Fi only by default | Any network | 2.6 GB on mobile data can cost real money. Explicit opt-in switch. |
| Block models that don't fit RAM | Allow with warning | A model that doesn't fit gets the app killed (OOM). Better to say "needs 8 GB". |
| Qwen3/Qwen3.5 as lite models | Gemma 3 1B | Gemma 3 1B is *gated* on Hugging Face (login + licence), impossible to download anonymously in-app. Qwen2.5 0.5B has no `.litertlm`. |
| Catalog in APK assets | Remote config | No server, no network call besides the model itself. v2 could fetch a signed catalog. |
| `ceil(totalMem / 10^9)` for RAM | `totalMem / 2^30` | Matches the number on the box (an "8 GB" phone reports ~7.5 × 10^9 bytes). |

## 6. Common mistakes

1. **Downloading in a ViewModel coroutine** — dies when the screen or process dies.
2. **Not declaring the foreground service type** — crashes on Android 14+.
3. **Writing directly to the final file name** — a crash leaves a corrupt "complete" model.
4. **Trusting a `200` response on resume** — appending a full file to a partial one corrupts it.
   We detect `200` and restart.
5. **Unpinned URLs (`/resolve/main/`)** — the file changes upstream and every checksum fails.
6. **Hashing with `readBytes()`** — loads 2.6 GB into memory → OutOfMemoryError. We stream.
7. **Forgetting to disable the default WorkManager initializer** when using Hilt workers →
   "Could not instantiate ModelDownloadWorker".
8. **Two DataStore instances on one file** → corruption. Singleton provider.
9. **Deleting a model while it is loaded** → native crash. We unload first.
10. **Leaking a library type through your API.** Our setters first returned DataStore's
    `Preferences` (because `edit {}` returns it), so every module calling them needed DataStore
    on its classpath and the build failed. Fix: a private `update {}` helper returning `Unit`.
11. **Testing DataStore with real files on Windows.** The file-based DataStore replaces its file
    with `File.renameTo`, which can't overwrite on Windows, so the 2nd write failed locally (Linux CI
    and Android are fine). Tests now use `InMemoryPreferencesDataStore` (core:testing), a test
    double of the same `DataStore<Preferences>` interface — same logic tested, faster, OS-independent.

## 7. Try it yourself

1. **Show download speed.** In `ModelDownloader`, also report bytes/second (bytes since last
   report ÷ elapsed time) and display "4.2 MB/s" under the progress bar. Hint: add a field to
   the progress `workDataOf(...)`.
2. **Verify on startup.** Add a "Verify file" button that re-hashes `model.litertlm` with
   `Sha256.ofFile` (run it in a worker — it takes seconds) and deletes it on mismatch.
3. **Add a fourth chat model.** Use `hf.ps1`-style queries (see PROGRESS.md) to get the real size
   and SHA-256 of another `litert-community` `.litertlm` file and add it to `models.json`.
   `ModelCatalogTest` will tell you if anything is malformed.

## 8. Interview questions

1. **How do you download a 2.6 GB file reliably on Android?**
   A WorkManager `CoroutineWorker` running as a foreground `dataSync` service with a progress
   notification, unique work per model, Wi-Fi constraint, exponential backoff. OkHttp streams 1 MiB
   chunks into `model.part`, resuming with HTTP Range; SHA-256 is computed while streaming; only a
   verified file is atomically renamed to its final name.

2. **How does resume work exactly?**
   On start I check `model.part`'s size N, hash those N bytes, and send `Range: bytes=N-`.
   On `206` I append; on `200` the server ignored the range, so I reset the digest and rewrite from
   zero. Pause just cancels the job — the partial file is the saved progress.

3. **Why verify a checksum, and where did you get it?**
   To detect corruption (network, storage) and tampering before loading native code on the file.
   The values come from Hugging Face's LFS metadata — the LFS object id is the SHA-256 — and the
   URL is pinned to a commit, so the expected hash can never become stale.

4. **What if storage runs out?**
   Before writing, the worker requires free space ≥ remaining bytes × 1.2 and fails fast with a
   message showing how much space is needed. WorkManager's `requiresStorageNotLow` adds a second guard.

5. **How do you show progress in Compose?**
   The worker calls `setProgress()`; the repository observes `getWorkInfosByTagFlow` and combines it
   with settings and file state into a `List<ModelState>`; the ViewModel exposes it as `StateFlow`.

6. **How do you guarantee the app only talks to Hugging Face?**
   One `OkHttpClient` in the app, with a network interceptor that rejects any non-HTTPS request or
   any host outside huggingface.co / *.huggingface.co / *.hf.co — checked on every redirect hop.

7. **How do you recommend a model?**
   Read `ActivityManager.MemoryInfo.totalMem`, convert to marketed GB (ceil of bytes / 10^9), and pick
   the largest chat model whose `minRamGb` fits; models that don't fit are blocked.

8. **Why DataStore instead of SharedPreferences?**
   Async API with coroutines/Flow, no main-thread I/O, transactional edits, and change notifications.
   SharedPreferences' `apply()` can still block on `onPause` and silently lose writes.

9. **How do you test the downloader without the internet?**
   MockWebServer: a real local HTTP server. Tests cover fresh download, resume with a `Range` header
   and `206`, a server ignoring Range (`200`), checksum mismatch deleting the file, a truncated
   response keeping `.part`, and HTTP 500.

10. **Why derive status instead of storing it?**
    WorkManager already persists job state and the filesystem is the truth about what's
    downloaded. A third copy would drift (e.g. app killed between "download done" and
    "status saved").

## 9. Check your understanding

1. Which HTTP status means "the server honoured my Range request"?
2. Why is the file renamed only after the checksum passes?
3. What does "Pause" do internally?
4. Why is `ModelStatusResolver` a pure function?
5. Why did we not ship Gemma 3 1B?

---

<details>
<summary>Answers</summary>

1. `206 Partial Content`.
2. So the final name always means "complete and verified"; a crash can't produce a corrupt "installed" model.
3. It cancels the unique WorkManager job; `model.part` stays on disk and the next job resumes from it.
4. So the tricky mapping (job state + files → UI status) can be unit-tested without WorkManager or a file system.
5. It is gated on Hugging Face (needs login and licence acceptance), so the app cannot download it anonymously.
</details>
