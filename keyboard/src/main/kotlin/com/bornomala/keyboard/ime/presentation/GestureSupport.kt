package com.bornomala.keyboard.ime.presentation

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
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
