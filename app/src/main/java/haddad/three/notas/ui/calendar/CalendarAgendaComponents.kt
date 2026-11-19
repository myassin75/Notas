package haddad.three.notas.ui.calendar

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
import haddad.three.notas.R
import haddad.three.notas.data.NoteKind
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.edit.NoteIcon
import haddad.three.notas.ui.edit.resolveNoteIcon
import haddad.three.notas.ui.theme.LocalIsDark
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

            // "+ New event" solid primary pill button
            Surface(
                onClick = onNewEventClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.calendar_new_event),
                        maxLines = 1,
                        softWrap = false,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Distinguishable header for agenda categories (Events, Tasks, Birthdays)
 * with a themed icon, category label, and count badge.
 */
@Composable
fun AgendaCategoryHeader(
    title: String,
    iconName: String,
    count: Int,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badgeBgColor: Color? = null,
    badgeTextColor: Color? = null,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val defaultBadgeBg = MaterialTheme.colorScheme.primaryContainer
    val defaultBadgeText = MaterialTheme.colorScheme.onPrimaryContainer
    val finalBadgeBg = badgeBgColor ?: defaultBadgeBg
    val finalBadgeText = badgeTextColor ?: defaultBadgeText

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = iconName,
            size = 18.dp,
            tint = iconTint,
        )
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = textPrimary,
                ),
        )
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = finalBadgeBg,
                modifier = Modifier.defaultMinSize(minHeight = 20.dp, minWidth = 20.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = finalBadgeText,
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
        // Time label column (e.g. "09:00" above "AM", or "All day")
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            if (event.isAllDay) {
                Text(
                    text = stringResource(R.string.calendar_all_day),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                )
            } else {
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
                } else if (event.timeDisplay.isNotBlank()) {
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
        }

        // Event Card
        Surface(
            shape = CalendarDesignTokens.ItemCardShape,
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier =
                Modifier
                    .weight(1f)
                    .clip(CalendarDesignTokens.ItemCardShape),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Clickable area for details (Icon, Title, Subtitle, Shoulder)
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onClick)
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val defaultIconColor = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                    val defaultIconBg = if (isDark) Color(0xFF2C2C30) else Color(0xFFE5E5EA)
                    val resolvedIconBg =
                        if (event.iconBgColor != Color.Unspecified && event.iconBgColor != CalendarDesignTokens.CategoryPurpleBg) {
                            event.iconBgColor
                        } else {
                            defaultIconBg
                        }
                    val resolvedIconTint =
                        if (event.iconTint != Color.Unspecified && event.iconTint != CalendarDesignTokens.CategoryPurpleIcon) {
                            event.iconTint
                        } else {
                            defaultIconColor
                        }

                    // Category Icon Squircle
                    Box(
                        modifier =
                            Modifier
                                .size(CalendarDesignTokens.IconBoxSize)
                                .background(resolvedIconBg, shape = CalendarDesignTokens.IconBoxShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CalendarEventIcon(
                            iconName = event.iconName,
                            tint = resolvedIconTint,
                            size = 20.dp,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title, Description, and Shoulder Chip
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
                        val displaySubtitle = event.description.ifBlank { event.subtitle }
                        if (displaySubtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displaySubtitle,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        color = textSecondary,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!event.shoulderName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val fallbackPrimary = MaterialTheme.colorScheme.primary
                            val chipColor =
                                remember(event.shoulderColorHex, fallbackPrimary) {
                                    haddad.three.notas.ui.components
                                        .parseNoteColorHex(event.shoulderColorHex, fallbackPrimary)
                                }
                            val (badgeBg, badgeFg) =
                                remember(chipColor, isDark) {
                                    haddad.three.notas.ui.components
                                        .readableBadgeColors(chipColor, isDark)
                                }
                            Surface(
                                shape = CircleShape,
                                color = badgeBg,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "local_offer",
                                        size = 11.dp,
                                        tint = badgeFg,
                                        weight = FontWeight.Bold,
                                        filled = false,
                                    )
                                    Text(
                                        text = event.shoulderName,
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = badgeFg,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                // If it's a task with toggle support, show interactive completion checkbox with 44dp touch target
                if (event.isTask && onToggleDone != null) {
                    Box(
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onToggleDone(event.id, event.isDone) },
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
                    Box(
                        modifier =
                            Modifier
                                .padding(end = 12.dp)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val pegColor = if (isDark) Color.White else primaryColor
    val padBorder = if (isDark) primaryColor else MaterialTheme.colorScheme.outlineVariant
    val padBg = if (isDark) Color(0xFF222226) else MaterialTheme.colorScheme.surface
    val headerStripBg = primaryColor
    val dayNumberColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!isDark) {
            Box(
                modifier =
                    Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
        }

        Box(
            modifier = Modifier.size(width = 68.dp, height = 72.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 1. Calendar Pad Body
            Box(
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .size(width = 68.dp, height = 68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(padBg)
                        .border(
                            BorderStroke(1.5.dp, padBorder),
                            RoundedCornerShape(14.dp),
                        ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Accent header strip
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(headerStripBg),
                    )
                    // Day number body - crisp white "20"
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
                                    fontSize = 26.sp,
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
                        .width(68.dp)
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 4.dp, height = 9.dp)
                            .background(pegColor, RoundedCornerShape(2.dp)),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 4.dp, height = 9.dp)
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
                    color = MaterialTheme.colorScheme.primary,
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
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = stringResource(R.string.calendar_new_event),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
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
