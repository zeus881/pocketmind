# v2 Phase 12 — Edit & resend, versions, pins, folders, highlighted search

Part C of the plan, built on the phase 11 data model.

## What changed

| Where | Change |
|---|---|
| `core/model` | `BranchInfo(index, count)` |
| `data/chat/ConversationTree.kt` (new) | Pure tree functions: `path(leaf)`, `siblings`, `branchInfo`, `newestLeafUnder` (cycle-safe) |
| `ChatRepository` / `OfflineChatRepository` | `observeMessages` = current branch; `observeBranches`; `editAndResend` (new sibling question, model rebuilt with only earlier turns); `showVersion(±1)`; regenerate **keeps** the old answer as a version; `setPinned`, `moveToFolder`; summary cache cleared when the branch changes |
| `data/chat/FolderRepository.kt` (new) | Create (trimmed, max 40 chars, blank refused), rename, delete (chats kept) |
| `ChatDao` | `deleteSummary` |
| Chat UI | Long-press a question → Edit / Copy (also via TalkBack's actions); edit dialog; "‹ 2 / 3 ›" version switcher under edited questions and regenerated answers; disabled while an answer streams |
| History UI | Folder chips (All / folders / New folder; rename or delete the selected one); per-chat menu: Pin/Unpin, Move to folder (incl. new folder), Rename, Delete; pin icon; search words highlighted (same word-start rule as the search) |
| Tests | `ConversationTreeTest` (6), repository (+4: regenerate versions, edit branch, edit refused for answers, pins/folders), `FolderRepositoryTest`, `SearchHighlightTest` (4), `ChatViewModelTest` (+2), `HistoryViewModelTest` (+2), new screenshots (versions, pinned + folders, highlighted search) |

265 JVM tests, coverage 81.5 %.

## Concepts

**The visible conversation is a path in a tree.** With `parentId` on every message and `currentLeafId` on the chat,
"the chat" is the path leaf → root. Editing a question adds a *sibling* question (same parent) and moves the leaf
there; regenerating adds a sibling answer. Nothing is deleted, so every version stays reachable.

**Switching versions.** "Next version of message X" means: take X's next sibling, then follow the *newest* child at
every level down to a leaf, i.e. show that branch as it was last continued. `ConversationTree.newestLeafUnder`.

**The model's memory must follow.** The native conversation holds what the model saw. After an edit or a version
switch, that's a different conversation, so the repository marks the session out of sync (it's rebuilt from the
visible path before the next message) and clears the cached summary, which described the old branch.

**Pure functions for tricky logic.** Tree walking has edge cases (empty chat, unknown leaf, corrupt cycles). As a pure
object over lists, it's tested exhaustively in milliseconds. The repository only wires it to Room.

**Accessible long-press.** `combinedClickable(onLongClickLabel = …)` exposes the long-press as a named action, so
TalkBack users find "Message options" in the actions menu. A long-press with no label is invisible to them.

**Grouping callbacks.** History's content composable needs 11 callbacks. A `HistoryActions` data class keeps the
signature readable, and `HistoryActions.None` makes screenshot tests one line.

**Highlight what matched.** The highlighter uses the same word-start rule as the FTS search, so a highlighted word is
exactly why the chat appears. "dro" highlights "Drone" but not "hydro". Showing *why* a result matched builds trust
in search.

## Code walkthrough

```kotlin
// Regenerate: keep the old answer, make the new one its sibling
val messages = currentPath(chatId)
val lastUserIndex = messages.indexOfLast { it.role == TurnRole.USER }
chatDao.setCurrentLeaf(chatId, messages[lastUserIndex].id)      // the question is the leaf again
prepareSessionLocked(chatId, settings, messages.take(lastUserIndex), messages[lastUserIndex].text)
// saveAssistantMessage() then stores the answer with parentId = current leaf = that question
```

```kotlin
// Edit & resend: a sibling question; the model sees only the turns BEFORE it
val edited = Message(id = UUID…, role = USER, text = newText, parentId = path[index].parentId)
chatDao.deleteSummary(chatId); sessionInSync = false
prepareSessionLocked(chatId, settings, path.take(index), prompt = newText)
messageDao.upsert(edited.asEntity()); chatDao.setCurrentLeaf(chatId, edited.id)
```

```kotlin
// Version switch
val versions = ConversationTree.siblings(all, message)
val target = versions.getOrNull(versions.indexOf(message) + offset) ?: return
chatDao.setCurrentLeaf(chatId, ConversationTree.newestLeafUnder(all, target.id))
```

## Trade-offs

- **The tree is computed in Kotlin, not SQL.** A recursive CTE could walk it in SQLite, but chats have tens to
  hundreds of messages. Loading them and walking in memory is simple, fast and testable.
- **Old versions cost storage.** Text only, small. A future "delete this version" could prune a branch.
- **Folders are flat.** No nesting; pins and search cover most organisation needs without the UI weight of a tree.
- **No foreign key on `folderId`** (phase 11): deleting a folder clears its chats' `folderId` in one transaction
  instead.

## Interview questions

1. **How would you implement "edit a sent message" in a chat app without losing history?**
   Model messages as a tree with parent pointers and keep a current-leaf pointer per chat. An edit inserts a sibling
   and moves the leaf; the UI shows the path and a version switcher for nodes with siblings.

2. **After switching branches, what else must change besides the UI?**
   Any derived state tied to the old path: the LLM's conversation (rebuild from the new path), cached summaries,
   and anything keyed by turn position.

3. **Why keep tree logic in a pure object?**
   It has many edge cases and no I/O. Pure functions can be tested exhaustively and quickly, and reused by the
   repository and, later, by export (phase 13).

4. **How do you make long-press actions accessible?**
   Give them a label (`onLongClickLabel`) so they appear in TalkBack's actions, or add `customActions` semantics.
   Offer the same action elsewhere where possible.

5. **Why does the version switcher get disabled while an answer is streaming?**
   Switching would change the conversation the running generation belongs to. The answer would be saved under a
   branch the user no longer sees, and the model's context would be inconsistent.

6. **How do you design a composable with many callbacks?**
   Group them in an actions data class or interface (`HistoryActions`), keep state immutable, and provide a no-op
   instance for previews and tests.

7. **Why highlight with the same matching rule as the search?**
   Consistency: users see exactly what made a result match. A substring highlight on a word-start search would mark
   things the search didn't use (or miss what it did).

8. **What happens to chats when their folder is deleted, and how is that guaranteed?**
   They move back to "no folder". The DAO clears `folderId` and deletes the folder in one `@Transaction`, so there's
   no state where chats point to a missing folder.
