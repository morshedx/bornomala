package com.bornomala.keyboard.backup

import com.bornomala.keyboard.backup.crypto.CryptoBox
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoBoxTest {

    @Test
    fun `encrypt then decrypt with same passphrase round-trips`() {
        val plain = "hello বাংলা keyboard 123".toByteArray(Charsets.UTF_8)
        val blob = CryptoBox.encrypt(plain, "correct horse".toCharArray())
        val out = CryptoBox.decrypt(blob, "correct horse".toCharArray())
        assertThat(out).isEqualTo(plain)
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong passphrase fails authentication`() {
        val blob = CryptoBox.encrypt("secret".toByteArray(), "rightpass".toCharArray())
        CryptoBox.decrypt(blob, "wrongpass".toCharArray())
    }

    @Test
    fun `each encryption uses a fresh salt and iv`() {
        val a = CryptoBox.encrypt("x".toByteArray(), "pass12".toCharArray())
        val b = CryptoBox.encrypt("x".toByteArray(), "pass12".toCharArray())
        assertThat(a).isNotEqualTo(b)
    }
}
