package haddad.three.notas.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark

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

    val (primaryTitle, secondaryTitle) =
        remember(title, subtitle) {
            val trimmed = title.trim()
            val parts = trimmed.split(" ")
            if (subtitle.isNullOrBlank() && parts.size >= 2 && parts.last().all { it.isDigit() }) {
                parts.dropLast(1).joinToString(" ") to parts.last()
            } else {
                trimmed to subtitle
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Month and Year (e.g., "November" over "2026")
        AnimatedContent(
            targetState = primaryTitle to secondaryTitle,
            transitionSpec = {
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
            },
            label = "CalendarHeaderTitleAnimation",
            modifier = Modifier.weight(1f, fill = false),
        ) { (currentPrimaryTitle, currentSecondaryTitle) ->
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(
                    text = currentPrimaryTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = textPrimary,
                        ),
                )
                if (!currentSecondaryTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSecondaryTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = textSecondary,
                            ),
                    )
                }
            }
        }

        // Right: Navigation Controls Cluster: [Today]  <  >
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(
                visible = showTodayButton,
                enter =
                    fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        expandHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit =
                    fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                        shrinkHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            ) {
                val todayBg = Color.Transparent
                val todayText = MaterialTheme.colorScheme.primary

                Surface(
                    onClick = onToday,
                    shape = CircleShape,
                    color = todayBg,
                    modifier =
                        Modifier
                            .height(32.dp)
                            .defaultMinSize(minWidth = 56.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_today),
                            maxLines = 1,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = todayText,
                                ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Previous Button (<) with subtle circular background
            val navButtonBg = if (isDark) Color(0xFF242424) else Color(0xFFF2F2F5)
            Surface(
                onClick = onPrevious,
                shape = CircleShape,
                color = navButtonBg,
                modifier = Modifier.size(36.dp),
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

            // Next Button (>) with subtle circular background
            Surface(
                onClick = onNext,
                shape = CircleShape,
                color = navButtonBg,
                modifier = Modifier.size(36.dp),
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
