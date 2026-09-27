package com.bornomala.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bornomala.keyboard.ime.presentation.KeyboardConfiguratorPreview
import com.bornomala.keyboard.settings.SettingsActivity
import com.bornomala.keyboard.settings.domain.SettingsRepository
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.presentation.components.HeightHandle
import com.bornomala.keyboard.settings.presentation.components.ResizableKeyboardPreview
import com.bornomala.keyboard.theme.BornomalaTheme
import com.bornomala.keyboard.theme.KeyboardDimens
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.LucideIcons
import com.bornomala.keyboard.theme.keyboardColorsFor
import com.bornomala.keyboard.theme.keyboardMetrics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Launcher entry point: the first-run welcome flow. Welcome (with terms) → turn the keyboard
 * on in system input settings → switch to it in the system picker → optional "Make it yours"
 * (theme, number row, height) → done. The two system steps advance on their own as soon as
 * Android reports the change, and steps that are already done are skipped.
 *
 * If the keyboard is already enabled and selected when the app launches, the flow is skipped
 * and Settings opens directly, so existing users never see it.
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Checked only on a fresh launch: once the flow is running (or restored after a
        // configuration change) it carries on to the optional steps itself.
        if (savedInstanceState == null && isImeEnabled(this) && isImeSelected(this)) {
            openSettings()
            return
        }
        enableEdgeToEdge()
        val actions = OnboardingActions(
            onOpenInputSettings = {
                startActivity(
                    Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onShowPicker = {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            },
            onTheme = { theme -> lifecycleScope.launch { settingsRepository.setKeyboardTheme(theme) } },
            onNumberRow = { on -> lifecycleScope.launch { settingsRepository.setNumberRowEnabled(on) } },
            onHeight = { scale -> lifecycleScope.launch { settingsRepository.setKeyboardHeightScale(scale) } },
            onOpenSettings = { openSettings() },
            onFinish = { finish() },
        )
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(Settings.DEFAULTS)
            // Same look as the settings app: phone light/dark mode and Material You colours.
            BornomalaTheme(themeMode = settings.themeMode, dynamicColor = true) {
                OnboardingScreen(settings = settings, actions = actions)
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}

private class OnboardingActions(
    val onOpenInputSettings: () -> Unit,
    val onShowPicker: () -> Unit,
    val onTheme: (KeyboardTheme) -> Unit,
    val onNumberRow: (Boolean) -> Unit,
    val onHeight: (Float) -> Unit,
    val onOpenSettings: () -> Unit,
    val onFinish: () -> Unit,
)

private enum class OnboardingStep { WELCOME, TURN_ON, SWITCH, CUSTOMIZE, DONE }

/** The first step still to do after [from], skipping the system steps already completed. */
private fun nextStep(from: OnboardingStep, enabled: Boolean, selected: Boolean): OnboardingStep = when (from) {
    OnboardingStep.WELCOME -> when {
        !enabled -> OnboardingStep.TURN_ON
        !selected -> OnboardingStep.SWITCH
        else -> OnboardingStep.CUSTOMIZE
    }
    OnboardingStep.TURN_ON -> if (selected) OnboardingStep.CUSTOMIZE else OnboardingStep.SWITCH
    OnboardingStep.SWITCH -> OnboardingStep.CUSTOMIZE
    OnboardingStep.CUSTOMIZE, OnboardingStep.DONE -> OnboardingStep.DONE
}

/** How often the system steps re-check the input-method state while the screen is resumed. */
private const val POLL_INTERVAL_MS = 500L

/** Pause on the "Turned on" confirmation before moving to the next step. */
private const val ADVANCE_DELAY_MS = 900L

/** Steps shown in the progress bar (Done has none). */
private const val PROGRESS_STEPS = 4

@Composable
private fun OnboardingScreen(settings: Settings, actions: OnboardingActions) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(isImeEnabled(context)) }
    var selected by remember { mutableStateOf(isImeSelected(context)) }

    // The system steps finish outside the app: the input settings screen (we come back via
    // onResume) and the picker dialog (no callback at all). Re-check while resumed, and only
    // on those two steps, then advance once the change shows up.
    LaunchedEffect(step) {
        if (step != OnboardingStep.TURN_ON && step != OnboardingStep.SWITCH) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                enabled = isImeEnabled(context)
                selected = isImeSelected(context)
                val done = if (step == OnboardingStep.TURN_ON) enabled else selected
                if (done) {
                    delay(ADVANCE_DELAY_MS)
                    step = nextStep(step, enabled, selected)
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                step = step,
                onSkip = { step = OnboardingStep.DONE },
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }) togetherWith
                        fadeOut(tween(120))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { current ->
                when (current) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        agreed = agreed,
                        onAgreedChange = { agreed = it },
                        onGetStarted = {
                            enabled = isImeEnabled(context)
                            selected = isImeSelected(context)
                            step = nextStep(OnboardingStep.WELCOME, enabled, selected)
                        },
                    )
                    OnboardingStep.TURN_ON -> TurnOnStep(enabled = enabled, onOpen = actions.onOpenInputSettings)
                    OnboardingStep.SWITCH -> SwitchStep(selected = selected, onChoose = actions.onShowPicker)
                    OnboardingStep.CUSTOMIZE -> CustomizeStep(
                        settings = settings,
                        actions = actions,
                        onContinue = { step = OnboardingStep.DONE },
                    )
                    OnboardingStep.DONE -> DoneStep(actions = actions)
                }
            }
        }
    }
}

@Composable
private fun TopBar(step: OnboardingStep, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == OnboardingStep.DONE) {
            Spacer(Modifier.weight(1f))
        } else {
            val progressText = stringResource(R.string.onboarding_progress_cd, step.ordinal + 1, PROGRESS_STEPS)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = progressText },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(PROGRESS_STEPS) { index ->
                    val color by animateColorAsState(
                        targetValue = if (index <= step.ordinal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        label = "progress",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
        if (step == OnboardingStep.CUSTOMIZE) {
            TextButton(onClick = onSkip, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

/**
 * Shared layout for the picture steps: a rounded stage that takes the free height, then the
 * headline and body, optional extras, and the actions at the bottom.
 */
@Composable
private fun StepLayout(
    title: String,
    body: String?,
    stage: @Composable BoxScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    extras: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
            content = stage,
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        extras()
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The "done" state of a system step: a tonal button with a check, which does nothing. */
@Composable
private fun CompletedAction(text: String) {
    FilledTonalButton(
        onClick = {},
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(LucideIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

// --- Welcome -------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onGetStarted: () -> Unit,
) {
    var showTerms by rememberSaveable { mutableStateOf(false) }
    StepLayout(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        stage = {
            BackdropLetters()
            TransliterationDemo(Modifier.fillMaxWidth().padding(20.dp))
        },
        extras = {
            Row(Modifier.fillMaxWidth()) {
                Pledge(LucideIcons.WifiOff, stringResource(R.string.onboarding_pledge_offline), Modifier.weight(1f))
                Pledge(LucideIcons.EyeOff, stringResource(R.string.onboarding_pledge_tracking), Modifier.weight(1f))
                Pledge(LucideIcons.Lock, stringResource(R.string.onboarding_pledge_device), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = onAgreedChange)
                Text(
                    text = stringResource(R.string.onboarding_agree_prefix) + " ",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(role = Role.Checkbox) { onAgreedChange(!agreed) },
                )
                Text(
                    text = stringResource(R.string.onboarding_terms_link),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { showTerms = true },
                )
            }
        },
        actions = {
            PrimaryAction(
                text = stringResource(R.string.onboarding_get_started),
                onClick = onGetStarted,
                enabled = agreed,
            )
        },
    )
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text(stringResource(R.string.onboarding_terms_title)) },
            text = {
                Text(
                    text = stringResource(R.string.onboarding_terms_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showTerms = false }) { Text(stringResource(R.string.onboarding_terms_ok)) }
            },
        )
    }
}

@Composable
private fun Pledge(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Large faint Bangla letters behind a stage's content. */
@Composable
private fun BoxScope.BackdropLetters() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp, fontWeight = FontWeight.SemiBold)
    Text("অ", style = style, color = color, modifier = Modifier.align(Alignment.TopStart).offset((-12).dp, (-36).dp))
    Text("ক", style = style, color = color, modifier = Modifier.align(Alignment.CenterEnd).offset(28.dp, 12.dp))
    Text("ম", style = style, color = color, modifier = Modifier.align(Alignment.BottomStart).offset(36.dp, 52.dp))
}

/**
 * One word typed letter by letter, with what the keyboard shows after each keystroke and the
 * suggestions it offers for the finished word. The outputs are what Bornomala produces: the
 * raw phonetic result while typing, then the dictionary/loanword pick for the whole word.
 */
private class DemoWord(val roman: String, val outputs: List<String>, val suggestions: List<String>)

private val DemoWords = listOf(
    DemoWord("ami", listOf("আ", "আম", "আমি"), listOf("আমি", "আমী", "আমিই")),
    DemoWord(
        "bhalobashi",
        listOf("ব", "ভ", "ভা", "ভাল", "ভালো", "ভালোব", "ভালোবা", "ভালোবাস", "ভালোবাশ", "ভালোবাসি"),
        listOf("ভালোবাসি", "ভালবাসি", "ভালোবাশি"),
    ),
    DemoWord(
        "computer",
        listOf("চ", "চো", "চম", "চম্প", "চম্পু", "চম্পুত", "চম্পুতে", "কম্পিউটার"),
        listOf("কম্পিউটার", "computer", "কম্পিউটারে"),
    ),
)

private const val DEMO_KEY_MS = 170L
private const val DEMO_HOLD_MS = 2000L

@Composable
private fun TransliterationDemo(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var wordIndex by rememberSaveable { mutableIntStateOf(0) }
    var typed by remember { mutableIntStateOf(0) }
    // Runs only while the screen is visible; stops with the step.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val word = DemoWords[wordIndex]
                for (i in 1..word.roman.length) {
                    typed = i
                    delay(DEMO_KEY_MS)
                }
                delay(DEMO_HOLD_MS)
                typed = 0
                wordIndex = (wordIndex + 1) % DemoWords.size
                delay(DEMO_KEY_MS)
            }
        }
    }
    val word = DemoWords[wordIndex]
    val finished = typed == word.roman.length
    Column(
        modifier = modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.onboarding_you_type).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = word.roman.take(typed),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (typed == 0) "" else word.outputs[typed - 1],
                    style = MaterialTheme.typography.headlineSmall,
                )
                BlinkingCaret()
            }
        }
        Row(
            modifier = Modifier.alpha(if (finished) 1f else 0f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            word.suggestions.forEachIndexed { index, text ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (index == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlinkingCaret() {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caret-alpha",
    )
    Box(
        Modifier
            .padding(start = 2.dp)
            .width(2.dp)
            .height(28.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary),
    )
}

// --- Turn on / Switch ----------------------------------------------------------------------

@Composable
private fun TurnOnStep(enabled: Boolean, onOpen: () -> Unit) {
    StepLayout(
        title = stringResource(R.string.onboarding_turn_on_title),
        body = stringResource(R.string.onboarding_turn_on_body),
        stage = {
            SystemCard(title = stringResource(R.string.onboarding_system_keyboards)) {
                PlaceholderRow { Switch(checked = true, onCheckedChange = null) }
                AppRow { Switch(checked = enabled, onCheckedChange = null) }
                PlaceholderRow { Switch(checked = false, onCheckedChange = null) }
            }
        },
        extras = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    LucideIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_turn_on_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            if (enabled) {
                CompletedAction(stringResource(R.string.onboarding_turned_on))
            } else {
                PrimaryAction(stringResource(R.string.onboarding_turn_on_action), onOpen)
            }
        },
    )
}

@Composable
private fun SwitchStep(selected: Boolean, onChoose: () -> Unit) {
    StepLayout(
        title = stringResource(R.string.onboarding_switch_title),
        body = stringResource(R.string.onboarding_switch_body),
        stage = {
            SystemCard(title = stringResource(R.string.onboarding_system_picker)) {
                PlaceholderRow(leading = { RadioButton(selected = !selected, onClick = null) })
                AppRow(leading = { RadioButton(selected = selected, onClick = null) })
            }
        },
        actions = {
            if (selected) {
                CompletedAction(stringResource(R.string.onboarding_switched))
            } else {
                PrimaryAction(stringResource(R.string.onboarding_switch_action), onChoose)
            }
        },
    )
}

/** A drawing of an Android system list, for the steps that happen in system UI. */
@Composable
private fun SystemCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clearAndSetSemantics {},
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            )
            content()
        }
    }
}

/** Another keyboard in the system list, drawn as grey placeholders. */
@Composable
private fun PlaceholderRow(
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(placeholder))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(96.dp).height(10.dp).clip(CircleShape).background(placeholder))
            Box(Modifier.width(60.dp).height(8.dp).clip(CircleShape).background(placeholder))
        }
        trailing()
    }
}

/** Bornomala's own row in the system list, highlighted. */
@Composable
private fun AppRow(
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))) {
            Image(painterResource(R.mipmap.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.5f),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onboarding_languages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

// --- Make it yours -------------------------------------------------------------------------

@Composable
private fun CustomizeStep(settings: Settings, actions: OnboardingActions, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_customize_title), style = MaterialTheme.typography.headlineMedium)
            ThemePicker(selected = settings.keyboardTheme, onSelect = actions.onTheme)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = settings.numberRowEnabled,
                        role = Role.Switch,
                        onValueChange = actions.onNumberRow,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.onboarding_number_row),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.numberRowEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.onboarding_height),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                )
                Text(
                    stringResource(R.string.onboarding_height_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryAction(stringResource(R.string.onboarding_continue), onContinue)
        }
        // Pinned to the bottom like the live keyboard; the tray colour runs under the nav bar.
        Box(
            Modifier
                .fillMaxWidth()
                .background(keyboardColorsFor(settings.keyboardTheme, isSystemInDarkTheme()).keyboardBackground)
                .navigationBarsPadding(),
        ) {
            ResizableKeyboardPreview(
                settings = settings,
                onHeightChange = actions.onHeight,
                modifier = Modifier.fillMaxWidth(),
                handle = HeightHandle.TOP,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
        }
    }
}

@Composable
private fun ThemePicker(selected: KeyboardTheme, onSelect: (KeyboardTheme) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // Start with the current theme in view: the default (Solarized) is last in the row.
    LaunchedEffect(Unit) {
        val index = ColorThemes.indexOf(selected)
        scrollState.scrollTo(with(density) { ((SwatchSize + SwatchGap) * index).roundToPx() })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.onboarding_theme) + " · " + selected.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(SwatchGap),
        ) {
            ColorThemes.forEach { theme ->
                val colors = keyboardColorsFor(theme, systemDark)
                val isSelected = theme == selected
                Box(
                    modifier = Modifier
                        .size(SwatchSize)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(colors.keyboardBackground)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(theme) }
                        .semantics { contentDescription = theme.displayName },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.keyBackground),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset((-7).dp, (-7).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.accentKeyBackground),
                    )
                }
            }
        }
    }
}

/** The colour themes; the photo theme needs a picked image, so it lives in Settings only. */
private val ColorThemes = KeyboardTheme.entries.filter { it != KeyboardTheme.IMAGE }

private val SwatchSize = 56.dp
private val SwatchGap = 12.dp

// --- Done ----------------------------------------------------------------------------------

@Composable
private fun DoneStep(actions: OnboardingActions) {
    StepLayout(
        title = stringResource(R.string.onboarding_done_title),
        body = null,
        stage = {
            BackdropLetters()
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .rotate(-6f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LucideIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp).rotate(6f),
                )
            }
        },
        extras = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tip(LucideIcons.Globe, R.string.onboarding_tip_switch, R.string.onboarding_tip_switch_desc)
                Tip(LucideIcons.Languages, R.string.onboarding_tip_sound, R.string.onboarding_tip_sound_desc)
                Tip(LucideIcons.Settings, R.string.onboarding_tip_settings, R.string.onboarding_tip_settings_desc)
            }
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = actions.onOpenSettings,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.onboarding_settings), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = actions.onFinish,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.onboarding_finish), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
}

@Composable
private fun Tip(icon: ImageVector, title: Int, description: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- System state --------------------------------------------------------------------------

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isImeSelected(context: Context): Boolean {
    val id = AndroidSettings.Secure.getString(
        context.contentResolver,
        AndroidSettings.Secure.DEFAULT_INPUT_METHOD,
    ) ?: return false
    return id.startsWith(context.packageName + "/")
}
