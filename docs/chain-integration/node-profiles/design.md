# Universal node profiles

## Scope

Introduce reusable per-chain/per-network node profiles, encrypted node
credentials, a profile manager, and a shared settings surface. The manager owns
configuration and selection only; protocol clients and coin adapters remain
chain-specific.

## Delta matrix

| # | Area | Existing behavior | Node-profile behavior | Why | Checklist | Status |
|---|---|---|---|---|---|---|
| 1 | Profile identity | Zcash endpoints are stored by URL; Pirate uses a hardcoded endpoint | Profile identity is `(chainUid, network, profileId)` and credentials are bound to that identity | Prevents a node or secret from one coin/network being selected for another | NET-5 | implemented for ZEC/ARRR |
| 2 | Endpoint validation | Zcash only accepts HTTPS URLs; Pirate parses a host and port | Validate scheme, host, port, chain protocol and TLS policy before save; permit cleartext only for explicitly supported local/onion use | Reject malformed or unsafe endpoints before an adapter sees them | NET-5, NET-4 | implemented |
| 3 | Authentication | Current lightwalletd endpoint API has no auth fields; some other node records store secrets in Room | Store new profile secrets encrypted by an Android Keystore AES-GCM key, with chain/network/profile identity as AAD; only offer auth modes a transport can actually send | Prevent plaintext storage and cross-profile credential reuse | NET-4 | new profile storage implemented; gRPC auth blocked by SDK; legacy Monero/EVM secrets are not migrated |
| 4 | Protocol capability | Chain plugins create and probe their own clients | A protocol-specific handler validates/probes a profile; gRPC lightwalletd and Electrum JSON-RPC are separate handlers | A common settings model must not imply wire-protocol compatibility | NET-4 | gRPC probes implemented for ZEC/ARRR; FIRO not integrated |
| 5 | Activation | Zcash changes endpoints through its plugin reload flow; Pirate currently has no user-driven switch | Selecting a profile emits one chain-scoped change and rebuilds only that chain's adapters | Apply a node change without a process restart or cross-chain churn | NET-3 | implemented for ZEC/ARRR |
| 6 | Failure reporting | Probe/network failures are handled by each chain manager | Keep invalid URL, TLS, auth rejection, protocol mismatch and timeout as distinct test results | Users need actionable diagnostics and a failed node must not look synced | NET-3, NET-4 | partial; auth unsupported is explicit, probe failures are currently grouped as unreachable |
| 7 | Funds and signing | Node profiles do not alter keys or signing | Keys remain local; nodes only provide chain data and transaction broadcast | Node choice must not change signing authority or wallet identity | TX-1, TX-2 | unchanged |
| 8 | Data cleanup | Node settings are not consistently cleaned with account removal | Profile removal deletes the encrypted credential record; deleting a wallet does not delete a shared chain node profile | Credentials should outlive wallets only when the user retains the profile | ACC-3 | implemented by Room cascade |
| 9 | History and amounts | Coin adapters own transaction semantics | Node profile code does not transform transaction amounts or history | These concerns remain chain-specific | AMT-1, HIST-1 | unchanged |

## Protocol and SDK constraint

The current `co.electriccoin.lightwallet.client.model.LightWalletEndpoint`
contains only host, port and TLS. The client creates its own gRPC channel, and
`SlipstreamSynchronizer.new` constructs that client internally. Slipstream's
Rust engine also receives only host, port and TLS. Therefore Basic/Bearer gRPC
metadata cannot be implemented in `walletkit-chain-pirate` alone. Supporting
authenticated lightwalletd requires an upstream SDK/client extension (including
the Kotlin gRPC channel and the Slipstream/Rust sync path) or a separately
designed proxy protocol. Until that work is complete, authenticated gRPC
profiles must not be advertised as working.

The pinned ZODL SDK source is published under AGPL-3.0-only with a separate
commercial-license exception. Any distributable SDK fork or vendored change
needs a license review before it is used in a release.

The profile migration imports existing Zcash URLs only when they contain no
userinfo, query, fragment or path. Unsafe legacy URLs are removed from the old
endpoint table and the active endpoint is reset to a public default; any
credentials previously embedded in those URLs must be re-entered through the
credential form.

## Checklist coverage

| Checklist | Applicability |
|---|---|
| ADDR-1..4 | Not applicable: this feature does not parse or normalize payment addresses |
| AMT-1..4 | Not applicable: this feature does not calculate or display amounts |
| HIST-1..7 | Not applicable: this feature does not create transaction history |
| TX-1..7 | Node switching does not change signing or transaction construction; transport changes require chain-specific validation |
| NET-1..5 | Applicable to endpoint validation, probing, errors and active-node switching |
| ACC-1..5 | ACC-3 applies to credential/profile cleanup; remaining items are unchanged |
| PAR-1..3 | No wallet derivation changes; profile identity and network selection must remain the same on iOS |

## Verification plan

- Unit-test endpoint validation, profile CRUD, active-profile isolation and
  credential AAD mismatch rejection.
- Verify migration from existing Zcash URL settings and Pirate's built-in
  endpoint without losing stored values.
- Verify URL credentials in legacy Zcash endpoint records are not copied into
  the new plaintext endpoint column.
- Test gRPC TLS probe against a valid, invalid-certificate, wrong-network and
  unavailable endpoint.
- Test authenticated gRPC only after the SDK/Rust transport extension exists;
  until then record it as not run rather than treating TLS reachability as auth
  support.
