package com.bornomala.keyboard.core.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {

    @Test
    fun `success carries data and reports isSuccess`() {
        val result = AppResult.success(42)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isFailure).isFalse()
        assertThat(result.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `failure reports isFailure and getOrNull returns null`() {
        val result = AppResult.failure(AppError.NotFound())
        assertThat(result.isFailure).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.getOrDefault(7)).isEqualTo(7)
    }

    @Test
    fun `map transforms success and preserves failure`() {
        assertThat(AppResult.success(2).map { it * 3 }.getOrNull()).isEqualTo(6)

        val failure: AppResult<Int> = AppResult.failure(AppError.Storage())
        assertThat(failure.map { it * 3 }.isFailure).isTrue()
    }

    @Test
    fun `flatMap chains success`() {
        val result = AppResult.success(5).flatMap { AppResult.success(it + 1) }
        assertThat(result.getOrNull()).isEqualTo(6)
    }

    @Test
    fun `onSuccess and onFailure invoke correct branches`() {
        var seenSuccess: Int? = null
        AppResult.success(9).onSuccess { seenSuccess = it }
        assertThat(seenSuccess).isEqualTo(9)

        var seenError: AppError? = null
        AppResult.failure(AppError.Validation()).onFailure { seenError = it }
        assertThat(seenError).isInstanceOf(AppError.Validation::class.java)
    }

    @Test
    fun `appRunCatching wraps thrown exception as failure`() {
        val result = appRunCatching<Int> { error("boom") }
        assertThat(result.isFailure).isTrue()
        val error = (result as AppResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(error.message).isEqualTo("boom")
    }
}
