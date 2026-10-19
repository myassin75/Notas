package three.haddad.smartnotas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.edit.NoteIcon
import three.haddad.smartnotas.ui.edit.resolveNoteIcon
import three.haddad.smartnotas.ui.theme.LocalIsDark
import java.time.LocalDate

/**
 * Section header for agenda items with an optional "+ New event" action button.
 */
@Composable
fun AgendaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showNewEventButton: Boolean = true,
    onNewEventClick: () -> Unit = {},
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier =
            modifier
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textPrimary,
                    ),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
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

        if (showNewEventButton) {
            Spacer(modifier = Modifier.width(12.dp))

            // "+ New event" solid purple pill button
            Surface(
                onClick = onNewEventClick,
                shape = CircleShape,
                color = CalendarDesignTokens.PrimaryPurple,
                modifier = Modifier.defaultMinSize(minHeight = 36.dp, minWidth = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "add",
                        size = 16.dp,
                        tint = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.calendar_new_event),
                        maxLines = 1,
                        softWrap = false,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = Color.White,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * An individual event/task card within the agenda.
 *
 * Displays a time badge on the left (2 lines: hour + AM/PM), and a styled card on the right
 * with an icon squircle, title, subtitle, and clickable action.
 */
@Composable
fun AgendaEventCard(
    event: CalendarEventUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleDone: ((Long, Boolean) -> Unit)? = null,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time label column (e.g. "09:00" above "AM")
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            val parts = event.timeDisplay.trim().split(" ")
            if (parts.size >= 2) {
                Text(
                    text = parts[0],
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                        ),
                )
                Text(
                    text = parts[1],
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                        ),
                )
            } else {
                Text(
                    text = event.timeDisplay,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                        ),
                )
            }
        }

        // Event Card
        Surface(
            onClick = onClick,
            shape = CalendarDesignTokens.ItemCardShape,
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category Icon Squircle
                Box(
                    modifier =
                        Modifier
                            .size(CalendarDesignTokens.IconBoxSize)
                            .background(event.iconBgColor, shape = CalendarDesignTokens.IconBoxShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CalendarEventIcon(
                        iconName = event.iconName,
                        tint = event.iconTint,
                        size = 20.dp,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = if (event.isDone && event.isTask) textSecondary else textPrimary,
                                textDecoration = if (event.isDone && event.isTask) TextDecoration.LineThrough else null,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.subtitle,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                color = textSecondary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // If it's a task with toggle support, show interactive completion checkbox with 36dp touch target
                if (event.isTask && onToggleDone != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onToggleDone(event.id, !event.isDone) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (event.isDone) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(26.dp)
                                        .background(CalendarDesignTokens.DotGreen.copy(alpha = 0.2f), shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 16.dp,
                                    tint = CalendarDesignTokens.DotGreen,
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = textSecondary.copy(alpha = 0.5f),
                                            shape = CircleShape,
                                        ),
                            )
                        }
                    }
                } else if (event.isDone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
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
 * Universal icon renderer for calendar items supporting Material symbols, custom drawables, and emojis.
 */
@Composable
fun CalendarEventIcon(
    iconName: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val noteIcon =
        remember(iconName) {
            resolveNoteIcon(
                iconKey = iconName,
                kind = NoteKind.NOTE,
            )
        }
    when (noteIcon) {
        is NoteIcon.Symbol -> {
            RememberMaterialRoundedSymbol(
                name = noteIcon.name,
                size = size,
                tint = tint,
                filled = false,
                modifier = modifier,
            )
        }
        is NoteIcon.Drawable -> {
            Icon(
                painter = painterResource(noteIcon.resId),
                contentDescription = null,
                tint = tint,
                modifier = modifier.size(size),
            )
        }
        is NoteIcon.Emoji -> {
            Text(
                text = noteIcon.text,
                fontSize = (size.value * 0.9f).sp,
                lineHeight = (size.value * 0.9f).sp,
                modifier = modifier,
            )
        }
        else -> {
            RememberMaterialRoundedSymbol(
                name = CalendarDesignTokens.cleanIconName(iconName),
                size = size,
                tint = tint,
                modifier = modifier,
            )
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
        onClick = onClick,
        shape = CalendarDesignTokens.ItemCardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cake icon squircle
            Box(
                modifier =
                    Modifier
                        .size(CalendarDesignTokens.IconBoxSize)
                        .background(CalendarDesignTokens.BirthdayBoxBg, shape = CalendarDesignTokens.IconBoxShape),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = CalendarDesignTokens.cleanIconName(birthday.iconName),
                    size = 20.dp,
                    tint = CalendarDesignTokens.BirthdayIconTint,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Birthday title & info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = birthday.name,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
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
                    style =
                        MaterialTheme.typography.bodySmall.copy(
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
 * Modern circular desk calendar illustration for the empty state.
 *
 * Features a circular pale-purple background containing a desk calendar pad with
 * two top binder rings and the actual selected day number displayed in purple.
 */
@Composable
fun CalendarEmptyIllustration(
    dayNumber: Int,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val circleBg = if (isDark) CalendarDesignTokens.DarkPurpleSelected else CalendarDesignTokens.SoftLavender
    val pegColor = if (isDark) Color(0xFFC49FE0) else CalendarDesignTokens.PrimaryPurple
    val padBorder = if (isDark) Color(0xFF5A4468) else Color(0xFFC7B3D3)
    val padBg = if (isDark) Color(0xFF262130) else Color.White
    val headerStripBg = if (isDark) Color(0xFF483256) else Color(0xFFE2D0E6)
    val dayNumberColor = if (isDark) Color(0xFFD6B5EB) else CalendarDesignTokens.PrimaryPurple

    Box(
        modifier =
            modifier
                .size(116.dp)
                .background(circleBg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(width = 62.dp, height = 66.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 1. Calendar Pad Body
            Box(
                modifier =
                    Modifier
                        .padding(top = 3.5.dp)
                        .size(width = 62.dp, height = 62.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(padBg)
                        .border(
                            BorderStroke(1.5.dp, padBorder),
                            RoundedCornerShape(12.dp),
                        ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mauve header strip
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .background(headerStripBg),
                    )
                    // Day number body
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$dayNumber",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dayNumberColor,
                                ),
                        )
                    }
                }
            }

            // 2. Two top binder rings threading into the header
            Row(
                modifier =
                    Modifier
                        .width(62.dp)
                        .padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 3.5.dp, height = 8.5.dp)
                            .background(pegColor, RoundedCornerShape(2.dp)),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 3.5.dp, height = 8.5.dp)
                            .background(pegColor, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Modern empty state card matching the reference design.
 *
 * Displays a large white rounded card containing:
 * 1. Centered circular calendar illustration with two binder rings and selected day number.
 * 2. Prominent heading: "A little room in your day".
 * 3. Two lines of supporting text.
 * 4. Exactly one prominent "+ New event" button.
 */
@Composable
fun CalendarEmptyState(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.calendar_empty_room_title),
    onCreateEventClick: (() -> Unit)? = null,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Centered circular calendar illustration
            CalendarEmptyIllustration(
                dayNumber = selectedDate.dayOfMonth,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent heading
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                    ),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Two lines of supporting text
            Text(
                text = stringResource(R.string.calendar_empty_no_events_desc),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                    ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.calendar_empty_enjoy_space_desc),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                    ),
            )

            if (onCreateEventClick != null) {
                Spacer(modifier = Modifier.height(24.dp))

                // Exactly one prominent "+ New event" button inside the card
                Surface(
                    onClick = onCreateEventClick,
                    shape = CircleShape,
                    color = CalendarDesignTokens.PrimaryPurple,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 140.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "add",
                            size = 18.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.calendar_new_event),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color.White,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible overload for string message based empty state.
 */
@Composable
fun CalendarEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    onCreateEventClick: (() -> Unit)? = null,
) {
    CalendarEmptyState(
        selectedDate = LocalDate.now(),
        modifier = modifier,
        title = message,
        onCreateEventClick = onCreateEventClick,
    )
}
