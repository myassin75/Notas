package haddad.three.notas.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.data.InteractionState
import haddad.three.notas.data.NoteLayoutMode
import haddad.three.notas.data.NoteSwipeAction
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.ui.common.RememberInlineSearchField
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.NoteCardUiModel
import haddad.three.notas.ui.components.RememberFilledTonalIconButton
import haddad.three.notas.ui.components.SwipeableRememberNoteCard
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import haddad.three.notas.ui.theme.semanticPalette

/**
 * Top-bar title that doubles as the search entry point. Renders a single Row of:
 *
 *   [animated content area (weight 1)] [layout icon button] [expand/collapse-all icon button] [search/close icon button]
 *
 * The animated content area crossfades between empty space and [RememberInlineSearchField].
 * Only the layout/expand-collapse [AnimatedVisibility] below drives horizontal width
 * changes; giving this slot its own expand/shrink as well used to fight that animation
 * and made the search bar appear to slide in from inconsistent directions.
 *
 * Intended call site: the LargeTopAppBar's `title` slot. The `actions` slot is left
 * for selection-mode chrome only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchableTopBarTitle(
    searchOpen: Boolean,
    searchFocusRequestKey: Int,
    query: String,
    noteLayoutMode: NoteLayoutMode,
    showLayoutToggle: Boolean,
    layoutToggleEnabled: Boolean,
    showExpandCollapseAllToggle: Boolean,
    allSectionsCollapsed: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onToggleExpandCollapseAll: () -> Unit,
    onToggleSearch: () -> Unit,
    onCreateNote: () -> Unit = {},
) {
    Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (searchOpen) {
                    RememberInlineSearchField(
                        focusRequestKey = searchFocusRequestKey,
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholderText = stringResource(R.string.home_search_placeholder),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onToggleSearch() }
                                .semantics { contentDescription = "Close search" },
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            weight = FontWeight.Normal,
                            filled = false,
                        )
                    }
                } else {
                    Text(
                        text = "Notas",
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val isDark = LocalIsDark.current
                        val semantic = MaterialTheme.semanticPalette

                        // Search icon button
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isDark) {
                                            Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                                    shape = CircleShape,
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                    shape = CircleShape,
                                                )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { onToggleSearch() }
                                    .semantics { contentDescription = "Search" },
                            contentAlignment = Alignment.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "search",
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        }

                        // + Add button
                        Surface(
                            shape = CircleShape,
                            color = semantic.primaryAction,
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .clickable { onCreateNote() }
                                    .semantics { contentDescription = "Add note" },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "add",
                                    size = 18.dp,
                                    tint = semantic.primaryActionForeground,
                                    weight = FontWeight.Bold,
                                    filled = false,
                                )
                                Text(
                                    text = "Add",
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = semantic.primaryActionForeground,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
}

internal enum class SectionBadgeStyle { ARCHIVE, TRASH }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchSectionPillDivider(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spatialSpec,
        label = "sectionChevron",
    )
    val contentDescriptionExpand = stringResource(R.string.section_expand_cd, label)
    val contentDescriptionCollapse = stringResource(R.string.section_collapse_cd, label)
    val pillBackground =
        if (muted) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val labelColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val countBackground =
        if (muted) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val countColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier =
                Modifier
                    .background(pillBackground, MaterialTheme.shapes.extraExtraLarge)
                    .semantics {
                        contentDescription =
                            if (expanded) {
                                contentDescriptionCollapse
                            } else {
                                contentDescriptionExpand
                            }
                    }.appClickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            Box(
                modifier =
                    Modifier
                        .background(countBackground, MaterialTheme.shapes.extraExtraLarge)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = countColor,
                )
            }
            RememberMaterialRoundedSymbol(
                name = "expand_more",
                size = 18.dp,
                tint = labelColor,
                weight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

@Composable
internal fun StateBadgedNoteCard(
    note: NoteWithItems,
    model: NoteCardUiModel,
    interaction: InteractionState,
    onOpen: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    badgeText: String,
    badgeStyle: SectionBadgeStyle,
    modifier: Modifier = Modifier,
    reminderNotificationsAllowed: Boolean = true,
) {
    val backgroundColor =
        when (badgeStyle) {
            SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.secondaryContainer
            SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.errorContainer
        }
    val foregroundColor =
        when (badgeStyle) {
            SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.onSecondaryContainer
            SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.onErrorContainer
        }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.graphicsLayer { alpha = 0.88f }) {
            SwipeableRememberNoteCard(
                note = note,
                model = model,
                interaction = interaction,
                onOpenNote = onOpen,
                onSwipeAction = onSwipeAction,
                swipeEnabled = false,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .background(backgroundColor, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = foregroundColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
