#!/usr/bin/env python3
"""Generate and validate Tariffia Digital Asset Links files."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = {
    "dev": ROOT / "assetlinks" / "assetlinks-dev.json",
    "release": ROOT / "assetlinks" / "assetlinks.json",
}
PACKAGES = {
    "dev": (
        "app.tariffia.wallet.dev",
        "app.tariffia.wallet.appcenter.dev",
        "app.tariffia.wallet.fdroidci.dev",
    ),
    "release": ("app.tariffia.wallet",),
}
RELATIONS = [
    "delegate_permission/common.handle_all_urls",
    "delegate_permission/common.get_login_creds",
]
FINGERPRINT_RE = re.compile(r"^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$")
PLACEHOLDER_RE = re.compile(r"REPLACE|PLACEHOLDER|TODO|EXAMPLE", re.IGNORECASE)


def normalize_fingerprint(value: str) -> str:
    value = value.strip()
    if value.upper().startswith("SHA256:"):
        value = value.split(":", 1)[1].strip()
    value = value.replace(" ", "").upper()
    if not FINGERPRINT_RE.fullmatch(value):
        raise argparse.ArgumentTypeError(
            "fingerprint must be SHA-256 as 32 uppercase colon-separated bytes"
        )
    return value


def build_file(environment: str, fingerprints: list[str]) -> list[dict]:
    return [
        {
            "relation": list(RELATIONS),
            "target": {
                "namespace": "android_app",
                "package_name": package,
                "sha256_cert_fingerprints": list(dict.fromkeys(fingerprints)),
            },
        }
        for package in PACKAGES[environment]
    ]


def write_file(environment: str, fingerprints: list[str]) -> None:
    path = FILES[environment]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(build_file(environment, fingerprints), indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {path.relative_to(ROOT)} with {len(PACKAGES[environment])} package(s).")


def validate_file(
    environment: str,
    *,
    allow_empty: bool,
    forbidden_fingerprints: set[str],
) -> list[str]:
    path = FILES[environment]
    errors: list[str] = []
    if not path.exists():
        return [f"{path.relative_to(ROOT)}: missing"]

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.relative_to(ROOT)}: invalid JSON: {exc}"]

    if not isinstance(data, list):
        return [f"{path.relative_to(ROOT)}: top-level value must be an array"]

    expected = set(PACKAGES[environment])
    seen: set[str] = set()
    for index, statement in enumerate(data):
        prefix = f"{path.relative_to(ROOT)}[{index}]"
        if not isinstance(statement, dict):
            errors.append(f"{prefix}: statement must be an object")
            continue

        relations = statement.get("relation")
        if not isinstance(relations, list) or set(relations) != set(RELATIONS):
            errors.append(f"{prefix}: relation must contain exactly {RELATIONS}")

        target = statement.get("target")
        if not isinstance(target, dict):
            errors.append(f"{prefix}: target must be an object")
            continue
        if target.get("namespace") != "android_app":
            errors.append(f"{prefix}: target.namespace must be android_app")

        package = target.get("package_name")
        if package not in expected:
            errors.append(f"{prefix}: unexpected package_name {package!r}")
        elif package in seen:
            errors.append(f"{prefix}: duplicate package_name {package}")
        else:
            seen.add(package)

        fingerprints = target.get("sha256_cert_fingerprints")
        if not isinstance(fingerprints, list):
            errors.append(f"{prefix}: sha256_cert_fingerprints must be an array")
            continue
        if not fingerprints and not allow_empty:
            errors.append(f"{prefix}: no fingerprints; deployment remains blocked")
        if len(fingerprints) != len(set(fingerprints)):
            errors.append(f"{prefix}: duplicate fingerprints")
        for fingerprint in fingerprints:
            if not isinstance(fingerprint, str):
                errors.append(f"{prefix}: fingerprint must be a string")
                continue
            if PLACEHOLDER_RE.search(fingerprint):
                errors.append(f"{prefix}: placeholder fingerprint {fingerprint!r}")
                continue
            normalized = fingerprint.strip().replace(" ", "").upper()
            if not FINGERPRINT_RE.fullmatch(normalized):
                errors.append(f"{prefix}: invalid SHA-256 fingerprint {fingerprint!r}")
                continue
            if normalized in forbidden_fingerprints:
                errors.append(f"{prefix}: fingerprint is not valid for this environment")

    missing = sorted(expected - seen)
    if missing:
        errors.append(f"{path.relative_to(ROOT)}: missing packages: {', '.join(missing)}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=sorted(FILES))
    parser.add_argument("--debug-fingerprint", type=normalize_fingerprint)
    parser.add_argument("--play-fingerprint", type=normalize_fingerprint)
    parser.add_argument("--direct-fingerprint", type=normalize_fingerprint)
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate both files instead of writing one",
    )
    parser.add_argument(
        "--allow-empty",
        action="store_true",
        help="with --check, permit empty fingerprint arrays",
    )
    args = parser.parse_args()

    if args.check:
        if args.environment:
            parser.error("--check validates both files; omit --environment")
        debug = {args.debug_fingerprint} if args.debug_fingerprint else set()
        production = {
            value
            for value in (args.play_fingerprint, args.direct_fingerprint)
            if value
        }
        errors = validate_file(
            "dev", allow_empty=args.allow_empty, forbidden_fingerprints=production
        )
        errors += validate_file(
            "release", allow_empty=args.allow_empty, forbidden_fingerprints=debug
        )
        if errors:
            print("Asset Links validation failed:")
            for error in errors:
                print(f"  - {error}")
            return 1
        print("Asset Links validation passed.")
        return 0

    if not args.environment:
        parser.error("provide --environment dev|release, or use --check")

    if args.environment == "dev":
        if not args.debug_fingerprint:
            parser.error("dev generation requires --debug-fingerprint")
        write_file("dev", [args.debug_fingerprint])
    else:
        if not args.play_fingerprint or not args.direct_fingerprint:
            parser.error("release generation requires --play-fingerprint and --direct-fingerprint")
        if args.play_fingerprint == args.direct_fingerprint:
            parser.error("Play app-signing and direct release fingerprints must be different")
        write_file("release", [args.play_fingerprint, args.direct_fingerprint])

    return 0


if __name__ == "__main__":
    sys.exit(main())
