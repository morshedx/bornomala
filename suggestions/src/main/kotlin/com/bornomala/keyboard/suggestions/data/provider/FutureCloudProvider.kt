package com.bornomala.keyboard.suggestions.data.provider

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * INERT IN V1 — DO NOT WIRE THIS INTO THE ENGINE.
 *
 * This class exists solely to prove the [SuggestionProvider] extension seam: a remote
 * suggestion source can be added later by implementing this interface, without
 * touching [com.bornomala.keyboard.suggestions.domain.SuggestionEngine] or the
 * offline path.
 *
 * It performs NO network I/O of any kind. The app declares no INTERNET permission, and
 * this stub upholds that contract by:
 * - reporting [isAvailable] == `false` for every language, so a correctly written
 *   engine never even calls [suggest]; and
 * - returning an empty success / no-op from every method as a defensive second layer.
 *
 * It is deliberately NOT provided into the engine's provider set in
 * `SuggestionsModule` (only [OfflineProvider] is bound). Activating cloud suggestions
 * in a future version means implementing real logic here AND adding INTERNET
 * permission AND binding it — three explicit, reviewable steps.
 */
@Singleton
class FutureCloudProvider @Inject constructor() : SuggestionProvider {

    override val id: String = ID
    override val priority: Int = SuggestionProvider.PRIORITY_CLOUD

    /**
     * Always `false` in V1. No network is available or permitted, so the engine must
     * skip this provider entirely. This is the single switch that keeps the seam inert.
     */
    override fun isAvailable(language: SuggestionLanguage): Boolean = false

    /**
     * Defensive no-op. Even if some caller ignored [isAvailable], this returns an empty
     * success rather than performing any request — there is no network code here at all.
     */
    override suspend fun suggest(request: SuggestionRequest): AppResult<List<Suggestion>> =
        AppResult.Success(emptyList())

    /** No-op: nothing is learned remotely in V1. */
    override suspend fun learn(
        word: String,
        previousWord: String,
        secondPreviousWord: String,
        language: SuggestionLanguage,
    ): AppResult<Unit> = AppResult.Success(Unit)

    companion object {
        const val ID = "future_cloud"
    }
}
