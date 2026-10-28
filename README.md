# Tariffia Wallet

Tariffia Wallet is an independent, non-custodial cryptocurrency wallet for Android. It is derived from Unstoppable Wallet Android `0.54.0` and is being developed as a separate application with its own identity, configuration, and release process.

> **Development status:** active pre-release. The application is not ready for production use or mainnet funds. Branding, backend services, and release infrastructure are still being migrated. Swap, private send, and CrossPay are disabled in the current build while their backend is replaced.

> **Independence:** Tariffia Wallet is not affiliated with, endorsed by, or supported by Horizontal Systems or the Unstoppable Wallet project.

## Overview

Tariffia Wallet inherits the upstream multi-chain wallet architecture and is being adapted to use independent configuration and services.

- Non-custodial key storage and wallet backup
- Multi-chain support, including Bitcoin, EVM networks, Solana, TON, Tron, Monero, Zcash, and others
- WalletConnect support; on-chain swaps and CrossPay are disabled until the Tariffia backend is available
- Passkey-based wallet creation and recovery
- Market data, portfolio tracking, and address monitoring

Some upstream integrations are temporarily unavailable while they are replaced. Do not assume that a feature present in the codebase is production-ready.

## Repository

- GitHub: [https://github.com/komvalut/tariffia-wallet](https://github.com/komvalut/tariffia-wallet)
- Application ID: `app.tariffia.wallet`
- Deep link scheme: `tariffia`
- Minimum Android version: Android 9 (API 28)
- Compile SDK: 37

## Requirements

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools compatible with the configured AGP version
- `local.properties` with `sdk.dir=...`, or an `ANDROID_HOME` environment variable

## Build

The project uses product flavors for distribution. For local development:

```bash
./gradlew assembleBaseDebug
```

Release build:

```bash
./gradlew assembleBaseRelease
```

Tests and lint:

```bash
./gradlew test
./gradlew lint
```

Build outputs are written under `app/build/outputs/apk/`.

Release signing is enabled only when the `TARIFFIA_KEYSTORE_FILE`, `TARIFFIA_KEYSTORE_PASSWORD`, `TARIFFIA_KEY_ALIAS`, and `TARIFFIA_KEY_PASSWORD` environment variables are present. See [docs/signing.md](docs/signing.md). The `test` signing configuration is for development only and must not be used for published builds.

Release operations: [release checklist](docs/release-checklist.md), [signing](docs/signing.md), [asset links](docs/assetlinks.md), [hosting](docs/hosting.md).

## Configuration

Sensitive values are read from environment variables at build time. If a variable is unset, the corresponding `BuildConfig` value is empty and dependent integrations may be unavailable.

| Variable | Purpose |
| --- | --- |
| `WALLET_CONNECT_PROJECT_ID` | WalletConnect/Reown project ID |
| `PASSKEY_RP_ID` | Passkey relying-party ID; defaults to `APP_LINKS_HOST` when unset |
| `MARKET_API_KEY` | Market data API key |
| `ALCHEMY_API_KEY` | EVM RPC provider key |
| `ETHERSCAN_KEY` | Ethereum explorer API key |
| `BSCSCAN_KEY` | BNB Chain explorer API key |
| `BLOCKSCOUT_KEY` | Blockscout API key |
| `TRONGRID_API_KEYS` | TronGrid API keys, comma-separated |
| `SOLANA_JUPITER_API_KEY` | Jupiter API key |
| `TONAPI_KEY` | TON API key |
| `THORCHAIN_API_KEY` | THORChain API key |
| `FASTNEAR_API_KEY` | FastNEAR API key |
| `TWITTER_BEARER_TOKEN` | Social integration token |
| `UDN_API_KEY` | UDN API key |
| `CHAINALYSIS_API_KEY` | Chainalysis API key |
| `HASH_DIT_API_KEY` | HashDit API key |
| `TARIFFIA_KEYSTORE_FILE` | Release keystore path |
| `TARIFFIA_KEYSTORE_PASSWORD` | Release keystore password |
| `TARIFFIA_KEY_ALIAS` | Release key alias |
| `TARIFFIA_KEY_PASSWORD` | Release key password |
| `TARIFFIA_REQUIRE_SIGNING` | Fail release builds when signing material is missing |

Never commit API keys, signing keys, or other secrets to the repository.

### Passkeys and app links

Android passkeys require the relying-party host to publish Digital Asset Links:

```text
https://<PASSKEY_RP_ID>/.well-known/assetlinks.json
```

The intended production host is `tariffia.app`. It must not be enabled until DNS, HTTPS, and the signing-certificate fingerprints are configured. App Links use `APP_LINKS_HOST`, which is currently set per build type in `app/build.gradle.kts`.

### WalletConnect

WalletConnect metadata is configured in `app/build.gradle.kts`. The application name is `Tariffia Wallet`; the metadata URL and icon must be hosted on the Tariffia domain before release.

## Privacy and Tor

Tariffia Wallet is non-custodial. It does not require an account or KYC, and private keys remain on the user's device. Public blockchain data is inherently public, and third-party services may still see the data sent to them even when the network path is private.

The application includes an embedded Tor client. Tor is opt-in and disabled by default. When enabled, supported app traffic, including wallet backends, market data, and explorer/RPC requests, is routed through the app's Tor SOCKS proxy. The app fails closed when the proxy is unavailable and does not silently fall back to a direct connection. Changing the Tor setting requires an app restart.

Tor does not cover traffic from Custom Tabs or external browsers, Google Play services or billing, or other applications on the device. Network-level privacy also depends on the operating system and the selected third-party services.

## Security

- Report vulnerabilities through the repository's GitHub Security tab. Do not open a public issue containing exploit details.
- The repository contains a test keystore for debug builds only. Distribution requires a protected release keystore supplied through the environment, as described in [docs/signing.md](docs/signing.md).
- API keys are supplied through the build environment and are not stored in source control.
- Treat this pre-release software as experimental. Use test wallets and test funds only.

## License

The upstream source is licensed under the MIT License. See [LICENSE](LICENSE) for the license text and [NOTICE](NOTICE) for upstream attribution.

Tariffia-specific copyright and third-party notices are still being finalized.
