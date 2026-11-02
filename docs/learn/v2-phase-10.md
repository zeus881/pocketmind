# v2 Phase 10 — Screen redesign and screenshot tests

Parts B and F of the plan: every screen polished on the new design system, and locked down by **64 golden screenshots**
(16 screen states × phone light, phone dark, tablet, phone at 200 % font). Also AUDIT **L-7** (jump to latest).

## What changed

| Where | Change |
|---|---|
| Chat | Answers as full-width text (no grey bubble), so code blocks and tables have room; calmer user bubble; pill-shaped input; welcome screen with four suggestion chips; "jump to latest" button; messages animate in (`animateItem`); centred at a readable width on wide windows |
| All screens | `Modifier.readableWidth()` (new, design system): content capped at 720 dp and centred |
| Brand palette | Softer light primary container; amber tertiary (illustration accents); teal-tinted surface containers instead of Material's default purple-tinted ones |
| Settings, Translate | `SettingsActions` / `TranslateActions` interfaces: the screens render without a ViewModel (tests, previews) |
| `core:testing/Screenshots.kt` (new) | `captureScreenshots(name) { … }`: 4 device variants with Roborazzi + Robolectric native graphics |
| Feature convention + app | Roborazzi Gradle plugin 1.77.0; golden images in each module's `src/test/screenshots/` |
| 9 screenshot test classes | Chat (4 states), History (2), Models, Settings, Benchmark, Onboarding, Documents (4), Translate, Lock |
| CI | `verifyRoborazziDebug` step + diff upload on failure; a manual **Record screenshots** workflow that records the images on Linux |

## Concepts

**Screenshot (snapshot) testing.** Render a screen in a known state, save the pixels as a *golden* image, and on every
later run render again and compare. It catches visual regressions that unit tests can't see: a colour change, a
clipped label at large font, a layout that breaks on tablets.

**How it runs without an emulator.** Robolectric's *native graphics* mode (`@GraphicsMode(NATIVE)`) uses Android's real
rendering code (Skia) on the JVM. Roborazzi launches the composable in a test activity with the requested size, font
scale and night mode, then captures the bitmap. A screen renders in about a second on a laptop.

**Record vs verify.**
- `./gradlew recordRoborazziDebug` writes or updates the goldens. Do this when a change is *meant* to change the UI,
  then review the new images in the PR.
- `./gradlew verifyRoborazziDebug` fails if anything differs (CI), and uploads the diff images.
- A plain `testDebugUnitTest` skips the capture: Roborazzi only draws in its own tasks.

**Golden images are code.** They're committed and reviewed like code. A PR that changes the UI shows the before/after
pictures in the diff.

**Cross-platform rendering.** Fonts are rasterised slightly differently on Windows, macOS and Linux. Goldens recorded
on a Windows laptop may differ by a few pixels on the Linux CI machine. The `Record screenshots` workflow records them
on CI's own OS; commit those, and verification becomes exact.

**Testable screens: state in, events out.** A screen that takes `vm: SettingsViewModel` can only be drawn with Hilt,
DataStore and coroutines. Taking `vm: SettingsActions` (an interface the ViewModel implements) lets tests pass a no-op
object. This is the same "stateless content" split the other screens already had.

**Design decisions visible in the images.**
- *Answers without bubbles.* An answer is a document, not a chat line. Full width means less wrapping of code and
  tables; user messages stay bubbles, so turns are still easy to tell apart.
- *Readable width.* About 45–90 characters per line reads best; on a 1,280 dp tablet that means centring at ~720 dp.
  In the chat, the side margins are *list padding*, so the whole width still scrolls.
- *Suggestions.* An empty chat is a blank page. Four concrete examples show what the assistant can do and get the
  first message sent with one tap.

## Code walkthrough

```kotlin
// core:testing — one call renders 4 variants
fun captureScreenshots(name: String, devices: List<ScreenshotDevice> = ScreenshotDevice.entries,
                       content: @Composable () -> Unit) {
    devices.forEach { device ->
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_${device.name.lowercase()}.png",
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = device.widthDp, heightDp = device.heightDp)
                fontScale(device.fontScale)
                if (device.dark) uiMode(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
            },
        ) { PocketMindTheme(themeMode = if (device.dark) DARK else LIGHT, dynamicColor = false) { content() } }
    }
}

// A test
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenshotTest {
    @Test fun emptyChat() = captureScreenshots("chat_empty") { render(ChatUiState(model = ModelUi.Ready("GPU"))) }
}
```

```kotlin
// MessageList: readable width without shrinking the scroll area
BoxWithConstraints(modifier.fillMaxSize()) {
    val sideMargin = maxOf(PmSpacing.l, (maxWidth - PmSize.readableContentWidth) / 2)
    LazyColumn(state = listState, reverseLayout = true,
               contentPadding = PaddingValues(horizontal = sideMargin, vertical = PmSpacing.m)) { … }
    AnimatedVisibility(visible = showJump, …) {           // derivedStateOf { firstVisibleItemIndex > 0 }
        SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) { … }
    }
}
```
`derivedStateOf` matters here: `firstVisibleItemIndex` changes on every scroll frame, but `showJump` changes only
when it crosses 0, so the button recomposes twice instead of 60 times a second.

## Trade-offs

- **Goldens from Windows.** They are correct for this machine; CI may need the Linux recording once (manual step).
  The alternative, tolerant comparisons, hides real small regressions.
- **Four variants per state.** That covers the main risks (theme, width, font size) at reasonable cost: 64 images and
  about a minute of rendering. Landscape phone and RTL could be added later.
- **No custom illustrations per screen.** The shared drawn illustration keeps one visual language and zero assets;
  bespoke art could replace it later without changing any screen code.

## Interview questions

1. **What does a screenshot test catch that a UI test doesn't?**
   Visual regressions: colours, spacing, clipping, overlapping, broken layouts at large font or on tablets. A UI test
   checks behaviour (this button exists and does X), not appearance.

2. **How do screenshot tests run without an emulator?**
   Robolectric with native graphics renders Compose using Android's real graphics stack on the JVM; Roborazzi
   captures the bitmap and compares it with the stored golden.

3. **When do you re-record goldens, and how do you review them?**
   When a change is intended to alter the UI. Re-record, then review the image diff in the PR like code. A failing
   verify on an unrelated PR means an unintended visual change.

4. **Why can screenshots differ between a laptop and CI?**
   Font rasterisation and anti-aliasing differ per OS, and so do some graphics library details. Record the goldens on
   the same OS that verifies them (CI), or keep a fixed rendering environment.

5. **What makes a composable easy to screenshot test?**
   Stateless content: it takes immutable UI state and callbacks (or a small actions interface), with no direct
   ViewModel, repository or Hilt access. Then any state can be rendered.

6. **Why use `derivedStateOf` for the "jump to latest" button?**
   It derives a boolean from a fast-changing value and only notifies readers when the boolean changes, avoiding
   recomposition on every scroll frame.

7. **How do you keep long lines readable on a tablet without making margins unscrollable?**
   Put the margin into the list's `contentPadding` (computed from `BoxWithConstraints`) instead of making the list
   narrower. The content is centred, and touches anywhere scroll it.

8. **Which variants would you screenshot for a production app, and why?**
   Light/dark (theme bugs), compact/expanded width (layout), large font (accessibility), RTL (mirroring), and
   long-text locales (truncation). Pick the set that matches where your real bugs come from.

## Manual step

On GitHub: Actions → **Record screenshots** → Run workflow; download `golden-screenshots`; copy the PNGs into the
modules' `src/test/screenshots/`; commit. From then on, CI's `verifyRoborazziDebug` compares like for like.
