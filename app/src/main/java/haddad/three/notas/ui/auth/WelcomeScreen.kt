package haddad.three.notas.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.AppIconImage
import haddad.three.notas.ui.components.GoogleSignInButton
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.components.SecondaryButton
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Notas Welcome & Onboarding screen matching Figma reference (Node 14:47).
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSkipForNow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel? = null,
) {
    val palette = LocalSemanticPalette.current
    val scrollState = rememberScrollState()
    val loginState = viewModel?.loginState?.collectAsStateWithLifecycle()?.value

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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Brand Card (360x290 in Figma)
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    shape = RoundedCornerShape(NotasTokens.CardRadiusLarge),
                    colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppIconImage(modifier = Modifier.size(80.dp))

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Notas",
                            style = NotasTypography.DisplayTitle,
                            color = palette.primaryText,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Notes, tasks, reminders - done right",
                            style = NotasTypography.Body,
                            color = palette.secondaryText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Account Entry Buttons Section (360x328 in Figma)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (loginState?.errorMessage != null) {
                        AuthErrorBanner(
                            message = loginState.errorMessage,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }

                    PrimaryButton(
                        text = "Create account",
                        onClick = onCreateAccount,
                        trailingIcon = {
                            RememberMaterialRoundedSymbol(
                                name = "arrow_forward",
                                size = 18.dp,
                                tint = palette.primaryActionForeground,
                                contentDescription = "Continue",
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SecondaryButton(
                        text = "Log in",
                        onClick = onLogin,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Alternative divider with "or"
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

                    Spacer(modifier = Modifier.height(14.dp))

                    GoogleSignInButton(
                        onClick = onGoogleSignIn,
                        loading = loginState?.isLoading == true,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SecondaryButton(
                        text = "Skip for now",
                        onClick = onSkipForNow,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Guest usage stores data on this device only.",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Bottom Creator Credit
            Text(
                text = "by Mohamed Yassin ♥",
                style = NotasTypography.Caption,
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
    }
}
