package three.haddad.smartnotas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTokens
import three.haddad.smartnotas.ui.theme.NotasTypography

/**
 * Circular user avatar displaying user initials or downloaded profile image.
 */
@Composable
fun UserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: Dp = NotasTokens.AvatarSize,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalSemanticPalette.current

    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(palette.divider.copy(alpha = 0.5f))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = initials.trim(),
                style = if (size >= NotasTokens.AvatarSizeLarge) {
                    NotasTypography.ScreenTitle
                } else {
                    NotasTypography.ModalTitle
                },
                color = palette.primaryText,
            )
        }
    }
}

/**
 * Standard Account Header matching Notas Profile Figma designs (Nodes 3:3, 14:317, 52:9946).
 */
@Composable
fun AccountHeader(
    displayName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    initials: String = displayName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase().ifBlank { "N" },
    photoUrl: String? = null,
    badgeText: String? = null,
    avatarSize: Dp = NotasTokens.AvatarSize,
    onEditClick: (() -> Unit)? = null,
) {
    val palette = LocalSemanticPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(
            initials = initials,
            photoUrl = photoUrl,
            size = avatarSize,
            onClick = onEditClick,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = displayName,
            style = NotasTypography.ModalTitle,
            color = palette.primaryText,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = NotasTypography.Helper,
            color = palette.secondaryText,
        )

        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = palette.inputSurface,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .border(1.dp, palette.divider.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badgeText,
                    style = NotasTypography.Caption,
                    color = palette.secondaryText,
                )
            }
        }

        if (onEditClick != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, palette.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.cardSurface,
                    contentColor = palette.primaryText,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = "Edit profile",
                    style = NotasTypography.BodyMedium13,
                    color = palette.primaryText,
                )
            }
        }
    }
}
