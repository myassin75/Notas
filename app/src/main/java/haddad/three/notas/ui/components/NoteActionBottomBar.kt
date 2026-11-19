@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package haddad.three.notas.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.common.isSmallLandscape
import haddad.three.notas.ui.feedback.LocalHapticEnabled
import haddad.three.notas.ui.feedback.performLongPressHaptic
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Note/list action state. Exactly one of [archived]/[trashed] is expected to be true; when both
 * are false the note is in the normal "active" shelf.
 */
enum class NoteShelfState { ACTIVE, ARCHIVED, TRASHED }

/**
 * Action buttons for the Edit Note / Edit List screens, rendered as a full-width bottom bar
 * anchored to the navigation inset. The button set swaps based on [shelfState]:
 *
 *   ACTIVE   -> Edit/Done, Pin, Star, Notification, Mark done, Archive, Trash
 *   ARCHIVED -> Unarchive, Trash
 *   TRASHED  -> Restore, Delete forever
 *
 * Each action is an icon stacked above its label. [visible] gates the slide-in/slide-out
 * animation. Callers flip it to false when the IME is up (the rich-text toolbar takes over)
 * or when the content has been scrolled away from the top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteActionBottomBar(
    shelfState: NoteShelfState,
    existing: Boolean,
    isEditMode: Boolean,
    pinned: Boolean,
    completed: Boolean,
    visible: Boolean,
    onToggleEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleCompleted: () -> Unit,
    onArchive: () -> Unit,
    onNotification: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    showEditAction: Boolean = true,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = spatialSpec, initialOffsetY = { it }) + fadeIn(animationSpec = fadeInSpec),
        exit = slideOutVertically(animationSpec = spatialSpec, targetOffsetY = { it }) + fadeOut(animationSpec = fadeOutSpec),
        modifier = modifier,
    ) {
        NoteActionBottomBarContent(
            shelfState = shelfState,
            existing = existing,
            isEditMode = isEditMode,
            pinned = pinned,
            completed = completed,
            onToggleEdit = onToggleEdit,
            onTogglePin = onTogglePin,
            onToggleCompleted = onToggleCompleted,
            onArchive = onArchive,
            onNotification = onNotification,
            onUnarchive = onUnarchive,
            onTrash = onTrash,
            onRestore = onRestore,
            onDeleteForever = onDeleteForever,
            showEditAction = showEditAction,
        )
    }
}

/**
 * Static content of the action bottom bar (no visibility animation wrapping).
 *
 * Extracted from [NoteActionBottomBar] so callers that want to drive the show/hide
 * transition themselves (e.g. a parent [androidx.compose.animation.AnimatedContent]
 * that swaps this bar with a format toolbar) can mount the content directly without
 * fighting a nested `AnimatedVisibility`. The original [NoteActionBottomBar] keeps
 * its `AnimatedVisibility` wrapper for callers that just want a scroll-hide bar.
 */
@Composable
fun NoteActionBottomBarContent(
    shelfState: NoteShelfState,
    existing: Boolean,
    isEditMode: Boolean,
    pinned: Boolean,
    completed: Boolean,
    onToggleEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleCompleted: () -> Unit,
    onArchive: () -> Unit,
    onNotification: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    showEditAction: Boolean = false,
) {
    var overflowMenuOpen by remember { mutableStateOf(false) }
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var isPressedAnim by remember { mutableStateOf(false) }

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressedAnim) 0.88f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "doneButtonScale",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (completed) 1.25f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "doneIconScale",
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (completed) 360f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "doneIconRotation",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val targetBackgroundColor = if (completed) Color(0xFF1E8E3E) else primaryColor
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "doneButtonColor",
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (completed) 4.dp else 1.dp,
        animationSpec = tween(durationMillis = 250),
        label = "doneButtonElevation",
    )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Pin
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTogglePin() }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (pinned) "push_pin" else "push_pin",
                    size = 20.dp,
                    tint = onSurface,
                    weight = FontWeight.Normal,
                    filled = pinned,
                )
                Text(
                    text = "Pin",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = onSurface,
                        ),
                )
            }

            // 2. Notify
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNotification() }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "notifications",
                    size = 20.dp,
                    tint = onSurface,
                    weight = FontWeight.Normal,
                    filled = false,
                )
                Text(
                    text = "Notify",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = onSurface,
                        ),
                )
            }

            // 3. Done (Pill Button with noticeable state change and dynamic spring click feedback)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = backgroundColor,
                shadowElevation = shadowElevation,
                modifier =
                    Modifier
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }.clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (hapticEnabled) view.performLongPressHaptic()
                            isPressedAnim = true
                            coroutineScope.launch {
                                delay(120)
                                isPressedAnim = false
                            }
                            onToggleCompleted()
                        },
            ) {
                val pillForeground = if (completed) Color.White else MaterialTheme.colorScheme.onPrimary
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check_circle",
                        size = 18.dp,
                        tint = pillForeground,
                        weight = FontWeight.Medium,
                        filled = completed,
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                    rotationZ = iconRotation
                                },
                    )
                    AnimatedContent(
                        targetState = completed,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(220)) +
                                    scaleIn(initialScale = 0.85f)
                            ) togetherWith
                                (
                                    fadeOut(animationSpec = tween(180)) +
                                        scaleOut(targetScale = 1.15f)
                                )
                        },
                        label = "doneButtonTextAnim",
                    ) { isCompleted ->
                        Text(
                            text =
                                if (isCompleted) {
                                    stringResource(R.string.search_filter_status_completed)
                                } else {
                                    stringResource(R.string.edit_bottom_bar_mark_done)
                                },
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = pillForeground,
                                ),
                        )
                    }
                }
            }

            // 4. Overflow Menu Button (...)
            Box {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { overflowMenuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val radius = 2.2.dp.toPx()
                        val dotColor = onSurface
                        val centerY = size.height / 2f
                        val spacing = 5.5.dp.toPx()
                        val centerX = size.width / 2f

                        drawCircle(dotColor, radius, Offset(centerX - spacing, centerY))
                        drawCircle(dotColor, radius, Offset(centerX, centerY))
                        drawCircle(dotColor, radius, Offset(centerX + spacing, centerY))
                    }
                }

                // Floating Menu Card
                DropdownMenu(
                    expanded = overflowMenuOpen,
                    onDismissRequest = { overflowMenuOpen = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    // Row 1: Archive
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    overflowMenuOpen = false
                                    onArchive()
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "archive",
                            size = 20.dp,
                            tint = onSurface,
                            weight = FontWeight.Normal,
                            filled = false,
                        )
                        Text(
                            text = "Archive",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = onSurface,
                                ),
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    // Row 2: Trash
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    overflowMenuOpen = false
                                    onTrash()
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.error,
                            weight = FontWeight.Normal,
                            filled = false,
                        )
                        Text(
                            text = "Trash",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.error,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard action item: icon above label, tappable column with rounded-rect ripple.
 * Width is fixed by default; callers can pass weighted modifiers when a smaller action
 * set should share the whole bar.
 */
@Composable
private fun ActionItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconScale: Float = 1f,
    compact: Boolean = false,
) {
    val clickableModifier =
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    if (compact) {
        Row(
            modifier = clickableModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = icon,
                size = 19.dp,
                tint = iconTint,
                weight = FontWeight.Medium,
                modifier = Modifier.scale(iconScale),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Column(
        modifier = clickableModifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = icon,
            size = 24.dp,
            tint = iconTint,
            weight = FontWeight.Medium,
            modifier = Modifier.scale(iconScale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // Ellipsis rather than a hard clip, matching the Star / Pin / Done items. Without
            // it an over-long label is cut mid-word, which reads as a broken row rather than a
            // truncated word.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Edit/Done toggle: animates the icon + label between the two states with a shared motion spec,
 * with save haptics only when leaving edit mode.
 */
@Composable
private fun EditActionItem(
    isEditMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val clickableModifier =
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClick = {
                    if (isEditMode) {
                        if (hapticEnabled) view.performLongPressHaptic()
                    }
                    onClick()
                },
            )
    val iconContent: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedContent(
            targetState = isEditMode,
            label = "bottomBarEditIcon",
            transitionSpec = {
                androidx.compose.animation.scaleIn(animationSpec = effectsSpec) togetherWith
                    androidx.compose.animation.scaleOut(animationSpec = effectsSpec)
            },
        ) { editing ->
            RememberMaterialRoundedSymbol(
                name = if (editing) "done" else "edit",
                size = if (compact) 19.dp else 24.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
    }
    val labelContent: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedContent(
            targetState = isEditMode,
            label = "bottomBarEditLabel",
            transitionSpec = {
                androidx.compose.animation.fadeIn(animationSpec = effectsSpec) togetherWith
                    androidx.compose.animation.fadeOut(animationSpec = effectsSpec)
            },
        ) { editing ->
            Text(
                text =
                    stringResource(
                        if (editing) R.string.edit_bottom_bar_done else R.string.edit_bottom_bar_edit,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (compact) {
        Row(
            modifier = clickableModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            Spacer(Modifier.width(3.dp))
            labelContent()
        }
    } else {
        Column(
            modifier = clickableModifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent()
            Spacer(Modifier.height(4.dp))
            labelContent()
        }
    }
}

/**
 * Mark done / not done. Toggle pulses on activate (scale bump + primary tint), and
 * the FILL=1 vs FILL=0 variant of `check_circle` flips with completion state so the
 * affordance reads visually as well as via label. Recurrence is handled at the call
 * site - the VM routes the click through repository.markCompleted, which in turn
 * advances rather than completes a recurring note.
 */
@Composable
private fun DoneActionItem(
    completed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val colorEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    var pulsing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.35f else 1f,
        animationSpec = effectsSpec,
        finishedListener = { if (pulsing) pulsing = false },
        label = "bottomBarDoneScale",
    )
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurface
    val tint by animateColorAsState(
        targetValue = if (pulsing || completed) activeTint else inactiveTint,
        animationSpec = colorEffectsSpec,
        label = "bottomBarDoneColor",
    )
    val clickableModifier =
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClick = {
                    if (!completed) {
                        if (hapticEnabled) view.performLongPressHaptic()
                        pulsing = true
                    }
                    onClick()
                },
            )
    val iconContent: @Composable () -> Unit = {
        RememberMaterialRoundedSymbol(
            name = "check_circle",
            filled = completed,
            size = if (compact) 19.dp else 24.dp,
            tint = tint,
            weight = FontWeight.Medium,
            modifier = Modifier.scale(scale),
        )
    }
    val labelContent: @Composable () -> Unit = {
        Text(
            text =
                stringResource(
                    if (completed) {
                        R.string.edit_bottom_bar_mark_not_done
                    } else {
                        R.string.edit_bottom_bar_mark_done
                    },
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (compact) {
        Row(
            modifier = clickableModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            Spacer(Modifier.width(3.dp))
            labelContent()
        }
    } else {
        Column(
            modifier = clickableModifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent()
            Spacer(Modifier.height(4.dp))
            labelContent()
        }
    }
}

/**
 * Pin toggle: same pulse choreography as [StarActionItem] and [DoneActionItem], but tinted with
 * the theme's tertiary colour and using the `push_pin` FILL toggle. Deliberately NOT yellow and
 * not star-shaped - pin (placement on Home) and star (favorite + widget) are different features
 * and must not be mistaken for each other.
 */
@Composable
private fun PinActionItem(
    pinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val colorEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    var pulsing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.35f else 1f,
        animationSpec = effectsSpec,
        finishedListener = { if (pulsing) pulsing = false },
        label = "bottomBarPinScale",
    )
    val activeTint = MaterialTheme.colorScheme.tertiary
    val tint by animateColorAsState(
        targetValue = if (pulsing || pinned) activeTint else MaterialTheme.colorScheme.onSurface,
        animationSpec = colorEffectsSpec,
        label = "bottomBarPinColor",
    )
    val clickableModifier =
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClick = {
                    if (!pinned) {
                        if (hapticEnabled) view.performLongPressHaptic()
                        pulsing = true
                    }
                    onClick()
                },
            )
    val iconContent: @Composable () -> Unit = {
        // Fill swap alongside the tint change, so the state reads without relying on colour.
        // Upright, unlike the tilted badge in GroupHeader: in a row of upright toolbar glyphs a
        // rotated pin reads as crooked rather than as deliberate.
        RememberMaterialRoundedSymbol(
            name = "push_pin",
            filled = pinned,
            size = if (compact) 19.dp else 24.dp,
            tint = tint,
            weight = FontWeight.Medium,
            modifier = Modifier.scale(scale),
        )
    }
    val labelContent: @Composable () -> Unit = {
        Text(
            text =
                stringResource(
                    if (pinned) R.string.edit_bottom_bar_unpin else R.string.edit_bottom_bar_pin,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (compact) {
        Row(
            modifier = clickableModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            Spacer(Modifier.width(3.dp))
            labelContent()
        }
    } else {
        Column(
            modifier = clickableModifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent()
            Spacer(Modifier.height(4.dp))
            labelContent()
        }
    }
}
