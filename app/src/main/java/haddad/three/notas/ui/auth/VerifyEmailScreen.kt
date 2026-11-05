package haddad.three.notas.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import haddad.three.notas.ui.components.NotasTextField
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Verify Email screen matching Notas Figma reference (Node 46:2010).
 */
@Composable
fun VerifyEmailScreen(
    email: String,
    onBackClick: () -> Unit,
    onChangeEmailClick: () -> Unit,
    onVerifiedSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    actionCode: String? = null,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val state by viewModel.verifyEmailState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(email) {
        if (email.isNotBlank() && state.email != email) {
            viewModel.initVerifyEmail(email)
        }
    }

    LaunchedEffect(actionCode) {
        if (!actionCode.isNullOrBlank()) {
            viewModel.applyEmailVerificationCode(actionCode, onVerifiedSuccess)
        }
    }

    val displayEmail = if (state.email.isNotBlank()) state.email else email

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
                    text = "Verify your email",
                    style = NotasTypography.ScreenTitle,
                    color = palette.primaryText,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Confirm your email address to finish creating your Notas account.",
                    style = NotasTypography.Body,
                    color = palette.secondaryText,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Grouped Card
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
                        // Email display & change email row
                        Text(
                            text = "Email address",
                            style = NotasTypography.Label,
                            color = palette.secondaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = displayEmail.ifBlank { "example@email.com" },
                                style = NotasTypography.BodyMedium,
                                color = palette.primaryText,
                            )
                            Text(
                                text = "Change email",
                                style = NotasTypography.Label,
                                color = palette.primaryAction,
                                modifier = Modifier.clickable(onClick = onChangeEmailClick),
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Pending verification pill
                        Row(
                            modifier =
                                Modifier
                                    .background(palette.inputSurface, shape = RoundedCornerShape(100.dp))
                                    .border(1.dp, palette.divider.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .background(NotasTokens.SemanticWarning, shape = CircleShape),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pending verification",
                                style = NotasTypography.CaptionMedium,
                                color = palette.primaryText,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "A 6-digit verification code was sent to $displayEmail. Enter the code below to verify your email and activate cloud sync. (Check your Spam folder if it does not appear in your inbox).",
                            style = NotasTypography.Body,
                            color = palette.secondaryText,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 6-digit OTP Input Field
                        NotasTextField(
                            value = state.otpCode,
                            onValueChange = viewModel::onOtpCodeChange,
                            label = "6-Digit Verification Code",
                            placeholder = "123456",
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.errorMessage != null) {
                            AuthErrorBanner(
                                message = state.errorMessage ?: "",
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }

                        // Primary Action: Verify OTP Code
                        PrimaryButton(
                            text = "Verify Code",
                            onClick = {
                                viewModel.verifyOtpCode(onVerifiedSuccess)
                            },
                            loading = state.isVerifyingCode,
                            enabled = state.otpCode.length == 6,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Resend Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = viewModel::resendVerificationEmail,
                                enabled = state.canResend,
                            ) {
                                Text(
                                    text = "Resend code",
                                    style = NotasTypography.Button,
                                    color = if (state.canResend) palette.primaryAction else palette.secondaryText.copy(alpha = 0.5f),
                                )
                            }

                            val timerText =
                                if (state.canResend) {
                                    "Available now"
                                } else {
                                    "Available in 00:${state.resendCountdown.toString().padStart(2, '0')}"
                                }

                            Text(
                                text = timerText,
                                style = NotasTypography.CaptionMedium,
                                color = palette.secondaryText,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Secondary Fallback: Check email link
                        TextButton(
                            onClick = {
                                viewModel.checkEmailVerificationStatus(onVerifiedSuccess)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isChecking,
                        ) {
                            Text(
                                text = if (state.isChecking) "Checking..." else "I clicked the email link instead",
                                style = NotasTypography.CaptionMedium,
                                color = palette.primaryAction,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Account recovery notice box
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(palette.inputSurface, shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, palette.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                        ) {
                            Column {
                                Text(
                                    text = "Keep your account connected",
                                    style = NotasTypography.BodyMedium,
                                    color = palette.primaryText,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Your email helps you sign in and recover access to Notas.",
                                    style = NotasTypography.Helper,
                                    color = palette.secondaryText,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom link
            TextButton(
                onClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "Back to create account",
                    style = NotasTypography.BodyMedium,
                    color = palette.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
