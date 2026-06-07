# CLAUDE.md

Guidance for Claude Code working in this repo. Read `SPEC.md` for the full product spec.

## What this is

Bornomala — a privacy-first Android keyboard (IME) supporting English QWERTY and Bangla Avro phonetic. Fully offline in V1. No internet permission. Architecture must leave room for cloud suggestions later without major refactor.

## Hard constraints (do not violate)

- **No internet permission.** Do not add `android.permission.INTERNET` or any network call in V1.
- **No analytics, no tracking, no telemetry, no data collection.** All data stays on device.
- **No blocking work on the Main thread.** Use Coroutines + Flow. IME input path must stay responsive.
- **No placeholders, TODOs, mock implementations, or pseudo-code** in delivered code. Ship production-quality.
- **Bangla is rule-based**, not a flat letter map. All transliteration goes through `TransliterationEngine` with longest-match-first.

## Performance budgets

- Cold start `< 300ms`
- Key press latency `< 16ms`
- Memory `< 100 MB`

When touching the input path or UI, keep these in mind. Avoid allocations in hot paths (per-keystroke).

## Extreme performance + least battery (priority constraint)

Treat battery and latency as first-class. Rules:

- **Zero wakelocks. No background services, no `WorkManager`, no polling, no timers.** Keyboard is event-driven only — work happens on keystroke, then the CPU goes idle.
- **No animations on the hot path.** Key-press feedback must be cheap (no per-frame recomposition storms). Prefer `derivedStateOf` / `remember` to cut recomposition. Hoist state; keep recomposition scopes tiny.
- **Per-keystroke path allocates nothing.** Reuse buffers (`StringBuilder`, char arrays). No `Regex`, no boxing, no `List`/`Map` creation per key. Transliteration rule lookup uses a prebuilt trie/lookup table, not runtime compilation.
- **Lazy-load everything heavy.** Dictionaries, emoji catalog, Room DBs load on first actual use, off the Main thread (`Dispatchers.Default`/`IO`), never at IME `onCreate`. Cold-start path stays minimal.
- **Coalesce DB writes.** Learning/frequency updates and clipboard inserts batch + debounce (e.g. write on word-commit or idle, not per char). Use a single bounded write coroutine; never block input on disk.
- **No vibration/sound unless user enabled it** — and use the cheapest API (`HapticFeedbackConstants`), no custom vibrator patterns.
- **Avoid GC pressure:** object pooling for candidate/suggestion lists, primitive collections where it matters.
- **Single shared `CoroutineScope` per service**, cancelled in `onDestroy`. No leaked scopes/jobs. No `Dispatchers.Main` blocking, ever.
- **Compose:** stable/immutable data classes for keyboard state, `@Stable`/`@Immutable` annotations, key the `LazyGrid`/lists, avoid lambdas capturing unstable refs in the key path.
- **Measure:** include Macrobenchmark hooks for cold start + frame timing, and a microbenchmark for the transliteration hot path. Budgets above are enforced, not aspirational.

## Architecture

Clean Architecture. Multi-module. Dependency flow: `presentation → domain → data`.

Modules:

```
:app              entry point, IME service wiring, DI graph root
:core             shared utils, base types, Result wrappers
:keyboard         InputMethodService, key layouts, keyboard state, Compose KeyboardView
:transliteration  TransliterationEngine, rule tables, candidate generation
:suggestions      SuggestionEngine, SuggestionProvider (OfflineProvider now, cloud later)
:emoji            emoji data, categories, recent/frequent persistence
:clipboard        clipboard history (Room, max 100, pin/search)
:settings         SettingsScreen, DataStore preferences
:theme            Material 3 themes (Light/Dark/System), Samsung-inspired tokens
```

Each feature module splits into `presentation` / `domain` / `data` where it has all three layers. Domain holds interfaces; data implements them; presentation (Compose) depends on domain only.

## Key interfaces (stable contracts)

```kotlin
interface TransliterationEngine {
    fun processInput(input: String): TransliterationResult
    fun delete()
    fun reset()
}

interface SuggestionProvider   // OfflineProvider (V1), FutureCloudProvider (later)
```

Do not change these signatures without strong reason — they are the extension seams.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · InputMethodService · Hilt · Coroutines · Flow · Room · DataStore.

- Persistence: Room (user dictionary, clipboard history, emoji usage). Preferences: DataStore.
- DI: Hilt. Service-scoped components for the IME.
- UI: Compose only. No XML layouts for keyboard views.

## Testing

- Target `80%+` coverage.
- Must test: transliteration engine, suggestions, clipboard, language switching, keyboard state.
- Unit + integration + UI tests. Transliteration engine is pure logic — test it exhaustively with table-driven cases (see examples in `SPEC.md`).

## Build

```
./gradlew assembleDebug      # build debug APK
./gradlew test               # unit tests
./gradlew connectedAndroidTest   # instrumented/UI tests
./gradlew lint               # static checks
```

(Scaffold Gradle wrapper + modules before these run — repo not yet initialized.)

## Conventions

- Match surrounding code style: naming, comment density, idioms.
- Keep transliteration rules in data tables, not hardcoded `when` chains, so the dictionary can expand.
- New features go in their own module following the layer split above.
- Verify any file/symbol from memory still exists before relying on it — codebase is young and moving.
