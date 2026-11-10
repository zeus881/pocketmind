# V3 Phase 2a — One database for Android and iOS (Room KMP and shared settings)

## What we built

- **`shared:data`**, a Kotlin Multiplatform module (Android + iOS) that now owns PocketMind's local database:
  the 11 Room entities, 7 DAOs, the manual migration 4→5 and `PocketMindDatabase` — moved from v2's
  `core:database` with their git history. Also the settings class `SettingsDataSource` (DataStore Preferences).
- **No schema change.** Same tables, version 8, same identity hash. Room's KMP build exported `8.json` again and it
  was byte-for-byte identical, which is the proof that a v2 install opens it without a migration.
- `core:database` and `core:datastore` now hold only the **Android side**: SQLCipher encryption with a Keystore
  key, where the settings file lives, and the Hilt bindings.
- An **iOS test of the real schema** (bundled SQLite driver): chat search through the FTS4 table, including Hindi
  text, and notes. It passed on the macOS runner, which closed audit risk D-6.
- Build plumbing: convention plugins `pocketmind.kmp.android.library` and `pocketmind.kmp.room`, the KSP safety rules
  shared by Hilt and Room, and Kover coverage for KMP Android targets.

## Big picture

```
                    shared:data  (commonMain: one schema for both phones)
        entities ─ DAOs ─ Migrations(SQLiteConnection) ─ PocketMindDatabase ─ SettingsDataSource
                 │                                                     │
   Room KSP per target: kspAndroid / kspIosArm64 / kspIosSimulatorArm64 (generated DAO code)
                 │                                                     │
   ┌──────── Android (core:database, core:datastore) ───────┐   ┌──────── iOS (phase 2d/2e) ────────┐
   │ Room.databaseBuilder(context, …)                        │   │ Room.databaseBuilder<…>(path)       │
   │   .openHelperFactory(SQLCipher + Keystore key)          │   │   .setDriver(BundledSQLiteDriver()) │
   │ DataStore file "user_settings" (unchanged since v1)     │   │ iOS Data Protection on the file     │
   └─────────────────────────────────────────────────────────┘   └─────────────────────────────────────┘
```

## Concepts

### Room on Kotlin Multiplatform
- **What:** since Room 2.7 the same `@Entity`/`@Dao`/`@Database` code compiles for Android, iOS and the JVM. Room's
  annotation processor runs once per target and generates platform code.
- **Analogy:** one floor plan, built by two construction crews with their own tools.
- **Why:** chats, folders, versions, personas and notes behave identically on both phones, and the migration tests
  protect both.
- **Alternatives:** SQLDelight (SQL-first, very good KMP support) — rejected because Room keeps v2's 8 exported
  schemas, its AutoMigrations and its migration tests unchanged.

### `expect object` and `@ConstructedBy`
On the JVM, Room finds the generated `PocketMindDatabase_Impl` by reflection. Kotlin/Native has no reflection, so
the database names a constructor object: `expect object PocketMindDatabaseConstructor`. Room's processor writes the
`actual` for every target. `-Xexpect-actual-classes` is needed because expect/actual *classes and objects* are
still a Beta feature (without it, each one is a warning — and warnings are errors in this project).

### Suspend-only DAOs
Room KMP accepts only `suspend` functions and `Flow` queries, because iOS has no "block this thread until the
database answers". `NoteDao` was deliberately blocking in v2 (a tool must return synchronously inside the model's
generation). Now the DAO is `suspend` and the Android adapter waits with `runBlocking` — on LiteRT-LM's background
thread, never on the main thread.

### Two driver worlds, one migration
Android keeps Room's *open helper* path so SQLCipher can plug in (`openHelperFactory`). iOS uses the *driver* path
(`setDriver(BundledSQLiteDriver())`). Migrations are written once against `SQLiteConnection`; we checked in the
room-runtime 2.8.5 bytecode that `RoomOpenHelper` (Android) and `BaseRoomConnectionManager` both call
`Migration.migrate(SQLiteConnection)`.

## Code walkthrough: a search on an iPhone

1. `IosDatabaseTest` builds `Room.inMemoryDatabaseBuilder<PocketMindDatabase>()` with `BundledSQLiteDriver()` —
   the reified, context-free builder that exists on non-Android targets.
2. `chatDao().upsert(...)`, `messageDao().upsert(...)` insert rows; the FTS triggers (defined by `MessageFtsEntity`)
   copy the text into `messages_fts`.
3. `chatDao().observeSummaries(word, word, "\"word*\"")` runs the history query: a LIKE on titles OR an FTS `MATCH`
   on messages. `.first()` takes the current result of the Flow.
4. The Hindi test proves the `unicode61` tokenizer splits Devanagari into words on iOS's SQLite build too.

## Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| Keep packages (`com.sanjay.pocketmind.core.database`) when moving | zero changes for the 5 modules that use the DB | the package name no longer matches the module name |
| Keep schema version 8 | nothing in the schema changed; a version bump would be a fake migration | none |
| Shared modules without DI annotations; Hilt provides them on Android | no Hilt/Koin mix in the Android app before the UI moves (phase 2c) | a few explicit `@Provides` functions |
| `SettingsModule` separate from `DataStoreModule` | end-to-end tests replace only the file, not the settings logic | one more small module |
| iOS database protected by iOS Data Protection, not SQLCipher | SQLCipher has no ready Room driver on iOS (AUDIT_V3 D-4) | weaker than app-level encryption while the phone is unlocked |
| sqlite-bundled **2.6.2** | the version Room 2.8.5 itself requires (from its Gradle module metadata) | not the newest (2.7.1) |

## Common mistakes

- **Moving the schema class and forgetting the schema files.** Migration tests build old databases from
  `schemas/<fqn>/<n>.json`; they moved with the class, and the test's path was updated.
- **Assuming coverage stays the same when code moves.** The DAO tests stayed in `core:database` but the code moved,
  so the merged coverage fell below the gate until `shared:data` joined the report. Lowering the gate would have
  hidden a real gap; including the module fixed the measurement.
- **Running out of memory silently.** WSL's 7.7 GB was not enough for a 4 GB Gradle daemon plus leftover daemons:
  the "daemon disappeared" message only became clear in `dmesg`.

## Try it yourself

1. Add a column with a default value to `NoteEntity`, bump the version to 9 with an `AutoMigration(8, 9)`, build,
   and look at the new `9.json`. Then undo it.
2. In `IosDatabaseTest`, search for a word that appears only in a chat title. Which part of the query matches?
3. Read `RoomNoteStore` and explain why `runBlocking` there is safe but would be a bug in a ViewModel.

## Interview questions

1. **How do you share a Room database between Android and iOS?** Room KMP: entities/DAOs/database in `commonMain`,
   KSP per target, `@ConstructedBy` + `expect object` for the constructor, platform builders (Android context /
   iOS file path with `BundledSQLiteDriver`).
2. **Why must DAOs be suspend in Room KMP?** Non-Android targets have no blocking database API; coroutines are the
   common abstraction, and it keeps disk work off the UI thread everywhere.
3. **How did you prove the move changed nothing for existing users?** Same version and identity hash; the
   re-exported schema JSON was identical; migration tests from every schema 1–8 still pass.
4. **How do you keep app-level encryption on Android with Room KMP?** Keep the Android builder's
   `openHelperFactory` (SQLCipher's `SupportOpenHelperFactory`); Room still calls the connection-based migrations.
5. **What is `expect`/`actual` and when do you prefer an interface instead?** A compile-time platform split.
   Interfaces + injection are easier to fake in tests; `expect` fits things a generator must produce (like Room's
   constructor) or tiny platform facts.
6. **Why not lower the coverage gate when it dropped after the move?** The drop was a measurement artefact (tested
   code left the report). Fixing the report keeps the gate meaningful.
7. **How do you test iOS-only behaviour without a Mac?** `iosTest` source set, run on a macOS CI runner
   (`iosSimulatorArm64Test`), with failures surfaced as annotations.
8. **What does `runBlocking` cost, and when is it acceptable?** It blocks the current thread until the coroutine
   finishes; acceptable on a background thread that must return a value synchronously (a tool callback), never on
   the main thread.

## Self-check

1. Which module would you change to add a new table? (`shared:data`)
2. Where does the Android database key live? (`noBackupFilesDir/database.key`, wrapped by Keystore — core:database)
3. Why does `SettingsDataSource` have no `@Inject`?
4. Which Room method do both Android and iOS migrations go through?
5. What did the iOS test prove that no Android test could?
