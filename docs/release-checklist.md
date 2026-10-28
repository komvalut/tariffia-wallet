# Tariffia Wallet Release Checklist

Last updated: 2026-10-28

Use this checklist for every production release. A checked box must be backed by a command result, a deployed URL, or a signed artifact, not by intent alone.

## RC1 Snapshot

Date: 2026-10-28

Tag target: `v1.0.0-rc1` on `main` in `komvalut/tariffia-wallet`

App version: `versionCode 185`, `versionName 1.0.0-rc1`

Release status: **BLOCKED**. The code-side migration is ready for an RC tag, but the external release gates are not complete.

| Check | Result | Evidence |
| --- | --- | --- |
| Translation coverage | Pass | `python3 .github/scripts/check_translations.py`: 1735 strings checked against 9 locales, all translated |
| Static UI branding | Pass | No runtime HS branding in resources; preview-only markdown footer updated to `© Tariffia` |
| CrossPay/USwap code gate | Pass | CrossPay tab and manager now require `SWAP_ENABLED`; preview and commit throw an unavailable error when disabled |
| Tariffia DNS | Fail | `tariffia.app` and `dev.tariffia.app` return `Could not resolve host` |
| Tariffia HTTPS endpoints | Fail | All checked URLs return HTTP `000` |
| Asset links | Fail | No host, no deployment, empty fingerprints |
| Release build | Not run | Android SDK unavailable; Java/keytool fails in this environment |
| Store metadata | Fail | `metadata/*` still contains HS branding |
| Network capture | Not run | Requires a built app and a running backend |

Blocking items before removing the RC label:

- Deploy DNS, TLS, asset links, FAQ, guides, coin catalogues, icon, analytics page, and the market API as described in `docs/hosting.md`.
- Resolve APP-15 (`cdn.blocksdecoded.com`) and APP-16 (`allbridge.blocksdecoded.com`), or record an explicit approval.
- Replace HS store metadata and complete the license/notice review.
- Create the release keystore, record both channel fingerprints, and deploy asset links.
- Build from the tag, run the device/network tests, and complete signing and artifact checks.

## 1. Scope and Decisions

- [ ] Release version and supported distribution channels are confirmed: Google Play and direct APK for v1.
- [ ] F-Droid remains deferred, or its signing and listing requirements have been separately approved.
- [x] Version code and version name in `app/build.gradle.kts` are final for RC1: `185` / `1.0.0-rc1`.
- [ ] Release notes and known limitations are written.
- [ ] The release does not claim production readiness for unfinished features.

## 2. Backend and Privacy Gate

- [ ] `docs/phase3-endpoint-matrix.md` has no unresolved R2 required production dependency.
- [ ] `docs/phase3-endpoint-matrix.md` has no unresolved R3 dependency.
- [x] CrossPay is gated by `SWAP_ENABLED` at the tab, view-model, manager, and provider-resolution layers.
- [ ] USwap is removed or hard-disabled and makes no outbound requests, confirmed by a network capture.
- [ ] Market data is replaced or disabled and makes no `blocksdecoded` requests.
- [ ] FAQ, guides, and coin catalogues are served by Tariffia-controlled infrastructure.
- [ ] `docs/hosting.md` has been implemented and verified on both hosts.
- [ ] CDN images are self-hosted or separately licensed and approved.
- [ ] All production API keys belong to Tariffia and are not inherited from upstream.
- [ ] No API key, keystore, password, or private endpoint is committed to the repository.
- [ ] Third-party licenses and NOTICE entries reflect the shipped dependencies and assets.
- [ ] Privacy policy, terms, support contact, and data-handling descriptions are current.
- [ ] Tor is still opt-in, disabled by default, fail-closed, and covered by the privacy description.

## 3. Signing

- [ ] `docs/signing.md` has been followed.
- [ ] The direct release keystore exists and has at least three verified encrypted backups.
- [ ] The Play upload key is configured in Play Console.
- [ ] Play App Signing is enabled and the app-signing certificate is recorded.
- [ ] Release builds use environment or external signing material, never `app/test.keystore`.
- [ ] A release build fails when required signing material is absent.
- [ ] The signed release APK's certificate matches the direct release fingerprint.
- [ ] The Play AAB upload certificate matches the Play Console record.
- [ ] No secret appears in Gradle output, CI logs, or the tagged commit.

## 4. App Links and Asset Links

- [ ] `docs/assetlinks.md` has been followed.
- [ ] `assetlinks/assetlinks.json` contains the Play app-signing and direct release fingerprints.
- [ ] `assetlinks/assetlinks-dev.json` contains the debug/test fingerprint and current debug package IDs.
- [ ] Production assetlinks is deployed at `https://tariffia.app/.well-known/assetlinks.json`.
- [ ] Development assetlinks is deployed at `https://dev.tariffia.app/.well-known/assetlinks.json`.
- [ ] Both URLs return HTTP 200, `Content-Type: application/json`, and no redirect.
- [ ] No placeholder fingerprint remains in either deployed file.
- [ ] The Google Digital Asset Links API returns the expected statement.
- [ ] The approved App Links path list is reflected in `AndroidManifest.xml`.
- [ ] Passkey registration and sign-in work on a physical device.
- [ ] `adb shell pm get-app-links app.tariffia.wallet` shows the expected verification state.

## 5. Build Verification

- [x] Translation coverage check passed: `python3 .github/scripts/check_translations.py`.
- [ ] A clean build was run from the release tag, not from a dirty worktree.
- [ ] Unit tests pass.
- [ ] Lint passes or every remaining warning is reviewed and documented.
- [ ] The release APK installs on a clean device or emulator.
- [ ] The Play AAB is accepted by Play Console internal testing.
- [ ] Wallet creation, backup, restore, send, receive, and settings work on a test wallet.
- [ ] At least one send and one receive path was tested on each supported chain that is claimed as production-ready.
- [ ] WalletConnect pairing works with a Tariffia-owned project.
- [ ] TON Connect and any enabled App Links paths work.
- [ ] Tor off/on/restart and proxy-failure behavior were tested.
- [ ] Disabled features show a clear unavailable state instead of crashing or silently calling a removed backend.
- [ ] A network proxy or capture shows no unexpected Horizontal Systems traffic.
- [ ] `rg` audit for HS domains is clean except for allowed attribution, dependency names, and historical notices.

## 6. Artifact and Metadata

- [ ] The signed artifact has the expected version code/name.
- [ ] The artifact is signed by the expected certificate.
- [ ] SHA-256 and MD5 checksums were generated from the final artifact.
- [ ] Release notes, privacy policy, and support links are reachable.
- [ ] Play Store listing, screenshots, and descriptions contain no upstream branding. `metadata/*` is still HS-branded.
- [ ] Play subscription management uses the actual `app.tariffia.wallet` package ID.
- [ ] Rate-app links use the actual package ID and HTTPS, or the prompt is removed.
- [ ] Play Data Safety and content-rating answers match the actual app behavior.
- [ ] The direct-download page links to the signed APK and checksums over HTTPS.
- [ ] The previous production artifact is retained for rollback.

## 7. Publishing

- [ ] The protected release workflow produced the artifacts.
- [ ] The direct APK and checksums are published from Tariffia-controlled storage.
- [ ] The Play release starts with internal testing, then a staged rollout.
- [ ] The exact commit and tag are recorded in the release notes.
- [ ] The release notes state any disabled or degraded features.
- [ ] The team has a named rollback owner and a tested rollback path.

## 8. Evidence Log

Record the exact commands and results here for the RC or production release.

```text
2026-10-28  python3 .github/scripts/check_translations.py
            1735 strings checked against 9 locales; all translated

2026-10-28  curl -sS https://tariffia.app/
            Could not resolve host: tariffia.app

2026-10-28  curl -sS https://dev.tariffia.app/
            Could not resolve host: dev.tariffia.app

2026-10-28  rg -n -i 'horizontalsystems|blocksdecoded\.com' app walletkit ...
            Remaining runtime URLs: cdn.blocksdecoded.com and allbridge.blocksdecoded.com
```

## 9. Post-Release Verification

- [ ] Install the Play build and the direct APK on separate devices.
- [ ] Confirm the installed certificate on the direct APK.
- [ ] Confirm passkeys and App Links after installation from each channel.
- [ ] Confirm no unexpected outbound requests from disabled features.
- [ ] Confirm RPC and market providers respond within the expected latency.
- [ ] Check crash reports and support channels for signing, passkey, Tor, or backend regressions.
- [ ] Confirm assetlinks remains reachable after CDN/cache changes.
- [ ] Record the release date, signer fingerprints, backend configuration, and any exceptions.
