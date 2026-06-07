# Bornomala Keyboard

A privacy-first Android keyboard for **English (QWERTY)** and **Bangla (Avro Phonetic)** — fully offline, no internet permission, no tracking, no data collection. Everything stays on device.

<p align="left">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="96" alt="Bornomala icon"/>
</p>

## Features

- **Bangla via Avro Phonetic** — the official OmicronLab rule set + a faithful parser (longest-match with prefix/suffix/exact context rules). `ami → আমি`, `bangladesh → বাংলাদেশ`, `shikkha → শিক্ষা`.
- **English QWERTY** — shift/caps, symbols & numeric pad, long-press alternates + symbol hints, auto-capitalization, double-space → ". ".
- **Suggestions** — offline frequency dictionaries + a learned user dictionary (Room); learned words are boosted. Pluggable `SuggestionProvider` leaves room for a future cloud source without refactoring.
- **Toolbar** — emoji picker, numeric pad, clipboard history, settings, hide; collapses to suggestions while typing.
- **Clipboard history** — captured on copy, pin/delete, 100-item cap (Room).
- **Emoji** — categories, recent/frequent (persisted).
- **Cursor control** — toolbar arrows and volume keys (Vol-Up → right, Vol-Down → left).
- **Theming** — Light / Dark / System, Samsung-inspired; adjustable height, haptics, sound.
- **Accessibility** — TalkBack labels, high-contrast option, large touch targets.

## Privacy

- **No `INTERNET` permission.** Only `VIBRATE` (optional haptics).
- No analytics, no telemetry, no network calls. All storage is local (Room + DataStore).

## Architecture

Clean Architecture, multi-module, `presentation → domain → data`.

```
:app              entry point, DI graph root, IME ↔ feature wiring (port adapters)
:core             Result types, DispatcherProvider, coroutine scopes
:keyboard         KeyboardImeService (InputMethodService), layouts, state, Compose UI
:transliteration  Avro engine + bundled OmicronLab rule dictionary (JSON)
:suggestions      SuggestionEngine, OfflineProvider, user dictionary (Room)
:emoji            catalog, search, recent/frequent (Room)
:clipboard        history, pin/search, eviction (Room)
:settings         SettingsScreen, DataStore preferences
:theme            Material 3 themes + keyboard tokens
:microbenchmark   transliteration hot-path benchmarks
```

The keyboard depends only on inbound **ports** (`TransliterationPort`, `SuggestionPort`, `KeyboardSettingsPort`); `:app` binds them to the real feature engines via Hilt — the single seam for swapping providers later.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · Coroutines/Flow · Room · DataStore · kotlinx.serialization.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 26).

```bash
# debug APK
./gradlew :app:assembleDebug

# unit tests (incl. the Avro engine table tests)
./gradlew test

# transliteration hot-path microbenchmark (needs a device)
./gradlew :microbenchmark:connectedReleaseAndroidTest
```

### Release signing

Release builds are signed from a gitignored `keystore.properties` (never committed):

```properties
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/bornomala-<version>-release.apk
```

> The committed repo does **not** include the keystore or its passwords. Generate your own before publishing and keep it safe — it is your app's signing identity.

## Install & enable

```bash
adb install -r app/build/outputs/apk/release/bornomala-<version>-release.apk
adb shell ime enable  com.morshedx.bornomala/com.bornomala.keyboard.ime.KeyboardImeService
adb shell ime set     com.morshedx.bornomala/com.bornomala.keyboard.ime.KeyboardImeService
```

Then open any text field; tap 🌐 to switch English ⇄ Bangla.

## Credits

Bangla input uses the **Avro Phonetic** scheme; rule data © [OmicronLab](https://www.omicronlab.com), adapted from jsAvroPhonetic / pyAvroPhonetic.

## License & warranty

This software is provided **"as is"**, without warranty of any kind, express or implied. The author accepts no liability for any damages, data loss, or other harm arising from its use.
