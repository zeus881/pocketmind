# v2 Phase 9 — Adaptive layouts, edge-to-edge, predictive back

Part B of the plan: the app should look right on phones, tablets, foldables and in landscape. It also fixes AUDIT
**L-5** (predictive back not enabled).

## What changed

| Where | Change |
|---|---|
| Version catalog | `material3-adaptive-navigation-suite` 1.4.0 and `material3.adaptive:adaptive` 1.3.0, both managed by the Compose BOM (no extra pins) |
| `app/ui/PocketMindAppUi.kt` | `NavigationSuiteScaffold` replaces the hand-made bottom bar: a bottom bar on phones, a navigation rail on wider or short windows, hidden outside the five tabs |
| `feature/history/navigation` | A `detailPane` slot: on windows ≥ 840 dp wide, History shows the list (40 %) and the selected chat (60 %) side by side |
| `app` | `HistoryChatDetail` fills the slot with the chat screen (its own ViewModel instance, switching chats in place) |
| `feature/chat/ChatViewModel` | `openChat(id)`: show another chat in the same screen; a running answer is stopped and saved |
| `AndroidManifest.xml` | `android:enableOnBackInvokedCallback="true"` (predictive back) |
| Test | `ChatViewModelTest`: `openChat` switches chats in place |

The library APIs were checked in the downloaded **sources jars** before writing any code. Findings:
`NavigationSuiteScaffold(navigationSuiteItems, layoutType, state, content)` is current (the overload without `state` is
hidden-deprecated); `NavigationSuiteType.None` exists; the scaffold *consumes* the bar's or rail's window insets for
its content; size-class breakpoints are `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND = 600` and
`WIDTH_DP_EXPANDED_LOWER_BOUND = 840`.

## Concepts

**Window size classes.** Instead of "phone vs tablet", Material describes the *window*: compact width (< 600 dp),
medium (600–839), expanded (≥ 840). A foldable is compact when folded and expanded when open; a tablet in split-screen
may be compact. Always decide by the window, never by the device type.

**Navigation suite.** The same five destinations are shown as a bottom bar (compact width, or short height in
landscape) or a navigation rail (otherwise). `NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo` makes that
choice; `NavigationSuiteType.None` hides navigation on non-tab screens (onboarding, models, benchmark).

**List-detail.** On wide windows a list and its selected item are shown together: email, settings, and chat apps all do
this. Selecting an item updates the right pane instead of navigating. On narrow windows the same list navigates.

**Slots keep modules independent.** History can't import Chat (module rule: no feature → feature). So the history
module offers a *slot*, a `@Composable (chatId: String?) -> Unit` parameter, and the app module, which knows every
feature, fills it with the chat screen. The same technique powers Material's own scaffolds.

**ViewModel keys.** Two chat screens can exist (the Chat tab and History's detail pane). `hiltViewModel(key =
"history-detail")` gives the pane its own `ChatViewModel`, so its state doesn't collide with the tab's.

**Edge-to-edge and insets.** The app draws behind the system bars (`enableEdgeToEdge()`, enforced for target SDK 35+).
Each component must pad for the bars it touches. The navigation suite consumes its side's insets for the content,
so screen Scaffolds don't add the same padding twice.

**Predictive back.** On Android 14+ a back swipe shows a preview of where you'll land, and on Android 15+ the
system animations (back-to-home, cross-activity) are shown. Apps opt in with `enableOnBackInvokedCallback="true"`.
Navigation Compose and `BackHandler` already use the new back APIs.

## Code walkthrough

```kotlin
val layoutType = if (onTopLevel) {
    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
} else NavigationSuiteType.None

NavigationSuiteScaffold(
    layoutType = layoutType,
    navigationSuiteItems = {
        TopLevelDestination.entries.forEach { destination ->
            item(selected = …, onClick = { navController.navigateToTopLevel(destination) },
                 icon = { Icon(destination.icon, contentDescription = null) },
                 label = { Text(stringResource(destination.labelRes)) })
        }
    },
) { NavHost(…) { … } }
```

```kotlin
// feature:history — decides by window width, renders the slot the app passed in
composable<HistoryRoute> {
    val wide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    if (detailPane == null || !wide) HistoryScreen(onOpenChat = onOpenChat) else HistoryListDetail(detailPane)
}
```

```kotlin
// app — fills the slot with the chat screen
@Composable
private fun HistoryChatDetail(chatId: String?, onOpenModels: () -> Unit) {
    val viewModel: ChatViewModel = hiltViewModel(key = "history-detail")
    LaunchedEffect(chatId) { viewModel.openChat(chatId) }
    ChatScreen(onOpenModels = onOpenModels, viewModel = viewModel)
}
```

## Build note

Two full builds stalled for more than an hour. The Gradle daemon held 4.4 GB while other build JVMs were paged out:
the 16 GB laptop ran out of memory with 12 parallel workers (Robolectric test JVMs take about 1 GB each).
`org.gradle.workers.max=6` and `kotlin.daemon.jvmargs=-Xmx2g` in `gradle.properties` fixed it; the full check is back
to about 2 minutes. Also: adaptive 1.3 deprecates `currentWindowAdaptiveInfo()` in favour of
`currentWindowAdaptiveInfoV2()` (supports the new large/extra-large width classes). The compiler caught it because
warnings are errors.

## Trade-offs

- **Two-pane only for History.** It's the one place where list-detail clearly helps. Settings and Documents may
  follow after the redesign (phase 10).
- **Selection is not a navigation destination.** Back from the two-pane goes to the previous tab, not to the
  previously selected chat. That's simpler and matches common tablet apps; a back stack of selections could come later.
- **Breakpoint 840 dp for two panes.** At 600–839 dp (small tablets, landscape phones) two panes would be cramped,
  so those windows get the rail but a single pane.

## Interview questions

1. **What are window size classes, and why use them instead of checking for a tablet?**
   Breakpoints of the current window's width and height (compact/medium/expanded). The same device changes class
   when folded, rotated or in split screen, so layouts should follow the window, not the device model.

2. **What does `NavigationSuiteScaffold` do for you?**
   It shows the right navigation component for the window (bar, rail, drawer), handles the insets for it, and lets
   you hide it (`NavigationSuiteType.None`), from one list of items.

3. **How do you build list-detail without breaking module boundaries?**
   The list module exposes a slot for the detail; the app module, which may depend on every feature, fills it. The list
   decides when to show two panes (window size) and keeps the selection in saveable state.

4. **Why give the detail pane's ViewModel a key?**
   Without a key, `hiltViewModel()` returns the ViewModel scoped to the current navigation entry. The key makes a
   separate instance, so the pane doesn't share state with another screen of the same type.

5. **What does "consuming window insets" mean in Compose?**
   A parent that has applied padding for some insets marks them consumed (`consumeWindowInsets`), so descendants'
   `windowInsetsPadding`/Scaffolds don't add the same padding again.

6. **What is predictive back, and how do you support it?**
   A system gesture preview of the back destination. Opt in with `android:enableOnBackInvokedCallback="true"`, use
   `OnBackPressedDispatcher`/`BackHandler`/Navigation (not the deprecated `onBackPressed`), and test that custom back
   handling still works.

7. **How did you verify the library API before coding?**
   Resolved the versions from the BOM, downloaded the sources jars from Google Maven, read the function signatures,
   deprecations and inset handling, then wrote code against what actually exists.

8. **How would you test adaptive layouts?**
   Screenshot tests at several window sizes (phone, foldable, tablet; phase 10), Compose UI tests with a fixed
   `WindowAdaptiveInfo`, and the resizable emulator or a real foldable for manual checks.

## To verify (emulator or tablet)

Resizable emulator: compact → bottom bar; medium → rail; expanded → rail + History list-detail. Swipe back on Android
14+: the predictive animation shows.
