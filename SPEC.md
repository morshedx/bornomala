# Bornomala Keyboard — Build Specification

You are a Staff Android Engineer specializing in Android IME development, Kotlin, Jetpack Compose, NLP, keyboard engines, and performance optimization.

Build a production-ready Android Keyboard App.

## Project Overview

Create a privacy-first Android keyboard application similar to Samsung Keyboard.

The keyboard supports:

1. English (QWERTY)
2. Bangla (Avro Phonetic)

The entire application must work offline.

No internet permission should be requested in V1.

The architecture must be designed so cloud suggestions can be added later without major refactoring.

## Primary Goals

- Fast typing experience
- Low memory usage
- Offline first
- Samsung Keyboard style UI
- Modular architecture
- Play Store ready
- Easily extensible

## Tech Stack

- Kotlin
- Android SDK
- Jetpack Compose
- InputMethodService
- Material 3
- Hilt
- Coroutines
- Flow
- Room
- DataStore

## Architecture

Use Clean Architecture.

Modules:

```
:app
:core
:keyboard
:transliteration
:suggestions
:emoji
:clipboard
:settings
:theme
```

Layers:

```
presentation
domain
data
```

Dependency flow:

```
presentation → domain → data
```

## Keyboard Types

### English Keyboard

Requirements:

- QWERTY layout
- Shift
- Caps Lock
- Symbols page
- Number page
- Long press characters
- Auto capitalization
- Double space period shortcut

### Bangla Keyboard

Implement Avro-style phonetic typing.

Examples:

```
ami → আমি
bangladesh → বাংলাদেশ
kemon → কেমন
valo → ভালো
khub → খুব
shob → সব
shikkha → শিক্ষা
```

Requirements:

- Fully offline
- Rule based engine
- Multi-character pattern matching
- Support conjuncts
- Support vowel signs
- Support dependent vowels
- Support independent vowels
- Support Bangla punctuation
- Support Avro compatible behavior

Do NOT create a simple letter mapping.

Create a dedicated TransliterationEngine.

## Transliteration Engine

Design:

```
TransliterationEngine
```

Responsibilities:

- Input buffer management
- Rule matching
- Candidate generation
- Word commit
- Backspace handling

Provide:

```kotlin
interface TransliterationEngine {
    fun processInput(input: String): TransliterationResult
    fun delete()
    fun reset()
}
```

Support longest-match-first algorithm.

Examples:

```
kh
gh
ng
ch
chh
sh
ss
kkh
gg
tt
dd
rr
```

Support future dictionary expansion.

## Input Method Service

Create:

```
KeyboardImeService
```

Responsibilities:

- Lifecycle management
- InputConnection handling
- Commit text
- Delete text
- Cursor management
- Selection updates

## Keyboard UI

Samsung Keyboard inspired design.

Requirements:

- Rounded keys
- Responsive spacing
- Proper touch targets
- Adaptive width keys
- Landscape support
- Tablet support

Screens:

```
KeyboardView
SuggestionBar
EmojiPanel
ClipboardPanel
SettingsScreen
```

## Language Switching

Requirements:

- Dedicated language switch key
- Instant switching
- Preserve keyboard state
- Remember last used language

Languages:

```
English
Bangla
```

## Suggestion System

V1:

Offline suggestions only.

Create:

```
SuggestionEngine
```

Data sources:

- English frequency dictionary
- Bangla frequency dictionary
- User dictionary

Features:

- Next word suggestion
- Current word suggestion
- Learning from typed words

Design interfaces for future cloud suggestions:

```kotlin
interface SuggestionProvider
```

```
OfflineProvider
FutureCloudProvider
```

## User Dictionary

Store:

- Frequently typed words
- Learned words
- Custom shortcuts

Use Room.

## Clipboard Manager

Requirements:

- Clipboard history
- Pin items
- Delete items
- Search items

Use Room.

Maximum history:

```
100 items
```

## Emoji Keyboard

Requirements:

- Emoji categories
- Recent emojis
- Search emojis
- Frequently used emojis

Persist usage history.

## Settings

Create:

```
SettingsScreen
```

Options:

- Theme
- Keyboard height
- Key press vibration
- Key press sound
- Number row toggle
- Suggestion toggle
- Clipboard toggle
- Auto capitalization
- Bangla transliteration settings

Use DataStore.

## Themes

Support:

```
Light
Dark
System
```

Samsung-inspired design language.

## Performance Requirements

Cold start:

```
< 300ms
```

Key press latency:

```
< 16ms
```

Memory:

```
< 100 MB
```

No blocking operations on Main Thread.

## Accessibility

Support:

- TalkBack
- Large fonts
- High contrast mode
- Accessibility labels

## Security & Privacy

Requirements:

- No internet permission
- No analytics
- No tracking
- No user data collection

All data remains on device.

## Testing

Generate:

```
Unit Tests
Integration Tests
UI Tests
```

Coverage target:

```
80%+
```

Test:

- Transliteration engine
- Suggestions
- Clipboard
- Language switching
- Keyboard state

## Deliverables

Generate:

1. Full project structure
2. Architecture diagrams
3. Module breakdown
4. Database schema
5. Transliteration engine implementation
6. Keyboard layouts
7. Compose UI
8. InputMethodService implementation
9. Room entities
10. DataStore implementation
11. Hilt setup
12. Test suite
13. Build instructions

Build production-quality code rather than sample code.

Avoid placeholders, TODOs, mock implementations, and pseudo-code unless explicitly unavoidable.

The resulting codebase should be capable of becoming a Play Store release after refinement and testing.
