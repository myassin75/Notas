package haddad.three.notas.ui.auth

import android.content.Context
import android.content.ContextWrapper
import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthResult
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AuthUser
import haddad.three.notas.data.auth.BackendProfile
import haddad.three.notas.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================================
    // Registration Validation & Flow Tests
    // =========================================================================================

    @Test
    fun register_emptyUsername_setsUsernameError() =
        runTest {
            viewModel.onRegisterUsernameChange("")
            viewModel.onRegisterEmailChange("user@example.com")
            viewModel.onRegisterPasswordChange("Password123")

            var successCalled = false
            viewModel.validateAndSubmitRegistration { successCalled = true }

            val state = viewModel.registerState.value
            assertEquals("Username is required", state.usernameError)
            assertFalse(successCalled)
        }

    @Test
    fun register_shortUsername_setsLengthError() =
        runTest {
            viewModel.onRegisterUsernameChange("ab")
            viewModel.onRegisterEmailChange("user@example.com")
            viewModel.onRegisterPasswordChange("Password123")

            var successCalled = false
            viewModel.validateAndSubmitRegistration { successCalled = true }

            val state = viewModel.registerState.value
            assertEquals("Username must be at least 3 characters", state.usernameError)
            assertFalse(successCalled)
        }

    @Test
    fun register_invalidEmail_setsEmailError() =
        runTest {
            viewModel.onRegisterUsernameChange("validUser")
            viewModel.onRegisterEmailChange("invalid-email")
            viewModel.onRegisterPasswordChange("Password123")

            viewModel.validateAndSubmitRegistration {}

            val state = viewModel.registerState.value
            assertEquals("Please enter a valid email address", state.emailError)
        }

    @Test
    fun register_weakPassword_setsPasswordError() =
        runTest {
            viewModel.onRegisterUsernameChange("validUser")
            viewModel.onRegisterEmailChange("user@example.com")

            // Too short
            viewModel.onRegisterPasswordChange("Pass1")
            viewModel.validateAndSubmitRegistration {}
            assertEquals("Password must be at least 8 characters", viewModel.registerState.value.passwordError)

            // Missing number
            viewModel.onRegisterPasswordChange("PasswordOnly")
            viewModel.validateAndSubmitRegistration {}
            assertEquals("Password must include at least one letter and one number", viewModel.registerState.value.passwordError)

            // Missing letter
            viewModel.onRegisterPasswordChange("12345678")
            viewModel.validateAndSubmitRegistration {}
            assertEquals("Password must include at least one letter and one number", viewModel.registerState.value.passwordError)
        }

    @Test
    fun register_validForm_submitsAndInitializesEmailVerification() =
        runTest {
            viewModel.onRegisterUsernameChange("validUser")
            viewModel.onRegisterEmailChange("user@example.com")
            viewModel.onRegisterPasswordChange("SecurePassword1")

            var registeredEmail: String? = null
            viewModel.validateAndSubmitRegistration { email ->
                registeredEmail = email
            }

            testScheduler.runCurrent()

            val regState = viewModel.registerState.value
            assertNull(regState.usernameError)
            assertNull(regState.emailError)
            assertNull(regState.passwordError)
            assertTrue(regState.isSuccess)
            assertFalse(regState.isLoading)
            assertEquals("user@example.com", registeredEmail)
            assertEquals("validUser", fakeRepository.registeredUser?.displayName)

            // Verifies email verification countdown was initialized and countdown is active
            val verifyState = viewModel.verifyEmailState.value
            assertEquals("user@example.com", verifyState.email)
            assertFalse(verifyState.canResend)

            // Once countdown finishes, resend is enabled
            advanceTimeBy(60000)
            testScheduler.runCurrent()
            assertTrue(viewModel.verifyEmailState.value.canResend)
        }

    @Test
    fun register_verificationEmailDispatchFailed_enablesImmediateResendAndShowsMessage() =
        runTest {
            fakeRepository.initialVerificationEmailDispatched = false

            viewModel.onRegisterUsernameChange("validUser")
            viewModel.onRegisterEmailChange("user@example.com")
            viewModel.onRegisterPasswordChange("SecurePassword1")

            var registeredEmail: String? = null
            viewModel.validateAndSubmitRegistration { email ->
                registeredEmail = email
            }

            advanceUntilIdle()

            val regState = viewModel.registerState.value
            assertTrue(regState.isSuccess)
            assertEquals("user@example.com", registeredEmail)

            // When initial dispatch fails: canResend is immediately true, countdown is 0, helpful message displayed
            val verifyState = viewModel.verifyEmailState.value
            assertTrue(verifyState.canResend)
            assertEquals(0, verifyState.resendCountdown)
            assertNotNull(verifyState.errorMessage)
            assertTrue(verifyState.errorMessage!!.contains("could not be sent"))
        }

    @Test
    fun register_repositoryError_displaysErrorMessage() =
        runTest {
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "An account with this email address already exists."

            viewModel.onRegisterUsernameChange("validUser")
            viewModel.onRegisterEmailChange("user@example.com")
            viewModel.onRegisterPasswordChange("SecurePassword1")

            var successCalled = false
            viewModel.validateAndSubmitRegistration { successCalled = true }

            advanceUntilIdle()

            val regState = viewModel.registerState.value
            assertFalse(regState.isLoading)
            assertFalse(regState.isSuccess)
            assertFalse(successCalled)
            assertEquals("An account with this email address already exists.", regState.errorMessage)
        }

    // =========================================================================================
    // Login Validation & Flow Tests
    // =========================================================================================

    @Test
    fun login_invalidEmailOrEmptyPassword_setsErrors() =
        runTest {
            viewModel.onLoginEmailChange("notanemail")
            viewModel.onLoginPasswordChange("")

            var success = false
            viewModel.validateAndSubmitLogin { success = true }

            val state = viewModel.loginState.value
            assertEquals("Please enter a valid email address", state.emailError)
            assertEquals("Password is required", state.passwordError)
            assertFalse(success)
        }

    @Test
    fun login_validCredentials_submitsSuccessfullyAndSyncsProfile() =
        runTest {
            viewModel.onLoginEmailChange("user@example.com")
            viewModel.onLoginPasswordChange("Secret123")

            var success = false
            viewModel.validateAndSubmitLogin { success = true }

            advanceUntilIdle()

            val state = viewModel.loginState.value
            assertNull(state.emailError)
            assertNull(state.passwordError)
            assertTrue(state.isSuccess)
            assertFalse(state.isLoading)
            assertTrue(success)
            assertNotNull(fakeRepository.syncedProfile)
        }

    @Test
    fun login_invalidCredentials_displaysError() =
        runTest {
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "Invalid email or password."

            viewModel.onLoginEmailChange("user@example.com")
            viewModel.onLoginPasswordChange("WrongPassword1")

            var success = false
            viewModel.validateAndSubmitLogin { success = true }

            advanceUntilIdle()

            val state = viewModel.loginState.value
            assertFalse(state.isSuccess)
            assertFalse(state.isLoading)
            assertFalse(success)
            assertEquals("Invalid email or password.", state.errorMessage)
        }

    // =========================================================================================
    // Google Sign-In Tests
    // =========================================================================================

    @Test
    fun googleSignIn_success_updatesStateAndSyncsProfile() =
        runTest {
            val fakeContext = object : ContextWrapper(null) {}
            var success = false

            viewModel.signInWithGoogle(fakeContext) { success = true }

            advanceUntilIdle()

            assertTrue(viewModel.loginState.value.isSuccess)
            assertFalse(viewModel.loginState.value.isLoading)
            assertTrue(success)
            assertNotNull(fakeRepository.syncedProfile)
        }

    @Test
    fun googleSignIn_cancellation_clearsLoadingWithoutError() =
        runTest {
            val fakeContext = object : ContextWrapper(null) {}
            fakeRepository.isCancelled = true

            var success = false
            viewModel.signInWithGoogle(fakeContext) { success = true }

            advanceUntilIdle()

            assertFalse(viewModel.loginState.value.isLoading)
            assertFalse(viewModel.loginState.value.isSuccess)
            assertNull(viewModel.loginState.value.errorMessage)
            assertFalse(success)
        }

    @Test
    fun googleSignIn_failure_setsErrorMessage() =
        runTest {
            val fakeContext = object : ContextWrapper(null) {}
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "Google Sign-In is not configured. Web Client ID is missing."

            var success = false
            viewModel.signInWithGoogle(fakeContext) { success = true }

            advanceUntilIdle()

            assertFalse(viewModel.loginState.value.isLoading)
            assertFalse(viewModel.loginState.value.isSuccess)
            assertEquals("Google Sign-In is not configured. Web Client ID is missing.", viewModel.loginState.value.errorMessage)
            assertFalse(success)
        }

    // =========================================================================================
    // Verify Email Tests
    // =========================================================================================

    @Test
    fun checkEmailVerificationStatus_whenVerified_syncsProfileAndCallsSuccess() =
        runTest {
            fakeRepository.currentUser =
                AuthUser(
                    uid = "user-123",
                    email = "user@example.com",
                    isEmailVerified = true,
                )

            var verified = false
            viewModel.checkEmailVerificationStatus { verified = true }

            advanceUntilIdle()

            val state = viewModel.verifyEmailState.value
            assertTrue(state.isVerified)
            assertFalse(state.isChecking)
            assertTrue(verified)
            assertNotNull(fakeRepository.syncedProfile)
        }

    @Test
    fun checkEmailVerificationStatus_whenNotVerified_showsHelpfulMessage() =
        runTest {
            fakeRepository.currentUser =
                AuthUser(
                    uid = "user-123",
                    email = "user@example.com",
                    isEmailVerified = false,
                )

            var verified = false
            viewModel.checkEmailVerificationStatus { verified = true }

            advanceUntilIdle()

            val state = viewModel.verifyEmailState.value
            assertFalse(state.isVerified)
            assertFalse(state.isChecking)
            assertFalse(verified)
            assertNotNull(state.errorMessage)
            assertTrue(state.errorMessage!!.contains("has not been verified yet"))
        }

    @Test
    fun applyEmailVerificationCode_success_verifiesAndSyncsProfile() =
        runTest {
            fakeRepository.currentUser =
                AuthUser(
                    uid = "user-123",
                    email = "user@example.com",
                    isEmailVerified = false,
                )

            var verified = false
            viewModel.applyEmailVerificationCode("action-code-xyz") { verified = true }

            advanceUntilIdle()

            assertEquals("action-code-xyz", fakeRepository.actionCodeApplied)
            assertTrue(viewModel.verifyEmailState.value.isVerified)
            assertTrue(verified)
        }

    @Test
    fun resendVerificationEmail_triggersRepository() =
        runTest {
            viewModel.initVerifyEmail("user@example.com")

            // Fast forward 60 seconds so canResend is true
            advanceTimeBy(60000)
            advanceUntilIdle()
            assertTrue(viewModel.verifyEmailState.value.canResend)

            viewModel.resendVerificationEmail()
            advanceUntilIdle()

            assertTrue(fakeRepository.verificationEmailSent)
        }

    @Test
    fun verifyOtpCode_success_verifiesAndUpdatesState() =
        runTest {
            fakeRepository.currentUser =
                AuthUser(
                    uid = "user-123",
                    email = "user@example.com",
                    isEmailVerified = false,
                )

            viewModel.onOtpCodeChange("123456")
            assertEquals("123456", viewModel.verifyEmailState.value.otpCode)

            var verified = false
            viewModel.verifyOtpCode { verified = true }

            advanceUntilIdle()

            assertEquals("123456", fakeRepository.otpVerifiedCode)
            assertTrue(viewModel.verifyEmailState.value.isVerified)
            assertFalse(viewModel.verifyEmailState.value.isVerifyingCode)
            assertTrue(verified)
        }

    @Test
    fun verifyOtpCode_shortCode_setsValidationError() =
        runTest {
            viewModel.onOtpCodeChange("123")
            viewModel.verifyOtpCode { }

            assertEquals("Please enter a 6-digit code", viewModel.verifyEmailState.value.errorMessage)
            assertNull(fakeRepository.otpVerifiedCode)
        }

    @Test
    fun verifyOtpCode_failure_displaysError() =
        runTest {
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "Invalid verification code. 4 attempts remaining."

            viewModel.onOtpCodeChange("654321")
            viewModel.verifyOtpCode { }

            advanceUntilIdle()

            assertFalse(viewModel.verifyEmailState.value.isVerified)
            assertEquals("Invalid verification code. 4 attempts remaining.", viewModel.verifyEmailState.value.errorMessage)
        }

    // =========================================================================================
    // Forgot Password Flow Tests
    // =========================================================================================

    @Test
    fun forgotPassword_invalidEmail_setsError() =
        runTest {
            viewModel.onForgotEmailChange("bad")
            viewModel.validateAndSendResetLink()

            val state = viewModel.forgotPasswordState.value
            assertEquals("Please enter a valid email address", state.emailError)
            assertFalse(state.isLinkSent)
        }

    @Test
    fun forgotPassword_validEmail_sendsLinkAndStartsCountdown() =
        runTest {
            viewModel.onForgotEmailChange("user@example.com")
            viewModel.validateAndSendResetLink()

            testScheduler.runCurrent()

            val state = viewModel.forgotPasswordState.value
            assertNull(state.emailError)
            assertTrue(state.isLinkSent)
            assertFalse(state.canResend)
            assertEquals("user@example.com", fakeRepository.resetEmailSentTo)

            advanceTimeBy(31000)
            testScheduler.runCurrent()
            assertTrue(viewModel.forgotPasswordState.value.canResend)
        }

    @Test
    fun forgotPassword_repositoryError_displaysError() =
        runTest {
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "User not found."

            viewModel.onForgotEmailChange("user@example.com")
            viewModel.validateAndSendResetLink()

            advanceUntilIdle()

            val state = viewModel.forgotPasswordState.value
            assertFalse(state.isLinkSent)
            assertFalse(state.isLoading)
            assertEquals("User not found.", state.errorMessage)
        }

    // =========================================================================================
    // Set New Password Flow Tests
    // =========================================================================================

    @Test
    fun setNewPassword_mismatch_setsConfirmPasswordError() =
        runTest {
            viewModel.onNewPasswordChange("NewPassword123")
            viewModel.onConfirmPasswordChange("DifferentPassword123")

            var success = false
            viewModel.validateAndSubmitNewPassword { success = true }

            val state = viewModel.setNewPasswordState.value
            assertNull(state.newPasswordError)
            assertEquals("Passwords do not match", state.confirmPasswordError)
            assertFalse(success)
        }

    @Test
    fun setNewPassword_withActionCode_confirmsResetSuccessfully() =
        runTest {
            viewModel.initSetNewPassword("action-code-reset-123")
            advanceUntilIdle()

            assertFalse(viewModel.setNewPasswordState.value.isLinkExpired)

            viewModel.onNewPasswordChange("ValidPass1")
            viewModel.onConfirmPasswordChange("ValidPass1")

            var success = false
            viewModel.validateAndSubmitNewPassword { success = true }

            advanceUntilIdle()

            val state = viewModel.setNewPasswordState.value
            assertNull(state.newPasswordError)
            assertNull(state.confirmPasswordError)
            assertTrue(state.isSuccess)
            assertTrue(success)
            assertEquals("action-code-reset-123", fakeRepository.passwordResetCompletedCode)
        }

    @Test
    fun setNewPassword_expiredActionCode_setsExpiredState() =
        runTest {
            fakeRepository.shouldSucceed = false
            fakeRepository.errorMessage = "The verification or password reset link is invalid, expired, or has already been used."

            viewModel.initSetNewPassword("expired-code-123")
            advanceUntilIdle()

            val state = viewModel.setNewPasswordState.value
            assertTrue(state.isLinkExpired)
            assertEquals("The verification or password reset link is invalid, expired, or has already been used.", state.errorMessage)
        }

    @Test
    fun signOut_triggersRepository() =
        runTest {
            viewModel.signOut()
            advanceUntilIdle()
            assertTrue(fakeRepository.signedOut)
        }

    // =========================================================================================
    // Navigation Routes Helper Tests
    // =========================================================================================

    @Test
    fun routes_verifyEmail_encodesCorrectly() {
        val routeWithEmail = Routes.verifyEmail(email = "test@example.com")
        assertEquals("verifyEmail?email=test%40example.com", routeWithEmail)

        val routeWithBoth = Routes.verifyEmail(email = "test@example.com", actionCode = "code123")
        assertEquals("verifyEmail?email=test%40example.com&actionCode=code123", routeWithBoth)

        val emptyRoute = Routes.verifyEmail()
        assertEquals("verifyEmail", emptyRoute)
    }

    @Test
    fun routes_setNewPassword_encodesCorrectly() {
        val routeWithCode = Routes.setNewPassword("resetCode#123")
        assertEquals("setNewPassword?actionCode=resetCode%23123", routeWithCode)

        val plainRoute = Routes.setNewPassword()
        assertEquals("setNewPassword", plainRoute)
    }

    @Test
    fun routes_authDestinations_areDistinct() {
        val routes =
            listOf(
                Routes.WELCOME,
                Routes.GUEST_PROFILE,
                Routes.REGISTER,
                Routes.LOGIN,
                Routes.VERIFY_EMAIL,
                Routes.FORGOT_PASSWORD,
                Routes.SET_NEW_PASSWORD,
                Routes.HOME,
            )
        assertEquals(routes.size, routes.distinct().size)
    }
}

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: Flow<AuthState> = _authState

    override var currentUser: AuthUser? = null

    var shouldSucceed: Boolean = true
    var errorMessage: String = "Operation failed"
    var isCancelled: Boolean = false
    var registeredUser: AuthUser? = null
    var loggedInUser: AuthUser? = null
    var resetEmailSentTo: String? = null
    var verificationEmailSent: Boolean = false
    var initialVerificationEmailDispatched: Boolean = true
    var actionCodeApplied: String? = null
    var otpVerifiedCode: String? = null
    var otpSent: Boolean = false
    var passwordResetCompletedCode: String? = null
    var passwordUpdated: String? = null
    var signedOut: Boolean = false
    var syncedProfile: BackendProfile? = null

    override suspend fun sendOtpVerification(): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        otpSent = true
        verificationEmailSent = true
        return AuthResult.Success(Unit)
    }

    override suspend fun verifyOtpCode(code: String): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        otpVerifiedCode = code
        currentUser = currentUser?.copy(isEmailVerified = true)
        return AuthResult.Success(Unit)
    }

    override suspend fun registerWithEmailAndPassword(
        email: String,
        password: String,
        username: String?,
    ): AuthResult<AuthUser> {
        if (isCancelled) return AuthResult.Cancelled
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        val user =
            AuthUser(
                uid = "fake-uid-123",
                email = email,
                isEmailVerified = false,
                displayName = username,
                verificationEmailSent = initialVerificationEmailDispatched,
            )
        currentUser = user
        registeredUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun loginWithEmailAndPassword(
        email: String,
        password: String,
    ): AuthResult<AuthUser> {
        if (isCancelled) return AuthResult.Cancelled
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        val user =
            AuthUser(
                uid = "fake-uid-123",
                email = email,
                isEmailVerified = true,
                displayName = "Test User",
            )
        currentUser = user
        loggedInUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signInWithGoogle(context: Context): AuthResult<AuthUser> {
        if (isCancelled) return AuthResult.Cancelled
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        val user =
            AuthUser(
                uid = "google-uid-456",
                email = "google@example.com",
                isEmailVerified = true,
                displayName = "Google User",
            )
        currentUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        verificationEmailSent = true
        return AuthResult.Success(Unit)
    }

    override suspend fun reloadUser(): AuthResult<AuthUser> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        val current = currentUser ?: return AuthResult.Error("No user")
        return AuthResult.Success(current)
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        resetEmailSentTo = email
        return AuthResult.Success(Unit)
    }

    override suspend fun confirmPasswordReset(
        actionCode: String,
        newPassword: String,
    ): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        passwordResetCompletedCode = actionCode
        return AuthResult.Success(Unit)
    }

    override suspend fun verifyPasswordResetCode(actionCode: String): AuthResult<String> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        return AuthResult.Success("user@example.com")
    }

    override suspend fun applyActionCode(actionCode: String): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        actionCodeApplied = actionCode
        currentUser = currentUser?.copy(isEmailVerified = true)
        return AuthResult.Success(Unit)
    }

    override suspend fun updatePassword(newPassword: String): AuthResult<Unit> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        passwordUpdated = newPassword
        return AuthResult.Success(Unit)
    }

    override suspend fun signOut() {
        signedOut = true
        currentUser = null
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = if (currentUser != null) "fake-id-token" else null

    override suspend fun syncBackendProfile(): AuthResult<BackendProfile> {
        if (!shouldSucceed) return AuthResult.Error(errorMessage)
        val profile =
            BackendProfile(
                id = currentUser?.uid ?: "user-123",
                email = currentUser?.email ?: "user@example.com",
                emailVerified = currentUser?.isEmailVerified ?: false,
                displayName = currentUser?.displayName,
            )
        syncedProfile = profile
        return AuthResult.Success(profile)
    }
}
