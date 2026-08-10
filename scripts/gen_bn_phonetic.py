#!/usr/bin/env python3
"""Regenerates suggestions/src/main/assets/dictionaries/bn_phonetic.txt.

The index maps the ambiguity-collapsed phonetic key of a Bangla word to the real words that
spell to it, best-first by corpus frequency. The key algorithm mirrors
`BanglaPhoneticKey.banglaKey` in :suggestions exactly — `BanglaPhoneticKeyTest` re-derives every
shipped line with the Kotlin implementation, so the two cannot drift apart silently.

Word sources: bn_frequency.txt (with real frequencies) plus any word already present in the
previous index (kept at a low synthetic frequency so coverage never regresses).

Run from the repo root:  python3 scripts/gen_bn_phonetic.py
"""

from __future__ import annotations

import sys
from collections import defaultdict

FREQ_PATH = "suggestions/src/main/assets/dictionaries/bn_frequency.txt"
INDEX_PATH = "suggestions/src/main/assets/dictionaries/bn_phonetic.txt"

MAX_WORDS_PER_KEY = 8

HASANT = "্"
NUKTA = "়"
KHANDA_TA = "ৎ"
TRANSPARENT = {"ঁ", "ঃ", NUKTA, "‌", "‍"}  # chandrabindu, visarga, nukta, ZW*

RI_VOWEL = "\u0002"  # internal marker for ঋ/ৃ ("ri"), matching the Kotlin sentinel

# Letter + nukta sequences the transliteration engine emits precomposed; the assets follow it.
PRECOMPOSE = {
    "\u09A1\u09BC": "\u09DC",  # dda + nukta  -> RRA
    "\u09A2\u09BC": "\u09DD",  # dha + nukta  -> RHA
    "\u09AF\u09BC": "\u09DF",  # ya  + nukta  -> YYA
}

VOWEL_SOUND = {
    "অ": "o", "ও": "o", "ো": "o",          # ো
    "আ": "a", "া": "a",                     # া
    "ই": "i", "ঈ": "i", "ি": "i", "ী": "i",
    "উ": "u", "ঊ": "u", "ু": "u", "ূ": "u",
    "ঋ": RI_VOWEL, "ৃ": RI_VOWEL,
    "এ": "e", "ে": "e",
    "ঐ": "i", "ৈ": "i",                     # `oi`: the leading `o` drops
    "ঔ": "u", "ৌ": "u",                     # `ou`: likewise
}

VOWEL_SIGNS = {
    "া", "ি", "ী", "ু", "ূ",
    "ৃ", "ে", "ৈ", "ো", "ৌ",
}

ANUSVARA = "\u0001"  # internal marker for ং/ঙ ("ng")

CONSONANT_SOUND = {
    "ক": "k", "খ": "k",
    "গ": "g", "ঘ": "g",
    "ঙ": ANUSVARA, "ং": ANUSVARA,
    "চ": "c", "ছ": "c",
    "জ": "j", "ঝ": "j",
    "ঞ": "n", "ণ": "n", "ন": "n",
    "ট": "t", "ঠ": "t", "ত": "t", "থ": "t", KHANDA_TA: "t",
    "ড": "d", "ঢ": "d", "দ": "d", "ধ": "d",
    "প": "p", "ফ": "p",
    "ব": "b", "ভ": "b",
    "ম": "m",
    "য": "j",  # ja-phala (after hasant) is handled below
    "র": "r",
    "ল": "l",
    "শ": "s", "ষ": "s", "স": "s",
    "হ": "h",
    "\u09DC": "r", "\u09DD": "r",  # RRA, RHA
    "\u09DF": "y",  # YYA
}

NUKTA_CLASS = {"d": "r", "j": "y"}

# Conjuncts whose roman spelling does not follow from their letters, indexed under the
# alternative spellings too (ক্ষ is typed kkh/kh, জ্ঞ gg/gy, ওয়- often w-).
SPELLING_VARIANTS = [
    ("ক" + HASANT + "ষ", "ক" + HASANT + "ক"),
    ("জ" + HASANT + "ঞ", "গ" + HASANT + "গ"),
    ("জ" + HASANT + "ঞ", "গ" + HASANT + "য"),
    ("\u0993\u09DF", "\u09AC"),
]


def precompose(text: str) -> str:
    for decomposed, composed in PRECOMPOSE.items():
        text = text.replace(decomposed, composed)
    return text


def has_explicit_vowel_next(word: str, start: int) -> bool:
    """True when the consonant before [start] is followed by a vowel sign or hasant."""
    i = start
    while i < len(word):
        ch = word[i]
        if ch == HASANT:
            return True
        if ch in VOWEL_SOUND:
            return ch in VOWEL_SIGNS
        if ch not in TRANSPARENT:
            return False
        i += 1
    return False


def events(word: str):
    """The word as ('C', class) / ('V', vowel-or-None) events, mirroring banglaKey's walk."""
    out = []
    after_hasant = False
    i = 0
    n = len(word)
    while i < n:
        ch = word[i]
        if ch == HASANT:
            after_hasant = True
            i += 1
            continue
        vowel = VOWEL_SOUND.get(ch)
        if vowel is not None:
            if vowel == RI_VOWEL:
                out.append(("C", "r"))
                out.append(("V", "i"))
            elif vowel == "o":
                out.append(("V", None))
            else:
                out.append(("V", vowel))
            after_hasant = False
            i += 1
            continue
        cls = CONSONANT_SOUND.get(ch)
        if cls is None:
            i += 1
            continue
        if ch == "য" and after_hasant:
            cls = "y"
        carries_inherent = cls != ANUSVARA and ch != KHANDA_TA
        i += 1
        if i < n and word[i] == NUKTA:
            i += 1
            out.append(("C", NUKTA_CLASS.get(cls, cls)))
        elif cls == ANUSVARA:
            out.append(("C", "n"))
            out.append(("C", "g"))
        else:
            out.append(("C", cls))
        after_hasant = False
        if carries_inherent and not has_explicit_vowel_next(word, i):
            out.append(("V", None))
    return out


def assemble(evts) -> str:
    """Collapses events into a key: same-class consonants merge only when no vowel separates them."""
    sb = []
    vowel_since = True
    for kind, val in evts:
        if kind == "V":
            if val:
                sb.append(val)
            vowel_since = True
        else:
            if vowel_since or not sb or sb[-1] != val:
                sb.append(val)
            vowel_since = False
    return "".join(sb)


def bangla_key(word: str) -> str:
    return assemble(events(word))


def keys_for(word: str) -> list[str]:
    """The base key plus the keys of alternative spellings, de-duplicated, base first."""
    out = [bangla_key(word)]
    for source, replacement in SPELLING_VARIANTS:
        if source in word:
            key = bangla_key(word.replace(source, replacement))
            if key and key not in out:
                out.append(key)
    return [k for k in out if k]


def load_frequencies() -> dict[str, int]:
    words: dict[str, int] = {}
    with open(FREQ_PATH, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            word = precompose(parts[0])
            try:
                freq = int(parts[1])
            except ValueError:
                continue
            words[word] = max(words.get(word, 0), freq)
    return words


def load_previous_index() -> dict[str, int]:
    """Words from the existing index, at a low synthetic frequency preserving their order."""
    words: dict[str, int] = {}
    try:
        handle = open(INDEX_PATH, encoding="utf-8")
    except FileNotFoundError:
        return words
    with handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "\t" not in line:
                continue
            _, rest = line.split("\t", 1)
            for rank, word in enumerate(rest.split(" ")):
                word = precompose(word.strip())
                if word:
                    words[word] = max(words.get(word, 0), MAX_WORDS_PER_KEY - rank)
    return words


def main() -> int:
    frequencies = load_frequencies()
    for word, synthetic in load_previous_index().items():
        frequencies.setdefault(word, synthetic)

    index: dict[str, list[tuple[int, str]]] = defaultdict(list)
    for word, freq in frequencies.items():
        for key in keys_for(word):
            index[key].append((freq, word))

    lines = [
        "# Bornomala Bangla phonetic-key index: ambiguity-collapsed key -> Bangla words.",
        "# Generated by scripts/gen_bn_phonetic.py from bn_frequency.txt; do not hand-edit.",
        "# The key algorithm is BanglaPhoneticKey.banglaKey; BanglaPhoneticKeyTest re-derives",
        "# every line below with the Kotlin implementation so the two cannot drift.",
    ]
    for key in sorted(index):
        ranked = sorted(index[key], key=lambda pair: (-pair[0], pair[1]))
        words = [word for _, word in ranked[:MAX_WORDS_PER_KEY]]
        lines.append(f"{key}\t{' '.join(words)}")

    with open(INDEX_PATH, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"wrote {INDEX_PATH}: {len(index)} keys, {len(frequencies)} words")
    return 0


if __name__ == "__main__":
    sys.exit(main())
