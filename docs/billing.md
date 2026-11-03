# PocketMind Pro — how billing works

## What Pro is

A **one-time purchase** (Play product id `pocketmind_pro`, type "in-app", not a subscription). It unlocks:

| Feature | Free | Pro | Where it is enforced |
|---|---|---|---|
| Bigger models (catalog `requiresPro: true`) | — | download allowed | `WorkManagerModelRepository.download` → `NEEDS_PRO` |
| PDF length | up to 50 pages (`PdfBoxTextExtractor.FREE_PDF_PAGES`) | any length | `PdfBoxTextExtractor.extract` → `NEEDS_PRO` |
| Your own personas | built-in personas; personas you already made keep working | create new ones | `PersonasViewModel.savePersona` + the Personas screen |
| Voice input, read aloud, photos, tools, chats, export | free | free | — (decision 2: voice stays free for accessibility) |

**Not built yet:** "extra themes" (decision 2). The app has no extra themes yet, so there is nothing to gate. When
themes are added, gate them with `ProStatus.isPro` like the rows above.

**No Pro-only model is in the catalog yet.** Decision 3: a bigger model (likely Gemma 4 E4B) is added only after its
real URL, size and SHA-256 are verified. Add it to `models.json` with `"requiresPro": true`.

## The price

Set **only in Play Console** (Monetize → Products → In-app products → `pocketmind_pro`). The app shows Play's own
formatted price in the user's currency (`ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`). There is no
price in the code (decision 4). Until Play answers (offline, or the product isn't published), the button says
"price shown by Google Play".

## How it works (Play Billing Library 9.1.0, checked in the artifact)

1. **Start of every app run:** `ProStatus.refresh()` → `queryPurchasesAsync(INAPP)`. If a `pocketmind_pro` purchase
   is in state PURCHASED, Pro is on; otherwise it is off (this is how refunds take effect). The answer is cached in
   DataStore, so Pro works offline and right after a restart. Play stays the authority: each refresh overwrites the
   cache.
2. **Buy:** Settings → PocketMind Pro → Play's purchase sheet (`launchBillingFlow`). The result arrives in
   `PurchasesUpdatedListener`.
3. **Acknowledge:** every PURCHASED, not-yet-acknowledged Pro purchase is acknowledged at once (`acknowledgePurchase`).
   **Play refunds purchases that aren't acknowledged within 3 days.**
4. **Pending payments** (e.g. cash at a shop): enabled with `enablePendingPurchases(enableOneTimeProducts())`. They
   unlock nothing until they complete; the next refresh, or the listener, then turns Pro on.
5. **Restore:** Settings → Restore purchase runs step 1 again (a new phone, a reinstall, the same Google account).

All of this runs **through the Play Store app**. PocketMind has no server and sends nothing of its own (decision 5).

## The trade-off: no server-side receipt check

Google recommends checking purchases on your own server (Play Developer API) as well. PocketMind has no server, by
design, so it trusts the Play Billing library on the phone. Consequences:

- **A determined attacker can patch the APK** to skip the check. That is acceptable for a one-time, low-price unlock
  of an app whose core features are free. It would **not** be acceptable for subscriptions, consumables or
  anything with ongoing cost to the developer. Those would need a server.
- **No forged-purchase attack through the network:** the app never accepts a purchase from anywhere except Play's
  own library.
- **Refunds and chargebacks work** without a server, because every start asks Play again.

If PocketMind ever adds subscriptions, add server verification (Play Developer API, real-time developer
notifications) first.

## Setup in Play Console (owner)

1. Monetize → Products → In-app products → **Create product**: id `pocketmind_pro`, a name and a description, and a
   price per country. Activate it.
2. The app must be uploaded (any track) with the BILLING permission, which the library adds automatically.
3. **License testers:** Settings → License testing → add your Google account. Test purchases are then free, show
   "Test card", and can be refunded from the Order management page.
4. Test on the phone: install from the internal track (phase 19). Then Settings → PocketMind Pro → buy with the test
   card → Pro unlocks. Reinstall → Restore purchase → Pro again. Refund in Play Console → restart the app → Pro is off.
