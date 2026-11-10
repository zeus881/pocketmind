# Release guide — from a tag to 100 % of users

Who: the owner (Sanjay). What: how a new version gets to users safely, and how to stop or undo it.

## One-time setup

1. **Release keystore + 4 GitHub secrets.** See PROGRESS.md, "MANUAL STEPS" (v1, steps 3–4).
2. **First upload by hand.** Play's API can only update an app that exists. Upload the first signed AAB yourself:
   Play Console → your app → Testing → Internal testing → Create release.
3. **Service account for uploads** (used by `.github/workflows/play.yml`):
   1. Google Cloud console → create a project (or use one) → IAM → Service accounts → Create. No roles are needed
      in Google Cloud.
   2. Keys → Add key → JSON. Download the file. It is a password: never commit it.
   3. Play Console → Users and permissions → Invite new user → the service account's e-mail → app permissions:
      **Release to testing tracks** (enough for the internal track), plus **View app information**.
   4. GitHub → Settings → Secrets → Actions → `PLAY_SERVICE_ACCOUNT_JSON` = the whole JSON file content.
4. **Play App Signing** stays on (Play's default): Google re-signs for users. Your upload key can be reset if it's
   lost; the app signing key can't be.

## Every release

1. Make sure `main`/`v2` is green in CI.
2. Pick the version (`vMAJOR.MINOR.PATCH`, optionally `-beta.N`). The versionCode is computed from the tag
   (`AppVersion` in build-logic), so it always increases.
3. Preview the notes: `cd mobile && ./gradlew :androidApp:generateReleaseNotes` and read
   `mobile/androidApp/src/main/play/release-notes/en-US/default.txt` (at most 500 characters, built from `feat:`/`fix:`/`perf:`
   commits since the previous tag). Edit commit messages before tagging, not the file afterwards: the workflow
   writes the file again. For Hindi, add `mobile/androidApp/src/main/play/release-notes/hi-IN/default.txt` by hand; otherwise
   Play shows the English text.
4. `git tag v2.0.0 && git push origin v2.0.0`.
   - `release.yml` → GitHub Release with the signed AAB + APK (for direct installs).
   - `play.yml` → the same AAB on Play's **internal** track (testers get it within minutes).
5. Install from the internal track on your phone, and do the phone checks in PROGRESS.md for anything this release
   changed.

## Staged rollout (production)

Play Console → Production → Create release → "Add from library" (pick the tested internal build) → **Staged
rollout**:

| Step | Share of users | Wait at least | Move on only if |
|---|---|---|---|
| 1 | 5 % | 2 days | no new crash cluster in Android vitals; no 1-star review describing a regression |
| 2 | 20 % | 2 days | crash-free users ≥ 99.5 %, ANR rate below Play's bad-behaviour threshold (0.47 %) |
| 3 | 50 % | 2 days | same, and the In-App Update prompt isn't producing complaints |
| 4 | 100 % | — | — |

PocketMind has no crash SDK (decision 1). Android vitals in Play Console (crashes and ANRs reported by Android
itself to Google) and user reviews are the signals; diagnostic reports that users choose to share (phase 20) add
detail.

## Halt criteria: stop the rollout when

- A crash or ANR cluster appears that the previous version didn't have.
- Chats are lost or can't be opened (the database migrations in phases 11, 14, 16–18 are the riskiest part).
- A model can't be downloaded or loaded on a whole class of phones.

**How:** Play Console → Production → the release → **Halt rollout**. Users who already updated keep the new
version. Nobody else gets it.

## Rollback

Play can't install an older versionCode over a newer one. To "roll back":
1. Halt the rollout (above).
2. Fix forward. Revert the bad commit on a branch, tag the next patch version (`v2.0.1`), and let `play.yml` upload
   it. Test it on the internal track, then roll it out (the 5 % step may be shortened for an urgent fix).
3. If data may be affected, write the recovery steps into the release notes and the GitHub Release text.

## In-App Updates and Review (what users see)

- **Update:** when a newer version is on the user's track, Play shows its own dialog once per check. The app keeps
  working while the update downloads. A bar then offers **Restart** or **Later** (Play installs it by itself when
  the app is closed). Nothing restarts without the user.
- **Review:** after the 5th completed answer, at most once every 90 days, the app asks Play for the review card. Play
  decides whether to show it (its own quota), so not seeing it is normal. To test: an internal-track build on a
  device where your account hasn't reviewed the app; Play shows the card without letting you submit.
