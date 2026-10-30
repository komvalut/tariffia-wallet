# Native TON swap via Omniston

## Scope and settlement choice

Add a TON-only, self-custodial swap flow using Omniston's v1beta8 RFQ API. The
first release supports only the `swap` settlement method for input/output
assets that are native TON or standard TON Jettons. Cross-chain order/HTLC
settlement is intentionally outside this screen.

The generic `USwapProvider` route is not used: it depends on the disabled
Tariffia swap backend. Quote, transaction building, wallet signing, broadcast
and tracking are performed directly by the TON client and Omniston.

`TonTransactionConverter` converts indexed/emulated TON events into UI/history
records; it is not a transaction builder. Omniston's ordered `TonMessage` list
is converted to TonKit's wallet `SendRequestEntity`, emulated for the preview,
then signed and sent by the active TonKit wallet.

## Delta matrix

| # | Area | Existing behavior | TON swap behavior | Why | Checklist | file:line |
|---|---|---|---|---|---|---|
| 1 | Swap route | Generic USwap requires Tariffia API | Request v1beta8 Omniston RFQ directly; only accept swap-settlement quotes with TON input/output assets | Keeps TON swaps independent of the unavailable Tariffia backend and rejects unsupported cross-chain settlements | NET-4 | |
| 2 | Amounts | TON transfers use BigDecimal and Jetton decimals | Convert human amounts to base-unit decimal strings with exact BigDecimal arithmetic; reject excess precision, zero, negative and overflow | RFQ/base-unit values must match the amount the signer sends | AMT-1, AMT-4, TX-7 | |
| 3 | Quote/preview | No native quote engine | Show input/output, Omniston minimum output, slippage, price impact, route, protocol fee and emulated network fee from the selected quote | Quote changes and can expire; the preview must be tied to the exact accepted quote | AMT-1, AMT-3, TX-1 | |
| 4 | Messages | TonKit handles ordinary TON/Jetton transfers and TonConnect requests | Preserve every ordered Omniston message, amount, payload and optional stateInit; reject unsupported message count or invalid BoC/address before signing | Dropping or reordering one message changes the swap contract call | TX-1, TX-7 | |
| 5 | Wallet/account | TON adapters are selected by active account | Only the active full-access TON account can quote/build/sign; available balances are checked again immediately before signing | The quote, input balance and signing key must belong to one account | ACC-1, TX-3 | |
| 6 | Fee and balance | TonKit estimates network fees and exposes native/Jetton balances | Emulate the exact built wallet request before confirmation; include TON gas balance even for Jetton input | Jetton balance does not pay TON network gas | TX-1, TX-3 | |
| 7 | Broadcast/retry | `TonKit.send` submits a signed BOC | Persist the signed external-message query in the existing Room swap record before broadcast; enqueue unique WorkManager tracking; treat send timeouts as unknown and never rebuild/resend automatically | A retry must not spend funds twice, and app/page lifetime must not erase tracking | TX-4, TX-5 | |
| 8 | Settlement/history | Transaction history may report `jettonSwap` after indexing | Update the existing swap-history record from Omniston settlement events; resume pending records at TON plugin startup; UI success is based on settlement, not merely a successful send call | Submitted and settled are distinct states, and history remains available after restart | NET-1, NET-4, HIST-1 | |
| 9 | App entry point | Coin overview Buy/Sell uses generic USwap | TON plugin supplies a TON-only native swap screen from the TON asset overview | Keeps other chains on their existing swap path | ACC-4 | |

## Invariants

- Request only `settlementParams.swap`; reject quotes whose input/output
  `AssetId` differs from the current selection.
- Store all amounts as base-unit strings / `BigInteger`; do not use `Double`.
- Use the newest `quoteUpdated` event. Clear it on `noQuote`, stream failure or
  unsubscribe. Ask Omniston to build against the selected `quoteId` immediately
  before the review step; reject a build error rather than sending stale data.
- Use `minOutputAmount` for user-selected slippage and show it as the minimum
  receive amount. Do not calculate a replacement minimum using floating point.
- Display optional price impact only when Omniston provides it; convert pips
  using 10,000 pips per 1%.
- Require the complete ordered message list and all message fields returned by
  Omniston. Reject more messages than the connected wallet's supported
  capacity.
- Preview by calling TonKit's emulation/details API. Do not generate the
  real signature until the user confirms.
- After signature, submit that exact BOC once. If the response is ambiguous,
  preserve an `unknown/pending` state and continue tracking; do not auto-retry
  with a newly built BOC.
- Save the accepted quote ID, TON trader address and external-message query
  before broadcast. Keep a unique WorkManager job for each pending record and
  resume records whose status is not terminal when the TON plugin starts.
- Persist settlement status and received amount into the existing `SwapRecord`
  history row so the history screen remains authoritative across page and app
  restarts.
- Do not support watch-only accounts or cross-chain `order`/HTLC settlement in
  the first release.

## Verification plan

- Unit tests: decimal conversion, amount precision/range, pips conversion,
  quote asset matching, quote expiry/stream clearing and message validation.
- Test fake Omniston JSON-RPC streams and BuildSwap response parsing without
  network access.
- Verify a real quote and signed transaction on TON testnet or sandbox where
  available. Otherwise record the vector as not run with the exact reason.
- Review the quote, minimum-output and message payload path against the
  Omniston and TON wallet specifications before enabling the UI in release.
