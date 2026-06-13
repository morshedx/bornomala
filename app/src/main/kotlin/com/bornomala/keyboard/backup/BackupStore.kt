package com.bornomala.keyboard.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, non-synced state for the backup feature: the signed-in email, last-backup time,
 * the auto-backup toggle, and the user's sync passphrase.
 *
 * The passphrase is wrapped with an Android Keystore AES key before being stored (so it is
 * not readable from the prefs file). This device-bound key is fine here — it only protects
 * the *local cache* so the background worker can encrypt without prompting; the backup blob
 * itself is encrypted with the passphrase (device-independent) so it still restores elsewhere.
 */
@Singleton
class BackupStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    var autoEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    val signedIn: Boolean get() = email != null
    val hasPassphrase: Boolean get() = prefs.contains(KEY_PASSPHRASE)

    fun savePassphrase(passphrase: CharArray) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val ct = cipher.doFinal(String(passphrase).toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ct // iv(12) + ciphertext
        prefs.edit().putString(KEY_PASSPHRASE, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun loadPassphrase(): CharArray? {
        val packed = prefs.getString(KEY_PASSPHRASE, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return runCatching {
            val iv = packed.copyOfRange(0, GCM_IV_LEN)
            val ct = packed.copyOfRange(GCM_IV_LEN, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8).toCharArray()
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bornomala_backup_passphrase"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_IV_LEN = 12
        const val KEY_EMAIL = "email"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_AUTO = "auto_enabled"
        const val KEY_PASSPHRASE = "passphrase"
    }
}
