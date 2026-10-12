package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalIsDark

/**
 * Section header for agenda items with a "+ New event" action button.
 */
@Composable
fun AgendaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNewEventClick: () -> Unit = {},
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

        Spacer(modifier = Modifier.width(12.dp))

        // "+ New event" pill button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onNewEventClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 16.dp,
                    tint = CalendarDesignTokens.PrimaryPurple,
                )
                Text(
                    text = stringResource(R.string.calendar_new_event),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = CalendarDesignTokens.PrimaryPurple,
                    ),
                )
            }
        }
    }
}

/**
 * An individual event/task card within the agenda.
 *
 * Displays a time badge on the left, and a styled card on the right
 * with an icon squircle, title, subtitle, and clickable action.
 */
@Composable
fun AgendaEventCard(
    event: CalendarEventUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time label column (e.g. "09:00 AM")
        Text(
            text = event.timeDisplay,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary,
            ),
            modifier = Modifier.width(62.dp),
        )

        // Event Card
        Surface(
            shape = CalendarDesignTokens.ItemCardShape,
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .weight(1f)
                .clip(CalendarDesignTokens.ItemCardShape)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category Icon Squircle
                Box(
                    modifier = Modifier
                        .size(CalendarDesignTokens.IconBoxSize)
                        .background(event.iconBgColor, shape = CalendarDesignTokens.IconBoxShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = event.iconName,
                        size = 20.dp,
                        tint = event.iconTint,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = textPrimary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            color = textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // If event is marked as completed
                if (event.isDone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(CalendarDesignTokens.DotGreen.copy(alpha = 0.15f), shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = 14.dp,
                            tint = CalendarDesignTokens.DotGreen,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Birthdays section card matching the reference design:
 * Cake icon in soft pink squircle, birthday person title, subtitle, and navigation chevron.
 */
@Composable
fun BirthdayCard(
    birthday: CalendarBirthdayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Surface(
        shape = CalendarDesignTokens.ItemCardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp)
            .clip(CalendarDesignTokens.ItemCardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cake icon squircle
            Box(
                modifier = Modifier
                    .size(CalendarDesignTokens.IconBoxSize)
                    .background(CalendarDesignTokens.BirthdayBoxBg, shape = CalendarDesignTokens.IconBoxShape),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = birthday.iconName,
                    size = 20.dp,
                    tint = CalendarDesignTokens.BirthdayIconTint,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Birthday title & info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = birthday.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = textPrimary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = birthday.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        color = textSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Trailing chevron
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = textSecondary,
            )
        }
    }
}

/**
 * Clean empty state placeholder when no events are scheduled.
 */
@Composable
fun CalendarEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "event_available",
                size = 28.dp,
                tint = CalendarDesignTokens.PrimaryPurple,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textPrimary,
            ),
        )

        if (!helperText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = textSecondary,
                ),
            )
        }
    }
}
