#!/usr/bin/env python3
"""Builds settings/src/main/assets/google_fonts.tsv, the catalog behind the Google Fonts picker.

Input is Google Fonts' public metadata (https://fonts.google.com/metadata/fonts), passed as a
local file path or fetched when omitted. Output: one family per line, most popular first:

    family<TAB>category

where category is one of sans|serif|display|handwriting|mono. The picker styles the key labels
(Latin letters), so every family is listed; Bangla glyphs fall back to the system font. Brand fonts (e.g. Google Sans) are left out: the Play services font
provider does not serve them.
"""
import json
import sys
import urllib.request
from pathlib import Path

URL = "https://fonts.google.com/metadata/fonts"
OUT = Path(__file__).resolve().parent.parent / "settings/src/main/assets/google_fonts.tsv"
CATEGORIES = {
    "Sans Serif": "sans",
    "Serif": "serif",
    "Display": "display",
    "Handwriting": "handwriting",
    "Monospace": "mono",
}


def main() -> None:
    if len(sys.argv) > 1:
        raw = Path(sys.argv[1]).read_text(encoding="utf-8")
    else:
        with urllib.request.urlopen(URL) as resp:
            raw = resp.read().decode("utf-8")
    families = json.loads(raw)["familyMetadataList"]
    rows = []
    for f in families:
        if f.get("isBrandFont") or not f.get("isOpenSource", True):
            continue
        name = f["family"]
        if "\t" in name or "\n" in name:
            continue
        category = CATEGORIES.get(f.get("category"), "sans")
        rows.append((f.get("popularity", 1 << 30), name, category))
    rows.sort(key=lambda r: (r[0], r[1]))
    OUT.write_text("".join(f"{n}\t{c}\n" for _, n, c in rows), encoding="utf-8")
    print(f"wrote {len(rows)} families to {OUT}")


if __name__ == "__main__":
    main()
