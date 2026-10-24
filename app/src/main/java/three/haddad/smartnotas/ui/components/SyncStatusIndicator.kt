package three.haddad.smartnotas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTokens
import three.haddad.smartnotas.ui.theme.NotasTypography

/**
 * Synchronization statuses defined in Notas Figma references (Frames 52:9340 – 52:9734).
 */
enum class SyncStatus {
    NOT_CONNECTED,
    SYNCING,
    SYNCED,
    OFFLINE,
    NETWORK_ERROR,
    SESSION_EXPIRED;

    val title: String
        get() = when (this) {
            NOT_CONNECTED -> "Not connected"
            SYNCING -> "Syncing"
            SYNCED -> "Synced"
            OFFLINE -> "Offline"
            NETWORK_ERROR -> "Error"
            SESSION_EXPIRED -> "Session Expired"
        }

    val bannerTitle: String
        get() = when (this) {
            NOT_CONNECTED -> "Status unavailable"
            SYNCING -> "Sync in progress"
            SYNCED -> "Cloud Sync is up to date"
            OFFLINE -> "You’re offline"
            NETWORK_ERROR -> "Network Error"
            SESSION_EXPIRED -> "Session Expired"
        }

    val bannerDescription: String
        get() = when (this) {
            NOT_CONNECTED -> "Connect your account to load sync status and timestamps."
            SYNCING -> "Keep your connection active. Cloud Sync is updating your device and private server."
            SYNCED -> "Automatic sync is separate from manual backup. History and timestamps are not loaded."
            OFFLINE -> "Your local notes, tasks and events are preserved. Reconnect to the internet to resume cloud operations."
            NETWORK_ERROR -> "Unable to reach your private server. Your local data is preserved. Check your connection and try again."
            SESSION_EXPIRED -> "Sign in again to resume cloud operations. Your local notes, tasks and events are preserved on this device."
        }
}

/**
 * Pill status indicator showing a status dot and label matching Figma references.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSemanticPalette.current

    val (dotColor, containerBg) = when (status) {
        SyncStatus.SYNCED -> Pair(NotasTokens.SemanticSuccess, NotasTokens.SemanticSuccess.copy(alpha = 0.12f))
        SyncStatus.SYNCING -> Pair(NotasTokens.SemanticInfo, NotasTokens.SemanticInfo.copy(alpha = 0.12f))
        SyncStatus.OFFLINE -> Pair(NotasTokens.SemanticWarning, NotasTokens.SemanticWarning.copy(alpha = 0.12f))
        SyncStatus.NETWORK_ERROR,
        SyncStatus.SESSION_EXPIRED -> Pair(NotasTokens.SemanticError, NotasTokens.SemanticError.copy(alpha = 0.12f))
        SyncStatus.NOT_CONNECTED -> Pair(palette.secondaryText, palette.divider.copy(alpha = 0.50f))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SyncAnimation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SyncRotation",
    )

    Row(
        modifier = modifier
            .background(color = containerBg, shape = RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (status == SyncStatus.SYNCING) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .rotate(rotation)
                    .border(1.5.dp, dotColor, CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.title,
            style = NotasTypography.CaptionMedium,
            color = palette.primaryText,
        )
    }
}

/**
 * Grouped Cloud Sync card matching Figma references (Frames 52:9340 – 52:9734).
 */
@Composable
fun SyncStatusCard(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    lastSynced: String = "—",
    lastBackup: String = "—",
    destination: String = "your private server",
    onRetryClick: (() -> Unit)? = null,
) {
    val palette = LocalSemanticPalette.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NotasTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Cloud Sync",
                        style = NotasTypography.CardHeader,
                        color = palette.primaryText,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatic sync with your private server.",
                        style = NotasTypography.Helper,
                        color = palette.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sync status",
                    style = NotasTypography.BodyMedium,
                    color = palette.secondaryText,
                )
                SyncStatusIndicator(status = status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Last synced",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastSynced,
                        style = NotasTypography.BodyMedium13,
                        color = palette.primaryText,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last backup",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastBackup,
                        style = NotasTypography.BodyMedium13,
                        color = palette.primaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Informational status banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = palette.inputSurface,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, palette.divider.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = status.bannerTitle,
                        style = NotasTypography.BodyMedium,
                        color = palette.primaryText,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status.bannerDescription,
                        style = NotasTypography.Helper,
                        color = palette.secondaryText,
                    )

                    if (onRetryClick != null && (status == SyncStatus.NETWORK_ERROR || status == SyncStatus.OFFLINE)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onRetryClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                text = "Try again",
                                style = NotasTypography.Button,
                                color = palette.primaryAction,
                            )
                        }
                    }
                }
            }
        }
    }
}
