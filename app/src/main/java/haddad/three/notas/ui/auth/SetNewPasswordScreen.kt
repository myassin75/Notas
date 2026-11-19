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
import androidx.compose.runtime.LaunchedEffect
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
import haddad.three.notas.ui.components.PasswordTextField
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.components.SecondaryButton
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Set New Password screen matching Notas Figma references (Nodes 46:2208, 52:9284, 52:9312).
 */
@Composable
fun SetNewPasswordScreen(
    onBackClick: () -> Unit,
    onBackToLoginClick: () -> Unit,
    onRequestNewLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionCode: String? = null,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val state by viewModel.setNewPasswordState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(actionCode) {
        if (!actionCode.isNullOrBlank()) {
            viewModel.initSetNewPassword(actionCode)
        }
    }

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
                        text = "New Password",
                        style = NotasTypography.SectionHeader,
                        color = palette.primaryText,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                when {
                    state.isLinkExpired -> {
                        // Expired / Invalid Link State
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(NotasTokens.CardRadius),
                            colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.stroke),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(56.dp)
                                            .background(palette.error.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "warning",
                                        size = 28.dp,
                                        tint = palette.error,
                                        contentDescription = null,
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Link expired",
                                    style = NotasTypography.SectionHeader,
                                    color = palette.primaryText,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "This password reset link has expired or has already been used. Please request a new one.",
                                    style = NotasTypography.Body,
                                    color = palette.secondaryText,
                                    textAlign = TextAlign.Center,
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                PrimaryButton(
                                    text = "Request new link",
                                    onClick = onRequestNewLinkClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    state.isSuccess -> {
                        // Success Confirmation State (Node 52:9312)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(NotasTokens.CardRadius),
                            colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.stroke),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
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
                                    text = "Password updated",
                                    style = NotasTypography.SectionHeader,
                                    color = palette.primaryText,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Your password has been changed successfully. You can now log in with your new credentials.",
                                    style = NotasTypography.Body,
                                    color = palette.secondaryText,
                                    textAlign = TextAlign.Center,
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                PrimaryButton(
                                    text = "Log in with new password",
                                    onClick = onBackToLoginClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    else -> {
                        // Password Entry Form (Node 46:2208 & 52:9284)
                        Text(
                            text = "Set new password",
                            style = NotasTypography.ScreenTitle,
                            color = palette.primaryText,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Your new password must be at least 8 characters and include at least one letter and one number.",
                            style = NotasTypography.Body,
                            color = palette.secondaryText,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (state.errorMessage != null) {
                            AuthErrorBanner(
                                message = state.errorMessage ?: "",
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }

                        // New Password Field
                        PasswordTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = "New password",
                            placeholder = "••••••••",
                            isError = state.newPasswordError != null,
                            errorMessage = state.newPasswordError,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Confirm Password Field
                        PasswordTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = "Confirm password",
                            placeholder = "••••••••",
                            isError = state.confirmPasswordError != null,
                            errorMessage = state.confirmPasswordError,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password Guidance Checklist
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val hasMinLength = state.newPassword.length >= 8
                            val hasLetterAndDigit = state.newPassword.any { it.isLetter() } && state.newPassword.any { it.isDigit() }
                            val passwordsMatch = state.newPassword.isNotEmpty() && state.newPassword == state.confirmPassword

                            GuidanceItem(text = "At least 8 characters", isMet = hasMinLength)
                            GuidanceItem(text = "Contains at least one letter and one number", isMet = hasLetterAndDigit)
                            if (state.confirmPassword.isNotEmpty()) {
                                GuidanceItem(text = "Passwords match", isMet = passwordsMatch)
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        PrimaryButton(
                            text = "Reset password",
                            onClick = {
                                viewModel.validateAndSubmitNewPassword(onSuccess = {})
                            },
                            loading = state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Bottom Navigation
            if (!state.isSuccess) {
                Row(
                    modifier =
                        Modifier
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

@Composable
private fun GuidanceItem(
    text: String,
    isMet: Boolean,
) {
    val palette = LocalSemanticPalette.current
    val tint = if (isMet) palette.success else palette.secondaryText
    val icon = if (isMet) "check_circle" else "radio_button_unchecked"

    Row(verticalAlignment = Alignment.CenterVertically) {
        RememberMaterialRoundedSymbol(
            name = icon,
            size = 16.dp,
            tint = tint,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = NotasTypography.Caption,
            color = if (isMet) palette.primaryText else palette.secondaryText,
        )
    }
}
