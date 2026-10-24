package three.haddad.smartnotas.data.auth

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
)

/**
 * Result wrapper for authentication operations.
 */
sealed interface AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>
    data class Error(
        val message: String,
        val code: String? = null,
        val cause: Throwable? = null,
    ) : AuthResult<Nothing>
    data object Cancelled : AuthResult<Nothing>
}

/**
 * Observable authentication state from Firebase.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
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
