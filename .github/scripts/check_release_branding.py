#!/usr/bin/env python3
"""Fail on retired upstream branding or CDN references in shipping files."""

from __future__ import annotations

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCAN_ROOTS = ("app/src", "walletkit/src", "components", "metadata")
TEXT_SUFFIXES = {".kt", ".java", ".xml", ".json", ".txt", ".md", ".pro"}
FORBIDDEN = {
    "cdn.blocksdecoded.com": "retired upstream CDN",
    "allbridge.blocksdecoded.com": "retired upstream CDN",
    "uw_logo_24": "retired U logo resource",
    "ic_unstoppable_icon_20": "retired U logo resource",
    "M4.5,1.75C2.9812": "retired U glyph",
    "launcher_plflag": "retired political launcher",
    "launcher_sinwar": "retired political launcher",
    "Unstoppable Crypto Wallet": "retired store branding",
    "M32.344": "retired U glyph",
    "M148.688": "retired U glyph",
    "M38.8125": "retired U glyph",
    "M21,3V19": "retired U glyph",
}
METADATA_LOCALES = {"en-US"}


def check_text() -> list[str]:
    errors: list[str] = []
    for root_name in SCAN_ROOTS:
        root = ROOT / root_name
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            for needle, reason in FORBIDDEN.items():
                if needle in text:
                    errors.append(f"{path.relative_to(ROOT)}: {reason}: {needle}")
    return errors


def check_metadata() -> list[str]:
    errors: list[str] = []
    metadata = ROOT / "metadata"
    if not metadata.exists():
        return ["metadata/: missing"]

    locales = {path.name for path in metadata.iterdir() if path.is_dir()}
    unexpected = sorted(locales - METADATA_LOCALES)
    if unexpected:
        errors.append(f"metadata/: unsupported locales present: {', '.join(unexpected)}")

    limits = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
    for name, limit in limits.items():
        path = metadata / "en-US" / name
        if not path.exists():
            errors.append(f"{path.relative_to(ROOT)}: missing")
            continue
        value = path.read_text(encoding="utf-8").strip()
        if not value:
            errors.append(f"{path.relative_to(ROOT)}: empty")
        elif len(value) > limit:
            errors.append(f"{path.relative_to(ROOT)}: {len(value)} characters, limit {limit}")

    icon = metadata / "en-US" / "images" / "icon.png"
    if not icon.exists():
        errors.append(f"{icon.relative_to(ROOT)}: missing")
    else:
        header = icon.read_bytes()[:24]
        if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
            errors.append(f"{icon.relative_to(ROOT)}: not a PNG")
        else:
            width, height = struct.unpack(">II", header[16:24])
            if (width, height) != (512, 512):
                errors.append(f"{icon.relative_to(ROOT)}: {width}x{height}, expected 512x512")

    return errors


def main() -> int:
    errors = check_text() + check_metadata()
    if errors:
        print("Release branding check failed:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print("Release branding check passed: no retired branding, CDN references or metadata locales.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
