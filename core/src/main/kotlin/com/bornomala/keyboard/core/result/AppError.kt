package com.bornomala.keyboard.core.result

import kotlinx.coroutines.CancellationException

/**
 * Typed error model shared across modules. Feature modules can match on these
 * variants to render appropriate UI without leaking exception types upward.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
) {

    /** A requested entity (word, clipboard item, emoji) was not found. */
    data class NotFound(
        override val message: String = "Resource not found",
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /** Local persistence (Room / DataStore / file) failed. */
    data class Storage(
        override val message: String = "Storage operation failed",
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /** Input failed validation (e.g. empty word, oversized clipboard entry). */
    data class Validation(
        override val message: String = "Invalid input",
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /** Anything not otherwise categorized. */
    data class Unknown(
        override val message: String = "Unknown error",
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    companion object {
        /**
         * Converts a [Throwable] into a typed [AppError].
         *
         * [CancellationException] is rethrown rather than swallowed, so structured
         * concurrency cancellation continues to propagate correctly.
         */
        fun from(throwable: Throwable): AppError {
            if (throwable is CancellationException) throw throwable
            return Unknown(
                message = throwable.message ?: "Unknown error",
                cause = throwable,
            )
        }
    }
}
