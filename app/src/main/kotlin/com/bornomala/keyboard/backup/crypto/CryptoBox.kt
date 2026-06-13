package com.bornomala.keyboard.backup.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based authenticated encryption for the backup blob.
 *
 * The key is derived from the user's sync passphrase with PBKDF2-HMAC-SHA256 (pure JDK, no
 * native lib) and a per-blob random salt, then used for AES-256-GCM. This is intentionally
 * NOT an Android Keystore key: a device-bound key could never decrypt the blob on a new
 * device, which would defeat restore. The same passphrase on any device reproduces the key.
 *
 * Blob layout: `magic(2) | version(1) | salt(16) | iv(12) | ciphertext+tag`.
 * A wrong passphrase fails decryption with [javax.crypto.AEADBadTagException].
 */
object CryptoBox {
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val MAGIC_HI = 0x42 // 'B'
    private const val MAGIC_LO = 0x4B // 'K'
    private const val VERSION = 1
    private const val HEADER = 3 // magic(2) + version(1)

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        val out = ByteArray(HEADER + SALT_LEN + IV_LEN + ct.size)
        out[0] = MAGIC_HI.toByte()
        out[1] = MAGIC_LO.toByte()
        out[2] = VERSION.toByte()
        System.arraycopy(salt, 0, out, HEADER, SALT_LEN)
        System.arraycopy(iv, 0, out, HEADER + SALT_LEN, IV_LEN)
        System.arraycopy(ct, 0, out, HEADER + SALT_LEN + IV_LEN, ct.size)
        return out
    }

    /** @throws javax.crypto.AEADBadTagException on a wrong passphrase or tampered data. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > HEADER + SALT_LEN + IV_LEN) { "Backup file is too small or corrupt" }
        require((blob[0].toInt() and 0xFF) == MAGIC_HI && (blob[1].toInt() and 0xFF) == MAGIC_LO) {
            "Not a Bornomala backup file"
        }
        val salt = blob.copyOfRange(HEADER, HEADER + SALT_LEN)
        val iv = blob.copyOfRange(HEADER + SALT_LEN, HEADER + SALT_LEN + IV_LEN)
        val ct = blob.copyOfRange(HEADER + SALT_LEN + IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
