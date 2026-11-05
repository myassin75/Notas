package haddad.three.notas.data.drive.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest

/**
 * Interface managing Google Drive OAuth 2.0 authorization with narrow appDataFolder scope.
 */
interface GoogleDriveAuthDelegate {
    suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean = false,
    ): GoogleDriveAuthorizationResult

    suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleDriveAuthorizationResult

    suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String? = null,
    )

    suspend fun invalidateToken(
        context: Context,
        token: String,
    )
}

sealed class GoogleDriveAuthorizationResult {
    data class Success(
        val accessToken: String,
        val accountEmail: String,
        val accountId: String? = null,
    ) : GoogleDriveAuthorizationResult()

    data class NeedsConsent(
        val request: IntentSenderRequest,
    ) : GoogleDriveAuthorizationResult()

    data class Failure(
        val cause: Throwable,
    ) : GoogleDriveAuthorizationResult()

    data object Cancelled : GoogleDriveAuthorizationResult()
}

object DriveAuthConstants {
    /**
     * Narrowest required scope: access to application-specific private data folder only.
     * Does NOT give access to user's general Google Drive files.
     */
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
}

