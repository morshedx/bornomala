package com.bornomala.keyboard.suggestions.domain

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest

/**
 * The extension seam for the suggestion subsystem.
 *
 * A provider is a source of candidates. V1 ships exactly one active implementation,
 * [com.bornomala.keyboard.suggestions.data.provider.OfflineProvider], which reads
 * bundled frequency dictionaries plus the on-device user dictionary. A future cloud
 * provider can be added by implementing this interface and registering it without
 * touching [SuggestionEngine] — see
 * [com.bornomala.keyboard.suggestions.data.provider.FutureCloudProvider], which is
 * inert in V1 (no network is ever performed).
 *
 * Implementations must:
 * - be safe to call from a background dispatcher,
 * - never block the main thread,
 * - return [AppResult] rather than throwing across this boundary, and
 * - respect [isAvailable] so the engine can skip disabled/unreachable providers
 *   cheaply without paying for a full [suggest] call.
 */
interface SuggestionProvider {

    /** Stable identifier used for ordering and de-duplication across providers. */
    val id: String

    /**
     * Relative priority when multiple providers are registered. Higher wins ties.
     * The offline provider uses [PRIORITY_OFFLINE]; future remote providers should
     * sit below it so on-device results always lead when scores are equal.
     */
    val priority: Int

    /**
     * Whether this provider can currently serve requests. The engine calls this
     * before [suggest] to avoid wasted work. A cloud provider would return `false`
     * in V1 (no network permission), which is exactly how the seam stays inert.
     */
    fun isAvailable(language: SuggestionLanguage): Boolean

    /**
     * Produces ranked candidates for [request]. Returning [AppResult.Failure] lets
     * the engine degrade gracefully (drop this provider, keep the others) instead
     * of failing the whole keystroke.
     */
    suspend fun suggest(request: SuggestionRequest): AppResult<List<Suggestion>>

    /**
     * Records that [word] was actually committed by the user, so the provider can
     * learn (increment frequency / bigram counts). No-op for read-only providers.
     * Implementations must coalesce/persist off the main thread and must not block
     * the caller's input path.
     *
     * @param previousWord the committed token before [word], for next-word learning;
     *   empty if none.
     */
    suspend fun learn(
        word: String,
        previousWord: String,
        secondPreviousWord: String,
        language: SuggestionLanguage,
    ): AppResult<Unit>

    companion object {
        const val PRIORITY_OFFLINE: Int = 100
        const val PRIORITY_CLOUD: Int = 10
    }
}
