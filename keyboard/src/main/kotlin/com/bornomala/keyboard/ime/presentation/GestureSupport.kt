package com.bornomala.keyboard.ime.presentation

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits for the active pointer to lift, but only up to [timeoutMillis]. Returns the
 * pointer change for the up event, or null if the timeout elapsed first (still held) or
 * the gesture was cancelled by another consumer.
 *
 * Used by [KeyView] for two timing-sensitive behaviours without spawning coroutines or
 * allocating per frame: detecting a long-press (release-or-timeout) and pacing key repeat
 * (lift-or-interval). Built on the raw pointer event loop to keep the key path cheap.
 */
internal suspend fun AwaitPointerEventScope.waitForUpOrCancellationWindowed(
    timeoutMillis: Long,
): PointerInputChange? = withTimeoutOrNull(timeoutMillis) {
    while (true) {
        val event = awaitPointerEvent()
        val anyPressed = event.changes.any { it.pressed }
        if (!anyPressed) {
            // All pointers up.
            return@withTimeoutOrNull event.changes.firstOrNull()
        }
        // If a change was consumed elsewhere, the gesture is being handled by another node.
        val consumed = event.changes.any { it.isConsumed }
        if (consumed) return@withTimeoutOrNull null
    }
    @Suppress("UNREACHABLE_CODE")
    null
}

/** Outcome of the spacebar arming window (the first [longPressTimeout] after touch-down). */
private enum class SpaceArm { TAP, CURSOR, CANCEL }

/**
 * Drives the spacebar's hold-and-swipe gesture from the raw pointer stream. Three phases, no
 * coroutine spawning and no per-frame allocation (only local primitives):
 *
 *  1. **Arm** — within [longPressTimeoutMillis] of touch-down: an early lift is a plain tap
 *     ([onTap]); crossing [slopPx] horizontally enters cursor mode; otherwise the press is held
 *     and falls through to repeat.
 *  2. **Repeat** — a held-still press emits [onRepeat] every [repeatIntervalMillis] (the classic
 *     hold-to-insert-spaces). Crossing [slopPx] at any time upgrades to cursor mode.
 *  3. **Cursor** — horizontal travel is accumulated and emitted as whole-character steps via
 *     [onCursorStep] (signed; negative = left), one call carrying however many characters the
 *     finger crossed since the last emit, so a fast flick is a single batched move.
 *
 * [startX] is the touch-down x in this pointer node's local space; all travel is measured from it.
 * Exactly one of [onTap]/[onRepeat]/[onCursorStep] paths runs per gesture (repeat may precede a
 * cursor upgrade). The caller's own touch-slop should drive [slopPx] so an ordinary tap that
 * micro-drifts still registers as a space.
 */
internal suspend fun AwaitPointerEventScope.detectSpacebarGesture(
    startX: Float,
    slopPx: Float,
    pxPerChar: Float,
    longPressTimeoutMillis: Long,
    repeatIntervalMillis: Long,
    onTap: () -> Unit,
    onRepeat: () -> Unit,
    onCursorStep: (Int) -> Unit,
) {
    var lastX = startX
    var accum = 0f

    // Phase 1 — arm: race an early lift / horizontal slop-cross against the long-press timeout.
    val armed: SpaceArm? = withTimeoutOrNull(longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: return@withTimeoutOrNull SpaceArm.CANCEL
            if (change.isConsumed) return@withTimeoutOrNull SpaceArm.CANCEL
            if (!change.pressed) {
                change.consume()
                return@withTimeoutOrNull SpaceArm.TAP
            }
            if (abs(change.position.x - startX) > slopPx) {
                lastX = change.position.x
                change.consume()
                return@withTimeoutOrNull SpaceArm.CURSOR
            }
        }
        @Suppress("UNREACHABLE_CODE") SpaceArm.CANCEL
    }

    when (armed) {
        SpaceArm.TAP -> {
            onTap()
            return
        }
        SpaceArm.CANCEL -> return
        SpaceArm.CURSOR -> Unit // fall through to the cursor loop below
        null -> {
            // Phase 2 — repeat: held still past the timeout. Fire once on entry, then on every
            // elapsed interval with no event; a slop-cross breaks out into the cursor loop.
            onRepeat()
            while (true) {
                val change = withTimeoutOrNull(repeatIntervalMillis) {
                    awaitPointerEvent().changes.firstOrNull()
                }
                if (change == null) {
                    onRepeat()
                    continue
                }
                if (change.isConsumed || !change.pressed) {
                    change.consume()
                    return
                }
                if (abs(change.position.x - startX) > slopPx) {
                    lastX = change.position.x
                    change.consume()
                    break // upgrade to cursor mode
                }
                // Small drift while held: ignore and keep the repeat cadence.
            }
        }
    }

    // Phase 3 — cursor: accumulate horizontal travel, emit whole-character steps.
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: return
        if (change.isConsumed) return
        val x = change.position.x
        accum += x - lastX
        lastX = x
        val steps = (accum / pxPerChar).toInt()
        if (steps != 0) {
            onCursorStep(steps)
            accum -= steps * pxPerChar
        }
        change.consume()
        if (!change.pressed) return
    }
}
