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
import androidx.compose.material3.HorizontalDivider
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
import java.time.format.TextStyle
import java.util.Locale

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
 * with an optional themed icon, category label, and count badge.
 */
@Composable
fun AgendaCategoryHeader(
    title: String,
    count: Int = 0,
    modifier: Modifier = Modifier,
    iconName: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badgeBgColor: Color? = null,
    badgeTextColor: Color? = null,
    titleColor: Color? = null,
) {
    val isDark = LocalIsDark.current
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val defaultTitleColor = if (iconName.isNullOrBlank()) MaterialTheme.colorScheme.primary else textPrimary
    val finalTitleColor = titleColor ?: defaultTitleColor
    val defaultBadgeBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f)
    val defaultBadgeText = MaterialTheme.colorScheme.primary
    val finalBadgeBg = badgeBgColor ?: defaultBadgeBg
    val finalBadgeText = badgeTextColor ?: defaultBadgeText

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!iconName.isNullOrBlank()) {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = 18.dp,
                tint = iconTint,
            )
        }
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = finalTitleColor,
                ),
        )
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = finalBadgeBg,
                modifier = Modifier.defaultMinSize(minHeight = 22.dp, minWidth = 24.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
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
    showOutsideTimeColumn: Boolean = false,
) {
    val isDark = LocalIsDark.current
    val itemCardBg = if (isDark) Color(0xFF242424) else Color(0xFFF4F4F7)
    val itemCardBorder = if (isDark) Color(0xFF3A3A3A) else Color(0xFFEAEAEF)
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    val contentCard: @Composable (Modifier) -> Unit = { innerModifier ->
        Surface(
            shape = CalendarDesignTokens.ItemCardShape,
            color = if (showOutsideTimeColumn) cardBg else itemCardBg,
            border = BorderStroke(1.dp, if (showOutsideTimeColumn) cardBorder else itemCardBorder),
            modifier =
                innerModifier
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
                    val defaultTaskBg = if (isDark) Color(0xFF1E3326) else Color(0xFFEBF5EE)
                    val defaultTaskTint = if (isDark) Color(0xFF81C784) else Color(0xFF2E8B57)
                    val defaultEventBg = if (isDark) Color(0xFF1E2D38) else Color(0xFFE6F4F5)
                    val defaultEventTint = if (isDark) Color(0xFF4DD0E1) else Color(0xFF00897B)

                    val resolvedIconBg =
                        when {
                            event.iconBgColor != Color.Unspecified && event.iconBgColor != CalendarDesignTokens.CategoryPurpleBg ->
                                event.iconBgColor
                            event.isTask -> defaultTaskBg
                            else -> defaultEventBg
                        }
                    val resolvedIconTint =
                        when {
                            event.iconTint != Color.Unspecified && event.iconTint != CalendarDesignTokens.CategoryPurpleIcon ->
                                event.iconTint
                            event.isTask -> defaultTaskTint
                            else -> defaultEventTint
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

                        // Subtitle logic
                        val displaySubtitle =
                            if (!showOutsideTimeColumn) {
                                when {
                                    event.isTask && event.timeDisplay.isNotBlank() && event.description.isNotBlank() &&
                                        !event.description.contains(event.timeDisplay) ->
                                        "${event.timeDisplay}\n${event.description}"
                                    event.timeDisplay.isNotBlank() && event.description.isNotBlank() &&
                                        !event.description.contains(event.timeDisplay) ->
                                        "${event.timeDisplay} · ${event.description}"
                                    event.description.isNotBlank() -> event.description
                                    event.subtitle.isNotBlank() -> event.subtitle
                                    else -> event.timeDisplay
                                }
                            } else {
                                event.description.ifBlank { event.subtitle }
                            }

                        if (displaySubtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displaySubtitle,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        color = textSecondary,
                                    ),
                                maxLines = if (event.isTask) 2 else 1,
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

    if (showOutsideTimeColumn) {
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

            contentCard(Modifier.weight(1f))
        }
    } else {
        contentCard(modifier.fillMaxWidth())
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
    horizontalPadding: Dp = CalendarDesignTokens.ScreenHorizontalPadding,
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
                .padding(horizontal = horizontalPadding, vertical = 6.dp),
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
 * Modern rounded squircle date badge illustration for the empty state matching the redesign.
 *
 * Displays a soft tinted squircle with the day-of-week abbreviation (e.g. "FRI")
 * and large day number (e.g. "13") in the active theme accent color.
 */
@Composable
fun CalendarEmptyIllustration(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val badgeBg =
        if (isDark) {
            primaryColor.copy(alpha = 0.22f)
        } else {
            primaryColor.copy(alpha = 0.12f)
        }

    val dayOfWeekAbbr =
        remember(selectedDate) {
            val locale = Locale.getDefault()
            selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
        }

    Box(
        modifier =
            modifier
                .size(width = 78.dp, height = 86.dp)
                .background(badgeBg, shape = RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dayOfWeekAbbr,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = primaryColor,
                        letterSpacing = 0.5.sp,
                    ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${selectedDate.dayOfMonth}",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    ),
            )
        }
    }
}

/**
 * Backward-compatible overload for dayNumber: Int.
 */
@Composable
fun CalendarEmptyIllustration(
    dayNumber: Int,
    modifier: Modifier = Modifier,
) {
    val date =
        remember(dayNumber) {
            runCatching { LocalDate.now().withDayOfMonth(dayNumber) }.getOrElse { LocalDate.now() }
        }
    CalendarEmptyIllustration(selectedDate = date, modifier = modifier)
}

/**
 * Centered empty state body matching Image 2 redesign:
 * - Date squircle badge ("FRI" / "13")
 * - Heading: "A little room in your day"
 * - Subtitle: "No events or tasks for this date. Add a plan, or enjoy the open space."
 * - Solid pill "+ New event" action button
 * - Horizontal divider
 * - Subtle footer: "A little space for what matters."
 */
@Composable
fun CalendarEmptyStateContent(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.calendar_empty_room_title),
    onCreateEventClick: (() -> Unit)? = null,
) {
    val isDark = LocalIsDark.current
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CalendarEmptyIllustration(selectedDate = selectedDate)

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
            text = stringResource(R.string.calendar_empty_no_events_desc) + "\n" +
                stringResource(R.string.calendar_empty_enjoy_space_desc),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                ),
        )

        if (onCreateEventClick != null) {
            Spacer(modifier = Modifier.height(24.dp))

            // Prominent solid "+ New event" pill button
            Surface(
                onClick = onCreateEventClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .height(46.dp)
                        .defaultMinSize(minWidth = 140.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
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

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(color = cardBorder)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.calendar_empty_footer),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

/**
 * Modern empty state card matching the reference design.
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

    Surface(
        shape = CalendarDesignTokens.CardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding),
    ) {
        CalendarEmptyStateContent(
            selectedDate = selectedDate,
            title = title,
            onCreateEventClick = onCreateEventClick,
        )
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
