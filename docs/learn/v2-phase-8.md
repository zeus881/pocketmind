# v2 Phase 8 — A real design system

Part B of the plan: tokens instead of raw numbers, a tuned type scale, shapes, motion, haptics, illustrated empty and
error states, a dynamic-colour switch, one shared Markdown renderer (AUDIT **L-6**), and a build check that keeps it
all consistent.

## What changed

| Where | Change |
|---|---|
| `core/designsystem/theme/Tokens.kt` (new) | `PmSpacing` (4 dp grid), `PmSize`, `PocketMindShapes`, `PmBubbleShapes`, `PmMotion` (durations + Material easing curves) |
| `core/designsystem/theme/Type.kt` (new) | `PocketMindTypography`: SemiBold titles, taller body line height for long answers, all in `sp` |
| `core/designsystem/theme/Theme.kt` | Applies the typography and shapes; `dynamicColor` now comes from the user's setting |
| `component/PmIllustration.kt`, `IllustrationTone.kt` (new) | Layered illustration drawn in code from theme colours, with a short "settle" animation |
| `component/PmHaptics.kt` (new) | Semantic haptics: `confirm`, `reject`, `toggle`, `longPress` |
| `component/MarkdownText.kt` (new) | The one flicker-free Markdown renderer (replaces two private copies) |
| `component/StateViews.kt` | Empty and error states use the illustration and tokens |
| Settings | "Colours from your wallpaper" switch (Android 12+), English + Hindi |
| 14 screen files | All 92 raw `dp` values replaced with tokens; bubble shapes from `PmBubbleShapes` |
| Chat, Settings | Haptics: send = confirm, error = reject, switches = toggle on/off |
| Docs → Ask | Removed a live region on the streaming answer (same TalkBack chatter as AUDIT M-3) |
| Root build | `checkDesignTokens` task, run by `./gradlew lint`: fails on a raw `dp` or `Color(0x…)` in a screen |
| Tests | `PmHapticsTest` |

## Concepts

**Design tokens.** Named design decisions (`PmSpacing.l = 16.dp`) used everywhere instead of literal values. They
give consistency (one grid, one palette), one place to change a decision, and a shared vocabulary between design and
code ("l spacing" instead of "about 16").

**A 4 dp grid.** Every spacing is a multiple of 4. Layouts line up, and the eye reads the rhythm as order, even if it
can't say why.

**Type scale.** Material 3 defines 15 text roles (display, headline, title, body, label × L/M/S). We keep the sizes
and change two things: SemiBold titles for hierarchy, and a taller body line height for multi-paragraph answers.
Sizes stay in `sp` so the user's font setting (up to 200 %) applies.

**Shapes and identity.** Rounded corners by size class (8 dp chips, 16 dp cards, 24 dp sheets), plus chat bubbles
with a small "tail" corner on the speaker's side. Small decisions like this make an app look designed rather than
stock.

**Motion tokens.** Durations (150/300/500 ms) and easing curves from Material motion. "Emphasized decelerate" starts
fast and lands softly: the right curve for something *arriving* on screen.

**Haptics with meaning.** `HapticFeedbackType.Confirm`, `Reject`, `ToggleOn`/`Off`, `LongPress` map to the phone's
tuned vibration patterns. Wrapping them in `PmHaptics` means "send" feels the same everywhere, and the system's haptic
setting is respected.

**Illustrations as code.** A `Canvas` draws circles in theme colours. It's automatically correct in dark mode and
with wallpaper colours, sharp at any density, tiny, and decorative (no content description).

**Dynamic colour as a choice.** Material You colours feel native; brand colours feel like *PocketMind*. A switch
respects both preferences. Below Android 12 the brand palette is always used.

**Guard rails in the build.** A design system erodes one "quick 15.dp" at a time. `checkDesignTokens` scans app and
feature sources and fails the build on raw `dp` values or hex colours. It's wired into `lint`, so CI enforces it.

## Code walkthrough

```kotlin
object PmSpacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp /* … */ }
object PmBubbleShapes {
    val user = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp)
}
// In a screen:  Modifier.padding(PmSpacing.l)   shape = PmBubbleShapes.user
```

```kotlin
@Composable
fun PmIllustration(icon: ImageVector, modifier: Modifier = Modifier, tone: IllustrationTone = Brand) {
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(PmMotion.MEDIUM_MS, easing = PmMotion.emphasizedDecelerate)) }
    Box(modifier.size(PmSize.illustration).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        Canvas(…) { drawCircle(backdrop); drawCircle(accent, …); drawCircle(disc, …) }
        Icon(icon, contentDescription = null, tint = palette.icon)
    }
}
```
`graphicsLayer` scales at draw time: the animation doesn't trigger layout or recomposition, only a redraw.

```kotlin
// Root build: the guard
val rawDp = Regex("""(?<![\w.])\d+(\.\d+)?\.dp\b""")
check(problems.isEmpty()) { "Use design tokens instead of raw values:\n" + problems.joinToString("\n") }
```

The token replacement itself was mechanical: a script mapped `4/8/12/16/24/32/48.dp` to the spacing tokens, the
three special sizes and the bubble shapes by hand, added the imports, and then the build and tests confirmed nothing
changed in behaviour.

## Trade-offs

- **System font, no custom font.** No extra download or licence, and it's what users' phones are tuned for. A brand
  font could come later as downloadable fonts.
- **Spacing tokens named by size (`l`), not by use (`cardPadding`).** They're reusable and easy to learn. Use-based
  names can come later as aliases if needed.
- **Line coverage doesn't see the design system** (Composables are excluded). Phase 10's screenshot tests are what
  protect it visually.
- **Dynamic colour defaults to on**, so existing users see no change. The brand look is one switch away.

## Interview questions

1. **What are design tokens, and why use them in Compose?**
   Named constants for design decisions (spacing, size, colour, type, shape, motion). They enforce consistency, make
   global changes one-line edits, and give design and engineering a shared vocabulary. In Compose they live in
   objects and in `MaterialTheme` (colorScheme, typography, shapes).

2. **How does `MaterialTheme` propagate colours and type to every composable?**
   Through CompositionLocals provided by `MaterialTheme(colorScheme, typography, shapes)`. Composables read
   `MaterialTheme.colorScheme.primary` etc., so swapping the scheme (dark mode, dynamic colour) updates the whole
   tree.

3. **What is dynamic colour, and when is it unavailable?**
   Material You colour schemes generated from the wallpaper (`dynamicLightColorScheme`/`dynamicDarkColorScheme`),
   available on Android 12+. On older versions you fall back to a static brand scheme.

4. **Why draw illustrations in a Canvas instead of shipping PNGs?**
   They adapt to theme and dark mode, are resolution-independent, add almost no APK size, and can be animated
   cheaply. The cost is that complex artwork is harder to draw in code; for simple geometric spot art it's ideal.

5. **Why animate with `graphicsLayer` rather than changing `Modifier.size`?**
   `graphicsLayer` transforms at draw time: no remeasure or relayout, so it's smooth and cheap. Animating size forces
   layout on every frame.

6. **How do you stop a design system from eroding over time?**
   Make the right thing easy (tokens, shared components) and the wrong thing fail: lint rules or build checks for raw
   values, screenshot tests for visual regressions, and code review.

7. **When should you add haptic feedback, and how do you keep it consistent?**
   For confirmations, errors, toggles and long presses: moments where touch confirms an action. Centralise it
   (`PmHaptics`) with semantic names that map to system haptic types, and respect the system setting.

8. **Why keep text sizes in `sp`?**
   `sp` scales with the user's font-size preference, which is essential for accessibility (up to 200 %). `dp` would
   ignore it. Layouts must then be tested at large font scales (phase 10 screenshots at font 2.0).
