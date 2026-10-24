package three.haddad.smartnotas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTokens
import three.haddad.smartnotas.ui.theme.NotasTypography

/**
 * High-contrast, pill-shaped primary action button matching Notas Figma design.
 *
 * Height: 56dp, Corner radius: 28dp.
 * Uses #000000 background and #FFFFFF text in standard light theme.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val palette = LocalSemanticPalette.current

    Button(
        onClick = {
            if (!loading && enabled) {
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(NotasTokens.ButtonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(NotasTokens.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.primaryAction,
            contentColor = palette.primaryActionForeground,
            disabledContainerColor = palette.primaryAction.copy(alpha = 0.38f),
            disabledContentColor = palette.primaryActionForeground.copy(alpha = 0.60f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = palette.primaryActionForeground,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = text,
                    style = NotasTypography.Button,
                    textAlign = TextAlign.Center,
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Secondary outlined button with crisp border matching Notas Figma design.
 *
 * Height: 56dp, Corner radius: 28dp, Surface: #FFFFFF, Border: #E5E5E5.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val palette = LocalSemanticPalette.current

    OutlinedButton(
        onClick = {
            if (!loading && enabled) {
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(NotasTokens.ButtonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(NotasTokens.ButtonRadius),
        border = BorderStroke(1.dp, palette.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.cardSurface,
            contentColor = palette.primaryText,
            disabledContainerColor = palette.cardSurface.copy(alpha = 0.60f),
            disabledContentColor = palette.primaryText.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = palette.primaryText,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = NotasTypography.Button,
                    textAlign = TextAlign.Center,
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Google Sign-In button displaying the official 4-color Google G mark.
 *
 * Height: 56dp, Corner radius: 28dp, Border: 1dp solid #E5E5E5.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    text: String = "Continue with Google",
) {
    val palette = LocalSemanticPalette.current

    OutlinedButton(
        onClick = {
            if (!loading && enabled) {
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(NotasTokens.ButtonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(NotasTokens.ButtonRadius),
        border = BorderStroke(1.dp, palette.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.cardSurface,
            contentColor = palette.primaryText,
            disabledContainerColor = palette.cardSurface.copy(alpha = 0.60f),
            disabledContentColor = palette.primaryText.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = palette.primaryText,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = NotasTypography.Button,
                    color = palette.primaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
