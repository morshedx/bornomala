# Third-Party Licenses

Bornomala Keyboard bundles or derives from the following open-source components. Each is the
property of its respective authors and is used under the license shown. All run entirely
offline.

---

## Avro Phonetic rule data — OmicronLab

Bangla transliteration rules are derived from the Avro Phonetic scheme / pyAvroPhonetic
(`avrodict.json`).

- Source: https://github.com/omicronlab/pyAvroPhonetic
- License: MIT
- Copyright © OmicronLab and contributors.

---

## Frequency word lists — hermitdave/FrequencyWords

English and Bangla frequency dictionaries used for suggestions
(`en_frequency.txt`, `bn_frequency.txt`).

- Source: https://github.com/hermitdave/FrequencyWords
- License: MIT
- Copyright © Hermit Dave.

---

## OpenBangla riti data

Bangla auto-correct entries (`avro_autocorrect.json`), suffix table (`avro_suffix.json`), and the
suggest-only dictionary words merged into `bn_phonetic.txt`. Unmodified upstream copies are kept
in `third_party/riti/` (commit `afee54a`).

- Source: https://github.com/OpenBangla/riti
- License: Mozilla Public License 2.0 (full text: `third_party/riti/LICENSE`,
  shipped as `avro_autocorrect.LICENSE`)
- Copyright © OpenBangla contributors.

---

## Lucide Icons

The keyboard's icon set is generated from Lucide SVGs.

- Source: https://github.com/lucide-icons/lucide
- License: ISC
- Copyright © Lucide Contributors. (Lucide is a fork of Feather Icons, © Cole Bemis, MIT.)

---

## JetBrains Mono

Optional keyboard/app font.

- Source: https://github.com/JetBrains/JetBrainsMono
- License: SIL Open Font License 1.1
- Copyright © JetBrains s.r.o.

---

## Emoji data — Unicode

Emoji glyphs and categories are based on the Unicode Emoji data. Emoji are rendered by the
system font; no emoji artwork is bundled.

- Source: https://unicode.org/emoji/
- License: Unicode License Agreement (Data Files).

---

## Frameworks & libraries (Apache License 2.0)

The app is built with the following, which are not redistributed as source but linked at
build time:

- Android Jetpack / AndroidX, Jetpack Compose, Material 3 — © The Android Open Source Project
- Kotlin & Kotlin Coroutines — © JetBrains s.r.o. and contributors
- Dagger Hilt — © Google LLC

All under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).

---

Full license texts are available at the source URLs above. For inquiries:
https://pocketware.vercel.app/bornomala/
