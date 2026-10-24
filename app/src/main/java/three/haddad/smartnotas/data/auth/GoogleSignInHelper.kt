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
        } catch (_: NoCredentialException) {
            // No Google account on device: seamlessly open Android Account Settings so user can add one
            openAddAccountSettings(context)
        } catch (e: Exception) {
            val msg = (e.message ?: "").lowercase()
            if (msg.contains("no credential") || msg.contains("no account") || msg.contains("no google account")) {
                openAddAccountSettings(context)
            } else {
                AuthResult.Error(
                    message = e.localizedMessage ?: "Google Sign-In failed",
                    code = "SIGN_IN_FAILED",
                    cause = e,
                )
            }
        }
    }

    private fun openAddAccountSettings(context: Context): AuthResult<AuthCredential> {
        return try {
            val addAccountIntent = android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(addAccountIntent)
            AuthResult.Cancelled
        } catch (_: Exception) {
            try {
                val syncIntent = android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(syncIntent)
                AuthResult.Cancelled
            } catch (_: Exception) {
                try {
                    val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    AuthResult.Cancelled
                } catch (ex: Exception) {
                    AuthResult.Error(
                        "Please add a Google account in Android Settings.",
                        code = "NO_CREDENTIAL",
                        cause = ex,
                    )
                }
            }
        }
    }
}
