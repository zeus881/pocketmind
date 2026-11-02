# v2 Phase 11 — Database v5: pins, folders, branches, full-text search

The data model for the chat features of phase 12, plus AUDIT **M-8** (slow, wildcard-unsafe search).

## What changed

| Where | Change |
|---|---|
| `core/model` | `Chat.pinned`, `Chat.folderId`, `Chat.currentLeafId`; `Message.parentId`; new `Folder` |
| `core/database` entities | `ChatEntity` (+3 columns, index on `folderId`), `FolderEntity`, `MessageEntity.parentId` (indexed), `MessageFtsEntity` (FTS4, `unicode61`, external content = `messages`) |
| `Migrations.kt` (new) | **Manual** `MIGRATION_4_5`: adds columns, links existing messages into a chain, sets each chat's current branch, creates the FTS table + sync triggers, builds the index |
| DAOs | `ChatDao.observeSummaries(query, likePattern, ftsQuery)` (pinned first, last message of the current branch); `setPinned`, `setFolder`, `setCurrentLeaf`; new `FolderDao` (delete folder keeps its chats); `MessageDao.get` |
| `data/chat/SearchQuery.kt` (new) | Escaped LIKE pattern + sanitised FTS expression from what the user typed |
| `OfflineChatRepository` | New messages store their `parentId`; the chat's `currentLeafId` moves forward; search uses `SearchQuery` |
| Build fix | `GitTagValueSource`: the version lookup no longer breaks the build when `git` isn't on the PATH |
| Tests | `MigrationTest` from v1, v2, v3, v4 + a v4 data test (parent chain, branch, search); `ChatDaoTest` (+3: pin order, folder delete, FTS sync); `SearchQueryTest` (4); repository chain + FTS tests |

## Concepts

**Messages as a tree.** Editing a question or regenerating an answer shouldn't destroy what was there. Each message
stores its `parentId`; alternatives are *siblings* (same parent). The chat stores `currentLeafId`, and the visible
conversation is the path from that leaf up to the root. A linear chat is just a tree with one branch. ChatGPT and
Claude model conversations the same way.

**Why this migration is manual.** Room's AutoMigration can add columns and tables, but it can't *compute data*.
Existing chats needed `parentId` = the previous message and `currentLeafId` = the newest message, and the search index
had to be filled. A hand-written `Migration(4, 5)` does that in SQL inside one transaction: if any statement fails,
SQLite rolls everything back and the user keeps v4.

**Matching Room's expected schema exactly.** After migrating, Room compares every table, column, index, FTS table and
trigger with what the code expects, and refuses to open the database on any difference. So the migration's SQL was
copied from Room's own generated `schemas/…/5.json`, not written from memory.

**No foreign key for `folderId` — on purpose.** SQLite can't add a foreign key to an existing table; it means
rebuilding `chats`. Rebuilding (create new, copy, drop old) inside a migration risks the `ON DELETE CASCADE` on
`messages` firing when the old table is dropped. Instead, `FolderDao.deleteFolder` clears `folderId` in the same
transaction that deletes the folder.

**Full-text search (FTS4).** `LIKE '%drone%'` reads every message (no index can help a leading `%`). An FTS table is an
inverted index: word → messages. With `content = messages` it stores no second copy of the text; triggers keep it in
sync on insert, update and delete. The `unicode61` tokenizer splits words by Unicode rules and folds case, which
matters for Hindi and accented text.

**Treat user input as data, not syntax.** Two traps found by the tests:
- In `LIKE`, `%` and `_` are wildcards. Searching "50%" must find a literal percent sign, so they're escaped with `\`
  and the query says `ESCAPE '\'`.
- In FTS, quotes, `*`, `-`, `OR`, `NEAR` are operators. Only letters, combining marks and digits are kept, and each word
  becomes a prefix term. The FTS4 prefix star goes **inside** the quotes (`"prop*"`); `"prop"*` silently matched
  nothing. Hindi vowel signs and the virama are Unicode *marks* (`\p{M}`), not letters, so without `\p{M}`
  "नमस्ते" was cut into pieces.

## Code walkthrough

```sql
-- MIGRATION_4_5, data part: link each message to the one before it in its chat
UPDATE messages SET parentId = (
    SELECT p.id FROM messages p
    WHERE p.chatId = messages.chatId
      AND (p.createdAt < messages.createdAt OR (p.createdAt = messages.createdAt AND p.id < messages.id))
    ORDER BY p.createdAt DESC, p.id DESC LIMIT 1)
-- …point each chat at its newest message, then:
CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(`text` TEXT NOT NULL, tokenize=unicode61, content=`messages`)
-- + 4 triggers copied from 5.json, then fill the index:
INSERT INTO `messages_fts`(`messages_fts`) VALUES('rebuild')
```

```kotlin
// SearchQuery.from("drone batt") → ftsQuery = "\"drone*\" \"batt*\""; from("50%") → likePattern = "50\\%"
val words = raw.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
val fts = words.joinToString(" ") { "\"$it*\"" }
```

```kotlin
// GitTagValueSource: run git inside a ValueSource so a missing executable means "no tag", not a failed build
override fun obtain(): String? = try { execOperations.exec { commandLine("git", "describe", …) } … } catch (e: Exception) { null }
```

## Trade-offs

- **Tree with a stored leaf vs a "selected child" per message.** One pointer per chat is simpler to keep consistent;
  switching branches means finding the newest leaf under a sibling (phase 12).
- **FTS index size.** It adds roughly the size of the words to the database. For chat text that's small, and the
  speed-up grows with history size.
- **Prefix matching only.** "copter" won't find "quadcopter" (FTS matches word *starts*). That's the usual trade-off
  for an index; titles still use `LIKE` (substring).

## Interview questions

1. **When do you need a manual Room migration instead of an AutoMigration?**
   When data must be transformed or derived (backfilling columns, splitting tables, rebuilding indexes), or when the
   change is ambiguous (renames, type changes) and specs can't express it.

2. **How do you make sure a manual migration matches the schema Room expects?**
   Export schemas, copy the generated SQL for new structures, and test: build real old databases from the committed
   schema JSONs, run the migration, and open them with the current database class (Room validates everything).

3. **How would you model edit-and-regenerate without losing old versions?**
   Store messages as a tree (`parentId`). Alternatives are siblings, and the chat keeps a pointer to the current leaf;
   the visible conversation is the path from leaf to root.

4. **Why is `LIKE '%term%'` slow, and what does FTS do differently?**
   A leading wildcard prevents index use, so every row is scanned. FTS builds an inverted index of tokens, so a match
   looks up the term (or prefix) directly.

5. **What's an external-content FTS table, and how is it kept in sync?**
   An FTS table that indexes another table's column without storing the text again (`content=messages`). Triggers on
   the content table delete and insert index entries on every change; `'rebuild'` re-indexes everything.

6. **How do you safely put user input into LIKE and FTS queries?**
   Bind it as a parameter (never string-concatenate SQL), escape LIKE wildcards with an `ESCAPE` clause, and reduce
   FTS input to plain terms so operators typed by the user can't change the query.

7. **Why avoid adding a foreign key in this migration?**
   SQLite can't add foreign keys with `ALTER TABLE`; it requires rebuilding the table, and dropping the old `chats`
   table could cascade-delete messages. Enforcing the rule in a DAO transaction gives the same guarantee safely.

8. **What did the build failure without `git` teach you about the configuration cache?**
   External processes run at configuration time must be declared to Gradle (providers/ValueSources). Their failures
   happen while the cache is stored, outside build-script `try/catch`, so the handling must live inside a
   `ValueSource`.

## Manual step

Before releasing, test the upgrade with a copy of your real chats:
`adb shell "run-as com.sanjay.pocketmind cat databases/pocketmind.db" > v4.db` (with the `-wal` file too), then open
it once with the new build on the phone and check that History, search and every chat look the same.
