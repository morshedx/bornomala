package com.bornomala.keyboard.di

import com.bornomala.keyboard.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import im.morshed.ota.OtaConfig
import javax.inject.Singleton

/**
 * Hands the OTA library its per-app configuration: the version-manifest URL it polls and the
 * bearer token for the R2 gateway. Both come from [BuildConfig] (set at build time from
 * local.properties / the environment), so nothing secret is committed.
 */
@Module
@InstallIn(SingletonComponent::class)
object OtaModule {
    @Provides
    @Singleton
    fun provideOtaConfig(): OtaConfig = OtaConfig(
        manifestUrl = BuildConfig.MANIFEST_URL,
        authToken = BuildConfig.UPDATE_TOKEN,
    )
}
