package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Guards the privacy contract: [FutureCloudProvider] must stay inert in V1 —
 * unavailable for every language and a no-op even if called.
 */
class FutureCloudProviderTest {

    private val provider = FutureCloudProvider()

    @Test
    fun `is unavailable for every language`() {
        for (language in SuggestionLanguage.entries) {
            assertThat(provider.isAvailable(language)).isFalse()
        }
    }

    @Test
    fun `priority is below the offline provider`() {
        assertThat(provider.priority).isLessThan(SuggestionProvider.PRIORITY_OFFLINE)
        assertThat(provider.priority).isEqualTo(SuggestionProvider.PRIORITY_CLOUD)
    }

    @Test
    fun `suggest is a no-op returning empty success`() = runTest {
        val result = provider.suggest(
            SuggestionRequest(currentWord = "any", previousWord = "", language = SuggestionLanguage.ENGLISH),
        )
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isEmpty()
    }

    @Test
    fun `learn is a no-op returning success`() = runTest {
        val result = provider.learn("word", "", "", SuggestionLanguage.ENGLISH)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }
}
