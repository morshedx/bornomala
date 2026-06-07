package com.bornomala.keyboard.core.result

/**
 * UI-facing state wrapper for asynchronous streams that must express a loading state.
 *
 * Use [Resource] in presentation layers (ViewModels exposing UI state). For one-shot
 * domain/data operations prefer [AppResult], which has no loading variant.
 */
sealed interface Resource<out T> {

    data object Loading : Resource<Nothing>

    data class Success<out T>(val data: T) : Resource<T>

    data class Error(
        val error: AppError,
        val data: Any? = null,
    ) : Resource<Nothing>

    companion object {
        fun <T> loading(): Resource<T> = Loading
        fun <T> success(data: T): Resource<T> = Success(data)
        fun error(error: AppError): Resource<Nothing> = Error(error)

        /** Bridges an [AppResult] into a [Resource] for direct UI consumption. */
        fun <T> from(result: AppResult<T>): Resource<T> = when (result) {
            is AppResult.Success -> Success(result.data)
            is AppResult.Failure -> Error(result.error)
        }
    }
}
