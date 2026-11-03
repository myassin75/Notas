package haddad.three.notas.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import haddad.three.notas.R
import haddad.three.notas.ui.common.MarkdownText
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberFilledTonalIconButton
import haddad.three.notas.ui.components.rememberResponsiveActionButtonSize
import haddad.three.notas.ui.components.settings.GroupPosition
import haddad.three.notas.ui.components.settings.GroupedListItem
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.modifiers.applyToScrollableList
import haddad.three.notas.ui.modifiers.rememberProgressiveBlurStyle
import haddad.three.notas.ui.settings.SettingsDesignTokens
import haddad.three.notas.ui.settings.SettingsSectionCard
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import haddad.three.notas.ui.theme.transparentLargeTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenAppSection: (sectionKey: String) -> Unit,
    helpVm: HelpViewModel,
    initialFocusSectionId: String = "",
) {
    val expandedKeys by helpVm.expandedKeys.collectAsStateWithLifecycle()
    val searchQuery by helpVm.searchQuery.collectAsStateWithLifecycle()
    val filteredSections by helpVm.filteredSections.collectAsStateWithLifecycle()

    val allSubsectionKeys =
        remember(filteredSections) {
            filteredSections.flatMap { s -> s.subsections.map { "${s.title}/${it.title}" } }
        }
    val allExpanded = allSubsectionKeys.isNotEmpty() && allSubsectionKeys.all { it in expandedKeys }

    val initialScrollIndex = helpVm.scrollIndex
    val initialScrollOffset = helpVm.scrollOffset
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialScrollIndex,
            initialFirstVisibleItemScrollOffset = initialScrollOffset,
        )
    val density = LocalDensity.current
    LaunchedEffect(scrollBehavior.state.heightOffsetLimit) {
        val heightOffsetLimit = scrollBehavior.state.heightOffsetLimit
        val restoredAwayFromTop = initialScrollIndex > 0 || initialScrollOffset > 0
        if (restoredAwayFromTop && heightOffsetLimit != 0f) {
            val collapseFraction =
                if (initialScrollIndex > 0) {
                    1f
                } else {
                    val thresholdPx = with(density) { 24.dp.toPx() }
                    (initialScrollOffset.toFloat() / thresholdPx).coerceIn(0f, 1f)
                }
            scrollBehavior.state.heightOffset = heightOffsetLimit * collapseFraction
        }
    }
    val topAlphaMultiplier by remember(listState) {
        derivedStateOf {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            if (listState.firstVisibleItemIndex > 0) {
                collapsedFraction
            } else {
                val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                val scrollFraction = (offsetPx / thresholdPx).coerceIn(0f, 1f)
                collapsedFraction * scrollFraction
            }
        }
    }
    val blurStyle =
        rememberProgressiveBlurStyle(
            bottomExtra = 0.dp,
            topExtra = 76.dp,
            topBlurProgressPower = 1.1f,
        )
    val blurMod =
        blurStyle?.applyToScrollableList(topAlphaMultiplier = topAlphaMultiplier)
            ?: Modifier
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        onDispose { helpVm.saveScrollState(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    // Opened at a subsection: expand it and bring it to the top, once per visit. The flag is
    // saveable so rotating keeps the user's own scrolling instead of jumping back. Help keeps
    // its search between visits, so an old query is cleared first and the focus waits for the
    // unfiltered list.
    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialFocusSectionId, searchQuery, filteredSections) {
        if (focusHandled || initialFocusSectionId.isEmpty()) return@LaunchedEffect
        if (searchQuery.isNotEmpty()) {
            helpVm.setSearchQuery("")
            return@LaunchedEffect
        }
        if (filteredSections !== helpVm.sections) return@LaunchedEffect
        val target =
            helpFocusSubsectionTitles[initialFocusSectionId]
                ?.let { title -> helpFocusTarget(filteredSections, title) }
        if (target != null) {
            helpVm.setExpanded(target.expandedKey, true)
            listState.scrollToItem(target.listIndex)
        }
        // Set only once the scroll is done, so a run cancelled midway is retried. FilePipe's
        // FaqScreen guards its focus the same way.
        focusHandled = true
        if (target == null) return@LaunchedEffect
        // Scrolling in code bypasses the app bar's scroll connection; collapse it to match, as
        // the scroll restore above does.
        val heightOffsetLimit = snapshotFlow { scrollBehavior.state.heightOffsetLimit }.first { it != 0f }
        scrollBehavior.state.heightOffset = heightOffsetLimit
    }
    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDesignTokens.ScreenBg,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = SettingsDesignTokens.CardBg,
                        border = BorderStroke(1.dp, SettingsDesignTokens.DividerColor),
                        modifier =
                            Modifier
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = "arrow_back",
                                autoMirror = true,
                                size = 20.dp,
                                tint = SettingsDesignTokens.TextPrimary,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        }
                    }
                },
                title = { Text(stringResource(R.string.help_title), color = SettingsDesignTokens.TextPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    val expandCollapseLabel =
                        stringResource(
                            if (allExpanded) R.string.help_collapse_all_cd else R.string.help_expand_all_cd,
                        )
                    Surface(
                        shape = CircleShape,
                        color = SettingsDesignTokens.CardBg,
                        border = BorderStroke(1.dp, SettingsDesignTokens.DividerColor),
                        modifier =
                            Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (allExpanded) {
                                        helpVm.collapseAll(allSubsectionKeys)
                                    } else {
                                        helpVm.expandAll(allSubsectionKeys)
                                    }
                                },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = if (allExpanded) "unfold_less" else "unfold_more",
                                size = 20.dp,
                                tint = SettingsDesignTokens.TextPrimary,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(blurMod),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search") {
                Surface(
                    shape = CircleShape,
                    color = SettingsDesignTokens.CardBg,
                    border = BorderStroke(1.dp, SettingsDesignTokens.DividerColor),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = helpVm::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(R.string.help_search_placeholder),
                                color = SettingsDesignTokens.TextSecondary,
                            )
                        },
                        leadingIcon = {
                            RememberMaterialRoundedSymbol(
                                name = "search",
                                size = 20.dp,
                                tint = SettingsDesignTokens.PrimaryPurple,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .appClickable { helpVm.setSearchQuery("") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "close",
                                        size = 18.dp,
                                        tint = SettingsDesignTokens.TextSecondary,
                                        weight = FontWeight.Normal,
                                        filled = false,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = SettingsDesignTokens.PrimaryPurple,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                    )
                }
            }

            if (filteredSections.isEmpty()) {
                item(key = "no_results") {
                    Text(
                        text = stringResource(R.string.help_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettingsDesignTokens.TextSecondary,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
            }

            filteredSections.forEach { section ->
                item(key = "label_${section.title}") {
                    Text(
                        text = section.title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SettingsDesignTokens.PrimaryPurple,
                            ),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
                    )
                }
                itemsIndexed(
                    items = section.subsections,
                    key = { _, sub -> "sub_${section.title}_${sub.title}" },
                ) { index, subsection ->
                    val key = "${section.title}/${subsection.title}"
                    val isExpanded = key in expandedKeys
                    val actions = helpSubsectionActions[subsection.title] ?: emptyList()
                    HelpSubsectionCard(
                        subsection = subsection,
                        isExpanded = isExpanded,
                        onToggle = { helpVm.setExpanded(key, !isExpanded) },
                        actions = actions,
                        onOpenAppSection = onOpenAppSection,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HelpSubsectionCard(
    subsection: HelpSubsection,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: List<HelpAction>,
    onOpenAppSection: (sectionKey: String) -> Unit,
) {
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "help_chevron_rotation",
    )

    val contentDescriptionExpand = stringResource(R.string.section_expand_cd, subsection.title)
    val contentDescriptionCollapse = stringResource(R.string.section_collapse_cd, subsection.title)

    SettingsSectionCard(
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                if (!isExpanded) contentDescriptionExpand else contentDescriptionCollapse
                        }.clickable {
                            onToggle()
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = subsection.title,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = SettingsDesignTokens.TextPrimary,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SettingsDesignTokens.SoftLavender),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        autoMirror = true,
                        size = 18.dp,
                        tint = SettingsDesignTokens.PrimaryPurple,
                        weight = FontWeight.Normal,
                        filled = false,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter =
                    expandVertically(
                        animationSpec = spatialSpec,
                        expandFrom = Alignment.Top,
                    ) + fadeIn(fadeInSpec),
                exit =
                    shrinkVertically(
                        animationSpec = spatialSpec,
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(fadeOutSpec),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = SettingsDesignTokens.DividerColor, thickness = 1.dp)
                    MarkdownText(
                        markdown = subsection.body,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = SettingsDesignTokens.TextSecondary,
                                fontSize = 14.sp,
                            ),
                    )
                    if (actions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            actions.forEach { action ->
                                Surface(
                                    shape = CircleShape,
                                    color = SettingsDesignTokens.SoftLavender,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                when (action) {
                                                    is HelpAction.OpenAppSection -> onOpenAppSection(action.sectionKey)
                                                }
                                            },
                                ) {
                                    Text(
                                        text = stringResource(action.labelRes),
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = SettingsDesignTokens.PrimaryPurple,
                                                fontSize = 13.sp,
                                            ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
