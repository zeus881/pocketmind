# Google Play "Data safety" form — answers for PocketMind

How to fill in Play Console → App content → Data safety. Every answer below follows from the code. If the
code changes (a new SDK, a new network call), check this page again before you publish.

## Section 1 — Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play counts data as "collected" only when it is sent **off the device**. PocketMind's own code makes no network request except downloading model files (no user data in them). There are no analytics, crash-reporting or ad SDKs. |
| Is all of the user data collected by your app encrypted in transit? | *(Not asked when nothing is collected.)* | Model downloads are HTTPS-only anyway (network security config). |
| Do you provide a way for users to request that their data is deleted? | *(Not asked when nothing is collected.)* | In the app: Settings → Your data → Delete all my data; uninstalling also deletes everything. |

Because the answer to the first question is **No**, Play shows **"No data collected"** and **"No data
shared with third parties"** on the store listing.

## Things that do NOT count as collection (and why)

- **Chats, notes, photos, PDFs and settings** are stored only on the phone, in app-private storage (the
  database is encrypted). Data that never leaves the device is not "collected" in Play's sense.
- **Model downloads** go to Hugging Face. The request carries only standard download information, such as the IP
  address, which any HTTP request has. It carries no user content.
- **Speech recognition** uses Android's on-device recognizer; **read aloud** uses offline voices only.
- **Google Play features** (Billing, In-App Updates and In-App Review, from v2 phases 19–20) run in the Play Store
  app and send no user content. Google's own handling of purchases is covered by Google's policy, not by this form
  (Play's guidance: data handled by Google Play services for those APIs need not be declared).

- **PocketMind Pro** (phase 20) is bought through Google Play Billing; the app asks Play which products the account
  owns and receives no payment information. Purchase data handled by Google Play for Play Billing need not be
  declared by the developer.
- **Play Billing's built-in logging is switched off** (found on the phone on 2026-11-03). Billing 9.1 includes Google's
  `datatransport` library, whose CCT backend would upload device diagnostics (model, build fingerprint, carrier code,
  country, locale, time zone) from the app to Google. `AndroidManifest.xml` removes that backend's registration, so
  queued events are deleted on the phone and never sent. `NoTelemetryBackendTest` fails the build if a library
  update registers a backend again. **Re-check this before every release**, together with the list below.
- **Crash log / diagnostic report** (phase 20): stored on the phone; shared only when the user picks a target in the
  share sheet. User-initiated sharing to a destination the user chooses is not "collection" by the developer.

## Before each release, check

1. `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`: no analytics, crash or ad SDK has been
   added (for example Firebase, Crashlytics, Sentry or AdMob).
2. `grep -r "http" --include=*.kt`: the only URLs are the Hugging Face model URLs in `models.json`, plus
   documentation links.
3. `app/src/main/res/xml/network_security_config.xml` still allows HTTPS only.

If any of these changes, update this page, the privacy policy (`docs/privacy-policy.md`) and the Play form
together.
