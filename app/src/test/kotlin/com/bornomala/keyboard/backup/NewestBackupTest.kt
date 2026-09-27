package com.bornomala.keyboard.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NewestBackupTest {

    private val legacy = BackupInfo("old", 10, modifiedAtMillis = 100, encrypted = true, legacy = true)
    private val plain = BackupInfo("plain", 20, modifiedAtMillis = 300)
    private val encrypted = BackupInfo("enc", 30, modifiedAtMillis = 200, encrypted = true)

    @Test
    fun `the most recent backup wins, whatever its kind`() {
        assertThat(newest(listOf(legacy, plain, encrypted))).isEqualTo(plain)
        assertThat(newest(listOf(legacy, encrypted))).isEqualTo(encrypted)
    }

    @Test
    fun `a lone legacy backup is still found`() {
        assertThat(newest(listOf(legacy))).isEqualTo(legacy)
    }

    @Test
    fun `no backups means none`() {
        assertThat(newest(emptyList())).isNull()
    }
}
