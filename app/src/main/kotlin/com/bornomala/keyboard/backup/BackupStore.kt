package com.bornomala.keyboard.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, non-synced state for the backup feature: the signed-in email, last-backup time and
 * the auto-backup toggle.
 */
@Singleton
class BackupStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    init {
        removeOldPassphrase()
    }

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

    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * Older versions could encrypt backups with a passphrase and kept it on the phone, wrapped
     * with an Android Keystore key. That option is gone, so drop the stored passphrase, its
     * toggle and the key the first time this runs after an update.
     */
    private fun removeOldPassphrase() {
        if (!prefs.contains(OLD_KEY_PASSPHRASE) && !prefs.contains(OLD_KEY_ENCRYPT)) return
        prefs.edit().remove(OLD_KEY_PASSPHRASE).remove(OLD_KEY_ENCRYPT).apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(OLD_KEY_ALIAS)
        }
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_AUTO = "auto_enabled"

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val OLD_KEY_ALIAS = "bornomala_backup_passphrase"
        const val OLD_KEY_PASSPHRASE = "passphrase"
        const val OLD_KEY_ENCRYPT = "encryption_enabled"
    }
}
