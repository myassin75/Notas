package three.haddad.smartnotas.ui.edit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.ActionType
import three.haddad.smartnotas.data.AppMediaStorage
import three.haddad.smartnotas.data.Importance
import three.haddad.smartnotas.data.NoteAction
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.NoteReminder
import three.haddad.smartnotas.data.RecurrenceRule
import three.haddad.smartnotas.data.RecurrenceUnit
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.data.labelRes
import three.haddad.smartnotas.data.recycleNoteActionIconBitmap
import three.haddad.smartnotas.data.toNoteActionIconDrawable
import three.haddad.smartnotas.domain.formatTimeOfDay
import three.haddad.smartnotas.ui.common.AppBottomSheet
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.ResponsiveActionLayout
import three.haddad.smartnotas.ui.common.rememberBottomSheetStateWithUnsavedChanges
import three.haddad.smartnotas.ui.common.responsiveActionLayout
import three.haddad.smartnotas.ui.components.RememberButton
import three.haddad.smartnotas.ui.components.RememberFilledTonalIconButton
import three.haddad.smartnotas.ui.components.RememberTextButton
import three.haddad.smartnotas.ui.components.RememberUnsavedChangesDialog
import three.haddad.smartnotas.ui.feedback.appClickable
import three.haddad.smartnotas.ui.feedback.appCombinedClickable
import three.haddad.smartnotas.ui.help.HELP_FOCUS_IMPORTANCE
import three.haddad.smartnotas.ui.help.HELP_FOCUS_VISIBILITY
import three.haddad.smartnotas.ui.help.LocalOpenHelp
import three.haddad.smartnotas.ui.settings.SettingsDesignTokens
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec
import java.text.DateFormat
import java.util.Date
import three.haddad.smartnotas.data.Visibility as NoteVisibility

private val OPTION_CELL_HEIGHT = 56.dp
private const val STARRED_WATERMARK_ALPHA = 0.28f

@Composable
fun OptionsPanel(
    shoulder: ShoulderEntity? = null,
    reminders: List<NoteReminder>,
    importance: Importance,
    visibility: NoteVisibility,
    pictureUri: String?,
    actions: List<NoteAction>,
    attachments: List<NoteAttachmentEntity>,
    onOpenShoulders: () -> Unit = {},
    onOpenReminder: () -> Unit,
    onSetImportance: (Importance) -> Unit,
    onSetVisibility: (NoteVisibility) -> Unit,
    onOpenPicture: () -> Unit,
    onBrowsePictureWithApp: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenAttachments: () -> Unit,
    modifier: Modifier = Modifier,
    reminderPermissionMissing: Boolean = false,
    // When true (archived / trashed), the Behavior row's internal bottom-sheet trigger is
    // suppressed. Every OTHER row already no-ops through its caller-provided `onOpen*` lambda
    // when read-only, but Behavior owns its own sheet so the gate has to live here.
    readOnly: Boolean = false,
    // Mirrors the starred-card visual treatment from the Home/History grid: a subtle yellow
    // wash on the panel surface plus a tilted watermark star at the top-end. Keeps the
    // starred cue consistent when the user opens a starred note or list into the editor.
    starred: Boolean = false,
    createdAt: Long? = null,
    updatedAt: Long? = null,
) {
    var behaviorOpen by rememberSaveable { mutableStateOf(false) }
    // The Behavior sheet's unconfirmed choices. Held here rather than in the sheet so they
    // survive the sheet leaving the screen while Help is open; see the sheet below.
    var draftVisibility by rememberSaveable { mutableStateOf(visibility) }
    var draftImportance by rememberSaveable { mutableStateOf(importance) }
    val openHelp = LocalOpenHelp.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val context = LocalContext.current
    val storedAttachmentCount =
        attachments.count { attachment ->
            AppMediaStorage.isAppStoredMediaUri(context, attachment.uri)
        }
    val linkedAttachmentCount = attachments.size - storedAttachmentCount
    val attachmentSummary =
        if (attachments.isEmpty()) {
            attachmentsSummary(attachments.size)
        } else {
            attachmentStorageSummary(
                fileSummary = attachmentsSummary(attachments.size),
                storedCount = storedAttachmentCount,
                linkedCount = linkedAttachmentCount,
            )
        }
    val firstAction = actions.firstOrNull()
    val firstActionIcon =
        remember(firstAction?.type, firstAction?.details, firstAction?.iconData) {
            firstAction?.let { action -> actionOptionsIcon(context, action) }
        }
    DisposableEffect(firstActionIcon) {
        val iconToRecycle = firstActionIcon
        onDispose {
            iconToRecycle?.recycleNoteActionIconBitmap()
        }
    }

    val baseContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val starredCueSpec =
        reducedMotionAwareSpec(
            tween<Float>(
                durationMillis = 280,
                easing = FastOutSlowInEasing,
            ),
        )
    val starredCueProgress by animateFloatAsState(
        targetValue = if (starred) 1f else 0f,
        animationSpec = starredCueSpec,
        label = "optionsPanelStarredCue",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Row 1: Shoulders
                OptionCell(
                    symbolName = "local_offer",
                    title = stringResource(R.string.view_options_group_shoulders),
                    summary = "",
                    onClick = onOpenShoulders,
                    modifier = Modifier.fillMaxWidth(),
                    summaryContent = {
                        if (shoulder != null) {
                            val shoulderColor =
                                remember(shoulder.colorHex) {
                                    if (shoulder.colorHex.isNullOrBlank()) {
                                        SettingsDesignTokens.PrimaryPurple
                                    } else {
                                        try {
                                            Color(android.graphics.Color.parseColor(if (shoulder.colorHex.startsWith("#")) shoulder.colorHex else "#${shoulder.colorHex}"))
                                        } catch (_: Throwable) {
                                            SettingsDesignTokens.PrimaryPurple
                                        }
                                    }
                                }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(shoulderColor),
                                )
                                Text(
                                    text = shoulder.name,
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = shoulderColor,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                )
                            }
                        } else {
                            Text(
                                text = "No shoulder",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = Color(0xFF89899C),
                                    ),
                            )
                        }
                    },
                )

                HorizontalDivider(color = Color(0xFFE9E3EF), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // Row 2: Reminder
                OptionCell(
                    symbolName = if (reminderPermissionMissing) "alarm_off" else "alarm",
                    title = stringResource(R.string.options_reminder),
                    summary = "",
                    onClick = onOpenReminder,
                    modifier = Modifier.fillMaxWidth(),
                    iconTint = if (reminderPermissionMissing) MaterialTheme.colorScheme.error else SettingsDesignTokens.PrimaryPurple,
                    summaryContent = {
                        ReminderOptionSummary(
                            reminders = reminders,
                            permissionMissing = reminderPermissionMissing,
                        )
                    },
                )

                HorizontalDivider(color = Color(0xFFE9E3EF), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // Row 3: Notification action
                OptionCell(
                    symbolName = firstAction?.type?.materialSymbolName() ?: "bolt",
                    icon = firstActionIcon,
                    title = stringResource(R.string.options_actions),
                    summary = actionsSummary(actions),
                    onClick = onOpenActions,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = Color(0xFFE9E3EF), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // Row 4: Behavior
                OptionCell(
                    symbolName = "visibility",
                    title = stringResource(R.string.options_behavior),
                    summary = "",
                    onClick =
                        if (readOnly) {
                            null
                        } else {
                            {
                                draftVisibility = visibility
                                draftImportance = importance
                                behaviorOpen = true
                            }
                        },
                    modifier = Modifier.fillMaxWidth(),
                    summaryContent = { BehaviorOptionSummary(visibility, importance) },
                )
            }
        }

        // Side-by-side Picture and Attachments action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenPicture),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "image",
                        size = 20.dp,
                        tint = SettingsDesignTokens.PrimaryPurple,
                        weight = FontWeight.Normal,
                        filled = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.options_picture),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF32313F),
                            ),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenAttachments),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "attach_file",
                        size = 20.dp,
                        tint = SettingsDesignTokens.PrimaryPurple,
                        weight = FontWeight.Normal,
                        filled = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.options_attachments),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF32313F),
                            ),
                    )
                }
            }
        }
        if (createdAt != null && createdAt > 0L) {
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
            ) {
                val createdFormatted =
                    remember(createdAt, context) {
                        val createdDate = Date(createdAt)
                        android.text.format.DateFormat
                            .getMediumDateFormat(context)
                            .format(createdDate) + " " +
                            formatTimeOfDay(context, createdAt)
                    }
                val createdLabel = stringResource(R.string.options_created, createdFormatted)
                if (updatedAt != null && updatedAt > 0L) {
                    val updatedFormatted =
                        remember(updatedAt, context) {
                            val updatedDate = Date(updatedAt)
                            android.text.format.DateFormat
                                .getMediumDateFormat(context)
                                .format(updatedDate) + " " +
                                formatTimeOfDay(context, updatedAt)
                        }
                    val updatedLabel = stringResource(R.string.options_updated, updatedFormatted)
                    val stacked =
                        responsiveActionLayout(
                            availableWidth = maxWidth,
                            effectiveFontScale = LocalDensity.current.fontScale,
                            itemCount = 2,
                        ) == ResponsiveActionLayout.STACKED
                    TimestampFooter(
                        createdLabel = createdLabel,
                        updatedLabel = updatedLabel,
                        stacked = stacked,
                    )
                } else {
                    TimestampText(
                        text = createdLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // The sheet draws in its own window, above any navigation, so it is only shown while this
    // screen is the resumed one. Opening Help from it takes it off screen as Help slides in;
    // coming back resumes this screen and shows it again with the drafts intact. The editor
    // itself autosaves when it stops, so nothing typed is lost either.
    if (behaviorOpen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        BehaviorSheet(
            visibility = visibility,
            importance = importance,
            draftVisibility = draftVisibility,
            draftImportance = draftImportance,
            onDraftVisibilityChange = { draftVisibility = it },
            onDraftImportanceChange = { draftImportance = it },
            onOpenHelp = openHelp,
            onConfirm = { nextVisibility, nextImportance ->
                if (nextVisibility != visibility) {
                    onSetVisibility(nextVisibility)
                }
                if (nextImportance != importance) {
                    onSetImportance(nextImportance)
                }
            },
            onDismiss = { behaviorOpen = false },
        )
    }
}

@Composable
private fun TimestampFooter(
    createdLabel: String,
    updatedLabel: String,
    stacked: Boolean,
) {
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TimestampText(text = createdLabel, modifier = Modifier.fillMaxWidth())
            TimestampText(text = updatedLabel, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimestampText(
                text = createdLabel,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "·",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            TimestampText(
                text = updatedLabel,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun TimestampText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun BoxScope.StarredRadiantWash(
    modifier: Modifier = Modifier,
    progress: Float,
) {
    val washAlpha = progress.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier.drawWithCache {
                val starCenter = Offset(size.width - 54.dp.toPx(), 50.dp.toPx())
                val brush =
                    Brush.radialGradient(
                        colorStops =
                            arrayOf(
                                0.00f to Color(0xFFF9A825).copy(alpha = 0.22f * washAlpha),
                                0.20f to Color(0xFFF9A825).copy(alpha = 0.15f * washAlpha),
                                0.44f to Color(0xFFF9A825).copy(alpha = 0.08f * washAlpha),
                                0.72f to Color(0xFFF9A825).copy(alpha = 0.035f * washAlpha),
                                0.92f to Color(0xFFF9A825).copy(alpha = 0.010f * washAlpha),
                                1.00f to Color.Transparent,
                            ),
                        center = starCenter,
                        radius = maxOf(size.width, size.height) * 1.35f,
                    )
                onDrawBehind {
                    drawRect(brush)
                }
            },
    )
}

@Composable
private fun OptionRow(
    symbolName: String,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    summaryContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.appClickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summaryContent != null) {
                Spacer(Modifier.size(6.dp))
                summaryContent()
            } else {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Grid cell used by the 2-column zone of the options panel. The fixed height keeps
 * paired cells symmetrical even when a value is long; overflowing metadata is
 * ellipsized because tapping the cell opens the full editor/sheet.
 *
 * Passing a null [onClick] drops the
 * clickable modifier so archived / trashed shelves can show inert cells. Surface
 * clips its content to the rounded shape, so the click ripple stays inside the card.
 */
@Composable
private fun DotSeparatedSummary(
    parts: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val bodyStyle = MaterialTheme.typography.bodySmall
    val dotFontSize = bodyStyle.fontSize * 1.45f
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(
                    text = "·",
                    style =
                        bodyStyle.copy(
                            fontSize = dotFontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = bodyStyle.lineHeight,
                        ),
                    color = color.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    maxLines = 1,
                )
            }
            Text(
                text = part,
                style = bodyStyle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier =
                    if (index == parts.lastIndex) {
                        Modifier
                    } else {
                        Modifier.weight(1f, fill = false)
                    },
            )
        }
    }
}

@Composable
private fun ReminderOptionSummary(
    reminders: List<NoteReminder>,
    permissionMissing: Boolean,
) {
    val summaryColor =
        if (permissionMissing && reminders.isNotEmpty()) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    if (reminders.isEmpty()) {
        Text(
            text = stringResource(R.string.common_none),
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val soonest = reminders.minByOrNull { it.reminderAt } ?: return
    val reminderAt = soonest.originalReminderAt ?: soonest.reminderAt
    val recurrence = soonest.recurrence
    val summaryContext = LocalContext.current
    val datePart =
        remember(reminderAt, summaryContext) {
            android.text.format.DateFormat
                .getMediumDateFormat(summaryContext)
                .format(Date(reminderAt)) + " " +
                formatTimeOfDay(summaryContext, reminderAt)
        }
    val rule = recurrence?.sanitized()
    val recurrenceLabel = rule?.let { compactRecurrenceLabel(it) }.orEmpty()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            if (recurrenceLabel.isEmpty()) {
                Text(
                    text = datePart,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                DotSeparatedSummary(listOf(datePart, recurrenceLabel), color = summaryColor)
            }
        }
        if (reminders.size > 1) {
            Spacer(Modifier.width(4.dp))
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val greenBg = if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
            val greenText = if (isDark) Color(0xFFC8E6C9) else Color(0xFF2E7D32)
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = greenBg,
                contentColor = greenText,
            ) {
                Text(
                    text = "+${reminders.size - 1} more",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun BehaviorOptionSummary(
    visibility: NoteVisibility,
    importance: Importance,
) {
    DotSeparatedSummary(
        listOf(
            stringResource(R.string.options_behavior_visibility_label, visibility.label()),
            stringResource(R.string.options_behavior_importance_label, importance.label()),
        ),
    )
}

@Composable
private fun OptionCell(
    symbolName: String,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    icon: Drawable? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    iconTint: Color = SettingsDesignTokens.PrimaryPurple,
    fixedHeight: Boolean = false,
    summaryContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val clickableModifier =
        when {
            onClick == null -> Modifier
            onLongClick != null -> Modifier.appCombinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
            else -> Modifier.appClickable(onClick = onClick)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickableModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF4EAF6)),
            contentAlignment = Alignment.Center,
        ) {
            OptionCellIcon(
                symbolName = symbolName,
                icon = icon,
                iconTint = iconTint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF32313F),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summaryContent != null) {
                summaryContent()
            } else if (summary.isNotBlank()) {
                Text(
                    summary,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF89899C),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = Color(0xFF89899C),
                weight = FontWeight.Normal,
                filled = false,
            )
        }
    }
}

@Composable
private fun OptionCellIcon(
    symbolName: String,
    icon: Drawable?,
    iconTint: Color,
) {
    val painter =
        remember(icon) {
            icon?.let {
                runCatching {
                    BitmapPainter(it.toBitmap(96, 96).asImageBitmap())
                }.getOrNull()
            }
        }
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    } else {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 20.dp,
            tint = iconTint,
            weight = FontWeight.Normal,
            filled = false,
        )
    }
}

@Composable
fun MediaStorageChip(
    storedInApp: Boolean,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            if (storedInApp) {
                R.string.media_storage_stored_in_app
            } else {
                R.string.media_storage_linked_original
            },
        )
    val statusContentDescription = stringResource(R.string.media_storage_status_cd)
    val linkedOriginalHint = stringResource(R.string.media_storage_linked_original_hint)
    val chipContentDescription =
        if (storedInApp) {
            "$statusContentDescription: $label"
        } else {
            "$statusContentDescription: $label. $linkedOriginalHint"
        }
    Surface(
        modifier =
            modifier.semantics {
                this.contentDescription = chipContentDescription
            },
        shape = MaterialTheme.shapes.extraExtraLarge,
        color =
            if (storedInApp) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (storedInApp) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private data class ChoiceOption<T>(
    val value: T,
    val label: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BehaviorSheet(
    visibility: NoteVisibility,
    importance: Importance,
    draftVisibility: NoteVisibility,
    draftImportance: Importance,
    onDraftVisibilityChange: (NoteVisibility) -> Unit,
    onDraftImportanceChange: (Importance) -> Unit,
    onOpenHelp: ((focusSection: String) -> Unit)?,
    onConfirm: (NoteVisibility, Importance) -> Unit,
    onDismiss: () -> Unit,
) {
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }

    val hasChanges = draftVisibility != visibility || draftImportance != importance
    val sheetState =
        rememberBottomSheetStateWithUnsavedChanges(
            isDirty = hasChanges,
            onShowDialog = { showUnsavedDialog = true },
        )

    AppBottomSheet(
        title = stringResource(R.string.options_behavior),
        subtitle = stringResource(R.string.options_behavior_subtitle),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        onDismiss = {
            if (hasChanges) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        actions = {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            RememberButton(
                onClick = {
                    onConfirm(draftVisibility, draftImportance)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_done))
            }
        },
    ) {
        ChoiceSectionHeader(
            title = stringResource(R.string.options_visibility),
            helpContentDescription = stringResource(R.string.options_visibility_help_cd),
            onHelp = onOpenHelp?.let { open -> { open(HELP_FOCUS_VISIBILITY) } },
        )
        NoteVisibility.entries.forEach { option ->
            ChoiceOptionRow(
                option = ChoiceOption(option, option.label(), option.description()),
                selected = draftVisibility,
                onSelect = onDraftVisibilityChange,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        ChoiceSectionHeader(
            title = stringResource(R.string.options_importance),
            helpContentDescription = stringResource(R.string.options_importance_help_cd),
            onHelp = onOpenHelp?.let { open -> { open(HELP_FOCUS_IMPORTANCE) } },
        )
        Importance.entries.forEach { option ->
            ChoiceOptionRow(
                option = ChoiceOption(option, option.label(), option.description()),
                selected = draftImportance,
                onSelect = onDraftImportanceChange,
            )
        }
    }

    if (showUnsavedDialog) {
        RememberUnsavedChangesDialog(
            onConfirm = {
                showUnsavedDialog = false
                onDismiss()
            },
            onDismiss = { showUnsavedDialog = false },
        )
    }
}

@Composable
private fun ChoiceSectionHeader(
    title: String,
    helpContentDescription: String,
    onHelp: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 4.dp),
        )
        if (onHelp != null) {
            // A compact form of the "?" button in Settings' top bar.
            RememberFilledTonalIconButton(
                onClick = onHelp,
                modifier =
                    Modifier
                        .size(32.dp)
                        .semantics { contentDescription = helpContentDescription },
                tooltipLabel = helpContentDescription,
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceOptionRow(
    option: ChoiceOption<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable { onSelect(option.value) }
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = option.value == selected,
            onClick = { onSelect(option.value) },
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Importance.label(): String = stringResource(labelRes())

@Composable
private fun Importance.description(): String =
    stringResource(
        when (this) {
            Importance.LOW -> R.string.importance_low_description
            Importance.DEFAULT -> R.string.importance_default_description
            Importance.HIGH -> R.string.importance_high_description
            Importance.CRITICAL -> R.string.importance_critical_description
        },
    )

@Composable
private fun NoteVisibility.label(): String = stringResource(labelRes())

@Composable
private fun NoteVisibility.description(): String =
    stringResource(
        when (this) {
            NoteVisibility.DEFAULT -> R.string.visibility_default_description
            NoteVisibility.PRIVATE -> R.string.visibility_private_description
            NoteVisibility.SECRET -> R.string.visibility_secret_description
        },
    )

/**
 * Short, human-readable recurrence label for the reminder option summary line. Mirrors
 * the long-form summary the picker emits but keeps it to one or two words ("Daily",
 * "Every 3 weeks", "Monthly") so the cell stays compact.
 */
@Composable
private fun compactRecurrenceLabel(rule: RecurrenceRule): String {
    val interval = rule.interval.coerceAtLeast(1)
    return if (interval == 1) {
        when (rule.unit) {
            RecurrenceUnit.HOUR -> stringResource(R.string.reminder_recurrence_hourly)
            RecurrenceUnit.DAY -> stringResource(R.string.reminder_recurrence_daily)
            RecurrenceUnit.WEEK -> stringResource(R.string.reminder_recurrence_weekly)
            RecurrenceUnit.MONTH -> stringResource(R.string.reminder_recurrence_monthly)
            RecurrenceUnit.YEAR -> stringResource(R.string.reminder_recurrence_yearly)
        }
    } else {
        pluralStringResource(
            when (rule.unit) {
                RecurrenceUnit.HOUR -> R.plurals.reminder_recurrence_every_hours
                RecurrenceUnit.DAY -> R.plurals.reminder_recurrence_every_days
                RecurrenceUnit.WEEK -> R.plurals.reminder_recurrence_every_weeks
                RecurrenceUnit.MONTH -> R.plurals.reminder_recurrence_every_months
                RecurrenceUnit.YEAR -> R.plurals.reminder_recurrence_every_years
            },
            interval,
            interval,
        )
    }
}

@Composable
private fun actionsSummary(actions: List<NoteAction>): String =
    when (actions.size) {
        0 -> stringResource(R.string.common_none)
        1 -> {
            val action = actions[0]
            if (action.type == ActionType.OPEN_LINK) {
                action.details.cleanLinkForOptionsSummary()
            } else {
                action.title.ifBlank {
                    pluralStringResource(R.plurals.options_actions_count, 1, 1)
                }
            }
        }
        else -> pluralStringResource(R.plurals.options_actions_count, actions.size, actions.size)
    }

private fun String.cleanLinkForOptionsSummary(): String =
    trim()
        .removePrefixIgnoreCase("https://")
        .removePrefixIgnoreCase("http://")
        .removePrefixIgnoreCase("www.")
        .trimEnd('/')

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) {
        drop(prefix.length)
    } else {
        this
    }

private fun actionOptionsIcon(
    context: Context,
    action: NoteAction,
): Drawable? =
    when (action.type) {
        ActionType.OPEN_APP -> appIcon(context.packageManager, action.details)
        ActionType.OPEN_SHORTCUT ->
            action.iconData.toNoteActionIconDrawable(context.resources)
                ?: shortcutFallbackIcon(context.packageManager, action.details)
        else -> null
    }

private fun appIcon(
    packageManager: PackageManager,
    packageName: String,
): Drawable? =
    if (packageName.isBlank()) {
        null
    } else {
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

private fun shortcutFallbackIcon(
    packageManager: PackageManager,
    intentUri: String,
): Drawable? {
    if (intentUri.isBlank()) return null
    val intent =
        runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
            .getOrNull() ?: return null
    val packageName =
        intent.component?.packageName
            ?: intent.`package`
            ?: intent.resolveActivity(packageManager)?.packageName
    return packageName?.let { appIcon(packageManager, it) }
}

@Composable
private fun attachmentStorageSummary(
    fileSummary: String,
    storedCount: Int,
    linkedCount: Int,
): String {
    val storedSummary =
        pluralStringResource(
            R.plurals.media_storage_stored_count,
            storedCount,
            storedCount,
        )
    val linkedSummary =
        pluralStringResource(
            R.plurals.media_storage_linked_count,
            linkedCount,
            linkedCount,
        )
    return stringResource(
        R.string.options_attachments_storage_summary,
        fileSummary,
        storedSummary,
        linkedSummary,
    )
}

@Composable
private fun attachmentsSummary(count: Int): String =
    when (count) {
        0 -> stringResource(R.string.common_none)
        else -> pluralStringResource(R.plurals.options_attachments_file_count, count, count)
    }
