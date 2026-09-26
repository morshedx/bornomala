#!/usr/bin/env python3
"""Regenerates suggestions/src/main/assets/dictionaries/bn_phonetic.txt.

The index maps the ambiguity-collapsed phonetic key of a Bangla word to the real words that
spell to it, best-first by corpus frequency. The key algorithm mirrors
`BanglaPhoneticKey.banglaKey` in :suggestions exactly — `BanglaPhoneticKeyTest` re-derives every
shipped line with the Kotlin implementation, so the two cannot drift apart silently.

Word sources: bn_frequency.txt (with real frequencies) plus any word already present in the
previous index's trusted column (kept at a low synthetic frequency so coverage never regresses).
Those words are "trusted": the keyboard may auto-pick them on space.

OpenBangla riti's dictionary (third_party/riti/dictionary.json, MPL-2.0, ~159k words without
frequencies) adds a third, suggest-only column: its words widen what roman input can reach and
tell the keyboard that a typed spelling is a real word, but never replace what was typed.

Line format:  key<TAB>trusted words…[<TAB>suggest-only words…]

Run from the repo root:  python3 scripts/gen_bn_phonetic.py
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict

FREQ_PATH = "suggestions/src/main/assets/dictionaries/bn_frequency.txt"
INDEX_PATH = "suggestions/src/main/assets/dictionaries/bn_phonetic.txt"
RITI_DICTIONARY_PATH = "third_party/riti/dictionary.json"

# A word cannot begin with a sign that attaches to a preceding letter (candrabindu, anusvara,
# visarga, nukta, vowel signs, hasant, au length mark, vocalic vowel signs); riti lists a few.
DEPENDENT_SIGNS = set("\u0981\u0982\u0983\u09BC\u09BE\u09BF\u09C0\u09C1\u09C2\u09C3\u09C4"
                      "\u09C7\u09C8\u09CB\u09CC\u09CD\u09D7\u09E2\u09E3")

MAX_WORDS_PER_KEY = 8
MAX_EXTRA_WORDS_PER_KEY = 4

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
    """Trusted words from the existing index, at a low synthetic frequency preserving order.

    Only the second column is read: re-reading the suggest-only column would promote riti words
    to trusted on the next regeneration.
    """
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
            trusted = line.split("\t")[1]
            for rank, word in enumerate(trusted.split(" ")):
                word = precompose(word.strip())
                if word:
                    words[word] = max(words.get(word, 0), MAX_WORDS_PER_KEY - rank)
    return words


def load_riti_words() -> list[str]:
    """riti's dictionary: {"<letter group>": [word, …]}; flattened, precomposed, de-duplicated."""
    with open(RITI_DICTIONARY_PATH, encoding="utf-8") as handle:
        groups = json.load(handle)
    words = {precompose(word.strip()) for group in groups.values() for word in group}
    return sorted(word for word in words if word and word[0] not in DEPENDENT_SIGNS)


def main() -> int:
    frequencies = load_frequencies()
    for word, synthetic in load_previous_index().items():
        frequencies.setdefault(word, synthetic)

    index: dict[str, list[tuple[int, str]]] = defaultdict(list)
    for word, freq in frequencies.items():
        for key in keys_for(word):
            index[key].append((freq, word))

    # Suggest-only: shortest first (closest to the bare key), then alphabetical for stability.
    # A word is kept only if it fits under its own key; only then is it also indexed under its
    # alternative-spelling keys, so every shipped word stays reachable under banglaKey(word).
    extra: dict[str, list[str]] = defaultdict(list)
    riti_words = [word for word in load_riti_words() if word not in frequencies]
    for word in sorted(riti_words, key=lambda w: (len(w), w)):
        keys = keys_for(word)
        if not keys or len(extra[keys[0]]) >= MAX_EXTRA_WORDS_PER_KEY:
            continue
        for key in keys:
            if len(extra[key]) < MAX_EXTRA_WORDS_PER_KEY:
                extra[key].append(word)

    lines = [
        "# Bornomala Bangla phonetic-key index: ambiguity-collapsed key -> Bangla words.",
        "# Generated by scripts/gen_bn_phonetic.py; do not hand-edit.",
        "# Format: key<TAB>trusted words (auto-pickable)[<TAB>suggest-only words].",
        "# Trusted words come from bn_frequency.txt. Suggest-only words come from OpenBangla riti's",
        "# dictionary (https://github.com/OpenBangla/riti), Mozilla Public License 2.0 — see",
        "# third_party/riti/LICENSE; this file is therefore also covered by the MPL-2.0.",
        "# The key algorithm is BanglaPhoneticKey.banglaKey; BanglaPhoneticKeyTest re-derives",
        "# every line below with the Kotlin implementation so the two cannot drift.",
    ]
    for key in sorted(set(index) | set(extra)):
        ranked = sorted(index.get(key, []), key=lambda pair: (-pair[0], pair[1]))
        words = [word for _, word in ranked[:MAX_WORDS_PER_KEY]]
        more = [word for word in extra.get(key, []) if word not in words]
        line = f"{key}\t{' '.join(words)}"
        if more:
            line += f"\t{' '.join(more)}"
        lines.append(line)

    with open(INDEX_PATH, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"wrote {INDEX_PATH}: {len(lines)} keys, {len(frequencies)} trusted + {len(riti_words)} riti words")
    return 0


if __name__ == "__main__":
    sys.exit(main())
