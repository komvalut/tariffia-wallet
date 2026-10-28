# Tariffia Hosting and Endpoint Deployment

Last updated: 2026-10-28

This document defines what must be deployed on `tariffia.app` and `dev.tariffia.app` before a production release. It covers the asset links file, static content, API reverse proxy, and the disabled USwap routes.

## Current Status

As of 2026-10-28, neither `tariffia.app` nor `dev.tariffia.app` resolves in DNS. All Tariffia-hosted URLs in the app are therefore unavailable until this document has been implemented. See `docs/release-checklist.md` for the release gate.

Do not proxy or fall back to Horizontal Systems infrastructure. A missing Tariffia asset must produce an error or an intentionally disabled state.

## DNS and TLS

1. Create an `A` and/or `AAAA` record for `tariffia.app` and `dev.tariffia.app`.
2. Issue a publicly trusted TLS certificate for both names. Keep the certificate renewable without downtime.
3. Serve HTTPS on port 443. Plain HTTP may redirect to HTTPS, but the HTTPS URL itself must not redirect.
4. Enable TLS 1.2 or newer.
5. Do not put authentication, a login page, a cookie wall, or bot protection in front of `/.well-known/assetlinks.json` or the static JSON paths.

## Required Host Layout

Use the same layout on both hosts. Production and development must use different asset links files and different API backends.

| Path | Content | Content type | Notes |
| --- | --- | --- | --- |
| `/.well-known/assetlinks.json` | `assetlinks/assetlinks.json` for production, `assetlinks/assetlinks-dev.json` for development | `application/json` | Exact path, no redirect, no placeholders |
| `/faq.json` | Reviewed FAQ index | `application/json` | Must match the app's expected FAQ schema |
| `/faq/<language>/<path>.md` | Reviewed FAQ articles | `text/markdown` | Relative links resolve under `/faq/` |
| `/guides/index.json` | Reviewed guide index | `application/json` | Must match the app's expected guide schema |
| `/guides/token_guides/en/*.md` | Reviewed guide articles | `text/markdown` | Other guide paths must also be published if linked |
| `/data/coins.json` | Pinned coin catalogue | `application/json` | Schema-compatible with the app |
| `/data/provider-coins.json` | Pinned provider catalogue | `application/json` | Schema-compatible with the app |
| `/icon.png` | WalletConnect/Tariffia icon | `image/png` | Stable square PNG, ideally 512 x 512 |
| `/analytics` | Analytics information page | `text/html` | Linked from app settings |
| `/api/v1/*` | Tariffia market and WalletConnect metadata API | Proxied | Must implement the paths the app calls |
| `/api/v3/*` | Disabled USwap API | `application/json` | Return `404` or `503`; never proxy to upstream |
| `/cdn/*` | Tariffia image CDN | `image/png` | Required; the app no longer uses the upstream CDN |

The app currently has no bundled FAQ, guide, or coin catalogue. If these files are absent, the affected screens show an error state or have no catalogue data. Publishing them is part of the release gate, not an optional CDN optimization.

## CDN Path Contract

All runtime image URLs are built by `walletkit/core/TariffiaCdn.kt` under `https://tariffia.app/cdn`. Missing images must return `404`; the app then renders its local placeholder rather than falling back to another host.

| Path | Item |
| --- | --- |
| `/cdn/blockchain-icons/32px/<uid>@3x.png` | Blockchain and platform icon |
| `/cdn/coin-icons/32px/<uid>@3x.png` | Coin icon |
| `/cdn/fiat-icons/<code>@3x.png` | Fiat currency icon |
| `/cdn/category-icons/<uid>@3x.png` | Coin category icon |
| `/cdn/fund-icons/<uid>@3x.png` | Investment fund icon |
| `/cdn/treasury-icons/<fundUid>@3x.png` | Treasury logo |
| `/cdn/auditor-icons/<name>@3x.png` | Audit partner logo |
| `/cdn/header-images/<name>@3x.png` | Market, TVL, ETF, and analytics header image |
| `/cdn/etf-tresuries/<ticker>@3x.png` | ETF issuer logo |

The filenames are part of the server contract and must match exactly, including apostrophes or spaces present in upstream names. Review every mirrored asset for ownership and redistribution rights before publication.

## API Routing

The app uses `MARKET_API_BASE_URL=https://tariffia.app/api` and calls paths under `/api/v1/`. For example:

- `/api/v1/defi-protocols/dapps`
- `/api/v1/...` market, coin, and label endpoints

Proxy `/api/` to the Tariffia backend without stripping `/api` unless the backend explicitly expects the stripped path. Preserve the `Host` header and the original request path. Do not rewrite API errors into an HTML page.

`USWAP_API_BASE_URL` is `https://tariffia.app/api/v3/`. Swap, private send, and CrossPay are disabled in the app, so this path must fail closed:

- Return `404` or `503` with `application/json` and no redirect.
- Do not proxy `/api/v3/` to USwap, BlocksDecoded, or any other upstream service.
- Do not return a successful empty response that could be mistaken for a valid quote.

## Nginx Reference

This is a reference configuration, not a complete hardened server configuration. Adapt paths, certificate settings, and the backend address to the actual host.

```nginx
server {
    listen 443 ssl;
    http2 on;
    server_name tariffia.app;

    root /srv/tariffia;
    ssl_certificate     /etc/letsencrypt/live/tariffia.app/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/tariffia.app/privkey.pem;

    add_header X-Content-Type-Options nosniff always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location = /.well-known/assetlinks.json {
        default_type application/json;
        add_header Cache-Control "public, max-age=300";
        add_header X-Content-Type-Options nosniff always;
        try_files $uri =404;
    }

    location = /faq.json {
        default_type application/json;
        add_header Cache-Control "public, max-age=300";
        try_files $uri =404;
    }

    location = /icon.png {
        default_type image/png;
        add_header Cache-Control "public, max-age=86400";
        try_files $uri =404;
    }

    location ^~ /faq/ {
        types {
            text/markdown md;
            application/json json;
        }
        default_type application/octet-stream;
        add_header Cache-Control "public, max-age=300";
        try_files $uri =404;
    }

    location ^~ /guides/ {
        types {
            text/markdown md;
            application/json json;
        }
        default_type application/octet-stream;
        add_header Cache-Control "public, max-age=300";
        try_files $uri =404;
    }

    location ^~ /data/ {
        default_type application/json;
        add_header Cache-Control "public, max-age=300";
        try_files $uri =404;
    }

    location ^~ /api/v3/ {
        default_type application/json;
        return 503 '{"error":"swap_disabled"}';
    }

    location ^~ /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
    }

    location ^~ /cdn/ {
        add_header Cache-Control "public, max-age=86400";
        try_files $uri =404;
    }

    location = /analytics {
        default_type text/html;
        try_files /analytics.html =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

The exact `/.well-known`, `faq.json`, `data`, `guides`, and `api` locations must take precedence over the single-page-site fallback. A request for a missing JSON file must not return `index.html` with status `200`.

## Development Host

Apply the same configuration to `dev.tariffia.app` with these differences:

- Deploy `assetlinks/assetlinks-dev.json`, which contains only development package IDs and debug/test fingerprints.
- Point `/api/` at the development backend.
- Keep `/api/v3/` disabled unless a deliberate development swap backend has been approved.
- Do not copy production fingerprints or production API credentials to the development host.

## Caching and Errors

- Asset links: short TTL during rollout, for example five minutes.
- Static JSON and Markdown: short TTL or versioned paths so corrections propagate.
- Images: longer TTL is acceptable.
- Do not cache `5xx` responses.
- Return the correct `Content-Type`. A browser-rendered JSON body is not proof of the correct header.
- Return `404` for genuinely missing static files. Do not return an HTML application shell.
- Keep error pages out of the asset links and API paths.

## Deployment Order

1. Configure DNS and TLS for `dev.tariffia.app`.
2. Deploy the development asset links and static files.
3. Deploy the development API proxy and the disabled `/api/v3/` response.
4. Verify every development URL with the commands below.
5. Repeat on `tariffia.app` after the production fingerprints and backend are approved.
6. Run the app with a proxy or network capture and confirm that no request goes to a Horizontal Systems host.
7. Update `docs/phase3-endpoint-matrix.md` and `docs/release-checklist.md` with the verified result.

## Verification Commands

Check DNS and TLS:

```bash
getent ahostsv4 tariffia.app
getent ahostsv4 dev.tariffia.app
curl -fsS -I https://tariffia.app/
```

Check the asset links response and content type:

```bash
curl -fsS -D - \
  https://tariffia.app/.well-known/assetlinks.json \
  -o /tmp/assetlinks.json
python3 -m json.tool /tmp/assetlinks.json >/dev/null
```

The headers must show `200`, `content-type: application/json`, and no `location` header. Repeat for `dev.tariffia.app`.

Check the static content and API behavior:

```bash
for path in \
  /faq.json \
  /guides/index.json \
  /data/coins.json \
  /data/provider-coins.json \
  /icon.png
do
  curl -fsS -o /dev/null -w "$path %{http_code} %{content_type}\n" "https://tariffia.app$path"
done

curl -sS -o /dev/null -w '/api/v3/ %{http_code}\n' https://tariffia.app/api/v3/providers
```

The USwap path must not return `200` and must not redirect.

Check Digital Asset Links:

```bash
curl -fsS \
  'https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://tariffia.app&relation=delegate_permission/common.handle_all_urls'
```

Check the app side after installing a build:

```bash
adb shell pm get-app-links app.tariffia.wallet
adb shell pm verify-app-links --re-verify app.tariffia.wallet
```

Passkey registration and sign-in still require a physical-device test.

## Failure Handling

- If DNS, TLS, or any required static file is unavailable, keep the production release blocked.
- If only the market API is unavailable, confirm that market screens show an unavailable state and that the wallet's core send/receive paths still work.
- If the asset links file is unavailable, passkeys and verified App Links must fail safely; do not weaken verification.
- Never add an upstream fallback URL to make an error disappear.
- Never deploy a placeholder fingerprint, placeholder API key, or an HTML error page at a JSON path.
