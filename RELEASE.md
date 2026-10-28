# Tariffia Wallet Release

This document describes the release process for Tariffia Wallet. The project is in pre-release development and is not yet approved for mainnet distribution.

### 1. Prepare the release

- Confirm the version code and version name in `app/build.gradle.kts`.
- Review the dependency and third-party notice changes.
- Run the available tests, lint, and a clean release build.
- Verify that Tor is still opt-in, disabled by default, and fail-closed.

### 2. Configure secrets and signing

- Provide the required values through the environment variables documented in [README](README.md).
- Use a protected release keystore. The tracked `app/test.keystore` is for debug builds only.
- Do not pass keystore passwords as command-line arguments or commit them to the repository.

### 3. Deploy Digital Asset Links

Publish the release and development asset-link files at the configured hosts:

- `assetlinks/assetlinks.json` → `https://tariffia.app/.well-known/assetlinks.json`
- `assetlinks/assetlinks-dev.json` → `https://dev.tariffia.app/.well-known/assetlinks.json`

Replace the empty `sha256_cert_fingerprints` arrays with the release and development signing-certificate fingerprints. Both files must be served as `application/json` over HTTPS. Do not enable App Links or passkeys until DNS, HTTPS, and the fingerprints have been verified.

### 4. Build and verify

Build the signed APK, record its checksum, and verify that the artifact matches the tagged source. Distribution channels such as Google Play and F-Droid require separate review and configuration.

### 5. Publish

- Create a GitHub release with the changelog and APK artifacts.
- Attach SHA-256 and MD5 checksums using Tariffia-specific filenames.
- Do not ship a build containing placeholder links, empty service credentials, or a test signing key.
