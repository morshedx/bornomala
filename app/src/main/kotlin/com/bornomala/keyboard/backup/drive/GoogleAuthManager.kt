package com.bornomala.keyboard.backup.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Obtains a Google OAuth access token for the Drive `drive.file` scope, using the modern
 * Identity Authorization API (the classic GoogleSignIn is deprecated). No client secret is
 * embedded — the Android OAuth client is identified by package name + signing SHA-1, which
 * must be registered in a Google Cloud project with the Drive API enabled.
 *
 * First grant needs a one-time consent UI (a [PendingIntent] launched from an Activity).
 * After that, [authorize] returns a fresh access token silently, so the background worker
 * can re-mint tokens without UI.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    sealed interface AuthState {
        /** Authorized; carries the access token and (best-effort) the account email. */
        data class Authorized(val accessToken: String, val email: String?) : AuthState
        /** First-time consent required; launch [pendingIntent] from an Activity. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : AuthState
    }

    /** Requests authorization. Returns a token if already granted, else a consent intent. */
    suspend fun authorize(): AuthState = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { cont.resume(toState(it)) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Parses the result returned by the consent [PendingIntent]. */
    fun resultFromIntent(data: Intent): AuthState.Authorized {
        val result = client.getAuthorizationResultFromIntent(data)
        return AuthState.Authorized(
            accessToken = result.accessToken ?: error("Authorization returned no access token"),
            email = result.toGoogleSignInAccount()?.email,
        )
    }

    private fun toState(result: AuthorizationResult): AuthState =
        if (result.hasResolution()) {
            AuthState.NeedsConsent(requireNotNull(result.pendingIntent) { "Resolution without intent" })
        } else {
            AuthState.Authorized(
                accessToken = result.accessToken ?: error("Authorization returned no access token"),
                email = result.toGoogleSignInAccount()?.email,
            )
        }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
