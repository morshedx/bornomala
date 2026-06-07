package com.bornomala.keyboard.suggestions.data

import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.suggestions.data.provider.FutureCloudProvider
import com.bornomala.keyboard.suggestions.domain.SuggestionProvider
import com.bornomala.keyboard.suggestions.domain.model.Suggestion
import com.bornomala.keyboard.suggestions.domain.model.SuggestionLanguage
import com.bornomala.keyboard.suggestions.domain.model.SuggestionRequest
import com.bornomala.keyboard.suggestions.domain.model.SuggestionSource
import com.bornomala.keyboard.suggestions.util.TestDispatcherProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Provider selection, merging, ranking, and learning fan-out in [DefaultSuggestionEngine]. */
class DefaultSuggestionEngineTest {

    private val dispatchers = TestDispatcherProvider()

    private fun request(current: String = "a", limit: Int = 3) =
        SuggestionRequest(current, "", SuggestionLanguage.ENGLISH, limit)

    /** Configurable fake provider for selection/merge tests. */
    private class FakeProvider(
        override val id: String,
        override val priority: Int,
        private val available: Boolean,
        private val results: List<Suggestion>,
        private val fail: Boolean = false,
        val learnCount: AtomicInteger = AtomicInteger(0),
    ) : SuggestionProvider {
        override fun isAvailable(language: SuggestionLanguage) = available
        override suspend fun suggest(request: SuggestionRequest): AppResult<List<Suggestion>> =
            if (fail) AppResult.Failure(com.bornomala.keyboard.core.result.AppError.Unknown("boom"))
            else AppResult.Success(results)
        override suspend fun learn(word: String, previousWord: String, language: SuggestionLanguage): AppResult<Unit> {
            learnCount.incrementAndGet()
            return AppResult.Success(Unit)
        }
    }

    private fun sug(word: String, score: Double, source: SuggestionSource = SuggestionSource.OFFLINE_DICTIONARY) =
        Suggestion(word, SuggestionLanguage.ENGLISH, source, score)

    @Test
    fun `unavailable providers are never queried`() = runTest {
        val cloud = FutureCloudProvider()
        val engine = DefaultSuggestionEngine(setOf(cloud), dispatchers)
        // FutureCloudProvider reports unavailable -> engine returns empty, no network.
        val result = engine.getSuggestions(request())
        assertThat(result).isEmpty()
    }

    @Test
    fun `only available providers contribute`() = runTest {
        val available = FakeProvider("a", 100, available = true, results = listOf(sug("apple", 0.9)))
        val unavailable = FakeProvider("b", 90, available = false, results = listOf(sug("avocado", 0.95)))
        val engine = DefaultSuggestionEngine(setOf(available, unavailable), dispatchers)

        val result = engine.getSuggestions(request())
        assertThat(result.map { it.word }).containsExactly("apple")
    }

    @Test
    fun `merges across providers and ranks by score`() = runTest {
        val p1 = FakeProvider("a", 100, true, listOf(sug("apple", 0.5), sug("ant", 0.9)))
        val p2 = FakeProvider("b", 50, true, listOf(sug("axe", 0.7)))
        val engine = DefaultSuggestionEngine(setOf(p1, p2), dispatchers)

        val result = engine.getSuggestions(request(limit = 3))
        assertThat(result.map { it.word }).containsExactly("ant", "axe", "apple").inOrder()
    }

    @Test
    fun `duplicate words keep the highest score`() = runTest {
        val high = FakeProvider("a", 100, true, listOf(sug("apple", 0.9)))
        val low = FakeProvider("b", 50, true, listOf(sug("apple", 0.3)))
        val engine = DefaultSuggestionEngine(setOf(high, low), dispatchers)

        val result = engine.getSuggestions(request())
        assertThat(result).hasSize(1)
        assertThat(result.first().score).isEqualTo(0.9)
    }

    @Test
    fun `provider failure is dropped and others survive`() = runTest {
        val failing = FakeProvider("a", 100, true, emptyList(), fail = true)
        val ok = FakeProvider("b", 50, true, listOf(sug("ok", 0.8)))
        val engine = DefaultSuggestionEngine(setOf(failing, ok), dispatchers)

        val result = engine.getSuggestions(request())
        assertThat(result.map { it.word }).containsExactly("ok")
    }

    @Test
    fun `honours the limit after merge`() = runTest {
        val p = FakeProvider("a", 100, true, listOf(sug("a1", 0.9), sug("a2", 0.8), sug("a3", 0.7), sug("a4", 0.6)))
        val engine = DefaultSuggestionEngine(setOf(p), dispatchers)
        assertThat(engine.getSuggestions(request(limit = 2))).hasSize(2)
    }

    @Test
    fun `learning is forwarded only to available providers`() = runTest {
        val available = FakeProvider("a", 100, available = true, results = emptyList())
        val unavailable = FakeProvider("b", 90, available = false, results = emptyList())
        val engine = DefaultSuggestionEngine(setOf(available, unavailable), dispatchers)

        engine.onWordCommitted("hello", "", SuggestionLanguage.ENGLISH)
        assertThat(available.learnCount.get()).isEqualTo(1)
        assertThat(unavailable.learnCount.get()).isEqualTo(0)
    }

    @Test
    fun `blank committed word is not learned`() = runTest {
        val p = FakeProvider("a", 100, true, emptyList())
        val engine = DefaultSuggestionEngine(setOf(p), dispatchers)
        engine.onWordCommitted("   ", "", SuggestionLanguage.ENGLISH)
        assertThat(p.learnCount.get()).isEqualTo(0)
    }

    @Test
    fun `empty provider set yields no suggestions`() = runTest {
        val engine = DefaultSuggestionEngine(emptySet(), dispatchers)
        assertThat(engine.getSuggestions(request())).isEmpty()
    }
}
