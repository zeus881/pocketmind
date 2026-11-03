# v2 Phase 20 — PocketMind Pro (Play Billing) and local diagnostics

Part I of the plan. Approved decisions 1, 2, 4 and 5: no crash SDK; a one-time Pro purchase (voice stays free);
the price is set only in Play Console; Play features run through the Play Store app.

## What changed

| Where | Change |
|---|---|
| `libs.versions.toml` | Play Billing Library **9.1.0** (`billing-ktx`, Google Maven); `BillingClient`, `PendingPurchasesParams`, `enableAutoServiceReconnection`, `queryPurchasesAsync`/`queryProductDetails`/`acknowledgePurchase` (ktx) checked with `javap` |
| `core:billing` (new module) | `ProStatus` (isPro, price offer, refresh, buy); `Entitlement` rules; `BillingGateway` + `PlayBillingGateway`; `PlayProStatus` (Play's answer wins, cached in DataStore for offline) |
| Gates | `WorkManagerModelRepository.download` (catalog `requiresPro`), `PdfBoxTextExtractor` (free version: 50 pages), `PersonasViewModel` + screen (new custom personas) → `ErrorKind.NEEDS_PRO` with a message pointing to Settings |
| `core:common/diagnostics` (new) | `CrashLog` (uncaught exceptions, last 3, on the phone), `DeviceFacts`, `DiagnosticReport` |
| App | the crash log is installed first in `Application.onCreate`; Pro is refreshed once per start |
| Settings | PocketMind Pro section (Play's price, Buy, Restore purchase); "Share diagnostic report" → share sheet |
| `core:testing` | `FakeProStatus` |
| Docs | `docs/billing.md` (gates, price, flow, the no-server trade-off, Play Console setup); privacy policy and data-safety updated |
| Tests | entitlement and status (4), Pro model gate, persona gate, settings Pro/restore/diagnostics, crash log and report |

## Concepts

**Entitlement, not "purchase events".** The question the app asks is "does this account own Pro *right now*?",
and the answer comes from `queryPurchasesAsync`. That one question covers buying on another phone, reinstalling,
refunds and payments that complete later. The purchase listener only speeds up the moment of buying.

**Acknowledge or lose the sale.** Play refunds a purchase automatically if the app doesn't acknowledge it within 3
days. Acknowledging every unacknowledged PURCHASED Pro purchase whenever we see it (refresh or listener) makes that
safe, even if the app was killed during the purchase.

**Pending purchases.** In some countries a purchase can be paid in cash later. The purchase exists, but is PENDING.
Unlocking it before it is PURCHASED would give Pro away for free. `Entitlement.ownsPro` only counts PURCHASED.

**Cache for offline, Play for truth.** `isPro = Play's answer this session ?: the cached answer`. The cache makes Pro
work in airplane mode right after a restart (PocketMind is an offline app!). Each successful refresh overwrites it,
so a refund turns Pro off at the next start with a network connection.

**Gates live where the rule is enforced, not in the UI.** The UI can't be trusted to remember every path. The PDF
limit sits in the extractor, so both Summarize and Ask obey it. The model limit sits in the repository's `download`,
so no screen can start a Pro download. The UI only explains (`NEEDS_PRO` → "PocketMind Pro reads any length").

**Diagnostics without surveillance.** A crash SDK sends stack traces to a server automatically. Here
`Thread.setDefaultUncaughtExceptionHandler` writes the trace to a file and then *calls the previous handler*, so
Android still shows its crash dialog and nothing about the crash changes. The report is built only when the user
asks, it goes through the share sheet (the user sees the text and picks the target), and it has no chat content.

## Code walkthrough

```kotlin
// PlayProStatus: Play's answer for this session wins; the cache covers the time before (and offline)
override val isPro = combine(settings.proCached, confirmed) { cached, fromPlay -> fromPlay ?: cached }
    .stateIn(scope, SharingStarted.Eagerly, false)

private suspend fun apply(purchases: List<PurchaseRecord>) {
    Entitlement.toAcknowledge(purchases).forEach { gateway.acknowledge(it.token) } // or Play refunds in 3 days
    val owned = Entitlement.ownsPro(purchases)                                     // PURCHASED only, not PENDING
    confirmed.value = owned
    settings.setProCached(owned)
}
```

```kotlin
// The PDF gate, before any text is extracted
if (document.numberOfPages > FREE_PDF_PAGES && !pro.isPro.value) {
    return@withContext AppResult.Failure(ErrorKind.NEEDS_PRO)
}
```

```kotlin
// CrashLog.install: record, then behave exactly as before
val previous = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    runCatching { record(thread.name, error) }
    previous?.uncaughtException(thread, error)
}
```

## Trade-offs

- **No server receipt validation.** A patched APK can unlock Pro. That is acceptable for a one-time low-price unlock
  whose core app is free; it is not acceptable for subscriptions (see `docs/billing.md`).
- **Free PDF limit of 50 pages.** My choice, to keep v1's "30-page PDF summarized" free; the owner can change one
  constant.
- **Existing custom personas keep working without Pro.** Taking away what users already made would feel like a
  bait-and-switch; only creating new ones is Pro.
- **Extra themes aren't built.** Decision 2 lists them, but the app has no extra themes to gate yet; this is noted in
  `docs/billing.md` and PROGRESS.md instead of shipping placeholder themes.
- **Crash log only for JVM crashes.** Native crashes (in LiteRT-LM) kill the process without a Java exception. The
  load guard from phase 1 records those around model loading, and Android vitals in Play Console shows them.

## Interview questions

1. **How do you implement a one-time in-app purchase on Android?**
   Create an INAPP product in Play Console and connect a BillingClient (with pending purchases enabled). Query
   ProductDetails for the price, then call `launchBillingFlow`. Handle `onPurchasesUpdated`, acknowledge PURCHASED
   purchases, and get the entitlement from `queryPurchasesAsync` at every start.

2. **Why must purchases be acknowledged?**
   Play refunds unacknowledged purchases after 3 days. This protects users when an app fails to deliver what they
   paid for.

3. **What is a pending purchase, and how should the app treat it?**
   A purchase whose payment hasn't completed (for example cash). Don't grant the item; grant it when its state
   becomes PURCHASED (seen by the listener or the next query).

4. **Client-side or server-side purchase verification?**
   A server (Play Developer API, real-time notifications) is stronger and needed for subscriptions and anything
   valuable or ongoing. Client-only is acceptable for a cheap one-time unlock when you accept that a patched APK
   can bypass it.

5. **How do refunds reach an app without a server?**
   `queryPurchasesAsync` no longer returns a refunded purchase. Because the app asks at every start and lets Play's
   answer overwrite its cache, the entitlement disappears.

6. **Where should feature gates live?**
   At the point the rule is enforced (repository, extractor, use case), so every path obeys it. The UI only explains
   and offers the upgrade.

7. **How do you collect crash information without a crash SDK?**
   Install an uncaught-exception handler that records the trace locally and then delegates to the previous handler.
   Offer a user-initiated report through the share sheet. Use Android vitals for aggregate crash and ANR data.

8. **How do you test billing logic without Play?**
   Put the Play calls behind a small gateway interface and test the rules (ownership, pending, acknowledgement,
   refunds, caching) with a fake. Then test the real flow with license testers on an internal-track build.

## To verify on device (needs the Play Console setup in docs/billing.md)

1. Internal-track build. Settings → PocketMind Pro shows Play's price.
2. Buy with the license-test card → "PocketMind Pro is unlocked". Then: a PDF over 50 pages works, and "New persona"
   is offered.
3. Uninstall and reinstall → Settings → Restore purchase → unlocked again.
4. Refund the test order in Play Console → restart the app (online) → Pro is off; existing personas still work.
5. Settings → Share diagnostic report → read the text in the share sheet (no chats).
