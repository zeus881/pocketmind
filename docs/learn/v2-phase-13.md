# v2 Phase 13 — Export, share to PocketMind, widget, quick-settings tile

Part C of the plan: getting content into and out of the app, and starting it from outside.

## What changed

| Where | Change |
|---|---|
| `data/chat/ChatExporter.kt` (new) | `ChatExporter` interface + `AndroidChatExporter`: Markdown (UTF-8) and A4 PDF (StaticLayout + `PdfDocument`); pure `ChatMarkdown` and `PdfPagination` |
| Chat screen | ⋮ menu: Save as Markdown / Save as PDF through the system "Save as" picker; "Chat saved" snackbar; file-name sanitising |
| `app/LaunchRequest.kt` (new) | `NewChat`, `OpenChat`, `ShareText`, `SharePdf` + pure `LaunchRequests.parse` |
| `MainActivity` | Reads the Intent on a fresh start and in `onNewIntent`; `launchMode="singleTop"`; refreshes the widget on leaving |
| App frame | Turns a request into navigation (new back-stack entry for a fresh chat); dropped during onboarding |
| `ChatRoute.draft`, `DocumentsShareRoute` | Shared text pre-filled in the input (never auto-sent); shared PDF summarized once (survives rotation) |
| Manifest | `ACTION_SEND` filters for `text/plain` ("Ask PocketMind") and `application/pdf` ("Summarize with PocketMind"); widget receiver; tile service |
| `widget/PocketMindWidget.kt` (new) | Glance 1.2.0 widget: New chat + 3 recent chats; **no titles when the app lock is on** |
| `widget/NewChatTileService.kt` (new) | Quick-settings tile → new chat; asks to unlock the phone first |
| Tests | `ChatExportTest` (4), `AndroidChatExporterTest` (Robolectric, 2), on-device PDF test, `LaunchRequestsTest` (5), VM export/draft (3), launch handling, entity round trips |

## Concepts

**Storage Access Framework (SAF).** `ActivityResultContracts.CreateDocument(mime)` opens the system "Save as" screen.
The user chooses the folder and name; the app receives a `content://` URI with permission for *that file only*. No
`WRITE_EXTERNAL_STORAGE`, no access to anything else: privacy by design.

**PDF without a library.** Android's `PdfDocument` gives each page a `Canvas`. `StaticLayout` wraps text the way a
TextView does. Lay out the whole conversation once, then cut it into pages at line boundaries (`PdfPagination`, a pure
function), and draw each slice with `translate` and `clipRect`. Robolectric has no native PDF writer, so the PDF
drawing is tested on a device (androidTest); the pagination maths is tested on the JVM.

**Share targets.** An `<intent-filter>` with `ACTION_SEND` and a MIME type puts the app in other apps' share sheets.
The shared data arrives as extras (`EXTRA_TEXT`, `EXTRA_SUBJECT`, `EXTRA_STREAM`). The sender grants temporary read
access to a `content://` URI. Shared text is *pre-filled, never auto-sent*: the user stays in control of what the model
sees.

**Intents are an edge concern.** `MainActivity` converts the Intent into a small sealed type (`LaunchRequest`), and the
navigation layer reacts to that. The parsing rules (join subject + body, cap the length, ignore unknown types) are a
pure function with unit tests.

**`singleTop` and `onNewIntent`.** A widget tap while the app is open would otherwise stack a second MainActivity.
With `singleTop` the running one receives `onNewIntent`. `savedInstanceState == null` prevents re-handling the same
Intent after a rotation.

**PendingIntent identity.** Android compares PendingIntents by action, data, type, class and categories, **not
extras**. Three widget rows that differ only in an extra would all open the first chat, so each row's Intent gets a
unique data URI (`pocketmind://chat/<id>`).

**Glance widgets.** You write Compose-like code (`Column`, `Text`, `Button`), and Glance converts it into RemoteViews,
which the launcher draws in its own process. Widgets aren't created by Hilt, so dependencies come through an
`@EntryPoint`. Updates are explicit (`updateAll`): here, when the user leaves the app, so there's no periodic battery
cost.

**Quick-settings tiles.** A `TileService` bound only by the system (`BIND_QUICK_SETTINGS_TILE`). On Android 14+
`startActivityAndCollapse` needs a `PendingIntent`. `unlockAndRun` makes sure a tile never opens private content over
the lock screen.

## Code walkthrough

```kotlin
// LaunchRequests.parse — pure, tested
ACTION_SEND -> when {
    mimeType == "application/pdf" && !streamUri.isNullOrBlank() -> LaunchRequest.SharePdf(streamUri)
    mimeType?.startsWith("text/") == true -> listOfNotNull(subject, text).map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().joinToString("\n\n").take(MAX_SHARED_TEXT).takeIf { it.isNotEmpty() }?.let { LaunchRequest.ShareText(it) }
    else -> null
}
```

```kotlin
// App frame: one place where requests become navigation
LaunchedEffect(launchRequest) {
    val request = launchRequest ?: return@LaunchedEffect
    if (navController.currentDestination?.hasRoute(OnboardingRoute::class) != true) when (request) {
        LaunchRequest.NewChat -> navController.navigate(ChatRoute())          // new entry → fresh ViewModel
        is LaunchRequest.OpenChat -> navController.navigateToChat(request.chatId)
        is LaunchRequest.ShareText -> navController.navigate(ChatRoute(draft = request.text))
        is LaunchRequest.SharePdf -> navController.navigate(DocumentsShareRoute(request.uri))
    }
    onLaunchHandled()
}
```

```kotlin
// Widget privacy
val locked = deps.settings().current().appLockEnabled
val recent = if (locked) emptyList() else deps.chatRepository().observeChats("").first().take(3)
```

## Trade-offs

- **Plain-text PDF.** The PDF shows the Markdown text, not rendered Markdown (no bold, no code-block background).
  It's readable and reliable; rendering Markdown to a canvas would need a renderer outside Compose.
- **Widget refresh on leaving the app.** It's cheap and covers the normal case; a chat created from the share sheet
  and abandoned without leaving the app shows up at the next refresh.
- **Widget and tile classes are excluded from the line gate.** They are system entry points (bound by the launcher
  and System UI) whose behaviour only exists on a device. The Intents they send are unit-tested
  (`WidgetIntentsTest`), and the classes are on the device checklist. That's the same policy as DI modules.
- **Shared PDF access is temporary.** The URI grant lives as long as the receiving activity. That's fine for "summarize
  now"; keeping the PDF for Ask-your-PDF later goes through the normal indexing, which reads it right away.

## Interview questions

1. **How do you save a user file without storage permissions?**
   Use the Storage Access Framework: `CreateDocument` returns a content URI for the location the user picked, and
   you write through `ContentResolver.openOutputStream(uri, "wt")`.

2. **How would you generate a multi-page PDF on Android without a library?**
   `PdfDocument` + `StaticLayout`: lay out the text at page width, split by line bottoms into page-height slices, and
   for each page translate and clip the canvas and draw the layout.

3. **How do you become a share target, and what must you be careful about?**
   Declare `ACTION_SEND` intent filters with MIME types. Treat the extras as untrusted input (validate, cap sizes),
   respect the temporary URI grant, and don't act automatically (no auto-sending shared text to the model).

4. **Why would three widget buttons all open the same chat, and how do you fix it?**
   PendingIntent equality ignores extras. Intents that differ only in extras map to the same PendingIntent. Make them
   differ in data (a unique URI) or request code.

5. **What's `singleTop`, and when do you need `onNewIntent`?**
   If the activity is already at the top of its task, a new Intent is delivered to `onNewIntent` instead of creating
   another instance. Needed for widgets, notifications and tiles that re-open a running app.

6. **How does a Glance widget get data, and when does it update?**
   In `provideGlance` (a suspend function) you load data, then call `provideContent { }`. It updates when you call
   `update`/`updateAll`, on the system's `updatePeriodMillis`, or on widget events; we use explicit updates.

7. **How do widgets get dependencies in a Hilt app?**
   Through an `@EntryPoint` interface installed in `SingletonComponent`, fetched with
   `EntryPointAccessors.fromApplication(context, …)`, because Hilt doesn't construct widgets or receivers' content.

8. **How do you stop a quick-settings tile from exposing private data on a locked phone?**
   Check `isLocked` and use `unlockAndRun { … }`, so the system asks for the PIN or fingerprint before the app opens.

## To verify on device

Share text from Chrome → "Ask PocketMind" → new chat with the text in the input. Share a PDF from Files →
"Summarize with PocketMind". Add the widget; with the app lock on, it shows no titles. Add the tile from the
quick-settings editor; tapping it on a locked phone asks to unlock first. Save a chat as PDF and open it.
