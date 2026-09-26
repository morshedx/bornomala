package com.bornomala.keyboard.di

import com.bornomala.keyboard.suggestions.domain.AvroRenderer
import com.bornomala.keyboard.transliteration.data.engine.AvroParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds `:suggestions`' [AvroRenderer] seam to the shared, stateless [AvroParser], so the bundled
 * auto-correct table renders through exactly the rules the keyboard types with.
 */
@Module
@InstallIn(SingletonComponent::class)
object AvroRendererModule {

    @Provides
    @Singleton
    fun provideAvroRenderer(parser: AvroParser): AvroRenderer = AvroRenderer(parser::parse)
}
