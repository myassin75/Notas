package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark

/**
 * Calendar navigation header matching the reference design:
 * - Left: Month/year or date range title and optional subtitle.
 * - Right: Navigation controls (< Today >).
 */
@Composable
fun CalendarNavigationHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showTodayButton: Boolean = true,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)
    val border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark))
    val buttonBg = if (isDark) CalendarDesignTokens.CardBgDark else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Title and optional subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary,
                ),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = textSecondary,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Navigation cluster (< Today >)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Previous Button (<)
            Surface(
                shape = CircleShape,
                color = buttonBg,
                border = border,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrevious),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        modifier = Modifier.graphicsLayer { scaleX = -1f },
                        size = 20.dp,
                        tint = textPrimary,
                    )
                }
            }

            // Today Pill Button
            if (showTodayButton) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToday),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_today),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = CalendarDesignTokens.PrimaryPurple,
                            ),
                        )
                    }
                }
            }

            // Next Button (>)
            Surface(
                shape = CircleShape,
                color = buttonBg,
                border = border,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 20.dp,
                        tint = textPrimary,
                    )
                }
            }
        }
    }
}
