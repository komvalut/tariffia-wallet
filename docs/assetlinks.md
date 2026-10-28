# App Links, Passkeys, and Asset Links

Last updated: 2026-10-28

## Purpose

Android uses Digital Asset Links to verify the relationship between an app package and a web host. Tariffia Wallet uses asset links for passkeys and may use them for HTTPS App Links.

The production and development files are separate. Never publish a debug fingerprint on the production host, and never publish a release fingerprint on the development host.

## Hosts and Files

| Environment | Host | Repository source | Deployed path |
| --- | --- | --- | --- |
| Production | `tariffia.app` | `assetlinks/assetlinks.json` | `/.well-known/assetlinks.json` |
| Development | `dev.tariffia.app` | `assetlinks/assetlinks-dev.json` | `/.well-known/assetlinks.json` |

The templates in `docs/templates/` are starting points. They contain placeholders and must not be deployed as-is.

## Required HTTP Behavior

Both files must be:

- Publicly reachable over HTTPS without authentication.
- Served with `Content-Type: application/json`.
- Returned with HTTP `200`.
- Served without redirects, including redirects to a different host or path.
- Available at the exact `/.well-known/assetlinks.json` path.
- Kept on a short cache TTL during rollout so corrections propagate quickly.

A redirect or an HTML error page will fail verification even if the browser displays the JSON correctly.

## Package Names

Production:

- `app.tariffia.wallet`

Development/debug:

- `app.tariffia.wallet.dev`
- `app.tariffia.wallet.appcenter.dev`
- `app.tariffia.wallet.fdroidci.dev`

The development package list must match the actual application IDs produced by the debug, CI, and F-Droid CI variants. Do not add a production fingerprint to the development file or vice versa.

If F-Droid distribution is added later, its package and signing certificate require a separate review. F-Droid may use its own signing key.

## Certificate Model

Production must account for both channels:

- Play-installed app: Play app-signing certificate.
- Direct APK: Tariffia direct release certificate.

If Play App Signing is not used, the Play build's own signing certificate must be used instead of the Play app-signing certificate.

Use the public SHA-256 certificate fingerprint in uppercase, colon-separated hexadecimal form. Do not use the APK file checksum, the keystore file checksum, or an MD5 value.

Development must use the debug/test certificate fingerprint. The tracked `app/test.keystore` is public test material and must never sign a release.

## Relations

Include both relations unless a concrete security review removes one:

```json
"relation": [
  "delegate_permission/common.handle_all_urls",
  "delegate_permission/common.get_login_creds"
]
```

`get_login_creds` is required for passkeys. `handle_all_urls` is required for verified HTTPS App Links.

## App Links Path Policy

Do not enable `android:autoVerify` for the whole `tariffia.app` host without an explicit path policy. Only paths that the app genuinely handles should be eligible for App Links.

Current candidates to review before adding an App Links intent filter:

- `/ton-connect` for TON Connect links.
- `/wc` only if a deliberate HTTPS WalletConnect entry point is introduced.
- No market/widget paths are required for HTTPS App Links; those currently use the custom `tariffia://` scheme.

The final path list must be approved and added to `AndroidManifest.xml` together with the assetlinks deployment. A broad host filter is not acceptable merely to make passkeys work; passkeys use the assetlinks file and relying-party configuration.

## Deployment Order

1. Generate and record the debug/test certificate fingerprint.
2. Fill the development template and deploy it to `dev.tariffia.app`.
3. Verify the development host with curl and the Google Digital Asset Links API.
4. Install a debug build and test passkey creation and sign-in.
5. Generate the release and Play app-signing fingerprints.
6. Fill the production template and deploy it to `tariffia.app`.
7. Verify with a direct release build and a Play internal-testing build.
8. Only then publish the production release.

## Verification

Check the response and content type:

```bash
curl -fsS -D - \
  https://tariffia.app/.well-known/assetlinks.json \
  -o /tmp/assetlinks.json
```

The headers must show `HTTP/2 200` or `HTTP/1.1 200`, `content-type: application/json`, and no `location:` header.

Validate JSON:

```bash
python3 -m json.tool /tmp/assetlinks.json >/dev/null
```

Check the statements published by Google:

```bash
curl -fsS \
  'https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://tariffia.app&relation=delegate_permission/common.handle_all_urls'
```

On a connected Android device, check App Links verification:

```bash
adb shell pm get-app-links app.tariffia.wallet
adb shell pm verify-app-links --re-verify app.tariffia.wallet
```

For passkeys, use a physical device and a test account to complete registration and sign-in. An emulator result alone is not sufficient.

If a fingerprint or package changes, redeploy the file and trigger verification again. Play-installed apps may continue using the Play signing certificate even when the local upload certificate changes.

## Failure Handling

- Do not weaken verification by accepting unverified links.
- Do not put placeholder strings in a deployed file.
- Do not disable TLS verification in the app.
- If assetlinks is unavailable, passkeys and verified App Links must fail safely and visibly.
- If the production host is not ready, keep the release blocked rather than shipping a placeholder.
