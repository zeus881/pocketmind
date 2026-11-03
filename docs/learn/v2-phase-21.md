# v2 Phase 21 — Punjabi, per-app language, accessibility

Part J of the plan: reach more people. India has more than 30 million Punjabi speakers, and accessibility is part of
"store quality", not an extra.

## What changed

| Where | Change |
|---|---|
| 12 × `values-pa/strings.xml` (new) | Every user-facing string in Punjabi (Gurmukhi script), including plurals; a script checked that en, hi and pa have exactly the same keys |
| `app/res/xml/locales_config.xml` + manifest `android:localeConfig` | English, Hindi, Punjabi: Android 13+ lists them under Settings > Apps > PocketMind > Language |
| Settings | "App language" (Same as the phone / English / हिन्दी / ਪੰਜਾਬੀ), Android 13+, with `LocaleManager` |
| `docs/accessibility-checklist.md` (new) | What was checked in the code, and a TalkBack walkthrough per screen to do on the phone |

## Concepts

**Android picks strings by locale, folder by folder.** `values/` is the default (English). `values-hi/` and
`values-pa/` override it for Hindi and Punjabi. A missing key falls back to English silently, which is why Android
Lint's `MissingTranslation` check is an error here. The script used in this phase compares the keys of every module.

**Format arguments carry positions.** `%1$d of %2$d` is written `%2$d ਵਿੱਚੋਂ %1$d` in Punjabi, because the word order
differs ("of 4, 1"). Numbered arguments let a translation reorder them; plain `%d %d` couldn't.

**Plurals per language.** `<plurals>` chooses between `one`/`other` (and more categories in some languages) by
CLDR rules, never with `if (n == 1)` in code. Punjabi uses the same two categories as English for these strings.

**Per-app language (Android 13+).** `LocaleManager.applicationLocales = LocaleList.forLanguageTags("pa")` makes
Android store the choice, recreate the activity and use `values-pa`. `localeConfig` tells the system which
languages the app really has, so the system settings list only those. On Android 8–12 there is no per-app
setting, so the picker is hidden and the app follows the phone (AppCompat's backport would add a dependency the app
doesn't otherwise need).

**Autonyms.** Languages are listed in their own script ("ਪੰਜਾਬੀ", not "Punjabi"), because someone looking for their
language may not read the current one. Those strings are `translatable="false"`.

**Accessibility as properties of the UI, checked in code.** Each interactive element needs a name (content
description or text), a role (button, switch, radio) and a state (on/off, selected). Each also needs a large enough
touch target (48 dp) and has to survive large fonts. Most come from using Material components correctly
(`toggleable`, `selectableGroup`, `IconToggleButton`); the rest is decided element by element (decorative icons get
`null`, so TalkBack doesn't read the same thing twice).

## Code walkthrough

```kotlin
// Android's own per-app language: no AppCompat, no restart code of our own
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageChooser() {
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val current = remember { manager?.applicationLocales?.get(0)?.language.orEmpty() }
    APP_LANGUAGES.forEach { (tag, label) ->
        ListItem(..., modifier = Modifier.selectable(selected = current == tag, role = Role.RadioButton) {
            manager?.applicationLocales = LocaleList.forLanguageTags(tag)   // "" = follow the phone
        })
    }
}
```

```xml
<!-- values/strings.xml vs values-pa/strings.xml: same key, reordered arguments -->
<string name="feature_onboarding_page_of">Page %1$d of %2$d</string>
<string name="feature_onboarding_page_of">ਸਫ਼ਾ %2$d ਵਿੱਚੋਂ %1$d</string>
```

## Trade-offs

- **The translations need a native speaker.** They were written carefully, with plain words, consistent terms (ਚੈਟ,
  ਮਾਡਲ, ਸੈਟਿੰਗਾਂ) and product names left in English. Tone and idiom still need a native Punjabi reader, and the same
  goes for the Hindi from phase 8 (manual step).
- **No picker below Android 13.** It avoids a dependency, and those phones still get Hindi or Punjabi when the phone
  itself is set to it.
- **The model answers in the language you write in.** The UI language doesn't change the model's language. Small
  models are weaker in Punjabi than in English or Hindi, which is a model limit, not an app setting.
- **TalkBack itself wasn't run.** The code checks catch missing names, roles and states. Order, grouping and how it
  sounds can only be judged on a phone (checklist).

## Interview questions

1. **How does Android choose which strings to show?**
   By resource qualifiers: it matches the configuration's locale to `values-<lang>` (and region), and falls back to
   `values/` for missing keys. Lint's MissingTranslation catches the gaps.

2. **Why use numbered format arguments in strings?**
   Translations can reorder them (`%2$d … %1$d`). Different languages put numbers and nouns in different places.

3. **How do you implement an in-app language picker?**
   On Android 13+, use `LocaleManager.setApplicationLocales` and declare `localeConfig`. On older versions use
   AppCompat's `AppCompatDelegate.setApplicationLocales` (with its storage service), or follow the system language.

4. **What are plurals resources, and why not `if (count == 1)`?**
   `<plurals>` uses each language's plural rules (one, few, many…, from CLDR). Hard-coded English logic breaks in
   languages with more categories.

5. **What makes a Compose screen accessible to TalkBack?**
   Meaningful content descriptions (and `null` for decorative icons), correct roles and states via semantics
   (`toggleable`, `selectable`, `Role.Switch`), headings, live regions for changing text, and logical grouping and
   order.

6. **How do you guarantee 48 dp touch targets in Compose?**
   Material components apply `minimumInteractiveComponentSize`. Custom clickables should use it too, or have at least
   that size, for example a full-width list row.

7. **How do you test for large fonts?**
   Render screens at font scale 2.0 in screenshot tests (Roborazzi), and check on a device with the largest font and
   display size. Avoid fixed heights on text.

8. **Why list languages in their own script?**
   A user who can't read the current UI language must still find their own. "ਪੰਜਾਬੀ" is recognisable to a Punjabi
   reader; "Punjabi" may not be.

## To verify on device

1. Android 13+: Settings → App language → ਪੰਜਾਬੀ. The app redraws in Punjabi, and the same choice shows under
   Android Settings → Apps → PocketMind → Language.
2. Read every screen in Punjabi and Hindi with a native speaker; note awkward phrases in PROGRESS.md.
3. Walk through `docs/accessibility-checklist.md` with TalkBack, and again at the largest font size.
