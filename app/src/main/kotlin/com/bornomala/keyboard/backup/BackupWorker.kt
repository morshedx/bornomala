package com.bornomala.keyboard.backup

import kotlinx.coroutines.CancellationException
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bornomala.keyboard.backup.drive.GoogleAuthManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Background auto-backup. A plain [CoroutineWorker] (constructed by the default WorkManager
 * factory) that pulls its dependencies through a Hilt [EntryPoint], so no hilt-work wiring is
 * needed. Skips silently when not configured; retries on transient auth/network failure.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backupManager(): BackupManager
        fun authManager(): GoogleAuthManager
        fun backupStore(): BackupStore
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val store = deps.backupStore()
        // Not configured → nothing to do.
        if (!store.autoEnabled) return Result.success()
        val token = (deps.authManager().authorize() as? GoogleAuthManager.AuthState.Authorized)
            ?.accessToken
            ?: return retryOrGiveUp() // grant lapsed → try again later
        return try {
            deps.backupManager().backUp(token)
            store.lastBackupAt = System.currentTimeMillis()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            retryOrGiveUp()
        }
    }

    /** Bounded: a periodic run that keeps failing waits for the next period instead. */
    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    private companion object {
        const val MAX_RETRIES = 5
    }
}
