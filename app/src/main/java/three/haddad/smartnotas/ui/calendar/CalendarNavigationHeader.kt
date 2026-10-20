package three.haddad.smartnotas.ui.calendar

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

    val annotatedTitle =
        remember(title, textPrimary, textSecondary) {
            val trimmed = title.trim()
            val parts = trimmed.split(" ")
            if (parts.size >= 2 && parts.last().all { it.isDigit() }) {
                val mainText = parts.dropLast(1).joinToString(" ")
                val yearText = parts.last()
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 18.sp,
                        ),
                    ) {
                        append(mainText)
                        append(" ")
                    }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Normal,
                            color = textSecondary,
                            fontSize = 18.sp,
                        ),
                    ) {
                        append(yearText)
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 18.sp,
                        ),
                    ) {
                        append(trimmed)
                    }
                }
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Date navigation cluster with date between the two arrows (< Date >)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            // Previous Button (<) with 40dp touch target
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onPrevious,
                    shape = CircleShape,
                    color = buttonBg,
                    border = border,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            modifier = Modifier.graphicsLayer { scaleX = -1f },
                            size = 18.dp,
                            tint = textPrimary,
                        )
                    }
                }
            }

            // Title and optional subtitle (Date positioned directly between the two arrows)
            AnimatedContent(
                targetState = annotatedTitle to subtitle,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                },
                label = "CalendarHeaderTitleAnimation",
            ) { (currentAnnotatedTitle, currentSubtitle) ->
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = currentAnnotatedTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!currentSubtitle.isNullOrBlank()) {
                        Text(
                            text = currentSubtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = textSecondary,
                                ),
                        )
                    }
                }
            }

            // Next Button (>) with 40dp touch target
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onNext,
                    shape = CircleShape,
                    color = buttonBg,
                    border = border,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 18.dp,
                            tint = textPrimary,
                        )
                    }
                }
            }
        }

        // Right: Today Pill Button (animated appearance/disappearance)
        AnimatedVisibility(
            visible = showTodayButton,
            enter =
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    expandHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            exit =
                fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                    shrinkHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)),
        ) {
            Surface(
                onClick = onToday,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier =
                    Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 48.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.calendar_today),
                        maxLines = 1,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }
        }
    }
}
