package com.bornomala.keyboard.transliteration.di

import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import com.bornomala.keyboard.transliteration.data.engine.AvroTransliterationEngine
import com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngineFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the transliteration module.
 *
 * Exposes:
 *  - [AvroParser] as a singleton — the OmicronLab Avro rule dictionary is loaded and indexed
 *    exactly once; the parser is stateless and shared across the app.
 *  - [TransliterationEngineFactory] so consumers (the IME) get their own *stateful* engine
 *    instance (each owns a per-word buffer) while sharing the immutable parser.
 */
@Module
@InstallIn(SingletonComponent::class)
object TransliterationModule {

    @Provides
    @Singleton
    fun provideAvroParser(): AvroParser = AvroParser.load()

    @Provides
    @Singleton
    fun provideEngineFactory(
        parser: AvroParser,
    ): TransliterationEngineFactory = TransliterationEngineFactory {
        AvroTransliterationEngine(parser)
    }
}
