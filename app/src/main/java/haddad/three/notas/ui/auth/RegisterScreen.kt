package haddad.three.notas.ui.auth

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
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.GoogleSignInButton
import haddad.three.notas.ui.components.NotasTextField
import haddad.three.notas.ui.components.PasswordTextField
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Create Account screen matching Notas Figma reference (Node 46:1957).
 */
@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisteredSuccess: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val state by viewModel.registerState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.notasBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Navigation Bar
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier =
                            Modifier
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
                        text = "Notas",
                        style = NotasTypography.SectionTitle,
                        color = palette.primaryText,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Heading Block
                Text(
                    text = "Create account",
                    style = NotasTypography.ScreenTitle,
                    color = palette.primaryText,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "A little space for everything on your mind.",
                    style = NotasTypography.Body,
                    color = palette.secondaryText,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Error Banner
                if (state.errorMessage != null) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        AuthErrorBanner(
                            message = state.errorMessage ?: "",
                            onDismiss = viewModel::clearErrors,
                        )
                        if (state.errorMessage?.contains("already exists", ignoreCase = true) == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Log in to your account →",
                                style = NotasTypography.BodyMedium,
                                color = palette.primaryAction,
                                modifier =
                                    Modifier
                                        .align(Alignment.End)
                                        .clickable(onClick = onLoginClick)
                                        .padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                // Grouped Form Card (360x337 in Figma)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(NotasTokens.CardRadius),
                    colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                    ) {
                        NotasTextField(
                            value = state.username,
                            onValueChange = viewModel::onRegisterUsernameChange,
                            label = "Username",
                            placeholder = "alex_morgan",
                            errorMessage = state.usernameError,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        NotasTextField(
                            value = state.email,
                            onValueChange = viewModel::onRegisterEmailChange,
                            label = "Email",
                            placeholder = "alex.morgan@example.com",
                            errorMessage = state.emailError,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PasswordTextField(
                            value = state.password,
                            onValueChange = viewModel::onRegisterPasswordChange,
                            label = "Password",
                            placeholder = "Create a password",
                            helperText = "Use at least 8 characters, including a letter and a number.",
                            errorMessage = state.passwordError,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                androidx.compose.foundation.text.KeyboardActions(
                                    onDone = { viewModel.validateAndSubmitRegistration(onRegisteredSuccess) },
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Action Button
                PrimaryButton(
                    text = "Create account",
                    onClick = { viewModel.validateAndSubmitRegistration(onRegisteredSuccess) },
                    loading = state.isLoading,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Alternative Divider with "or"
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = palette.divider,
                    )
                    Text(
                        text = "or",
                        style = NotasTypography.Label,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = palette.divider,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Sign In
                GoogleSignInButton(
                    onClick = onGoogleSignInClick,
                    loading = state.isLoading,
                )
            }

            // Bottom Link
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Already have an account? ",
                    style = NotasTypography.Body,
                    color = palette.secondaryText,
                )
                Text(
                    text = "Log in",
                    style = NotasTypography.BodyMedium,
                    color = palette.primaryText,
                    modifier = Modifier.clickable(onClick = onLoginClick),
                )
            }
        }
    }
}
