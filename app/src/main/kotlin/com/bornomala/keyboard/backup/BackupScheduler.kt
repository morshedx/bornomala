package com.bornomala.keyboard.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules automatic backups via WorkManager once the user enables auto-backup (after a
 * one-time sign-in + passphrase). A daily periodic backup runs when charging + on Wi-Fi
 * (battery/data friendly). A debounced one-shot can also be requested after data changes.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun enableDaily() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable() {
        workManager.cancelUniqueWork(DAILY_WORK)
        workManager.cancelUniqueWork(SOON_WORK)
    }

    /** Debounced one-shot backup ~15 min after a change; a newer request replaces the pending one. */
    fun backupSoon() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(SOON_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val DAILY_WORK = "bornomala_backup_daily"
        const val SOON_WORK = "bornomala_backup_soon"
    }
}
