package three.haddad.smartnotas.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import three.haddad.smartnotas.data.auth.AuthRepository
import three.haddad.smartnotas.data.auth.AuthResult
import three.haddad.smartnotas.data.auth.AuthState
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
)

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
)

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val isLinkSent: Boolean = false,
    val resendCountdown: Int = 30,
    val canResend: Boolean = true,
    val errorMessage: String? = null,
)

data class SetNewPasswordUiState(
    val actionCode: String? = null,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isLinkExpired: Boolean = false,
    val errorMessage: String? = null,
)

data class VerifyEmailUiState(
    val email: String = "",
    val isChecking: Boolean = false,
    val isVerified: Boolean = false,
    val canResend: Boolean = false,
    val resendCountdown: Int = 59,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _forgotPasswordState = MutableStateFlow(ForgotPasswordUiState())
    val forgotPasswordState: StateFlow<ForgotPasswordUiState> = _forgotPasswordState.asStateFlow()

    private val _setNewPasswordState = MutableStateFlow(SetNewPasswordUiState())
    val setNewPasswordState: StateFlow<SetNewPasswordUiState> = _setNewPasswordState.asStateFlow()

    private val _verifyEmailState = MutableStateFlow(VerifyEmailUiState())
    val verifyEmailState: StateFlow<VerifyEmailUiState> = _verifyEmailState.asStateFlow()

    private var verifyTimerJob: Job? = null
    private var forgotTimerJob: Job? = null

    // -----------------------------------------------------------------------------------------
    // Registration Form
    // -----------------------------------------------------------------------------------------
    fun onRegisterUsernameChange(value: String) {
        _registerState.update { it.copy(username = value, usernameError = null, errorMessage = null) }
    }

    fun onRegisterEmailChange(value: String) {
        _registerState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onRegisterPasswordChange(value: String) {
        _registerState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun validateAndSubmitRegistration(onSuccess: (email: String) -> Unit) {
        val current = _registerState.value
        val usernameError = when {
            current.username.trim().isEmpty() -> "Username is required"
            current.username.trim().length < 3 -> "Username must be at least 3 characters"
            else -> null
        }
        val emailError = when {
            current.email.trim().isEmpty() -> "Email address is required"
            !isValidEmail(current.email) -> "Please enter a valid email address"
            else -> null
        }
        val passwordError = when {
            current.password.isEmpty() -> "Password is required"
            current.password.length < 8 -> "Password must be at least 8 characters"
            !current.password.any { it.isLetter() } || !current.password.any { it.isDigit() } ->
                "Password must include at least one letter and one number"
            else -> null
        }

        if (usernameError != null || emailError != null || passwordError != null) {
            _registerState.update {
                it.copy(
                    usernameError = usernameError,
                    emailError = emailError,
                    passwordError = passwordError,
                )
            }
            return
        }

        viewModelScope.launch {
            _registerState.update { it.copy(isLoading = true, errorMessage = null) }
            val cleanEmail = current.email.trim()
            val cleanUsername = current.username.trim()

            when (val result = authRepository.registerWithEmailAndPassword(cleanEmail, current.password, cleanUsername)) {
                is AuthResult.Success -> {
                    _registerState.update { it.copy(isLoading = false, isSuccess = true) }
                    initVerifyEmail(cleanEmail)
                    onSuccess(cleanEmail)
                }
                is AuthResult.Error -> {
                    _registerState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _registerState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Login Form
    // -----------------------------------------------------------------------------------------
    fun onLoginEmailChange(value: String) {
        _loginState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onLoginPasswordChange(value: String) {
        _loginState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun validateAndSubmitLogin(onSuccess: () -> Unit) {
        val current = _loginState.value
        val emailError = when {
            current.email.trim().isEmpty() -> "Email address is required"
            !isValidEmail(current.email) -> "Please enter a valid email address"
            else -> null
        }
        val passwordError = when {
            current.password.isEmpty() -> "Password is required"
            else -> null
        }

        if (emailError != null || passwordError != null) {
            _loginState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                )
            }
            return
        }

        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.loginWithEmailAndPassword(current.email.trim(), current.password)) {
                is AuthResult.Success -> {
                    // Sync profile with backend serverless endpoint
                    authRepository.syncBackendProfile()
                    _loginState.update { it.copy(isLoading = false, isSuccess = true) }
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _loginState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _loginState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Google Sign-In
    // -----------------------------------------------------------------------------------------
    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, errorMessage = null) }
            _registerState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.signInWithGoogle(context)) {
                is AuthResult.Success -> {
                    authRepository.syncBackendProfile()
                    _loginState.update { it.copy(isLoading = false, isSuccess = true) }
                    _registerState.update { it.copy(isLoading = false, isSuccess = true) }
                    onSuccess()
                }
                is AuthResult.Cancelled -> {
                    // Silent cancellation: User dismissed the account dialog
                    _loginState.update { it.copy(isLoading = false) }
                    _registerState.update { it.copy(isLoading = false) }
                }
                is AuthResult.Error -> {
                    _loginState.update { it.copy(isLoading = false, errorMessage = result.message) }
                    _registerState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Verify Email
    // -----------------------------------------------------------------------------------------
    fun initVerifyEmail(email: String) {
        _verifyEmailState.update {
            it.copy(
                email = email,
                isChecking = false,
                isVerified = false,
                canResend = false,
                resendCountdown = 59,
                errorMessage = null,
            )
        }
        startVerifyCountdown()
    }

    private fun startVerifyCountdown() {
        verifyTimerJob?.cancel()
        verifyTimerJob = viewModelScope.launch {
            var remaining = 59
            while (remaining > 0) {
                delay(1000)
                remaining--
                _verifyEmailState.update { it.copy(resendCountdown = remaining, canResend = remaining == 0) }
            }
        }
    }

    fun resendVerificationEmail() {
        if (!_verifyEmailState.value.canResend) return
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AuthResult.Success -> {
                    startVerifyCountdown()
                }
                is AuthResult.Error -> {
                    _verifyEmailState.update { it.copy(errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> Unit
            }
        }
    }

    fun checkEmailVerificationStatus(onVerified: () -> Unit) {
        viewModelScope.launch {
            _verifyEmailState.update { it.copy(isChecking = true, errorMessage = null) }
            when (val result = authRepository.reloadUser()) {
                is AuthResult.Success -> {
                    if (result.data.isEmailVerified) {
                        _verifyEmailState.update { it.copy(isChecking = false, isVerified = true) }
                        authRepository.syncBackendProfile()
                        onVerified()
                    } else {
                        _verifyEmailState.update {
                            it.copy(
                                isChecking = false,
                                errorMessage = "Your email has not been verified yet. Please check your inbox and click the verification link.",
                            )
                        }
                    }
                }
                is AuthResult.Error -> {
                    _verifyEmailState.update { it.copy(isChecking = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _verifyEmailState.update { it.copy(isChecking = false) }
                }
            }
        }
    }

    fun applyEmailVerificationCode(actionCode: String, onVerified: () -> Unit) {
        viewModelScope.launch {
            _verifyEmailState.update { it.copy(isChecking = true, errorMessage = null) }
            when (val result = authRepository.applyActionCode(actionCode)) {
                is AuthResult.Success -> {
                    when (val reloadResult = authRepository.reloadUser()) {
                        is AuthResult.Success -> {
                            if (reloadResult.data.isEmailVerified) {
                                _verifyEmailState.update { it.copy(isChecking = false, isVerified = true) }
                                authRepository.syncBackendProfile()
                                onVerified()
                            } else {
                                _verifyEmailState.update {
                                    it.copy(
                                        isChecking = false,
                                        errorMessage = "Email verification code applied, but email is not verified.",
                                    )
                                }
                            }
                        }
                        is AuthResult.Error -> {
                            _verifyEmailState.update { it.copy(isChecking = false, errorMessage = reloadResult.message) }
                        }
                        is AuthResult.Cancelled -> {
                            _verifyEmailState.update { it.copy(isChecking = false) }
                        }
                    }
                }
                is AuthResult.Error -> {
                    _verifyEmailState.update { it.copy(isChecking = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _verifyEmailState.update { it.copy(isChecking = false) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Forgot Password
    // -----------------------------------------------------------------------------------------
    fun onForgotEmailChange(value: String) {
        _forgotPasswordState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun validateAndSendResetLink() {
        val current = _forgotPasswordState.value
        val emailError = when {
            current.email.trim().isEmpty() -> "Email address is required"
            !isValidEmail(current.email) -> "Please enter a valid email address"
            else -> null
        }

        if (emailError != null) {
            _forgotPasswordState.update { it.copy(emailError = emailError) }
            return
        }

        viewModelScope.launch {
            _forgotPasswordState.update { it.copy(isLoading = true, errorMessage = null) }
            val cleanEmail = current.email.trim()
            when (val result = authRepository.sendPasswordResetEmail(cleanEmail)) {
                is AuthResult.Success -> {
                    _forgotPasswordState.update {
                        it.copy(
                            isLoading = false,
                            isLinkSent = true,
                            canResend = false,
                            resendCountdown = 30,
                        )
                    }
                    startForgotCountdown()
                }
                is AuthResult.Error -> {
                    _forgotPasswordState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _forgotPasswordState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun startForgotCountdown() {
        forgotTimerJob?.cancel()
        forgotTimerJob = viewModelScope.launch {
            var remaining = 30
            while (remaining > 0) {
                delay(1000)
                remaining--
                _forgotPasswordState.update { it.copy(resendCountdown = remaining, canResend = remaining == 0) }
            }
        }
    }

    fun resendResetLink() {
        if (!_forgotPasswordState.value.canResend) return
        validateAndSendResetLink()
    }

    // -----------------------------------------------------------------------------------------
    // Set New Password
    // -----------------------------------------------------------------------------------------
    fun initSetNewPassword(actionCode: String?) {
        _setNewPasswordState.update {
            it.copy(
                actionCode = actionCode,
                newPassword = "",
                confirmPassword = "",
                newPasswordError = null,
                confirmPasswordError = null,
                isLoading = false,
                isSuccess = false,
                isLinkExpired = false,
                errorMessage = null,
            )
        }

        if (!actionCode.isNullOrBlank()) {
            verifyActionCode(actionCode)
        }
    }

    fun verifyActionCode(actionCode: String) {
        viewModelScope.launch {
            _setNewPasswordState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.verifyPasswordResetCode(actionCode)) {
                is AuthResult.Success -> {
                    _setNewPasswordState.update { it.copy(isLoading = false, isLinkExpired = false) }
                }
                is AuthResult.Error -> {
                    _setNewPasswordState.update { it.copy(isLoading = false, isLinkExpired = true, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _setNewPasswordState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onNewPasswordChange(value: String) {
        _setNewPasswordState.update { it.copy(newPassword = value, newPasswordError = null, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _setNewPasswordState.update { it.copy(confirmPassword = value, confirmPasswordError = null, errorMessage = null) }
    }

    fun validateAndSubmitNewPassword(onSuccess: () -> Unit) {
        val current = _setNewPasswordState.value
        val newPasswordError = when {
            current.newPassword.isEmpty() -> "New password is required"
            current.newPassword.length < 8 -> "Password must be at least 8 characters"
            !current.newPassword.any { it.isLetter() } || !current.newPassword.any { it.isDigit() } ->
                "Password must include at least one letter and one number"
            else -> null
        }
        val confirmPasswordError = when {
            current.confirmPassword.isEmpty() -> "Please confirm your password"
            current.confirmPassword != current.newPassword -> "Passwords do not match"
            else -> null
        }

        if (newPasswordError != null || confirmPasswordError != null) {
            _setNewPasswordState.update {
                it.copy(
                    newPasswordError = newPasswordError,
                    confirmPasswordError = confirmPasswordError,
                )
            }
            return
        }

        viewModelScope.launch {
            _setNewPasswordState.update { it.copy(isLoading = true, errorMessage = null) }

            val actionCode = current.actionCode
            val result = if (!actionCode.isNullOrBlank()) {
                authRepository.confirmPasswordReset(actionCode, current.newPassword)
            } else {
                authRepository.updatePassword(current.newPassword)
            }

            when (result) {
                is AuthResult.Success -> {
                    _setNewPasswordState.update { it.copy(isLoading = false, isSuccess = true) }
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _setNewPasswordState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is AuthResult.Cancelled -> {
                    _setNewPasswordState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun setLinkExpiredState(isExpired: Boolean) {
        _setNewPasswordState.update { it.copy(isLinkExpired = isExpired) }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    companion object {
        fun isValidEmail(email: String): Boolean {
            val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            return email.trim().matches(emailRegex)
        }
    }
}
