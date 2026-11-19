// File-level suppress because SignInClient.signOut() is deprecated but remains the documented
// path for clearing Play Services' Identity sign-in cache (no non-deprecated equivalent has
// shipped yet). Scoped to this source set so other deprecated-API uses elsewhere still get flagged.
@file:Suppress("DEPRECATION")

package haddad.three.notas.googletasks

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Tasks authentication using the modern Identity Services Authorization API.
 */
object GoogleTasksAuth {
    private const val TASKS_READONLY_SCOPE = "https://www.googleapis.com/auth/tasks.readonly"
    private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    private const val STATUS_CODE_CANCELED = 16
    private const val STATUS_CODE_SIGN_IN_CANCELLED = 12501
    private const val STATUS_CODE_SIGN_IN_CURRENTLY_IN_PROGRESS = 12502

    private val json = Json { ignoreUnknownKeys = true }
    private val requestedScopes = listOf(Scope(TASKS_READONLY_SCOPE), Scope(EMAIL_SCOPE))

    @Serializable
    private data class UserInfoResponse(
        val email: String? = null,
    )

    private fun authorizationRequest(forceAccountSelection: Boolean): AuthorizationRequest {
        val builder =
            AuthorizationRequest
                .Builder()
                .setRequestedScopes(requestedScopes)
        if (forceAccountSelection) {
            builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }
        return builder.build()
    }

    suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean,
    ): GoogleTasksAuthorizationResult =
        withContext(Dispatchers.IO) {
            try {
                val task =
                    Identity.getAuthorizationClient(context)
                        .authorize(authorizationRequest(forceAccountSelection))
                val result = task.await()
                result.toAuthorizationResult()
            } catch (e: ApiException) {
                GoogleTasksAuthorizationResult.Failure(e)
            } catch (e: Throwable) {
                GoogleTasksAuthorizationResult.Failure(e)
            }
        }

    suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleTasksAuthorizationResult =
        withContext(Dispatchers.IO) {
            if (data == null) return@withContext GoogleTasksAuthorizationResult.Failure(IllegalStateException("Empty consent result"))
            try {
                val result =
                    Identity.getAuthorizationClient(context)
                        .getAuthorizationResultFromIntent(data)
                result.toAuthorizationResult()
            } catch (e: ApiException) {
                GoogleTasksAuthorizationResult.Failure(e)
            } catch (e: Throwable) {
                GoogleTasksAuthorizationResult.Failure(e)
            }
        }

    suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String?,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        val account = accountEmail?.takeIf { it.isNotBlank() }?.let { Account(it, GOOGLE_ACCOUNT_TYPE) }
        if (account != null) {
            revokeGrantedScopes(context, account)
        } else if (!accessToken.isNullOrBlank()) {
            revokeToken(accessToken)
        }
        runCatching {
            Identity.getSignInClient(context).signOut().await()
        }
        Unit
    }

    suspend fun invalidateToken(
        context: Context,
        token: String,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, token)
        }
        Unit
    }

    private suspend fun revokeToken(accessToken: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn =
                    (URL(REVOKE_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        doOutput = true
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }
                try {
                    conn.outputStream.use { os ->
                        os.write("token=$accessToken".toByteArray(Charsets.UTF_8))
                    }
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }

    private suspend fun revokeGrantedScopes(
        context: Context,
        account: Account,
    ) {
        runCatching {
            val request =
                RevokeAccessRequest
                    .builder()
                    .setAccount(account)
                    .setScopes(requestedScopes)
                    .build()
            Identity.getAuthorizationClient(context).revokeAccess(request).await()
        }
    }

    private suspend fun AuthorizationResult.toAuthorizationResult(): GoogleTasksAuthorizationResult {
        if (hasResolution()) {
            val sender =
                pendingIntent?.intentSender
                    ?: return GoogleTasksAuthorizationResult.Failure(
                        IllegalStateException("Authorization result has resolution flag but no pendingIntent"),
                    )
            return GoogleTasksAuthorizationResult.NeedsConsent(
                IntentSenderRequest.Builder(sender).build(),
            )
        }
        val token = accessToken
        if (token.isNullOrBlank()) {
            return GoogleTasksAuthorizationResult.Failure(
                IllegalStateException("Authorization result has neither resolution nor access token"),
            )
        }
        if (TASKS_READONLY_SCOPE !in grantedScopes.orEmpty()) {
            return GoogleTasksAuthorizationResult.Failure(
                IllegalStateException("Google Tasks permission was not granted"),
            )
        }
        val accountFromBridge = toGoogleSignInAccount()?.email
        val email = accountFromBridge ?: fetchEmailFromUserInfo(token).orEmpty()
        return GoogleTasksAuthorizationResult.Success(accessToken = token, accountEmail = email)
    }

    private suspend fun fetchEmailFromUserInfo(accessToken: String): String? =
        withContext(Dispatchers.IO) {
            val conn =
                (URL(USERINFO_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doInput = true
                }
            try {
                if (conn.responseCode !in 200..299) return@withContext null
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                json.decodeFromString(UserInfoResponse.serializer(), body).email
            } catch (_: Throwable) {
                null
            } finally {
                conn.disconnect()
            }
        }
}

object DefaultGoogleTasksAuthDelegate : GoogleTasksAuthDelegate {
    override suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean,
    ): GoogleTasksAuthorizationResult = GoogleTasksAuth.authorize(context, forceAccountSelection)

    override suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleTasksAuthorizationResult = GoogleTasksAuth.parseConsentResult(context, data)

    override suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String?,
    ) {
        GoogleTasksAuth.disconnect(context, accountEmail, accessToken)
    }

    override suspend fun invalidateToken(
        context: Context,
        token: String,
    ) {
        GoogleTasksAuth.invalidateToken(context, token)
    }
}

/** Suspend-friendly wrapper around the Play Services [Task] so we don't pull in a play-services-coroutine artifact. */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        if (isComplete) {
            val ex = exception
            if (ex != null) {
                cont.resumeWithException(ex)
            } else if (isCanceled) {
                cont.cancel()
            } else {
                @Suppress("UNCHECKED_CAST")
                cont.resume(result as T)
            }
            return@suspendCancellableCoroutine
        }
        addOnSuccessListener { value -> cont.resume(value) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
        addOnCanceledListener { cont.cancel() }
    }
