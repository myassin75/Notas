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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.NotasTextField
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.components.SecondaryButton
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Forgot Password screen matching Notas Figma references (Nodes 46:2116 & 52:9250).
 */
@Composable
fun ForgotPasswordScreen(
    onBackClick: () -> Unit,
    onBackToLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val state by viewModel.forgotPasswordState.collectAsStateWithLifecycle()
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
                    if (!state.isLinkSent) {
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
                    }

                    Text(
                        text = "Reset Password",
                        style = NotasTypography.SectionHeader,
                        color = palette.primaryText,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!state.isLinkSent) {
                    // Screen Title & Subtitle
                    Text(
                        text = "Forgot password?",
                        style = NotasTypography.ScreenTitle,
                        color = palette.primaryText,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter the email associated with your account and we'll send you a link to reset your password.",
                        style = NotasTypography.Body,
                        color = palette.secondaryText,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Error Banner
                    if (state.errorMessage != null) {
                        AuthErrorBanner(
                            message = state.errorMessage ?: "",
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }

                    // Email Input
                    NotasTextField(
                        value = state.email,
                        onValueChange = viewModel::onForgotEmailChange,
                        label = "Email",
                        placeholder = "you@example.com",
                        isError = state.emailError != null,
                        errorMessage = state.emailError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Send Reset Link Button
                    PrimaryButton(
                        text = "Send reset link",
                        onClick = { viewModel.validateAndSendResetLink() },
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Confirmation View (Node 52:9250)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(NotasTokens.CardRadius),
                        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.stroke),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(palette.success.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "check_circle",
                                    size = 28.dp,
                                    tint = palette.success,
                                    contentDescription = null,
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Reset link sent",
                                style = NotasTypography.SectionHeader,
                                color = palette.primaryText,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "We sent an email to ${state.email}. Open the email and click the link to reset your password. (Please check your Spam or Junk folder if it does not appear in your inbox).",
                                style = NotasTypography.Body,
                                color = palette.secondaryText,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Resend timer or action
                            if (!state.canResend) {
                                Text(
                                    text = "Resend link in ${state.resendCountdown}s",
                                    style = NotasTypography.Caption,
                                    color = palette.secondaryText,
                                )
                            } else {
                                Text(
                                    text = "Didn't receive it? Resend link",
                                    style = NotasTypography.BodyMedium,
                                    color = palette.primaryText,
                                    modifier = Modifier
                                        .clickable { viewModel.resendResetLink() }
                                        .padding(vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SecondaryButton(
                        text = "Back to log in",
                        onClick = onBackToLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Bottom Footer
            if (!state.isLinkSent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Remember your password? ",
                        style = NotasTypography.Body,
                        color = palette.secondaryText,
                    )
                    Text(
                        text = "Log in",
                        style = NotasTypography.BodyMedium,
                        color = palette.primaryText,
                        modifier = Modifier.clickable(onClick = onBackToLoginClick),
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}
