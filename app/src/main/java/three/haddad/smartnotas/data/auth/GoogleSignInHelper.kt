package three.haddad.smartnotas.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleSignInHelper {

    suspend fun getGoogleAuthCredential(
        context: Context,
        serverClientId: String?,
    ): AuthResult<AuthCredential> = withContext(Dispatchers.Main) {
        if (serverClientId.isNullOrBlank()) {
            return@withContext AuthResult.Error(
                message = "Google Sign-In Web Client ID is not configured. Please configure OAuth Client ID in Firebase Console.",
                code = "CONFIG_MISSING",
            )
        }

        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                AuthResult.Success(authCredential)
            } else {
                AuthResult.Error("Unsupported credential type received from Credential Manager", code = "UNSUPPORTED_CREDENTIAL")
            }
        } catch (_: GetCredentialCancellationException) {
            // User dismissed or cancelled the Google Account picker dialog
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            AuthResult.Error(
                message = "No eligible Google account or OAuth credential found on this device. Please verify that a Google account is logged in on your device and that your signing certificate SHA-1 fingerprint is registered in Firebase Console.",
                code = "NO_CREDENTIAL",
                cause = e,
            )
        } catch (e: androidx.credentials.exceptions.GetCredentialCustomException) {
            val msg = e.message.orEmpty()
            val friendlyMsg = if (msg.contains("10") || msg.contains("16") || msg.contains("28436") || msg.contains("DeveloperError", ignoreCase = true)) {
                "Google Sign-In configuration error: Package name or signing certificate SHA-1 fingerprint is not recognized by Google Play Services. Please verify the SHA-1 in Firebase Console."
            } else {
                e.localizedMessage ?: "Google Sign-In failed"
            }
            AuthResult.Error(
                message = friendlyMsg,
                code = "OAUTH_CONFIG_ERROR",
                cause = e,
            )
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.localizedMessage ?: "Google Sign-In failed",
                code = "SIGN_IN_FAILED",
                cause = e,
            )
        }
    }
}
