package haddad.three.notas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography

/**
 * Grouped settings card container matching Notas Figma references (Nodes 3:3, 3:828, 3:916, 14:317).
 *
 * Corner radius: 22dp, Background: #FFFFFF, Border: 1dp solid #E5E5E5.
 */
@Composable
fun SettingsRowCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSemanticPalette.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = NotasTypography.BodyMedium,
                color = palette.primaryText,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = NotasTypography.Helper,
                color = palette.secondaryText,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(NotasTokens.CardRadius),
            colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
            border = BorderStroke(1.dp, palette.cardBorder),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * Interactive settings row item with title, optional subtitle, value text, and trailing content.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    showDivider: Boolean = true,
) {
    val palette = LocalSemanticPalette.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Text(
                        text = title,
                        style = NotasTypography.BodyMedium,
                        color = if (isDestructive) NotasTokens.SemanticError else palette.primaryText,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = NotasTypography.Helper,
                            color = palette.secondaryText,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (!valueText.isNullOrBlank()) {
                    Text(
                        text = valueText,
                        style = NotasTypography.Helper,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                } else if (onClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 18.dp,
                        tint = palette.secondaryText,
                        contentDescription = "Navigate",
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = palette.divider.copy(alpha = 0.5f),
            )
        }
    }
}
