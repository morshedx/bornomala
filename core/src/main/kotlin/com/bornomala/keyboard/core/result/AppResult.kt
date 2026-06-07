package com.bornomala.keyboard.core.result

/**
 * A discriminated result type for operations that can fail.
 *
 * Preferred over throwing across architectural boundaries: domain and data layers
 * return [AppResult] so callers handle success/failure explicitly. This keeps the
 * IME input path predictable and avoids exceptions on hot paths.
 *
 * Named [AppResult] (not `Result`) to avoid clashing with Kotlin's stdlib `Result`.
 */
sealed interface AppResult<out T> {

    data class Success<out T>(val data: T) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)

        fun failure(error: AppError): AppResult<Nothing> = Failure(error)

        fun failure(
            message: String,
            cause: Throwable? = null,
        ): AppResult<Nothing> = Failure(AppError.Unknown(message, cause))
    }
}

/** Returns the success value or `null` if this is a [AppResult.Failure]. */
fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> null
}

/** Returns the success value or [default] if this is a [AppResult.Failure]. */
fun <T> AppResult<T>.getOrDefault(default: T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> default
}

/** Maps the success value, leaving failures untouched. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/** Chains another [AppResult]-returning operation onto a success. */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }

/** Invokes [action] with the success value, returning the original result. */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

/** Invokes [action] with the error, returning the original result. */
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * Runs [block], wrapping a thrown exception in [AppResult.Failure].
 *
 * Use at I/O boundaries (Room, DataStore, file reads) where third-party code can throw.
 * Do not use on per-keystroke hot paths to avoid try/catch overhead and allocations.
 */
inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (t: Throwable) {
    AppResult.Failure(AppError.from(t))
}
