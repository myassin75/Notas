package three.haddad.smartnotas.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.GoogleSignInButton
import three.haddad.smartnotas.ui.components.NotasTextField
import three.haddad.smartnotas.ui.components.PasswordTextField
import three.haddad.smartnotas.ui.components.PrimaryButton
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTokens
import three.haddad.smartnotas.ui.theme.NotasTypography

/**
 * Login screen matching Notas Figma reference (Node 46:2066).
 */
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val state by viewModel.loginState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.notasBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(palette.cardSurface, shape = RoundedCornerShape(10.dp)),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            size = 20.dp,
                            tint = palette.primaryText,
                            contentDescription = "Back",
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Log In",
                        style = NotasTypography.SectionHeader,
                        color = palette.primaryText,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Screen Header
                Text(
                    text = "Welcome back",
                    style = NotasTypography.ScreenTitle,
                    color = palette.primaryText,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Log in to your own little corner of Notas.",
                    style = NotasTypography.Body,
                    color = palette.secondaryText,
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Error Banner
                if (state.errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = palette.error.copy(alpha = 0.1f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "error",
                                size = 20.dp,
                                tint = palette.error,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage ?: "",
                                style = NotasTypography.BodyMedium,
                                color = palette.error,
                            )
                        }
                    }
                }

                // Email Field
                NotasTextField(
                    value = state.email,
                    onValueChange = viewModel::onLoginEmailChange,
                    label = "Email",
                    placeholder = "you@example.com",
                    isError = state.emailError != null,
                    errorMessage = state.emailError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Field
                PasswordTextField(
                    value = state.password,
                    onValueChange = viewModel::onLoginPasswordChange,
                    label = "Password",
                    placeholder = "••••••••",
                    isError = state.passwordError != null,
                    errorMessage = state.passwordError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Forgot Password Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "Forgot password?",
                        style = NotasTypography.BodyMedium,
                        color = palette.primaryText,
                        modifier = Modifier
                            .clickable(onClick = onForgotPasswordClick)
                            .padding(vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Log in Primary Button
                PrimaryButton(
                    text = "Log in",
                    onClick = {
                        viewModel.validateAndSubmitLogin(onSuccess = onLoginSuccess)
                    },
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Or Divider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = palette.stroke,
                    )
                    Text(
                        text = "or",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = palette.stroke,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Google Sign In
                GoogleSignInButton(
                    onClick = onGoogleSignInClick,
                    loading = state.isLoading,
                    text = "Continue with Google",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Footer Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New to Notas? ",
                    style = NotasTypography.Body,
                    color = palette.secondaryText,
                )
                Text(
                    text = "Create account",
                    style = NotasTypography.BodyMedium,
                    color = palette.primaryText,
                    modifier = Modifier.clickable(onClick = onRegisterClick),
                )
            }
        }
    }
}
