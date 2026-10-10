package three.haddad.smartnotas.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.ui.common.HeroFramedImage
import three.haddad.smartnotas.ui.common.HeroFraming
import three.haddad.smartnotas.ui.common.MarkdownText
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol

import three.haddad.smartnotas.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import three.haddad.smartnotas.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import three.haddad.smartnotas.ui.edit.NoteIcon
import three.haddad.smartnotas.ui.feedback.appClickable
import three.haddad.smartnotas.ui.feedback.appCombinedClickable
import three.haddad.smartnotas.ui.nav.noteMorphContainer
import three.haddad.smartnotas.ui.theme.LocalHeroOnCards
import three.haddad.smartnotas.ui.theme.LocalShowNoteContentOnCards
import three.haddad.smartnotas.ui.theme.MorphPolygonShape
import three.haddad.smartnotas.ui.theme.elevatedCardColors
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Backward-compatible wrapper that accepts the raw [NoteWithItems] and converts it to
 * the immutable [NoteCardUiModel] before delegating to the model-based composable.
 * Prefer the [NoteCardUiModel] overload from new call sites so the UI tree stays
 * skippable on identical data.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteCard(
    note: NoteWithItems,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    activeInDetailPane: Boolean = false,
    reminderNotificationsAllowed: Boolean = true,
) {
    NoteCard(
        model = remember(note) { note.toNoteCardUiModel() },
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        selected = selected,
        activeInDetailPane = activeInDetailPane,
        reminderNotificationsAllowed = reminderNotificationsAllowed,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteCard(
    model: NoteCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    activeInDetailPane: Boolean = false,
    reminderNotificationsAllowed: Boolean = true,
) {
    val cardColors = elevatedCardColors()
    val heroEnabled = LocalHeroOnCards.current
    val showNoteContent = LocalShowNoteContentOnCards.current
    val heroPictureUri = model.pictureUri?.takeIf { heroEnabled }
    val showHero = heroPictureUri != null
    val surface = MaterialTheme.colorScheme.surface
    
    val cardContentColor = cardColors.contentColor
    val heroScrimColor = if (showHero) Color.Black else surface
    val cardShape = MaterialTheme.shapes.medium
    val pinnedIconDescription = stringResource(R.string.notecard_pinned_cd)

    // Pre-resolve every fragment of the merged TalkBack announcement so the body of
    // [remember] below can stay context-free.
    val cdNotePrefix = stringResource(R.string.notecard_cd_note, model.title)
    val cdListPrefix = stringResource(R.string.notecard_cd_list, model.title)
    val cdSeparator = stringResource(R.string.notecard_cd_separator)
    val cdSelected = stringResource(R.string.notecard_cd_selected)
    val cdCompleted = stringResource(R.string.notecard_cd_completed)
    val cdReminderForAnnouncement = stringResource(R.string.notecard_reminder_cd)
    val cdRecurringForAnnouncement = stringResource(R.string.notecard_recurring_cd)
    val cdPictureForAnnouncement = stringResource(R.string.notecard_picture_cd)
    val cdAttachmentForAnnouncement = stringResource(R.string.notecard_attachment_cd)
    val noteCardAnnouncement =
        remember(
            model.kind,
            model.title,
            model.body,
            model.completed,
            model.pinned,
            model.reminderAt,
            model.recurring,
            model.pictureUri,
            model.hasAttachment,
            selected,
            showNoteContent,
        ) {
            buildList {
                add(if (model.kind == NoteKind.LIST) cdListPrefix else cdNotePrefix)
                if (selected) add(cdSelected)
                if (model.completed) add(cdCompleted)
                // Body preview is meaningful for note cards; list cards visually show
                // checklist items, not the body, so we skip it for those. Hidden entirely
                // when the user turns off note content on cards.
                if (showNoteContent && model.kind != NoteKind.LIST && model.body.isNotBlank()) {
                    val firstLine =
                        model.body.lineSequence().firstOrNull { it.isNotBlank() }
                            ?: model.body
                    add(firstLine.trim().take(120))
                }
                if (model.reminderAt != null) add(cdReminderForAnnouncement)
                if (model.recurring) add(cdRecurringForAnnouncement)
                if (model.pinned) add(pinnedIconDescription)
                if (model.pictureUri != null) add(cdPictureForAnnouncement)
                if (model.hasAttachment) add(cdAttachmentForAnnouncement)
            }.joinToString(cdSeparator)
        }

    val sharedModifier = Modifier.noteMorphContainer(model.id)

    // Selection and Completed progresses are both Animatables (initialized at 0 +
    // LaunchedEffect that animates to the current target) instead of
    // animateFloatAsState. animateFloatAsState's initial value equals its first
    // targetValue, which would skip the bloom whenever a card mounts already-selected
    // or already-completed -- typical when LazyColumn briefly evicts a row at the
    // visible boundary as the bottom selection action bar slides in or the Done
    // section header reflows the list. The pattern below tweens from 0 in those
    // remount cases and survives same-key recomposition normally.
    val animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val selectionAnimatable = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        selectionAnimatable.animateTo(
            targetValue = if (selected) 1f else 0f,
            animationSpec = animationSpec,
        )
    }
    val selectionProgress = selectionAnimatable.value

    val completedAnimatable = remember { Animatable(0f) }
    LaunchedEffect(model.completed) {
        completedAnimatable.animateTo(
            targetValue = if (model.completed) 1f else 0f,
            animationSpec = animationSpec,
        )
    }
    val completedProgress = completedAnimatable.value
    // 1.0 when active -> 0.65 when fully completed; tweens with completedProgress.
    val completedCardAlpha = 1f - 0.35f * completedProgress

    // 1.0 when active -> 0.70 when fully completed; tweens with completedProgress.
    val completedSaturation = 1f - 0.30f * completedProgress

    /**
     * Done-badge progress factors out the selection bloom: when a completed card is
     * selected, the selection badge owns the TopEnd corner and the inline done badge
     * fades out so the two indicators don't visually overlap. Outside selection mode
     * (selectionProgress = 0), this just equals completedProgress.
     */
    val doneBadgeProgress = completedProgress * (1f - selectionProgress)

    // Done-state saturation reduction is applied as a RenderEffect on the card layer.
    // The dim alpha itself is kept inside the opaque Surface (see content below), so
    // swipe action backgrounds cannot bleed through completed cards while dragging.
    val completedRenderEffect =
        remember(completedSaturation) {
            if (completedSaturation >= 0.999f) {
                null
            } else {
                val matrix = ColorMatrix().apply { setToSaturation(completedSaturation) }
                val androidFilter = ColorFilter.colorMatrix(matrix).asAndroidColorFilter()
                android.graphics.RenderEffect
                    .createColorFilterEffect(androidFilter)
                    .asComposeRenderEffect()
            }
        }

    val selectionBorderColor = MaterialTheme.colorScheme.primary
    val selectionBorder =
        if (selected) {
            Modifier.border(BorderStroke(2.dp, selectionBorderColor), cardShape)
        } else {
            Modifier
        }
    val activePaneBorder =
        if (activeInDetailPane && !selected) {
            Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary), cardShape)
        } else {
            Modifier
        }
    val clickableModifier =
        if (onLongClick != null) {
            Modifier.appCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
            )
        } else {
            Modifier.appClickable(onClick = onClick)
        }
    val completedContainerColor =
        cardColors.containerColor
            .copy(alpha = completedCardAlpha)
            .compositeOver(MaterialTheme.colorScheme.background)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    renderEffect = completedRenderEffect
                }.then(sharedModifier)
                .clip(cardShape)
                
                .then(activePaneBorder)
                .then(selectionBorder)
                .then(clickableModifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = noteCardAnnouncement
                },
        shape = cardShape,
        color = completedContainerColor,
        contentColor = cardContentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = completedCardAlpha
                    },
        ) {
            if (showHero) {
                HeroBackground(
                    uri = heroPictureUri,
                    framing =
                        remember(model.pictureHeroFraming) {
                            HeroFraming.fromJsonString(model.pictureHeroFraming)
                        },
                    cacheRevision = model.pictureCacheRevision,
                    scrimTop = heroScrimColor.copy(alpha = 0.28f),
                    scrimBottom = heroScrimColor.copy(alpha = 0.56f),
                    shape = cardShape,
                )
            }
            // Watermark star for starred cards. Tilted ~-15deg, low alpha, parked at
            // the top-end. It deliberately sits *behind* the selection check overlay
            // and the trash "30 days left" chip (both painted later in this Box / by
            // the call site), so those affordances always win the corner. The star is
            // ornament-only -- TalkBack ignores it because the parent Surface already
            // declares mergeDescendants and contentDescription includes "Starred".
            // matchParentSize keeps the 96dp glyph out of the card's height: otherwise
            // a compact card (title and metadata only) grows to fit the watermark.
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (val headerIcon = model.icon) {
                            is NoteIcon.Symbol ->
                                RememberMaterialRoundedSymbol(
                                    name = headerIcon.name,
                                    size = 18.dp,
                                    tint = cardContentColor,
                                    weight = FontWeight.Medium,
                                    filled = headerIcon.filled,
                                    modifier = Modifier.alpha(0.75f),
                                )
                            is NoteIcon.Drawable ->
                                Icon(
                                    painterResource(headerIcon.resId),
                                    contentDescription = null,
                                    tint = cardContentColor,
                                    modifier =
                                        Modifier
                                            .size(18.dp)
                                            .alpha(0.75f),
                                )
                            is NoteIcon.Emoji ->
                                Text(
                                    text = headerIcon.text,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                    modifier = Modifier.alpha(0.85f),
                                )
                            NoteIcon.ListPlaceholder ->
                                RememberMaterialRoundedSymbol(
                                    name = DEFAULT_LIST_HEADER_SYMBOL,
                                    size = 18.dp,
                                    tint = cardContentColor,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.alpha(0.65f),
                                )
                            NoteIcon.NotePlaceholder ->
                                RememberMaterialRoundedSymbol(
                                    name = DEFAULT_NOTE_HEADER_SYMBOL,
                                    size = 18.dp,
                                    tint = cardContentColor,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.alpha(0.65f),
                                )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    model.title.ifBlank {
                                        if (model.kind == NoteKind.NOTE) {
                                            stringResource(R.string.edit_note_title_new)
                                        } else {
                                            stringResource(R.string.edit_list_title_new)
                                        }
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration =
                                    if (model.completed) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (doneBadgeProgress > 0f) {
                            Spacer(Modifier.width(6.dp))
                            // Cookie4Sided -> Sunny morph mirrors the selection badge's
                            // visual language (small polygon + check) but uses a distinct
                            // shape pair and the tertiary color so the two states read
                            // as different. Driven by [doneBadgeProgress] -- not raw
                            // [completedProgress] -- so the badge fades out when the
                            // card is selected, ceding the corner to the selection check.
                            val completedMorph =
                                remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Clover4Leaf) }
                            val completedBadgeShape = MorphPolygonShape(completedMorph, doneBadgeProgress)
                            Box(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            val s = 0.5f + 0.5f * doneBadgeProgress
                                            scaleX = s
                                            scaleY = s
                                            alpha = doneBadgeProgress
                                        }.clip(completedBadgeShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 12.dp,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                        // Starred was previously a small inline star at the title's
                        // trailing edge. It overlapped with the top-end selection
                        // check and trash days-left chip. The starred cue now lives
                        // as a low-alpha tilted watermark in the card's top-right
                        // corner (rendered on the outer Box below) plus a subtle
                        // yellow tint on the card surface; the announcement still
                        // includes "Starred" via the parent contentDescription.
                    }
                    if (showNoteContent) {
                        Spacer(Modifier.height(6.dp))
                        when (model.kind) {
                            NoteKind.NOTE -> {
                                if (model.body.isNotBlank()) {
                                    MarkdownText(
                                        markdown = model.body,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = cardContentColor),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.alpha(0.82f),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.common_empty_note),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = cardContentColor),
                                        modifier = Modifier.alpha(0.5f),
                                    )
                                }
                            }
                            NoteKind.LIST ->
                                ChecklistPreview(
                                    items = model.checklistPreviewItems,
                                    hiddenCount = model.checklistHiddenItemCount,
                                    contentColor = cardContentColor,
                                )
                        }
                    }
                    MetadataRow(
                        model = model,
                        reminderNotificationsAllowed = reminderNotificationsAllowed,
                        contentColor = cardContentColor,
                        showPictureIndicator = !showHero,
                    )
                }
            // Selection badge: shape morph + bloom. [selectionProgress] is hoisted to
            // the top of NoteCard (alongside [completedProgress]) so the same value
            // drives both the rendering here and the [doneBadgeProgress] computation
            // that fades the inline done badge out when this badge fades in.
            if (selectionProgress > 0f) {
                val selectionMorph =
                    remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie7Sided) }
                val selectionBadgeShape = MorphPolygonShape(selectionMorph, selectionProgress)
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(9.dp)
                            .size(26.dp)
                            .graphicsLayer {
                                val s = 0.5f + 0.5f * selectionProgress
                                scaleX = s
                                scaleY = s
                                alpha = selectionProgress
                            }.clip(selectionBadgeShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 15.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        weight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}


@Composable
private fun MetadataRow(
    model: NoteCardUiModel,
    reminderNotificationsAllowed: Boolean,
    contentColor: Color,
    showPictureIndicator: Boolean,
) {
    val reminderAt = model.reminderAt
    val isRecurring = model.recurring
    val hasPicture = showPictureIndicator && model.pictureUri != null
    val hasAttachment = model.hasAttachment
    val anyMetadata = reminderAt != null || hasPicture || hasAttachment
    if (!anyMetadata) return

    Spacer(Modifier.height(10.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
            if (hasPicture) {
                val cdPicture = stringResource(R.string.notecard_picture_cd)
                RememberMaterialRoundedSymbol(
                    name = "image",
                    size = 14.dp,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdPicture }
                            .alpha(0.6f),
                )
            }
            if (hasAttachment) {
                val cdAttachment = stringResource(R.string.notecard_attachment_cd)
                RememberMaterialRoundedSymbol(
                    name = "attach_file",
                    size = 14.dp,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdAttachment }
                            .alpha(0.6f),
                )
            }
            // Reminder slot - last in the row (rightmost). Replaces the old generic
            // notification icon with a short "MMM d" date string so the user reads the
            // due date directly off the card. When the note is recurring, a tiny
            // repeat icon precedes the date as a glanceable indicator.
            if (reminderAt != null) {
                val cdReminder = stringResource(R.string.notecard_reminder_cd)
                val reminderPermissionMissing = !reminderNotificationsAllowed
                RememberMaterialRoundedSymbol(
                    name = if (reminderPermissionMissing) "notifications_off" else "notifications",
                    size = 14.dp,
                    tint =
                        if (reminderPermissionMissing) {
                            MaterialTheme.colorScheme.error
                        } else {
                            contentColor
                        },
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdReminder }
                            .alpha(if (reminderPermissionMissing) 1f else 0.68f),
                )
                if (isRecurring) {
                    val cdRecurring = stringResource(R.string.notecard_recurring_cd)
                    RememberMaterialRoundedSymbol(
                        name = "repeat",
                        size = 14.dp,
                        tint = contentColor,
                        weight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .semantics { contentDescription = cdRecurring }
                                .alpha(0.68f),
                    )
                }
                Text(
                    text = formatShortReminderDate(reminderAt),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (reminderPermissionMissing) {
                            MaterialTheme.colorScheme.error
                        } else {
                            contentColor
                        },
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .alpha(if (reminderPermissionMissing) 1f else 0.85f),
                )
            }
        }
}

/**
 * Short, glanceable due-date label used on note cards. "MMM d" by default ("Apr 27"),
 * which is readable at the small card-metadata size without consuming a year column
 * for dates the user is most likely to care about (the next 12 months). Anything
 * past that horizon falls back to "MMM yyyy" so the card never silently shows a
 * date in the wrong year.
 */
private fun formatShortReminderDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val twelveMonthsMs = 365L * 24 * 60 * 60 * 1000
    val pattern = if (kotlin.math.abs(epochMillis - now) > twelveMonthsMs) "MMM yyyy" else "MMM d"
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

@Composable
private fun ChecklistPreview(
    items: List<NoteCardChecklistItemUiModel>,
    hiddenCount: Int,
    contentColor: Color,
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.common_empty_list),
            style = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
            modifier = Modifier.alpha(0.5f),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Indent children one gutter's width to mirror the editor hierarchy.
                    if (item.depth > 0) Spacer(Modifier.width(16.dp))
                    RememberMaterialRoundedSymbol(
                        name = if (item.checked) "check_box" else "check_box_outline_blank",
                        size = 16.dp,
                        tint = contentColor,
                        weight = FontWeight.Medium,
                        modifier = Modifier.alpha(if (item.checked) 0.6f else 0.85f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.alpha(if (item.checked) 0.55f else 0.92f),
                    )
                }
                val detailPreview =
                    item.details
                        .lineSequence()
                        .firstOrNull { line -> line.isNotBlank() }
                        ?.trim()
                        .orEmpty()
                if (detailPreview.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.depth > 0) Spacer(Modifier.width(16.dp))
                        Spacer(Modifier.width(26.dp))
                        Text(
                            text = detailPreview,
                            style = MaterialTheme.typography.bodySmall.copy(color = contentColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(if (item.checked) 0.42f else 0.62f),
                        )
                    }
                }
            }
        }
        if (hiddenCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.notecard_extra_items, hiddenCount, hiddenCount),
                style = MaterialTheme.typography.labelMedium.copy(color = contentColor),
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}
@Composable
private fun BoxScope.HeroBackground(
    uri: String,
    framing: HeroFraming?,
    cacheRevision: Long,
    scrimTop: Color,
    scrimBottom: Color,
    shape: Shape,
) {
    // Cover and scrim stay inside the card's shared container, beneath its text.
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .clip(shape),
    ) {
        HeroFramedImage(
            imageUri = uri,
            framing = framing,
            cacheRevision = cacheRevision,
            imageAlpha = 1f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(colors = listOf(scrimTop, scrimBottom))),
        )
    }
}

