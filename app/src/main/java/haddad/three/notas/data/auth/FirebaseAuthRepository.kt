package haddad.three.notas.data.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import haddad.three.notas.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendApiClient: BackendApiClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + ioDispatcher)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _appAuthState = MutableStateFlow<AppAuthState>(AppAuthState.Initializing)
    override val appAuthState: Flow<AppAuthState> = _appAuthState.asStateFlow()

    override val currentUser: AuthUser?
        get() = auth?.currentUser?.toAuthUser()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                val authUser = user.toAuthUser()
                _authState.value = AuthState.Authenticated(authUser)
                _appAuthState.value = AppAuthState.LoadingProfile(authUser)
                repositoryScope.launch {
                    when (val profileResult = syncBackendProfile()) {
                        is AuthResult.Success -> {
                            _appAuthState.value = AppAuthState.Authenticated(authUser, profileResult.data)
                        }
                        is AuthResult.Error -> {
                            _appAuthState.value = AppAuthState.RecoverableError(
                                user = authUser,
                                message = profileResult.message,
                                code = profileResult.code,
                            )
                        }
                        is AuthResult.Cancelled -> Unit
                    }
                }
            } else {
                _authState.value = AuthState.Unauthenticated
                _appAuthState.value = AppAuthState.Guest
            }
        } ?: run {
            _authState.value = AuthState.Unauthenticated
            _appAuthState.value = AppAuthState.Guest
        }
    }

    private fun checkConfigured(): AuthResult.Error? {
        if (auth == null) {
            return AuthResult.Error(
                message = "Firebase is not initialized. Please ensure google-services.json is in the app/ folder and Firebase Authentication is enabled in Firebase Console.",
                code = "FIREBASE_NOT_CONFIGURED",
            )
        }
        return null
    }

    override suspend fun registerWithEmailAndPassword(
        email: String,
        password: String,
        username: String?,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val result = auth!!.createUserWithEmailAndPassword(email, password).awaitResult()
            val user = result.user ?: return@withContext AuthResult.Error("Registration succeeded but no user was returned")

            // Update display name if username was supplied
            if (!username.isNullOrBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    user.updateProfile(profileUpdates).awaitResult()
                } catch (_: Exception) {
                    // Profile name update is secondary; user is already created
                }
            }

            // Send verification email
            var emailDispatched = false
            try {
                sendVerificationEmailToUser(user)
                emailDispatched = true
            } catch (e: Exception) {
                android.util.Log.w("FirebaseAuthRepository", "Failed to dispatch verification email to ${user.email}", e)
            }

            AuthResult.Success(user.toAuthUser(verificationEmailSent = emailDispatched))
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun loginWithEmailAndPassword(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val result = auth!!.signInWithEmailAndPassword(email, password).awaitResult()
            val user = result.user ?: return@withContext AuthResult.Error("Login succeeded but no user was returned")
            AuthResult.Success(user.toAuthUser())
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        // Fetch server client ID if configured (from string resources or fallback)
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName).let { id ->
            if (id != 0) id else context.resources.getIdentifier("default_web_client_id", "string", "haddad.three.notas")
        }
        val serverClientId = if (resId != 0) context.getString(resId) else null

        if (serverClientId.isNullOrBlank()) {
            return@withContext AuthResult.Error(
                message = "Google Sign-In is not configured. Web Client ID (default_web_client_id) is missing. Please add OAuth 2.0 Web Client ID from Firebase Console.",
                code = "CONFIG_MISSING",
            )
        }

        val credentialResult = GoogleSignInHelper.getGoogleAuthCredential(context, serverClientId)
        if (credentialResult is AuthResult.Error) return@withContext credentialResult
        if (credentialResult is AuthResult.Cancelled) return@withContext AuthResult.Cancelled

        val authCredential = (credentialResult as AuthResult.Success).data
        try {
            val result = auth!!.signInWithCredential(authCredential).awaitResult()
            val user = result.user ?: return@withContext AuthResult.Error("Google Sign-In succeeded but no user was returned")
            AuthResult.Success(user.toAuthUser())
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val user = auth!!.currentUser ?: return@withContext AuthResult.Error("No user currently logged in", code = "UNAUTHENTICATED")
            sendVerificationEmailToUser(user)
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    internal fun buildActionCodeSettings(): ActionCodeSettings? {
        return try {
            val continueUrl = "https://notas-7db14.firebaseapp.com/__/auth/action"
            ActionCodeSettings.newBuilder()
                .setUrl(continueUrl)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    context.packageName,
                    /* installIfNotAvailable = */ false,
                    /* minimumVersion = */ null,
                )
                .build()
        } catch (e: Exception) {
            android.util.Log.w("FirebaseAuthRepository", "Failed to build ActionCodeSettings", e)
            null
        }
    }

    private suspend fun sendVerificationEmailToUser(user: FirebaseUser) {
        val settings = buildActionCodeSettings()
        if (settings != null) {
            try {
                user.sendEmailVerification(settings).awaitResult()
                return
            } catch (e: Exception) {
                android.util.Log.w(
                    "FirebaseAuthRepository",
                    "sendEmailVerification with ActionCodeSettings failed; falling back to default sendEmailVerification",
                    e,
                )
            }
        }
        user.sendEmailVerification().awaitResult()
    }

    override suspend fun reloadUser(): AuthResult<AuthUser> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val user = auth!!.currentUser ?: return@withContext AuthResult.Error("No user currently logged in", code = "UNAUTHENTICATED")
            user.reload().awaitResult()
            val updatedUser = auth!!.currentUser?.toAuthUser() ?: user.toAuthUser()
            _authState.value = AuthState.Authenticated(updatedUser)
            AuthResult.Success(updatedUser)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            auth!!.sendPasswordResetEmail(email).awaitResult()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun confirmPasswordReset(
        actionCode: String,
        newPassword: String,
    ): AuthResult<Unit> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            auth!!.confirmPasswordReset(actionCode, newPassword).awaitResult()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val email = auth!!.verifyPasswordResetCode(actionCode).awaitResult()
            AuthResult.Success(email)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            auth!!.applyActionCode(actionCode).awaitResult()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun updatePassword(newPassword: String): AuthResult<Unit> = withContext(ioDispatcher) {
        checkConfigured()?.let { return@withContext it }

        try {
            val user = auth!!.currentUser ?: return@withContext AuthResult.Error("No user currently logged in", code = "UNAUTHENTICATED")
            user.updatePassword(newPassword).awaitResult()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            mapFirebaseException(e)
        }
    }

    override suspend fun signOut() {
        auth?.signOut()
        _authState.value = AuthState.Unauthenticated
        _appAuthState.value = AppAuthState.Guest
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = withContext(ioDispatcher) {
        try {
            auth?.currentUser?.getIdToken(forceRefresh)?.awaitResult()?.token
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun syncBackendProfile(): AuthResult<BackendProfile> = withContext(ioDispatcher) {
        var idToken = getIdToken() ?: return@withContext AuthResult.Error("User is not authenticated with Firebase", code = "UNAUTHENTICATED")
        var result = backendApiClient.getProfile(idToken)
        if (result is AuthResult.Error && result.code == "UNAUTHORIZED") {
            val refreshedToken = getIdToken(forceRefresh = true)
            if (refreshedToken != null) {
                idToken = refreshedToken
                result = backendApiClient.getProfile(idToken)
            }
        }
        val user = currentUser
        if (result is AuthResult.Success && user != null) {
            _appAuthState.value = AppAuthState.Authenticated(user, result.data)
        } else if (result is AuthResult.Error && user != null) {
            _appAuthState.value = AppAuthState.RecoverableError(user, result.message, result.code)
        }
        result
    }

    private fun <T> mapFirebaseException(e: Exception): AuthResult<T> {
        val message = when (e) {
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 8 characters with letters and numbers."
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
            is FirebaseAuthUserCollisionException -> "An account with this email address already exists."
            is FirebaseAuthActionCodeException -> "The verification or password reset link is invalid, expired, or has already been used."
            is FirebaseNetworkException -> "Network error. Please check your internet connection and try again."
            is FirebaseTooManyRequestsException -> "Too many attempts. Requests from this device are temporarily blocked. Please try again later."
            is com.google.firebase.auth.FirebaseAuthException -> when (e.errorCode) {
                "ERROR_QUOTA_EXCEEDED", "quota-exceeded" ->
                    "Email verification quota exceeded for this project. Please try again later or contact support."
                "ERROR_TOO_MANY_REQUESTS", "too-many-requests" ->
                    "Too many requests. Please wait a few moments before requesting another verification email."
                else -> e.localizedMessage ?: "Authentication operation failed."
            }
            else -> e.localizedMessage ?: "Authentication operation failed."
        }
        return AuthResult.Error(message = message, cause = e)
    }
}

private fun FirebaseUser.toAuthUser(verificationEmailSent: Boolean = true): AuthUser = AuthUser(
    uid = uid,
    email = email.orEmpty(),
    isEmailVerified = isEmailVerified,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    verificationEmailSent = verificationEmailSent,
)

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
