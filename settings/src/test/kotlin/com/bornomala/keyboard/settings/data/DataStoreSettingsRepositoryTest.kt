package com.bornomala.keyboard.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.bornomala.keyboard.core.result.AppResult
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.settings.util.TestDispatcherProvider
import com.bornomala.keyboard.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for [DataStoreSettingsRepository] against a real Preferences
 * DataStore backed by a temp file (Robolectric supplies the Android runtime). Verifies
 * defaults, write -> read round-trips, clamping, and reset.
 */
@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        tempFile = File.createTempFile("settings_test", ".preferences_pb").apply { delete() }
        dataStoreScope = CoroutineScope(SupervisorJob() + testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        repository = DataStoreSettingsRepository(
            dataStore = dataStore,
            dispatchers = TestDispatcherProvider(testDispatcher),
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `initial emission is defaults`() = runTest(testDispatcher) {
        repository.settings.test {
            assertThat(awaitItem()).isEqualTo(Settings.DEFAULTS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writing theme mode persists and re-emits`() = runTest(testDispatcher) {
        repository.settings.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            val result = repository.setThemeMode(ThemeMode.DARK)
            assertThat(result).isInstanceOf(AppResult.Success::class.java)

            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writing multiple booleans persists each`() = runTest(testDispatcher) {
        repository.setKeyPressVibration(true)
        repository.setSuggestionsEnabled(false)
        repository.setNumberRowEnabled(true)
        repository.setClipboardEnabled(false)

        repository.settings.test {
            val latest = awaitItem()
            assertThat(latest.keyPressVibration).isTrue()
            assertThat(latest.suggestionsEnabled).isFalse()
            assertThat(latest.numberRowEnabled).isTrue()
            assertThat(latest.clipboardEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keyboard height scale is clamped before persisting`() = runTest(testDispatcher) {
        repository.setKeyboardHeightScale(10f)

        repository.settings.test {
            assertThat(awaitItem().keyboardHeightScale)
                .isEqualTo(Settings.MAX_KEYBOARD_HEIGHT_SCALE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset restores defaults`() = runTest(testDispatcher) {
        repository.setThemeMode(ThemeMode.DARK)
        repository.setKeyPressVibration(true)
        repository.setSuggestionsEnabled(false)

        val resetResult = repository.resetToDefaults()
        assertThat(resetResult).isInstanceOf(AppResult.Success::class.java)

        repository.settings.test {
            assertThat(awaitItem()).isEqualTo(Settings.DEFAULTS)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
