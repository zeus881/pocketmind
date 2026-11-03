# Accessibility checklist

Two parts: what was checked in the code (v2 phase 21, 2026-11-03), and the walkthrough that needs a real phone with
TalkBack (marked "to verify on device" until done).

## Checked in the code

| Check | Result |
|---|---|
| Every icon-only button has a content description | Yes. Every `IconButton`/`IconToggleButton`/FAB passes a `stringResource(...)` description, in all three languages. |
| Icons with `contentDescription = null` are decorative | Yes. All 10 sit next to text that says the same (tab labels, chip labels, the search field's label, row titles, the onboarding illustration, the lock icon above "PocketMind is locked"). |
| Touch targets ≥ 48 dp | Material 3 `IconButton`, `IconToggleButton`, `RadioButton`, `Switch` and `Checkbox` enforce `minimumInteractiveComponentSize` (48 dp). Custom clickable rows are `ListItem`s (≥ 56 dp). Tokens: `PmSize.touchTarget = 48.dp`. |
| Toggles are announced with their state | Settings switches use `Modifier.toggleable(role = Role.Switch)` on the whole row; the Tools button is an `IconToggleButton` ("on/off"); the theme and language choosers use `selectableGroup` + `Role.RadioButton` ("1 of 4, selected"). |
| Headings | Settings section titles use `semantics { heading() }`, so TalkBack users can jump between sections. |
| Live updates | The streaming status ("PocketMind is writing…", "Answer ready"), the model-loading text and the "update ready" bar are polite live regions. |
| Locked app | While locked, the content underneath is removed from the accessibility tree (`clearAndSetSemantics`). |
| Large fonts | Roborazzi screenshot tests render every main screen at **font scale 2.0** (`PHONE_FONT_200`, phase 10). Long Hindi and Punjabi strings wrap instead of being cut off (no fixed heights on text rows). |
| Colour contrast | Material 3 colour roles (`onSurface`, `onSurfaceVariant`, `primary`) from the tuned palette (phase 8); no text is drawn in raw colours (`checkDesignTokens`). |
| Languages | English, Hindi (`values-hi`), Punjabi in Gurmukhi (`values-pa`, phase 21) for every user-facing string; `localeConfig` + in-app picker (Android 13+). |

**Screenshot goldens need re-recording.** Phase 21 changed Settings (language picker, Pro section, diagnostics),
and phases 16–20 changed the chat input, the top bar and the answer footer. Run the "Record screenshots"
workflow on Linux (see PROGRESS.md) and commit the new PNGs. Until then `verifyRoborazziDebug` in CI reports the
differences.

## TalkBack walkthrough (to verify on device)

Turn on TalkBack (Settings → Accessibility → TalkBack). For each screen, swipe right through every element and
check that each one is announced with a clear name, a role, and a state where one applies. Then double-tap the main
actions.

| Screen | Check |
|---|---|
| Onboarding | 4 pages announced with "Page x of 4"; Skip / Next / "I understand — get started" reachable |
| Chat (empty) | Suggestions are buttons; persona chips say "selected"; mic, photo, prompt library, send are named |
| Chat (answer) | The answer is read as text; "Copy answer", "Read aloud", "Regenerate answer" are named; "Used: Calculator" is read; version switcher says "Version 2 of 3" |
| Chat (top bar) | "New chat", "Tools (calculator, date, units, notes), on/off", "More options" |
| History | Search field labelled; each chat row opens; "More options for <title>"; pinned header; folder chips selectable; Undo in the snackbar is reachable |
| Documents | Tabs Summarize / Ask your PDF; "Choose PDF"; progress "Summarizing part x of y" is announced; source chips are buttons |
| Translate | From / To pickers, "Swap languages", text field labelled, "Copy translation" |
| Models | Each card: name, facts, state; Download / Pause / Resume / Cancel / Check file named |
| Settings | Sections are headings; switches announce on/off; theme and language are radio groups; sliders announce their value |
| Personas | Built-in personas say "Built in"; "Delete <name>" buttons; the Pro note is read when "New persona" needs Pro |
| Lock screen | Only "PocketMind is locked" and "Unlock" are reachable; nothing behind it |

Also, at **font size 200 %** (Settings → Display → Font size, largest) and display size largest: nothing overlaps
and every button stays reachable on a small phone. Record findings here with the date.
