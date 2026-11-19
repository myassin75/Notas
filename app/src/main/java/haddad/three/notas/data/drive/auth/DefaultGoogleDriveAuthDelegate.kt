package haddad.three.notas.data.drive.auth

import android.content.Context
import android.content.Intent

object DefaultGoogleDriveAuthDelegate : GoogleDriveAuthDelegate {
    private val unavailable =
        UnsupportedOperationException("Google Drive backup is not available in the offline build")

    override suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean,
    ): GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Failure(unavailable)

    override suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleDriveAuthorizationResult = GoogleDriveAuthorizationResult.Failure(unavailable)

    override suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String?,
    ) = Unit

    override suspend fun invalidateToken(
        context: Context,
        token: String,
    ) = Unit
}
