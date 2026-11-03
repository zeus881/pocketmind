# v2 Phase 18 — Encryption at rest and control over your data

Part G of the plan (security and privacy). Approved decision 6: chats are encrypted with SQLCipher, the key is
wrapped by Android Keystore, and the APK size cost is measured.

## What changed

| Where | Change |
|---|---|
| `gradle/libs.versions.toml` | `net.zetetic:sqlcipher-android` **4.20.0** (latest on Maven Central, checked 2026-11-03); `SupportOpenHelperFactory(byte[])` and `SQLiteDatabase.openDatabase(path, password, …)` checked with `javap` |
| `core:database/security/` (new) | `WrappedDatabasePassphrase` + `KeystoreKeyWrapper` (random 256-bit key, stored only wrapped with AES-GCM from Android Keystore); `EncryptionMigrator` + `SqlCipherOperations` (one-time `sqlcipher_export`, verified by row counts, safe swap); `EncryptedOpenHelperFactory` (decides on first DB access, off the main thread) |
| `DatabaseModule` | Room uses the encrypting factory |
| `core:database` | `UserDataStore` (read everything / clear everything) so other modules never need Room itself; `ChatDao.getAll()` |
| `core:datastore` | `resetKeepingModel()` |
| `data:chat/UserData.kt` (new) | `UserDataControl`: export everything as one ZIP (Markdown per chat, JSON with all versions, notes, personas, prompts, settings, photos); delete everything except models |
| Settings | New "Your data" section: Export all my data (system "Save as"), Delete all my data (with a clear confirmation) |
| Docs | `docs/privacy-policy.md` updated (encryption, export/delete, voice, photos, tools); `docs/data-safety.md` (Play form answers and a pre-release check) |
| Tests | 9 encryption logic tests (fake cipher, real files), export/delete tests with real Room, settings VM test; `EncryptionDeviceTest` for the real SQLCipher + Keystore |

## Concepts

**Encryption at rest.** Android already sandboxes app files, and on modern phones the whole storage is encrypted
while the phone is locked. App-level encryption protects against what's left: a rooted or forensic copy of the app's
files, a backup that slipped through, or a bug that exposes the file. SQLCipher encrypts every page of the SQLite
file with AES-256; without the key the file looks like random bytes. (Even the 16-byte header "SQLite format 3"
disappears, which is how our migrator tells the two apart.)

**Where does the key live?** Never in code, and never in plain text in a file. The flow:
1. On first use, `SecureRandom` creates 32 random bytes; we use them as 64 hex characters.
2. Android Keystore creates an AES-256-GCM key that **can't be exported**. On most phones it lives in secure
   hardware (TEE or StrongBox).
3. We encrypt ("wrap") our database key with it and store only the result (IV + ciphertext + tag) in
   `noBackupFilesDir/database.key`.
4. At startup we unwrap it in memory and give it to SQLCipher.

A stolen `database.key` is useless without that phone's Keystore.

**A migration that can't lose chats.** Existing users have a plain database. Converting it in place would be risky:
an interrupted conversion means a half-encrypted file. Instead:
1. `ATTACH DATABASE 'pocketmind.db.encrypting' AS encrypted KEY '…'`, then `SELECT sqlcipher_export('encrypted')`
   writes a full encrypted copy. `PRAGMA user_version` is copied too, because Room checks it.
2. Both files are opened and the row count of **every table** is compared.
3. Only then are the files swapped (original → `.plain-backup`, copy → `pocketmind.db`), the old WAL/SHM files are
   deleted, and finally the backup.

If the app dies at any point, the next start finds a consistent state: no swap yet, or the backup alone (which is
restored). If anything fails, the plain database keeps working and the migration is tried again next time.

**Do slow work where Room does it.** The migration can take seconds for a big history. Doing it in the Hilt provider
could run it on the main thread (an ANR). `EncryptedOpenHelperFactory` returns a *deferred* helper, so the choice
(migrate, then encrypted or plain) happens on the first real database access, which Room always makes on a
background thread.

**Data portability and deletion.** GDPR-style rights (access, portability, erasure) are simple when all data is on
the phone, but the user still needs buttons for them. The export has a human-readable part (Markdown) and a
machine-readable part (JSON with every message version and its `parentId` tree). Delete keeps the models: they
aren't personal data, and downloading 2.6 GB again helps nobody.

## Code walkthrough

```kotlin
// The one-time export, inside SQLCipher
database.rawExecSQL("ATTACH DATABASE '${target.path.replace("'", "''")}' AS encrypted KEY '$key'")
database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
database.execSQL("PRAGMA encrypted.user_version = ${database.version}")
database.rawExecSQL("DETACH DATABASE encrypted")
```

```kotlin
// Decide on first access (a background thread), never at app start
override fun create(configuration) = DeferredOpenHelper(configuration) { choose(configuration) }
private fun choose(configuration): SupportSQLiteOpenHelper {
    val name = configuration.name ?: return plain.create(configuration) // in-memory
    val key = passphrase.get()
    return when (migrate(databaseFile(name), key)) {
        NOTHING_TO_DO, MIGRATED -> encrypted(key).create(configuration)
        FAILED -> plain.create(configuration)          // retried next start; chats stay readable
    }
}
```

```kotlin
// Wrapping with Keystore: IV first, then ciphertext + GCM tag
val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
return cipher.iv + cipher.doFinal(secret)
```

## Trade-offs

- **APK size.** SQLCipher adds a native library (2.01 MB uncompressed for arm64 in the AAR). The measured APK
  difference is in `docs/performance-budget.md`.
- **Speed.** Every page read is decrypted. For a chat database (small rows, mostly reads of recent messages) this is
  far below what a user notices, but it isn't zero. The Macrobenchmark on the phone measures it.
- **The key depends on the Keystore.** If Android ever lost the Keystore key (very rare, e.g. a broken system
  update), the database could not be opened. That is the price of a key that can't leave the phone, and one reason
  the export exists.
- **Not tied to the app lock.** The Keystore key doesn't require the fingerprint for every use; otherwise the
  widget or a background job could never read data. The app lock protects the UI; encryption protects the file.
- **The export is not encrypted.** A ZIP must be readable anywhere. The UI and the README inside it say so.

## Interview questions

1. **Android already encrypts storage. Why encrypt the app database too?**
   Defence in depth. File-based encryption protects data at rest only while the phone is locked and against
   offline attacks on the whole disk. App-level encryption also protects against rooted access, accidental
   backups or leaks of app files.

2. **How do you store an encryption key securely on Android?**
   Generate it randomly. Wrap it with a non-exportable Android Keystore key (AES-GCM). Store only the wrapped bytes,
   in a no-backup location. Unwrap it in memory at runtime. Never hard-code keys or derive them from device ids.

3. **What is AES-GCM, and why is the IV stored with the ciphertext?**
   An authenticated encryption mode: it encrypts and adds a tag that detects tampering. The IV must be unique per
   encryption but isn't secret, so it is stored next to the ciphertext.

4. **How do you migrate an existing plaintext SQLite database to SQLCipher safely?**
   Export to a new encrypted file with `ATTACH … KEY` and `sqlcipher_export`, copy `user_version`, and verify the
   copy. Swap files with renames, delete stale WAL/SHM files, and handle crashes at every step so the original
   is never lost.

5. **Why not run the migration when Hilt creates the database?**
   Providers may be called on the main thread, and a migration can take seconds (ANR). Deferring it to the open
   helper's first `getWritableDatabase`, which Room calls off the main thread, keeps the UI responsive.

6. **How do you test code that needs native SQLCipher and the Keystore?**
   Split it. Test the orchestration (steps, verification, crash recovery) with a fake cipher and real files on the
   JVM, and the key logic with a fake wrapper. Then a small instrumented test runs the real library and Keystore
   on a device.

7. **What should "Delete all my data" delete, and what not?**
   Everything personal: chats, notes, personas, prompts, photos, indexes and settings. Keep what is needed for the
   app to work and isn't personal (downloaded models, the fact that onboarding was seen). Confirm clearly, and say
   what stays.

8. **Why does Play's Data safety form say "No data collected" for an app that stores chats?**
   Play defines collection as transmitting data off the device. PocketMind stores data locally and sends none (model
   downloads carry no user data), so nothing is collected or shared, and the form must stay true as the app changes.

## To verify on device

1. Install over the existing app (chats from before this version present). Open the chat list: all chats are there.
   `adb shell run-as com.sanjay.pocketmind head -c 16 databases/pocketmind.db | xxd` no longer shows
   "SQLite format 3".
2. `./gradlew :core:database:connectedDebugAndroidTest` (Keystore round trip, real migration and encrypted open,
   wrong key refused).
3. Settings → Your data → Export all my data → save to Downloads; open the ZIP on a computer.
4. Settings → Your data → Delete all my data → confirm. History is empty, the theme is back to System, and the model
   is still selected and answers.
