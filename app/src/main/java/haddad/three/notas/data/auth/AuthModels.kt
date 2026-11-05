package haddad.three.notas.data.auth

import kotlinx.serialization.Serializable

/**
 * Domain representation of an authenticated user.
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val isEmailVerified: Boolean,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val verificationEmailSent: Boolean = true,
)

/**
 * Result wrapper for authentication operations.
 */
sealed interface AuthResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AuthResult<T>

    data class Error(
        val message: String,
        val code: String? = null,
        val cause: Throwable? = null,
    ) : AuthResult<Nothing>

    data object Cancelled : AuthResult<Nothing>
}

/**
 * Observable authentication state from Firebase (legacy / core).
 */
sealed interface AuthState {
    data object Loading : AuthState

    data object Unauthenticated : AuthState

    data class Authenticated(
        val user: AuthUser,
    ) : AuthState
}

/**
 * Explicit and reliable end-to-end application authentication lifecycle states.
 */
sealed interface AppAuthState {
    /** Initializing Firebase Auth & session persistence */
    data object Initializing : AppAuthState

    /** Unauthenticated state (No active Firebase session) */
    data object Unauthenticated : AppAuthState

    /** Intentional Guest state */
    data object Guest : AppAuthState

    /** Authentication operation is in progress */
    data object Authenticating : AppAuthState

    /** Authenticated with Firebase, actively loading or syncing profile from backend */
    data class LoadingProfile(
        val user: AuthUser,
    ) : AppAuthState

    /** Fully authenticated with Firebase identity and synchronized backend profile */
    data class Authenticated(
        val user: AuthUser,
        val backendProfile: BackendProfile? = null,
    ) : AppAuthState

    /** Recoverable authentication or backend profile sync error (retains Firebase identity) */
    data class RecoverableError(
        val user: AuthUser?,
        val message: String,
        val code: String? = null,
        val canRetry: Boolean = true,
    ) : AppAuthState
}

/**
 * Profile DTO returned by GET /api/v1/me.
 */
@Serializable
data class BackendProfile(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastSyncAt: String? = null,
)

@Serializable
data class BackendApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: BackendApiError? = null,
)

@Serializable
data class BackendApiError(
    val code: String,
    val message: String,
)

@Serializable
data class SendVerificationEmailResponse(
    val message: String,
    val email: String? = null,
)

@Serializable
data class VerifyOtpRequest(
    val code: String,
)

@Serializable
data class RequestPasswordResetRequest(
    val email: String,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)

@Serializable
data class OtpStatusResponse(
    val status: String,
    val emailVerified: Boolean,
)
