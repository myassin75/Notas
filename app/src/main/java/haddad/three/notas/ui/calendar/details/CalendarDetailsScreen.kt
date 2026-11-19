package haddad.three.notas.ui.calendar.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import haddad.three.notas.R
import haddad.three.notas.data.AccentTheme
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.ui.calendar.CalendarDesignTokens
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.edit.NoteIcon
import haddad.three.notas.ui.edit.resolveNoteIcon
import haddad.three.notas.ui.theme.GlassVariant
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.LocalThemeState
import haddad.three.notas.ui.theme.glassContainerColor
import java.util.Locale

@Composable
fun CalendarDetailsRoute(
    onBack: () -> Unit,
    onEditItem: (NoteWithItems) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarDetailsScreen(
        uiState = uiState,
        onBack = onBack,
        onEditClick = {
            uiState.rawNote?.let { onEditItem(it) }
        },
        onToggleDone = viewModel::toggleTaskCompletion,
        modifier = modifier,
    )
}

@Composable
fun CalendarDetailsScreen(
    uiState: CalendarDetailsUiState,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val semanticPalette = LocalSemanticPalette.current
    val themeState = LocalThemeState.current
    val isBlackTheme = themeState.blackThemeActive(isDark) || themeState.accentTheme == AccentTheme.PURE_BLACK

    val effectiveAccent = if (uiState.accentColor != Color.Unspecified) uiState.accentColor else MaterialTheme.colorScheme.primary
    val globalActionAccent =
        if (isDark) {
            if (isBlackTheme) Color(0xFFD4D4DE) else semanticPalette.primaryAction
        } else {
            MaterialTheme.colorScheme.primary
        }
    val onGlobalActionAccent = if (isDark) Color(0xFF242424) else Color.White

    val headerScreenBg = if (isDark) Color.Transparent else MaterialTheme.colorScheme.background
    val cardSurfaceBg = if (isDark) glassContainerColor(GlassVariant.StandardCard, isDark = true) else Color(0xFFFFFFFF)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    if (uiState.isLoading) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(headerScreenBg),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = effectiveAccent)
        }
        return
    }

    if (uiState.notFound) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(headerScreenBg)
                    .statusBarsPadding()
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.calendar_details_not_found),
                    style = MaterialTheme.typography.titleMedium.copy(color = textPrimary),
                )
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = onBack) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_left",
                        size = 28.dp,
                        tint = textPrimary,
                    )
                }
            }
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(headerScreenBg),
    ) {
        // ==================== HEADER AREA ====================
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
        ) {
            // 1. Back button (clean, minimal chevron left)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "arrow_back",
                        size = 24.dp,
                        tint = textPrimary,
                    )
                }
            }

            // 2. Date, Day and Illustration Row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.dateText,
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = effectiveAccent,
                                lineHeight = 46.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.weekdayText,
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Normal,
                                color = effectiveAccent,
                                lineHeight = 46.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                    )
                }

                // Event Illustration (Birthday cake or large icon)
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.isBirthday) {
                        BirthdayCakeIllustration(
                            tint = effectiveAccent,
                            size = 72.dp,
                        )
                    } else {
                        CalendarDetailsHeaderIcon(
                            iconKey = uiState.iconKey,
                            isTask = uiState.isTask,
                            title = uiState.title,
                            tint = effectiveAccent,
                            size = 56.dp,
                        )
                    }
                }
            }
        }

        // ==================== MAIN DETAILS CARD ====================
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            val fabSize = maxWidth * (64f / 473f)
            val fabEndPadding = maxWidth * ((473f - 398f) / 473f)
            val titleEndPadding = maxWidth * (207f / 473f) + 12.dp

            // White Details Card with smooth S-curve cutout shape
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = remember { CalendarDetailsCardShape() },
                color = cardSurfaceBg,
                shadowElevation = 0.dp,
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .navigationBarsPadding(),
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Title
                    Text(
                        text = uiState.title,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                lineHeight = 34.sp,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, end = titleEndPadding),
                    )

                    // Description (if present)
                    if (uiState.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.description,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = textSecondary,
                                    lineHeight = 22.sp,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 30.dp),
                        )
                    }

                    // Divider
                    Spacer(modifier = Modifier.height(22.dp))
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = dividerColor,
                        modifier = Modifier.padding(horizontal = 30.dp),
                    )
                    Spacer(modifier = Modifier.height(22.dp))

                    // Details Rows
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // 1. Color row
                        DetailsRow(
                            icon = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(20.dp)
                                            .background(effectiveAccent, CircleShape),
                                )
                            },
                            content = {
                                Text(
                                    text = stringResource(R.string.calendar_details_color),
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = textPrimary,
                                        ),
                                )
                            },
                        )

                        // 2. Timed Event: Start Time → End Time and Duration
                        if (!uiState.isTask && !uiState.isAllDay && uiState.startTimeText != null && uiState.endTimeText != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "schedule",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = uiState.startTimeText,
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = textPrimary,
                                                ),
                                        )
                                        Text(
                                            text = " → ",
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 16.sp,
                                                    color = textMuted,
                                                ),
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                        )
                                        Text(
                                            text = uiState.endTimeText,
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = textPrimary,
                                                ),
                                        )
                                        if (!uiState.durationText.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = uiState.durationText,
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 14.5.sp,
                                                        color = textMuted,
                                                    ),
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        // 3. All-day row
                        if (!uiState.isTask && uiState.isAllDay) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "hourglass_top",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text = stringResource(R.string.calendar_details_all_day),
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }

                        // 4. Task-specific: Scheduled Time
                        if (uiState.isTask && uiState.taskScheduledTimeText != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "schedule",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text = uiState.taskScheduledTimeText,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }

                        // 5. Task-specific: Completion Status (interactive toggle)
                        if (uiState.isTask) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = if (uiState.isDone) "check_circle" else "radio_button_unchecked",
                                        size = 20.dp,
                                        tint = if (uiState.isDone) CalendarDesignTokens.DotGreen else textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text =
                                            if (uiState.isDone) {
                                                stringResource(R.string.calendar_details_status_completed)
                                            } else {
                                                stringResource(R.string.calendar_details_status_todo)
                                            },
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                                onClick = onToggleDone,
                            )
                        }

                        // 6. Task-specific: Checklist Items Summary
                        if (uiState.isTask && uiState.checklistSummary != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "checklist",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text = uiState.checklistSummary,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }

                        // 7. Recurrence row
                        if (uiState.recurrencePrimary != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "repeat",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = uiState.recurrencePrimary,
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = textPrimary,
                                                ),
                                        )
                                        if (!uiState.recurrenceSecondary.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = uiState.recurrenceSecondary,
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 14.5.sp,
                                                        color = textMuted,
                                                    ),
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        // 8. Reminders row
                        if (uiState.reminderSummary != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "notifications",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text = uiState.reminderSummary,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }

                        // 9. Location row
                        if (uiState.locationSummary != null) {
                            DetailsRow(
                                icon = {
                                    RememberMaterialRoundedSymbol(
                                        name = "location_on",
                                        size = 20.dp,
                                        tint = textMuted,
                                    )
                                },
                                content = {
                                    Text(
                                        text = uiState.locationSummary,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }

                        // 10. Shoulder / Category row
                        if (uiState.shoulderName != null) {
                            DetailsRow(
                                icon = {
                                    if (uiState.shoulderColor != null) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(16.dp)
                                                    .background(uiState.shoulderColor, CircleShape),
                                        )
                                    } else {
                                        RememberMaterialRoundedSymbol(
                                            name = "folder",
                                            size = 20.dp,
                                            tint = textMuted,
                                        )
                                    }
                                },
                                content = {
                                    Text(
                                        text = uiState.shoulderName,
                                        style =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = textPrimary,
                                            ),
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Circular Edit FAB sitting in the upper-right alcove
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = fabEndPadding)
                        .size(fabSize)
                        .background(globalActionAccent, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = fabSize / 2),
                            role = Role.Button,
                            onClick = onEditClick,
                            onClickLabel = stringResource(R.string.calendar_details_edit),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "edit",
                    size = fabSize * (24f / 64f),
                    tint = onGlobalActionAccent,
                )
            }
        }
    }
}

@Composable
private fun DetailsRow(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickableModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}

@Composable
private fun CalendarDetailsHeaderIcon(
    iconKey: String?,
    isTask: Boolean,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 54.dp,
) {
    val resolved =
        remember(iconKey, isTask) {
            val kind = if (isTask) NoteKind.LIST else NoteKind.NOTE
            resolveNoteIcon(iconKey, kind)
        }

    when (resolved) {
        is NoteIcon.Symbol -> {
            RememberMaterialRoundedSymbol(
                name = resolved.name,
                size = size,
                tint = tint,
                filled = false,
                modifier = modifier,
            )
        }
        is NoteIcon.Drawable -> {
            Icon(
                painter = painterResource(resolved.resId),
                contentDescription = null,
                tint = tint,
                modifier = modifier.size(size),
            )
        }
        is NoteIcon.Emoji -> {
            Text(
                text = resolved.text,
                fontSize = (size.value * 0.85f).sp,
                lineHeight = (size.value * 0.85f).sp,
                modifier = modifier,
            )
        }
        else -> {
            val fallbackSymbol =
                remember(title, isTask) {
                    val lower = title.lowercase(Locale.getDefault())
                    when {
                        isTask -> "checklist"
                        lower.contains("coffee") || lower.contains("cafe") -> "local_cafe"
                        lower.contains("meet") || lower.contains("sync") -> "groups"
                        lower.contains("call") || lower.contains("phone") -> "call"
                        lower.contains("flight") || lower.contains("travel") -> "flight"
                        lower.contains("doctor") || lower.contains("hospital") -> "local_hospital"
                        lower.contains("shop") || lower.contains("grocery") -> "shopping_cart"
                        else -> "edit_note"
                    }
                }
            RememberMaterialRoundedSymbol(
                name = fallbackSymbol,
                size = size,
                tint = tint,
                modifier = modifier,
            )
        }
    }
}
