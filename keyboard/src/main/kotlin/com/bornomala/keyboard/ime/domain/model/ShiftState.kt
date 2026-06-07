package com.bornomala.keyboard.ime.domain.model

/**
 * Tri-state shift, mirroring physical keyboard behaviour:
 *  - [OFF]: lowercase.
 *  - [SHIFTED]: next single character is uppercase, then auto-reverts to [OFF].
 *  - [CAPS_LOCK]: every character is uppercase until toggled off.
 *
 * Auto-capitalization sets [SHIFTED]; a double-tap on shift enters [CAPS_LOCK].
 */
enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK,
    ;

    /** True when the next emitted character should be uppercase. */
    val isUpper: Boolean
        get() = this == SHIFTED || this == CAPS_LOCK

    /**
     * Result of a single tap on the shift key. Cycling order:
     * OFF -> SHIFTED -> CAPS_LOCK -> OFF. (Double-tap-to-caps is handled at the service
     * layer via timing; this models the deterministic single-tap cycle.)
     */
    fun toggled(): ShiftState = when (this) {
        OFF -> SHIFTED
        SHIFTED -> CAPS_LOCK
        CAPS_LOCK -> OFF
    }

    /** Shift state after committing one character (SHIFTED auto-reverts; others persist). */
    fun afterCharCommit(): ShiftState = if (this == SHIFTED) OFF else this
}
