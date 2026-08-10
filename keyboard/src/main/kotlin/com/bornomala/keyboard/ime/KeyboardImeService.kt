package com.bornomala.keyboard.ime

import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import com.bornomala.keyboard.ime.data.editor.InputConnectionEditorPort
import com.bornomala.keyboard.ime.data.layout.LayoutProvider
import com.bornomala.keyboard.ime.domain.input.InputConfig
import com.bornomala.keyboard.ime.domain.input.InputInteractor
import com.bornomala.keyboard.clipboard.domain.repository.ClipboardRepository
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardLanguage
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.Suggestion
import com.bornomala.keyboard.ime.domain.port.KeyboardSettings
import com.bornomala.keyboard.ime.domain.port.KeyboardSettingsPort
import com.bornomala.keyboard.ime.domain.port.SuggestionPort
import com.bornomala.keyboard.ime.domain.port.TransliterationPort
import com.bornomala.keyboard.ime.domain.state.KeyboardStateHolder
import com.bornomala.keyboard.ime.presentation.KeyboardCallbacks
import com.bornomala.keyboard.ime.presentation.KeyboardScreen
import com.bornomala.keyboard.theme.BornomalaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The input method (keyboard) service — the app's true entry point, registered in the
 * manifest and bound by the system. It owns the input lifecycle and bridges the framework's
 * [android.view.inputmethod.InputConnection] to the framework-free [InputInteractor].
 *
 * Responsibilities:
 *  - build and host the Compose keyboard view inside the IME window (via [ImeComposeHost]);
 *  - keep the live [InputConnection] on the [InputConnectionEditorPort];
 *  - snapshot user settings into the interactor and the renderer (theme, height, toggles);
 *  - run suggestion lookups off the main thread and learning fire-and-forget;
 *  - provide cheap key-press feedback (haptics/sound) only when enabled.
 *
 * Performance: the per-key path is synchronous and allocation-light (handled by the
 * interactor). Suggestion queries and learning never block input — they run on the service
 * scope using the injected dispatchers. No wakelocks, timers, or background services.
 */
@AndroidEntryPoint
class KeyboardImeService : InputMethodService() {

    @Inject lateinit var layoutProvider: LayoutProvider
    @Inject lateinit var transliterationPort: TransliterationPort
    @Inject lateinit var suggestionPort: SuggestionPort
    @Inject lateinit var settingsPort: KeyboardSettingsPort
    @Inject lateinit var dispatchers: DispatcherProvider
    @Inject lateinit var clipboardRepository: ClipboardRepository

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }
    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    private val editorPort = InputConnectionEditorPort()
    private val stateHolder = KeyboardStateHolder()
    private val composeHost = ImeComposeHost()

    /** Settings snapshot driving the renderer (theme/height/suggestion toggle). */
    private val settingsState = MutableStateFlow(KeyboardSettings())

    /** Hot-path-readable feedback flags, updated whenever settings change. */
    @Volatile private var hapticsEnabled = false
    @Volatile private var soundEnabled = false
    @Volatile private var learnFromTyping = true

    /** Most recent system-clipboard text and when it was copied, for the strip paste chip. */
    @Volatile private var recentClipText: String? = null
    @Volatile private var recentClipAtMs: Long = 0L

    private lateinit var serviceScope: CoroutineScope
    private lateinit var interactor: InputInteractor
    private var suggestionJob: Job? = null
    private var keyboardView: ComposeView? = null

    private val callbacks = KeyboardCallbacks(
        onKey = { action -> interactor.onKey(action) },
        onLongPressChar = { ch -> interactor.onKey(KeyAction.Character(ch)) },
        onSuggestion = { word -> interactor.commitSuggestion(word) },
        onOpenSettings = { openSettings() },
        onToggleSettingsMenu = { stateHolder.toggleSettingsMenu() },
        onOpenSettingsSection = { section -> openSettings(section) },
        onToggleEmoji = { stateHolder.toggleEmoji() },
        onToggleNumbers = {
            if (stateHolder.current.page == KeyboardPage.NUMPAD) stateHolder.showAlpha()
            else stateHolder.showNumpad()
        },
        onToggleClipboard = { stateHolder.toggleClipboard() },
        onPaste = { text -> pasteText(text) },
        onClipSuggestion = {
            val text = stateHolder.current.clipSuggestion
            if (!text.isNullOrEmpty()) {
                pasteText(text)
                stateHolder.clearClipSuggestion()
                // Consumed: don't re-offer the same chip when this field is refocused.
                recentClipText = null
            }
        },
        onEmoji = { glyph ->
            interactor.resetComposing()
            currentInputConnection?.commitText(glyph, 1)
        },
        onHideKeyboard = { requestHideSelf(0) },
        onSearchKey = { action ->
            when (action) {
                is KeyAction.Character -> stateHolder.appendPanelQuery(action.char.toString())
                KeyAction.Space -> stateHolder.appendPanelQuery(" ")
                KeyAction.Backspace -> stateHolder.backspacePanelQuery()
                KeyAction.Enter -> stateHolder.setPanelSearch(false)
                else -> Unit
            }
        },
        onOpenSearch = { stateHolder.setPanelSearch(true) },
        onCloseSearch = { stateHolder.setPanelSearch(false) },
        onCursorSwipe = { delta -> interactor.onCursorSwipe(delta) },
    )

    private val interactorCallbacks = object : InputInteractor.Callbacks {
        override fun onWordCommitted(language: KeyboardLanguage, word: String) {
            if (learnFromTyping) suggestionPort.recordCommitted(language, word)
        }

        override fun onComposingChanged(language: KeyboardLanguage, currentWord: String) {
            refreshSuggestions(language, currentWord)
        }

        override fun onEmojiRequested() {
            // V1: the emoji panel (the :emoji module) can be hosted here in a later wiring
            // step. Left intentionally inert so the keyboard never blocks on it.
        }

        override fun onShowImePicker() {
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showInputMethodPicker()
        }

        override fun onFeedback(action: KeyAction) {
            performKeyFeedback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.mainImmediate)
        interactor = InputInteractor(
            editor = editorPort,
            transliteration = transliterationPort,
            stateHolder = stateHolder,
            callbacks = interactorCallbacks,
        )
        composeHost.onCreate()
        observeSettings()
        restoreLastLanguage()
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
    }

    override fun onCreateInputView(): View {
        // The Compose window-recomposer searches UP from the IME window root for the
        // ViewTree owners, so they must live on an ancestor of the input view — the IME
        // window's decor view — not only on the ComposeView itself.
        attachComposeOwnersToWindow()
        val view = ComposeView(this).apply {
            // Wrap to the keyboard's own height. Without an explicit WRAP_CONTENT the input
            // view can be measured at the full available height on some skins/after a swipe-up,
            // top-aligning the keys and leaving a large empty band below.
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(composeHost)
            setViewTreeViewModelStoreOwner(composeHost)
            setViewTreeSavedStateRegistryOwner(composeHost)
            setContent {
                val settings by settingsState.collectAsStateWithLifecycle()
                val state by stateHolder.state.collectAsStateWithLifecycle()
                BornomalaTheme(
                    theme = settings.keyboardTheme,
                    font = settings.keyboardFont,
                    metrics = com.bornomala.keyboard.theme.keyboardMetrics(
                        horizontalGapScale = settings.horizontalGapScale,
                        verticalGapScale = settings.verticalGapScale,
                        keyLabelScale = settings.keyLabelScale,
                        suggestionBarScale = settings.suggestionBarScale,
                        bottomGapScale = settings.bottomGapScale,
                        keyBorder = settings.keyBorder,
                    ),
                ) {
                    KeyboardScreen(
                        state = state,
                        layoutProvider = layoutProvider,
                        callbacks = callbacks,
                        keyHeightFraction = settings.keyHeightFraction,
                    )
                }
            }
        }
        keyboardView = view
        composeHost.onResume()
        return view
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        editorPort.connection = currentInputConnection
        interactor.resetComposing()
        stateHolder.setEnterIsAccent(isAccentAction(info))
        stateHolder.setEmailField(isEmailField(info))
        // Seed the empty/typing signal for the newly bound field so the strip starts correct.
        refreshHasText()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorPort.connection = currentInputConnection
        composeHost.onResume()
        // Start on the numpad for numeric/phone fields, otherwise the alphabetic page
        // (don't carry over the previous field's numpad/symbols state).
        if (isNumberField(info)) stateHolder.showNumpad() else stateHolder.showAlpha()
        // Reflect whether the field already holds text (e.g. editing an existing value): the
        // strip shows the tools for an empty field and suggestions once there is text.
        refreshHasText()
        // Offer next-word predictions for the empty field immediately.
        refreshSuggestions(stateHolder.current.language, currentWord = "")
        // If something was copied moments ago, offer it as a one-tap paste chip in the strip.
        maybeShowClipSuggestion()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        // Keep the empty/typing signal live as text is inserted, deleted, or the caret moves
        // (covers programmatic edits and deleting back to an empty field), independent of any
        // in-progress composing word.
        refreshHasText()
        if (!stateHolder.current.isComposing) return
        // While composing, the framework reports the composing region via candidatesStart/End.
        // If a selection appears, or the cursor lands outside that region, the user moved the
        // caret or edited elsewhere — drop the in-progress word so it is never committed at the
        // wrong place.
        val hasSelection = newSelStart != newSelEnd
        val cursorOutsideComposing = candidatesStart == -1 ||
            newSelStart < candidatesStart || newSelStart > candidatesEnd
        if (hasSelection || cursorOutsideComposing) {
            interactor.resetComposing()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        interactor.resetComposing()
        composeHost.onPause()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        editorPort.connection = null
        super.onFinishInput()
    }

    // Never expand into fullscreen/extract mode. Some launchers/OEM skins (and a swipe-up on
    // the keyboard) otherwise grow the input window to full height and top-align the keys,
    // leaving a large empty band below. Forcing this off keeps the keyboard at its content
    // height, anchored to the bottom, always.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Volume keys nudge the cursor while the keyboard is visible (a common power-user
        // accessibility aid). Opt-in via settings, and consumed only when the input view is
        // shown so volume control works normally otherwise. While media is actively playing,
        // we defer to the system so the keys adjust the media volume instead.
        if (volumeKeysControlCursor()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { moveCursor(1); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { moveCursor(-1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeysControlCursor() &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Volume keys act as cursor controls only when the user enabled it, the keyboard is shown,
     * and nothing is playing audio — if media is active the keys keep their normal volume role.
     */
    private fun volumeKeysControlCursor(): Boolean =
        isInputViewShown && settingsState.value.volumeKeyCursorControl && !audioManager.isMusicActive

    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        suggestionJob?.cancel()
        if (::serviceScope.isInitialized) serviceScope.cancel()
        composeHost.onDestroy()
        keyboardView = null
        super.onDestroy()
    }

    // --- settings ----------------------------------------------------------------------

    /**
     * Installs the Compose [composeHost] as the ViewTree lifecycle / view-model-store /
     * saved-state owner on the IME window's decor view, so the input [ComposeView] resolves
     * a window recomposer when it attaches. Without this, Compose throws
     * "ViewTreeLifecycleOwner not found" because the IME window root has no owners.
     */
    /** Commits a clipboard item's text into the field and closes the clipboard panel. */
    private fun pasteText(text: String) {
        interactor.resetComposing()
        currentInputConnection?.commitText(text, 1)
        stateHolder.hidePanel()
    }

    /** Records the latest system clipboard text into history (offline, on-device only). */
    private fun captureClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        // Remember the freshly-copied text so the next focus can offer a one-tap paste chip.
        recentClipText = text
        recentClipAtMs = System.currentTimeMillis()
        serviceScope.launch(dispatchers.io) { clipboardRepository.addItem(text) }
    }

    /**
     * Offers the freshly-copied clipboard text as a one-tap paste chip in the top strip when a
     * field gains focus, Gboard-style — but only within [CLIP_SUGGESTION_WINDOW_MS] of the copy.
     * Freshness is checked here on focus (no timer/wakelock); any key press then clears the chip.
     */
    private fun maybeShowClipSuggestion() {
        val text = recentClipText
        if (text != null && System.currentTimeMillis() - recentClipAtMs <= CLIP_SUGGESTION_WINDOW_MS) {
            stateHolder.setClipSuggestion(text)
        } else {
            stateHolder.clearClipSuggestion()
        }
    }

    /**
     * Moves the text cursor by [delta] characters (negative = left). Commits any in-progress
     * word first so the caret never lands inside a composing region. Used by the toolbar
     * arrows and the volume keys.
     */
    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        interactor.resetComposing()
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        val current = extracted?.let { it.startOffset + it.selectionStart } ?: return
        if (current < 0) return
        val target = (current + delta).coerceAtLeast(0)
        val max = extracted.text?.let { extracted.startOffset + it.length } ?: target
        ic.setSelection(target.coerceAtMost(max), target.coerceAtMost(max))
    }

    /**
     * Recomputes whether the edited field currently holds any text and pushes it to the state
     * so the top strip can switch between tools (empty field) and suggestions (while typing).
     *
     * "Has text" means any text before OR after the cursor, or an in-progress composing word —
     * so the toolbar returns the moment the user deletes back to an empty field. Cheap reads
     * (a single char each side) on the input thread; safe with no connection (treated as empty).
     */
    private fun refreshHasText() {
        val ic = currentInputConnection
        val hasText = if (stateHolder.current.isComposing) {
            true
        } else if (ic == null) {
            false
        } else {
            val before = ic.getTextBeforeCursor(1, 0)
            val after = ic.getTextAfterCursor(1, 0)
            !before.isNullOrEmpty() || !after.isNullOrEmpty()
        }
        stateHolder.setHasText(hasText)
    }

    /**
     * Opens the keyboard settings screen. Referenced by class name (not a compile-time type)
     * so :keyboard stays decoupled from :settings; the activity lives in the app package.
     */
    private fun openSettings(section: String? = null) {
        runCatching {
            val intent = android.content.Intent().apply {
                setClassName(applicationContext.packageName, SETTINGS_ACTIVITY)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (section != null) putExtra(SETTINGS_SECTION_EXTRA, section)
            }
            startActivity(intent)
        }
    }

    private fun attachComposeOwnersToWindow() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(composeHost)
        decor.setViewTreeViewModelStoreOwner(composeHost)
        decor.setViewTreeSavedStateRegistryOwner(composeHost)
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsPort.settings.collect { s ->
                settingsState.value = s
                hapticsEnabled = s.hapticsEnabled
                soundEnabled = s.soundEnabled
                learnFromTyping = s.learnFromTyping
                interactor.updateConfig(
                    InputConfig(
                        autoCapitalization = s.autoCapitalization,
                        doubleSpacePeriod = s.doubleSpacePeriod,
                        banglaTransliteration = s.banglaTransliterationEnabled,
                        suggestionsEnabled = s.suggestionsEnabled,
                        autoCorrectEnabled = s.autoCorrectEnabled,
                    ),
                )
                stateHolder.applySettings(
                    showNumberRow = s.showNumberRow,
                    suggestionsEnabled = s.suggestionsEnabled,
                )
            }
        }
    }

    private fun restoreLastLanguage() {
        serviceScope.launch {
            val last = settingsPort.settings.first().lastLanguage
            stateHolder.setLanguage(last)
        }
    }

    // --- suggestions -------------------------------------------------------------------

    private fun refreshSuggestions(language: KeyboardLanguage, currentWord: String) {
        if (!settingsState.value.suggestionsEnabled) {
            stateHolder.setSuggestions(emptyList())
            return
        }
        suggestionJob?.cancel()
        val (previous, secondPrevious) = previousWords()

        // Bangla (Avro) special case: `currentWord` is the raw latin the user typed. Gboard-style,
        // the strip leads with that literal latin so it can be committed as-is, followed by the
        // transliterated Bangla word + its commit candidate, then Bangla-dictionary completions.
        // The transliteration engine is single-threaded, so it is run here on the input thread
        // (not the background dispatcher) and only the dictionary lookup is offloaded.
        if (language == KeyboardLanguage.BANGLA && currentWord.isNotEmpty()) {
            val rendered = transliterationPort.transliterate(currentWord)
            val engineCandidates = transliterationPort.candidates(currentWord)
            suggestionJob = serviceScope.launch {
                val phonetic = withContext(dispatchers.default) {
                    suggestionPort.banglaPhonetic(currentWord, SUGGESTION_LIMIT)
                }
                val dict = withContext(dispatchers.default) {
                    if (rendered.isNotEmpty()) {
                        suggestionPort.query(
                            KeyboardLanguage.BANGLA, rendered, previous, secondPrevious, SUGGESTION_LIMIT,
                            blockOffensive = settingsState.value.blockOffensiveWords,
                        )
                    } else {
                        emptyList()
                    }
                }
                stateHolder.setSuggestions(
                    buildBanglaSuggestions(currentWord, rendered, phonetic, engineCandidates, dict),
                )
            }
            return
        }

        suggestionJob = serviceScope.launch {
            val results: List<Suggestion> = withContext(dispatchers.default) {
                suggestionPort.query(
                    language = language,
                    currentWord = currentWord,
                    previousWord = previous,
                    secondPreviousWord = secondPrevious,
                    limit = SUGGESTION_LIMIT,
                    blockOffensive = settingsState.value.blockOffensiveWords,
                )
            }
            stateHolder.setSuggestions(results)
        }
    }

    /**
     * Assembles the Bangla suggestion strip: the raw latin first, the transliterated word
     * (highlighted), any engine commit candidate, then dictionary completions — de-duplicated
     * by text and capped at [SUGGESTION_LIMIT].
     */
    private fun buildBanglaSuggestions(
        roman: String,
        rendered: String,
        phonetic: List<String>,
        engineCandidates: List<String>,
        dictionary: List<Suggestion>,
    ): List<Suggestion> {
        val out = ArrayList<Suggestion>(SUGGESTION_LIMIT)
        val seen = HashSet<String>()
        fun add(text: String, transliteration: Boolean, highlight: Boolean) {
            if (text.isEmpty() || !seen.add(text) || out.size >= SUGGESTION_LIMIT) return
            out.add(Suggestion(text = text, isAutoCorrect = highlight, isTransliteration = transliteration))
        }
        // Raw latin first (so the user can keep exactly what they typed), then the top phonetic
        // dictionary word as the highlighted auto-pick (committed on space, e.g. ছাড়া), then the
        // plain phonetic render and the remaining candidates.
        add(roman, transliteration = false, highlight = false)
        val autoPick = banglaAutoPick(roman, rendered, phonetic)
        if (autoPick != null) add(autoPick, transliteration = true, highlight = true)
        // The render is highlighted only when there is no phonetic auto-pick to take its place.
        add(rendered, transliteration = true, highlight = autoPick == null)
        phonetic.forEach { add(it, transliteration = true, highlight = false) }
        engineCandidates.forEach { add(it, transliteration = true, highlight = false) }
        dictionary.forEach { add(it.text, transliteration = true, highlight = false) }
        return out
    }

    /**
     * The phonetic-dictionary word that space may commit in place of the literal transliteration,
     * or null to keep exactly what was typed. Swapping is withheld when:
     *
     *  - the roman is still too short to be a finished word (a 1-2 letter prefix matches far too
     *    many dictionary words to guess from), or
     *  - the transliteration itself is one of the candidates — what the user typed already spells
     *    a real Bangla word, so it is not a misspelling to fix. The alternatives (including
     *    anything the user has taught the keyboard) stay one tap away on the strip.
     */
    private fun banglaAutoPick(roman: String, rendered: String, phonetic: List<String>): String? {
        if (roman.length < MIN_BANGLA_AUTO_PICK_LEN) return null
        if (rendered.isNotEmpty() && phonetic.contains(rendered)) return null
        return phonetic.firstOrNull()
    }

    /**
     * The last two committed tokens before the cursor (previous, secondPrevious), used for
     * bigram + trigram next-word prediction. Manual scan (no Regex) to stay allocation-light
     * on the input path. Either is empty when there aren't enough preceding words.
     */
    private fun previousWords(): Pair<String, String> {
        val before = editorPort.textBeforeCursor(PREVIOUS_WORD_LOOKBACK)
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return "" to ""
        // last token
        var end = trimmed.length
        var i = end - 1
        while (i >= 0 && !trimmed[i].isWhitespace()) i--
        val prev1 = trimmed.substring(i + 1, end)
        // skip whitespace, then the second-last token
        while (i >= 0 && trimmed[i].isWhitespace()) i--
        if (i < 0) return prev1 to ""
        end = i + 1
        while (i >= 0 && !trimmed[i].isWhitespace()) i--
        val prev2 = trimmed.substring(i + 1, end)
        return prev1 to prev2
    }

    // --- feedback ----------------------------------------------------------------------

    private fun performKeyFeedback() {
        if (hapticsEnabled) {
            val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            keyboardView?.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.KEYBOARD_PRESS
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                },
                flags,
            )
        }
        if (soundEnabled) {
            (getSystemService(AUDIO_SERVICE) as? AudioManager)
                ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun isNumberField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        return when (type and android.text.InputType.TYPE_MASK_CLASS) {
            android.text.InputType.TYPE_CLASS_NUMBER,
            android.text.InputType.TYPE_CLASS_PHONE,
            -> true
            else -> false
        }
    }

    private fun isEmailField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        if (type and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_CLASS_TEXT) return false
        return when (type and android.text.InputType.TYPE_MASK_VARIATION) {
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            -> true
            else -> false
        }
    }

    private fun isAccentAction(info: EditorInfo?): Boolean {
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT,
            -> true
            else -> false
        }
    }

    private companion object {
        const val SUGGESTION_LIMIT = 6
        const val PREVIOUS_WORD_LOOKBACK = 48

        /** Shortest roman input that may be silently swapped for a phonetic-dictionary word. */
        const val MIN_BANGLA_AUTO_PICK_LEN = 3

        /** How long after a copy the strip still offers a one-tap paste chip (Gboard-style). */
        const val CLIP_SUGGESTION_WINDOW_MS = 60_000L
        const val SETTINGS_ACTIVITY = "com.bornomala.keyboard.settings.SettingsActivity"

        /** Intent extra naming the settings section to open directly (see [SettingsSections]). */
        const val SETTINGS_SECTION_EXTRA = "com.bornomala.keyboard.SETTINGS_SECTION"
    }
}
