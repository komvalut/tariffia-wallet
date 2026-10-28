# Phase 3 Endpoint Matrix

Last updated: 2026-10-28

## Purpose

This document is the release gate for Tariffia Wallet's external dependencies. It tracks every service, endpoint, static asset source, and credential that can affect a production build.

Phase 3 must not be considered complete while a required production path still depends on a Horizontal Systems-controlled, Horizontal Systems-branded, or unresolved-license service.

## Distribution Decision

- v1 targets **Google Play** and a **directly distributed APK**.
- F-Droid is deferred. If it is added later, its signing certificate and distribution policy must be added to the assetlinks plan.
- Play builds require an Android App Bundle (`.aab`). Direct builds use a signed APK.
- Play-installed apps are signed by the Play app-signing key; directly installed APKs are signed by the Tariffia release key. Both certificates must be represented in the production assetlinks when both channels are supported.

## Classification

| Class | Meaning | Release rule |
| --- | --- | --- |
| R0 | Tariffia-owned or Tariffia-controlled | Allowed after ownership and HTTPS verification |
| R1 | Independent third party | Allowed after license, privacy, availability, and credential review |
| R2 | Horizontal Systems-controlled, branded, or upstream-hosted dependency | Must be replaced, disabled, or explicitly approved before release |
| R3 | Ownership, license, or contract unresolved | Must be resolved before release |

A dependency is not harmless merely because it is only used on a secondary screen. If it can send wallet, address, portfolio, or usage data, it is in scope.

## Application Configuration

| ID | Surface | Source | Current target | Class | Required action | Status |
| --- | --- | --- | --- | --- | --- | --- |
| APP-01 | Company and app website | `app/build.gradle.kts` | `https://tariffia.app` | R0 | Publish and verify the public site | Pending |
| APP-02 | Analytics information link | `app/build.gradle.kts` | `https://tariffia.app/analytics` | R0 | Publish the page or remove the link | Pending |
| APP-03 | Source repository | `app/build.gradle.kts` | `komvalut/tariffia-wallet` | R0 | Keep public repository available | Verified |
| APP-04 | Security/report email | `app/build.gradle.kts` | `support@tariffia.app` | R0 | Verify mailbox, ownership, and response process | Pending |
| APP-05 | Release notes | `app/build.gradle.kts` | GitHub releases API for `komvalut/tariffia-wallet` | R0 | Verify rate limits and release-tag format | Pending |
| APP-06 | App Links and passkey host | `APP_LINKS_HOST` | `dev.tariffia.app`, `tariffia.app` | R0 | Configure DNS, HTTPS, and `.well-known` | Pending |
| APP-07 | WalletConnect project | `WALLET_CONNECT_PROJECT_ID` | Empty unless supplied | R0 | Create a Tariffia Reown project and inject through CI | Pending |
| APP-08 | WalletConnect metadata | `app/build.gradle.kts` | `https://tariffia.app`, `/icon.png` | R0 | Host the final metadata page and icon | Pending |
| APP-09 | Passkey relying party | `PASSKEY_RP_ID`, `PASSKEY_RP_NAME` | Falls back to `APP_LINKS_HOST` | R0 | Set explicitly for production; verify assetlinks | Pending |
| APP-10 | FAQ | `FAQ_URL` | Horizontal Systems `unstoppable.money` raw content | R2 | Replace with Tariffia-hosted, versioned JSON | Pending |
| APP-11 | Guides | `GUIDES_URL` | Horizontal Systems raw GitHub content | R2 | Replace with Tariffia-hosted, versioned JSON | Pending |
| APP-12 | Coin catalogue | `COINS_JSON_URL` | Horizontal Systems `cryptocurrencies` repository | R2 | Mirror or self-host a pinned, reviewed copy | Pending |
| APP-13 | Provider coin catalogue | `PROVIDER_COINS_JSON_URL` | Horizontal Systems `cryptocurrencies` repository | R2 | Mirror or self-host a pinned, reviewed copy | Pending |
| APP-14 | Market data API | `MARKET_API_BASE_URL` | `api.blocksdecoded.com`, `api-dev.blocksdecoded.com` | R2 | Replace or disable the market feature; no fallback calls | Pending |
| APP-15 | Market and asset CDN | Image URLs in `walletkit` screens | `cdn.blocksdecoded.com` | R2 | Self-host reviewed assets or replace the provider | Pending |
| APP-16 | Allbridge integration | `AllBridgeTracker.kt` | `allbridge.blocksdecoded.com` | R2/R3 | Confirm ownership/license; replace or disable the feature | Pending |
| APP-17 | USwap API | `USWAP_API_BASE_URL` | `swap-api.unstoppable.money`, `swap-dev.unstoppable.money` | R2 | Remove or hard-disable the provider and its network calls | Blocking |
| APP-18 | USwap credentials | `USWAP_API_KEY_ANDROID`, `USWAP_API_KEY_FDROID` | Environment variables | R2 | Remove from the build after USwap is disabled | Pending |
| APP-19 | Ethereum RPC | `BLOCKS_DECODED_ETHEREUM_RPC` | `ethereum-rpc.publicnode.com` | R1 | Rename the setting and keep the independent endpoint | Verified |
| APP-20 | Address risk provider | `CHAINALYSIS_BASE_URL` | Chainalysis public API | R1 | Review contract, privacy impact, and key ownership | Pending |
| APP-21 | Address risk provider | `HASH_DIT_BASE_URL` | HashDit service | R1 | Review contract, privacy impact, and key ownership | Pending |
| APP-22 | Social API token | `TWITTER_BEARER_TOKEN` | Environment variable | R1 | Decide whether the feature ships; remove the token if not | Pending |
| APP-23 | Provider API keys | Build config and environment | Alchemy, Etherscan, BSCScan, Blockscout, TronGrid, Jupiter, TONAPI, THORChain, FastNEAR, UDN | R1 | Use Tariffia-owned keys, rotate, and document quotas | Pending |
| APP-24 | WalletConnect relay | `AppConfigProvider` | `relay.walletconnect.com` | R1 | Verify the Tariffia project and relay policy | Pending |
| APP-25 | Play subscription management package | `SubscriptionServiceGooglePlay.kt` | Hardcoded `io.horizontalsystems.bankwallet` | R2 | Use the actual application ID, e.g. `context.packageName`, before Play billing ships | Pending |
| APP-26 | Rate-app store links | `RateAppManager.kt` | Hardcoded `io.horizontalsystems.walletkit`, HTTP store URL | R2 | Use the actual application ID and HTTPS, or remove the rate prompt | Pending |

## Static Content Hosting

The following content must be served from Tariffia-controlled infrastructure, not proxied from Horizontal Systems:

| Asset | Proposed location | Requirements |
| --- | --- | --- |
| FAQ | `https://tariffia.app/faq.json` | Versioned, reviewed, checksum or ETag, no HS branding |
| Guides | `https://tariffia.app/guides/index.json` | Versioned, reviewed, links point to maintained content |
| Coin catalogue | `https://tariffia.app/data/coins.json` | Schema-compatible, pinned version, reviewed diff |
| Provider catalogue | `https://tariffia.app/data/provider-coins.json` | Schema-compatible, pinned version, reviewed diff |
| Compliance assets | Tariffia-controlled host or bundled resources | Document ownership and update process |
| WalletConnect icon | `https://tariffia.app/icon.png` | Stable URL, correct content type, no redirects |
| Asset links | `https://tariffia.app/.well-known/assetlinks.json` | Production fingerprints only |
| Dev asset links | `https://dev.tariffia.app/.well-known/assetlinks.json` | Debug fingerprints only |

Do not silently fall back to an upstream URL when a Tariffia asset is unavailable. A missing asset must disable or clearly mark the affected feature.

## Node and RPC Inventory

The remaining default nodes are independent public providers unless noted. They still require availability, privacy, and data-handling review.

| Area | Source | Current examples | Action |
| --- | --- | --- | --- |
| EVM networks | `EvmSyncSourceManager.kt` | PublicNode, LlamaRPC, dRPC, official chain RPCs | Keep public defaults; review rate limits and logging |
| BNB Chain | `EvmSyncSourceManager.kt` | Binance dataseeds, bscrpc, Omnia | Keep independent defaults; remove stale HS-specific labels |
| Monero | `MoneroNodeManager.kt` | xmr.rocks, xmr-tw, sethforprivacy, Monerujo, Cake, Stack | Verify each node and trusted status |
| Zano | `ZanoNodeManager.kt` | `node.zano.org` | Verify availability and fallback behavior |
| Zcash | `ZcashLightWalletEndpointManager.kt` | zec.rocks, Stardust | Review latency and privacy |
| THORChain | `ThorchainRpcSourceManager.kt` | Liquify, Keplr, Rorcual, Mayanode | Confirm API key ownership and quotas |
| Tron | `EvmSyncSourceManager.kt`, `TronKitManager.kt` | TronGrid, Pocket | Confirm API keys and rate limits |
| XRP | `XrpRpcSourceManager.kt` | XRPL Cluster, Ripple | Review availability and fallback |
| Other chains | Chain modules | Solana, TON, NEAR, Stellar, XRP, and others | Inventory provider-specific endpoints before release |

## Credentials

- No production credential may be committed to the repository.
- Each credential needs an owner, environment, rotation date, and recovery procedure.
- API keys must be scoped to Tariffia, not inherited from upstream.
- Developer machines must not use production keys.
- CI secrets must be protected, masked, and unavailable to pull requests from forks.
- Removing a provider means removing its key and build configuration, not only hiding its UI.

## Required Feature Behavior

| Feature | Required behavior while backend is unavailable |
| --- | --- |
| USwap | Provider not registered; no swap entry point; no background quote or tracking calls |
| Market | Explicit unavailable state; no request to `blocksdecoded` hosts |
| FAQ and guides | Bundled fallback or clear offline/error state |
| Coin catalogue | Valid bundled catalogue; no upstream fallback |
| Asset images | Local placeholder or bundled asset; no HS CDN request |
| Address risk checks | Feature disabled or clearly unavailable; no silent pass as "safe" |
| WalletConnect | Independent project; no HS metadata |

## Phase 3 Release Gate

A production release is blocked unless all of the following are true:

- [ ] No required production path uses an R2 dependency.
- [ ] No required production path uses an R3 dependency.
- [ ] USwap is removed or hard-disabled with no outbound requests.
- [ ] Market data is replaced or disabled with no blocksdecoded calls.
- [ ] FAQ, guides, and coin catalogues are served by Tariffia.
- [ ] CDN-hosted images are self-hosted or separately approved.
- [ ] Third-party licenses, notices, and contracts are reviewed.
- [ ] All production credentials belong to Tariffia and are stored outside the repository.
- [ ] A network capture or proxy test shows no unexpected HS traffic.
- [ ] `rg` audit is clean except for attribution, license, dependency names, and historical notices.
- [ ] Tor remains opt-in, disabled by default, fail-closed, and compatible with the replacement endpoints.
- [ ] Signing, assetlinks, and App Links checks in `docs/release-checklist.md` pass.

## Verification Commands

Run static checks from the repository root:

```bash
rg -n -i 'unstoppable\.money|blocksdecoded\.com|horizontalsystems' \
  app walletkit walletkit-chain-* subscriptions-* \
  --glob '!**/build/**' \
  --glob '!NOTICE' \
  --glob '!**/test/**'
```

Review every result. Test builds against a proxy or network observer to confirm that disabled features do not make background requests.
