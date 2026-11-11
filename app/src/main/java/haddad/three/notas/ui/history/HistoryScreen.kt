package haddad.three.notas.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import haddad.three.notas.R
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.NoteLayoutMode
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ViewOptionsPrefs
import haddad.three.notas.ui.common.BulkUndoableAction
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.common.bulkActionSnackbarMessage
import haddad.three.notas.ui.common.emptyStateSpacing
import haddad.three.notas.ui.common.isLandscape
import haddad.three.notas.ui.common.noteMosaicColumnCount
import haddad.three.notas.ui.common.rememberNotificationsAllowed
import haddad.three.notas.ui.components.EmptyArchiveIllustration
import haddad.three.notas.ui.components.EmptyTrashIllustration
import haddad.three.notas.ui.components.MultiActionSwipeRevealCard
import haddad.three.notas.ui.components.NoteCard
import haddad.three.notas.ui.components.NoteCardUiModel
import haddad.three.notas.ui.components.RememberConfirmDialog
import haddad.three.notas.ui.components.RememberDropdownMenuItem
import haddad.three.notas.ui.components.RememberFilledTonalIconButton
import haddad.three.notas.ui.components.RememberToggleButton
import haddad.three.notas.ui.components.SwipeRevealTile
import haddad.three.notas.ui.components.rememberResponsiveActionButtonSize
import haddad.three.notas.ui.components.rememberSelectionActionBarListClearance
import haddad.three.notas.ui.components.toNoteCardUiModel
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.modifiers.PillBottomBarHeight
import haddad.three.notas.ui.modifiers.PillBottomScrimExtra
import haddad.three.notas.ui.modifiers.applyToScrollableList
import haddad.three.notas.ui.modifiers.rememberContentOverflowScrollEnabled
import haddad.three.notas.ui.modifiers.rememberProgressiveBlurStyle
import haddad.three.notas.data.AccentTheme
import haddad.three.notas.ui.theme.GlassVariant
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.LocalReducedMotion
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.LocalSnackbarHostState
import haddad.three.notas.ui.theme.LocalThemeState
import haddad.three.notas.ui.theme.glassContainerColor
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import javax.inject.Inject
import kotlin.math.max

/** Which shelf the user is viewing. */
enum class HistorySection { ARCHIVE, TRASH }

/** Design tokens mapped from reference image (#F5F3F8 background, #FFFFFF main surface, etc.) */
internal object HistoryDesignTokens {
    val ScreenBg = Color(0xFFF5F3F8)
    val MainSurface = Color(0xFFFFFFFF)
    val ActiveLavenderSurface = Color(0xFFFBE8EA)
    val PrimaryPurple = Color(0xFF800000)
    val PrimaryText = Color(0xFF383743)
    val SecondaryText = Color(0xFF85839C)
    val IllustrationOutline = Color(0xFFAAA3B9)
    val IllustrationCircle = Color(0xFFF2E8F7)
}

@Composable
internal fun historyScreenBg(): Color =
    if (haddad.three.notas.ui.theme.LocalIsDark.current) Color.Transparent else MaterialTheme.colorScheme.background

@Composable
internal fun historyMainSurface(): Color =
    if (haddad.three.notas.ui.theme.LocalIsDark.current) {
        glassContainerColor(GlassVariant.StandardCard, isDark = true)
    } else {
        MaterialTheme.colorScheme.surface
    }

@Composable
internal fun historyActiveLavender(): Color {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val palette = LocalSemanticPalette.current
    return if (isDark) palette.selectedStateContainer else MaterialTheme.colorScheme.primaryContainer
}

@Composable
internal fun historyPrimaryPurple(): Color {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val palette = LocalSemanticPalette.current
    val themeState = LocalThemeState.current
    val isBlackTheme = themeState.blackThemeActive(isDark) || themeState.accentTheme == AccentTheme.PURE_BLACK
    return if (isDark) {
        if (isBlackTheme) Color(0xFFD4D4DE) else palette.primaryAction
    } else {
        MaterialTheme.colorScheme.primary
    }
}

@Composable
internal fun historyPrimaryText(): Color = MaterialTheme.colorScheme.onSurface

@Composable
internal fun historySecondaryText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun historyIllustrationOutline(): Color = MaterialTheme.colorScheme.outline

@Composable
internal fun historyIllustrationCircle(): Color {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val palette = LocalSemanticPalette.current
    return if (isDark) palette.selectedStateContainer else MaterialTheme.colorScheme.primaryContainer
}

/**
 * One-shot events emitted by [HistoryViewModel] for the UI layer to react to.
 * Currently only carries bulk-action completions so the screen can show an undo
 * snackbar; modeled as a sealed interface so additional event kinds can be added
 * without breaking existing collectors.
 */
sealed interface HistoryEvent {
    data class BulkActionPerformed(
        val action: BulkUndoableAction,
    ) : HistoryEvent
}

@Immutable
private data class HistoryNoteRow(
    val note: NoteWithItems,
    val card: NoteCardUiModel,
)

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
        viewOptionsPrefs: ViewOptionsPrefs,
    ) : ViewModel() {
        /**
         * Wall-clock instant captured when the view-model is created -- used to derive the
         * "days left" counter without re-invoking the system clock on every recomposition.
         */
        private val nowAtCreation = System.currentTimeMillis()

        val trashedItems: StateFlow<List<NoteWithItems>> =
            repository
                .observeTrashed()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val archivedItems: StateFlow<List<NoteWithItems>> =
            repository
                .observeArchived()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val noteLayoutMode: StateFlow<NoteLayoutMode> =
            viewOptionsPrefs
                .state
                .map { viewOptions -> viewOptions.noteLayoutMode }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteLayoutMode.LIST)

        private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

        private val _isOperating = MutableStateFlow(false)
        val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

        /**
         * One-shot events for the UI: bulk-action completions surface a snackbar with
         * Undo. The repository has already coalesced row writes into a single Flow
         * emission by the time these fire, so the list has reflowed and the snackbar
         * lands on settled state. Backed by a Channel (not a SharedFlow) so emission never
         * suspends the view model, each event delivers exactly once, and nothing replays on rotation.
         */
        private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
        val events: Flow<HistoryEvent> = _events.receiveAsFlow()

        /**
         * Most recent bulk action originating from selection mode. Cleared after undo,
         * after deletion is performed (since delete-forever is intentionally not
         * undoable), or when superseded by the next bulk action.
         */
        @Volatile
        private var lastBulkAction: BulkUndoableAction? = null

        fun toggleSelection(id: Long) {
            _selectedIds.value =
                if (id in _selectedIds.value) {
                    _selectedIds.value - id
                } else {
                    _selectedIds.value + id
                }
        }

        fun selectNotes(ids: Set<Long>) {
            _selectedIds.value = ids
        }

        fun clearSelection() {
            _selectedIds.value = emptySet()
        }

        fun pruneSelection(validIds: Set<Long>) {
            _selectedIds.value = _selectedIds.value.intersect(validIds)
        }

        fun restore(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.restoreFromTrash(id)
                emitSingleCardAction(BulkUndoableAction.Restored(setOf(id)))
            }
        }

        fun archiveFromTrash(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.archiveNote(id)
                emitSingleCardAction(BulkUndoableAction.ArchivedFromTrash(setOf(id)))
            }
        }

        /**
         * Permanent single-card delete. The screen guards this path with a
         * confirmation dialog (mirror of the bulk delete sheet); no snackbar fires
         * after - delete-forever has no undo so the dialog IS the confirmation.
         * [lastBulkAction] is cleared so a stale undo from an earlier action cannot
         * fire after the user's confirmed permanent delete.
         */
        fun deleteForever(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.deleteForever(id)
                lastBulkAction = null
            }
        }

        fun unarchive(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.unarchiveNote(id)
                emitSingleCardAction(BulkUndoableAction.Unarchived(setOf(id)))
            }
        }

        fun moveArchivedToTrash(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.moveToTrash(id)
                emitSingleCardAction(BulkUndoableAction.MovedArchiveToTrash(setOf(id)))
            }
        }

        /**
         * Records [action] as the most-recent undoable action and notifies the screen
         * so it can show a snackbar. Single-card swipe / tap callbacks share the same
         * snackbar plumbing as bulk-selection mode; both routes through
         * [HistoryEvent.BulkActionPerformed] and [undoLastBulkAction].
         */
        private fun emitSingleCardAction(action: BulkUndoableAction) {
            lastBulkAction = action
            _events.trySend(HistoryEvent.BulkActionPerformed(action))
        }

        fun emptyTrash(
            onSuccess: () -> Unit = {},
            onError: (Throwable) -> Unit = {},
        ) {
            if (_isOperating.value) return
            viewModelScope.launch {
                _isOperating.value = true
                try {
                    repository.emptyTrash()
                    lastBulkAction = null
                    onSuccess()
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    _isOperating.value = false
                }
            }
        }

        fun moveAllArchivedToTrash(
            onSuccess: () -> Unit = {},
            onError: (Throwable) -> Unit = {},
        ) {
            if (_isOperating.value) return
            viewModelScope.launch {
                _isOperating.value = true
                try {
                    val archivedSnapshot = archivedItems.value.map { it.note.id }.toSet()
                    repository.moveAllArchivedToTrash()
                    if (archivedSnapshot.isNotEmpty()) {
                        val action = BulkUndoableAction.MovedArchiveToTrash(archivedSnapshot)
                        lastBulkAction = action
                        _events.trySend(HistoryEvent.BulkActionPerformed(action))
                    }
                    onSuccess()
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    _isOperating.value = false
                }
            }
        }

        fun restoreSelected() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.restoreFromTrash(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.Restored(snapshot)
                lastBulkAction = action
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun archiveSelectedFromTrash() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.archiveNotes(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.ArchivedFromTrash(snapshot)
                lastBulkAction = action
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun unarchiveSelected() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.unarchiveNotes(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.Unarchived(snapshot)
                lastBulkAction = action
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun moveSelectedArchivedToTrash() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.moveToTrash(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.MovedArchiveToTrash(snapshot)
                lastBulkAction = action
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
            }
        }

        /**
         * Permanent delete is intentionally not undoable - rows are gone and reviving
         * them isn't possible. The screen guards this path with a confirmation
         * AppBottomSheet before invoking us. We clear [lastBulkAction] so a stale undo
         * from an earlier action doesn't accidentally fire after the user confirms.
         */
        fun deleteSelectedForever() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.deleteForever(snapshot)
                _selectedIds.value = emptySet()
                lastBulkAction = null
            }
        }

        /**
         * Reverses the most recent bulk action by issuing the inverse repository call.
         * Mirrors [HomeViewModel.undoLastBulkAction]; see [BulkUndoableAction] for the
         * full inverse mapping table.
         */
        fun undoLastBulkAction() {
            val action = lastBulkAction ?: return
            lastBulkAction = null
            viewModelScope.launch {
                when (action) {
                    is BulkUndoableAction.Archived -> repository.unarchiveNotes(action.ids)
                    is BulkUndoableAction.Trashed -> repository.restoreFromTrash(action.ids)
                    is BulkUndoableAction.MarkedDone ->
                        repository.markIncomplete(action.ids, action.snapshots)
                    is BulkUndoableAction.Restored -> repository.moveToTrash(action.ids)
                    // Not produced by History (pin / star are Home actions), but the exhaustive
                    // mapping keeps the inverse correct if that ever changes.
                    is BulkUndoableAction.Pinned -> repository.setPinned(action.ids, false)
                    is BulkUndoableAction.Unarchived -> repository.archiveNotes(action.ids)
                    is BulkUndoableAction.ArchivedFromTrash -> repository.moveToTrash(action.ids)
                    is BulkUndoableAction.MovedArchiveToTrash -> repository.archiveNotes(action.ids)
                }
            }
        }

        /**
         * Returns the number of whole days remaining before [note] is auto-deleted from the trash.
         * A non-trashed note or a note whose [trashedAt] is null returns null so the caller can
         * suppress the badge.
         */
        fun daysLeftInTrash(note: NoteWithItems): Int? {
            val trashedAt = note.note.trashedAt ?: return null
            val millisLeft = (trashedAt + NoteRepository.TRASH_RETENTION_MILLIS) - nowAtCreation
            if (millisLeft <= 0) return 0
            // Integer division on (millis / day) rounds down; add one day so the user sees
            // "1 day left" on the last day rather than "0 days left".
            return max(0, (millisLeft / (24L * 60L * 60L * 1000L)).toInt())
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryRoute(
    interactionPrefs: InteractionPrefs,
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    onVisibleItemCountChange: (Int) -> Unit,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
    activeNoteId: Long? = null,
    showSelectionActionBar: Boolean = true,
    onBack: () -> Unit = {},
) {
    val vm: HistoryViewModel = hiltViewModel()
    val trashed by vm.trashedItems.collectAsStateWithLifecycle()
    val archived by vm.archivedItems.collectAsStateWithLifecycle()
    val noteLayoutMode by vm.noteLayoutMode.collectAsStateWithLifecycle()
    val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue =
            haddad.three.notas.data
                .InteractionState(),
    )
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val archivedListState = rememberLazyListState()
    val trashedListState = rememberLazyListState()
    val archivedGridState = rememberLazyStaggeredGridState()
    val trashedGridState = rememberLazyStaggeredGridState()

    val screenBg = historyScreenBg()
    val mainSurface = historyMainSurface()
    val activeLavender = historyActiveLavender()
    val primaryPurple = historyPrimaryPurple()
    val primaryText = historyPrimaryText()
    val secondaryText = historySecondaryText()
    val illustrationOutline = historyIllustrationOutline()
    val illustrationCircle = historyIllustrationCircle()

    val reducedMotion = LocalReducedMotion.current
    val historySectionSpatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val historySectionFadeInSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val historySectionFadeOutSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val items =
        when (section) {
            HistorySection.ARCHIVE -> archived
            HistorySection.TRASH -> trashed
        }
    val rows =
        remember(items) {
            items.map { noteWithItems ->
                HistoryNoteRow(
                    note = noteWithItems,
                    card = noteWithItems.toNoteCardUiModel(),
                )
            }
        }
    val selectableVisibleIds = remember(rows) { rows.map { row -> row.card.id }.toSet() }
    val inSelectionMode = selectedIds.isNotEmpty()
    val isOperating by vm.isOperating.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val notificationsAllowed = rememberNotificationsAllowed()
    val undoLabel = stringResource(R.string.bulk_action_undo)
    val operationFailedMsg = stringResource(R.string.history_operation_failed)

    var moveAllArchivedToTrashConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var emptyTrashConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var bulkDeleteForeverOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteForeverNote by remember { mutableStateOf<NoteWithItems?>(null) }
    var revealedNoteCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var revealedNoteCardBounds by remember { mutableStateOf<Rect?>(null) }
    var historyScreenBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = inSelectionMode) { vm.clearSelection() }
    LaunchedEffect(section) { vm.clearSelection() }
    LaunchedEffect(section, noteLayoutMode) { revealedNoteCardId = null }
    LaunchedEffect(inSelectionMode) {
        if (inSelectionMode) {
            revealedNoteCardId = null
        }
    }
    LaunchedEffect(revealedNoteCardId) {
        if (revealedNoteCardId == null) {
            revealedNoteCardBounds = null
        }
    }
    LaunchedEffect(selectableVisibleIds) { vm.pruneSelection(selectableVisibleIds) }
    LaunchedEffect(section, rows.size) { onVisibleItemCountChange(rows.size) }
    LaunchedEffect(vm, snackbarHostState, context, undoLabel) {
        vm.events.collect { event ->
            when (event) {
                is HistoryEvent.BulkActionPerformed -> {
                    val message = bulkActionSnackbarMessage(context, event.action)
                    val result =
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoLastBulkAction()
                    }
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(screenBg)
                .onGloballyPositioned { coordinates ->
                    historyScreenBounds = coordinates.boundsInRoot()
                }.pointerInput(revealedNoteCardId, revealedNoteCardBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val downChange =
                                event.changes.firstOrNull { change ->
                                    change.changedToDownIgnoreConsumed()
                                } ?: continue
                            val activeCardBounds = revealedNoteCardBounds
                            val screenBounds = historyScreenBounds
                            val tapPositionInRoot =
                                if (screenBounds != null) {
                                    Offset(
                                        x = screenBounds.left + downChange.position.x,
                                        y = screenBounds.top + downChange.position.y,
                                    )
                                } else {
                                    downChange.position
                                }
                            if (
                                revealedNoteCardId != null &&
                                activeCardBounds != null &&
                                !activeCardBounds.contains(tapPositionInRoot)
                            ) {
                                revealedNoteCardId = null
                            }
                        }
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // 1. Header (Back button + History title)
            HistoryHeader(
                onBack = {
                    if (inSelectionMode) {
                        vm.clearSelection()
                    } else {
                        onBack()
                    }
                },
                title = stringResource(R.string.main_tab_history),
                primaryTextColor = primaryText,
                buttonBgColor = mainSurface,
                iconColor = primaryPurple,
            )

            Spacer(Modifier.height(16.dp))

            // 2. Segmented Control (Archive / Trash)
            HistorySegmentedControl(
                section = section,
                onSectionChange = onSectionChange,
                containerBg = mainSurface,
                activeBg = activeLavender,
                activeColor = primaryPurple,
                inactiveColor = secondaryText,
            )

            Spacer(Modifier.height(16.dp))

            // 3. Main Content Panel
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                shape = RoundedCornerShape(32.dp),
                color = mainSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp)),
                ) {
                    AnimatedContent(
                        targetState = section,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (reducedMotion) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (
                                    slideInHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                                        direction * fullWidth
                                    } + fadeIn(animationSpec = historySectionFadeInSpec)
                                ) togetherWith (
                                    slideOutHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                                        -direction * fullWidth
                                    } + fadeOut(animationSpec = historySectionFadeOutSpec)
                                )
                            }.using(SizeTransform(clip = true))
                        },
                        label = "historySectionContent",
                    ) { targetSection ->
                        val targetItems =
                            when (targetSection) {
                                HistorySection.ARCHIVE -> archived
                                HistorySection.TRASH -> trashed
                            }
                        val targetRows =
                            remember(targetItems) {
                                targetItems.map { noteWithItems ->
                                    HistoryNoteRow(
                                        note = noteWithItems,
                                        card = noteWithItems.toNoteCardUiModel(),
                                    )
                                }
                            }
                        val targetListState =
                            when (targetSection) {
                                HistorySection.ARCHIVE -> archivedListState
                                HistorySection.TRASH -> trashedListState
                            }
                        val targetGridState =
                            when (targetSection) {
                                HistorySection.ARCHIVE -> archivedGridState
                                HistorySection.TRASH -> trashedGridState
                            }
                        val targetListScrollEnabled =
                            rememberContentOverflowScrollEnabled(
                                listState = targetListState,
                                additionalScrollEnabled = true,
                            )
                        val selectionBarClearance =
                            rememberSelectionActionBarListClearance(
                                visible = showSelectionActionBar && inSelectionMode,
                            )

                        if (targetRows.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                HistoryBulkActionHeader(
                                    section = targetSection,
                                    itemCount = targetRows.size,
                                    enabled = !isOperating && !inSelectionMode,
                                    onAction = {
                                        if (targetSection == HistorySection.ARCHIVE) {
                                            moveAllArchivedToTrashConfirmOpen = true
                                        } else {
                                            emptyTrashConfirmOpen = true
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                                )
                                val contentPadding =
                                    PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 16.dp + selectionBarClearance,
                                    )
                                BoxWithConstraints(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                ) {
                                    val mosaicColumnCount = noteMosaicColumnCount(maxWidth)
                                    if (noteLayoutMode == NoteLayoutMode.MOSAIC && mosaicColumnCount > 1) {
                                        LazyVerticalStaggeredGrid(
                                            columns = StaggeredGridCells.Fixed(mosaicColumnCount),
                                            state = targetGridState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = contentPadding,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalItemSpacing = 6.dp,
                                        ) {
                                            if (targetSection == HistorySection.TRASH) {
                                                item(
                                                    key = "retention_notice",
                                                    contentType = "retention",
                                                    span = StaggeredGridItemSpan.FullLine,
                                                ) {
                                                    RetentionNotice(
                                                        containerColor = activeLavender,
                                                        contentColor = primaryPurple,
                                                        modifier =
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 12.dp),
                                                    )
                                                }
                                            }
                                            targetRows.forEach { row ->
                                                item(
                                                    key = row.card.id,
                                                    contentType =
                                                        if (targetSection == HistorySection.TRASH) {
                                                            "trashedRow"
                                                        } else {
                                                            "archivedRow"
                                                        },
                                                ) {
                                                    val noteId = row.card.id
                                                    val isSelected = noteId in selectedIds
                                                    val isActiveInDetailPane = !inSelectionMode && activeNoteId == noteId
                                                    var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                                    HistorySwipeCard(
                                                        model = row.card,
                                                        section = targetSection,
                                                        daysLeft =
                                                            vm
                                                                .daysLeftInTrash(row.note)
                                                                .takeIf { targetSection == HistorySection.TRASH },
                                                        onOpenNote = {
                                                            if (inSelectionMode) {
                                                                vm.toggleSelection(noteId)
                                                            } else {
                                                                onOpenNote(row.note, false)
                                                            }
                                                        },
                                                        onRestore = { vm.restore(row.note) },
                                                        onArchive = { vm.archiveFromTrash(row.note) },
                                                        onDeleteForever = { pendingDeleteForeverNote = row.note },
                                                        onUnarchive = { vm.unarchive(row.note) },
                                                        onMoveToTrash = { vm.moveArchivedToTrash(row.note) },
                                                        selected = isSelected,
                                                        activeInDetailPane = isActiveInDetailPane,
                                                        onLongClick = { vm.toggleSelection(noteId) },
                                                        swipeEnabled = !inSelectionMode,
                                                        reminderNotificationsAllowed = notificationsAllowed,
                                                        modifier =
                                                            Modifier.onGloballyPositioned { coordinates ->
                                                                cardBounds = coordinates.boundsInRoot()
                                                                if (revealedNoteCardId == noteId) {
                                                                    revealedNoteCardBounds = cardBounds
                                                                }
                                                            },
                                                        revealKey = noteId,
                                                        activeRevealKey = revealedNoteCardId,
                                                        onRevealStarted = {
                                                            revealedNoteCardId = noteId
                                                            revealedNoteCardBounds = cardBounds
                                                        },
                                                        onRevealClosed = {
                                                            if (revealedNoteCardId == noteId) {
                                                                revealedNoteCardId = null
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = targetListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = contentPadding,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            userScrollEnabled = targetListScrollEnabled,
                                        ) {
                                            if (targetSection == HistorySection.TRASH) {
                                                item(key = "retention_notice", contentType = "retention") {
                                                    RetentionNotice(
                                                        containerColor = activeLavender,
                                                        contentColor = primaryPurple,
                                                        modifier =
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 12.dp),
                                                    )
                                                }
                                            }
                                            items(
                                                items = targetRows,
                                                key = { row -> row.card.id },
                                                contentType = {
                                                    if (targetSection == HistorySection.TRASH) {
                                                        "trashedRow"
                                                    } else {
                                                        "archivedRow"
                                                    }
                                                },
                                            ) { row ->
                                                val noteId = row.card.id
                                                val isSelected = noteId in selectedIds
                                                val isActiveInDetailPane = !inSelectionMode && activeNoteId == noteId
                                                var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                                HistorySwipeCard(
                                                    model = row.card,
                                                    section = targetSection,
                                                    daysLeft =
                                                        vm
                                                            .daysLeftInTrash(row.note)
                                                            .takeIf { targetSection == HistorySection.TRASH },
                                                    onOpenNote = {
                                                        if (inSelectionMode) {
                                                            vm.toggleSelection(noteId)
                                                        } else {
                                                            onOpenNote(row.note, false)
                                                        }
                                                    },
                                                    onRestore = { vm.restore(row.note) },
                                                    onArchive = { vm.archiveFromTrash(row.note) },
                                                    onDeleteForever = { pendingDeleteForeverNote = row.note },
                                                    onUnarchive = { vm.unarchive(row.note) },
                                                    onMoveToTrash = { vm.moveArchivedToTrash(row.note) },
                                                    selected = isSelected,
                                                    activeInDetailPane = isActiveInDetailPane,
                                                    onLongClick = { vm.toggleSelection(noteId) },
                                                    swipeEnabled = !inSelectionMode,
                                                    reminderNotificationsAllowed = notificationsAllowed,
                                                    modifier =
                                                        Modifier.onGloballyPositioned { coordinates ->
                                                            cardBounds = coordinates.boundsInRoot()
                                                            if (revealedNoteCardId == noteId) {
                                                                revealedNoteCardBounds = cardBounds
                                                            }
                                                        },
                                                    revealKey = noteId,
                                                    activeRevealKey = revealedNoteCardId,
                                                    onRevealStarted = {
                                                        revealedNoteCardId = noteId
                                                        revealedNoteCardBounds = cardBounds
                                                    },
                                                    onRevealClosed = {
                                                        if (revealedNoteCardId == noteId) {
                                                            revealedNoteCardId = null
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                HistoryEmptyState(
                                    section = targetSection,
                                    outlineColor = illustrationOutline,
                                    circleColor = illustrationCircle,
                                    primaryTextColor = primaryText,
                                    secondaryTextColor = secondaryText,
                                )
                            }
                        }
                    }

                    if (showSelectionActionBar) {
                        HistorySelectionActionBar(
                            visible = inSelectionMode,
                            section = section,
                            selectedCount = selectedIds.size,
                            selectableVisibleIds = selectableVisibleIds,
                            onClearSelection = vm::clearSelection,
                            onSelectAll = { vm.selectNotes(selectableVisibleIds) },
                            onUnselectAll = vm::clearSelection,
                            onRestoreSelected = vm::restoreSelected,
                            onArchiveSelected = vm::archiveSelectedFromTrash,
                            onDeleteForeverSelected = { bulkDeleteForeverOpen = true },
                            onUnarchiveSelected = vm::unarchiveSelected,
                            onTrashSelected = vm::moveSelectedArchivedToTrash,
                            bottomPadding = 16.dp,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    pendingDeleteForeverNote?.let { noteToDelete ->
        RememberConfirmDialog(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            text = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_delete_forever),
            onConfirm = {
                pendingDeleteForeverNote = null
                vm.deleteForever(noteToDelete)
            },
            onDismiss = { pendingDeleteForeverNote = null },
            destructive = true,
        )
    }

    if (bulkDeleteForeverOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            text = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_delete_forever),
            onConfirm = {
                bulkDeleteForeverOpen = false
                vm.deleteSelectedForever()
            },
            onDismiss = { bulkDeleteForeverOpen = false },
            destructive = true,
        )
    }

    if (moveAllArchivedToTrashConfirmOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.history_archive_move_all_dialog_title),
            text = stringResource(R.string.history_archive_move_all_dialog_message),
            confirmLabel = stringResource(R.string.history_archive_move_all_to_trash),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                moveAllArchivedToTrashConfirmOpen = false
                vm.moveAllArchivedToTrash(
                    onError = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = operationFailedMsg,
                            )
                        }
                    },
                )
            },
            onDismiss = { moveAllArchivedToTrashConfirmOpen = false },
            destructive = true,
        )
    }

    if (emptyTrashConfirmOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.history_trash_empty_dialog_title),
            text = stringResource(R.string.history_trash_empty_dialog_message),
            confirmLabel = stringResource(R.string.history_trash_empty_dialog_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                emptyTrashConfirmOpen = false
                vm.emptyTrash(
                    onError = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = operationFailedMsg,
                            )
                        }
                    },
                )
            },
            onDismiss = { emptyTrashConfirmOpen = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistorySelectionActionBar(
    visible: Boolean,
    section: HistorySection,
    selectedCount: Int,
    selectableVisibleIds: Set<Long>,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onRestoreSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteForeverSelected: () -> Unit,
    onUnarchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter =
                expandVertically(animationSpec = spatialSpec, expandFrom = Alignment.Bottom) +
                    fadeIn(animationSpec = fadeInSpec),
            exit =
                shrinkVertically(animationSpec = spatialSpec, shrinkTowards = Alignment.Bottom) +
                    fadeOut(animationSpec = fadeOutSpec),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                val exitLabel = stringResource(R.string.home_select_exit_cd)
                val exitInteractionSource = remember { MutableInteractionSource() }
                val exitColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val cdSelectAll = stringResource(R.string.home_select_all)
                val selectAllInteractionSource = remember { MutableInteractionSource() }
                val cdUnselectAll = stringResource(R.string.home_unselect_all)
                val unselectAllInteractionSource = remember { MutableInteractionSource() }
                val unselectAllColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val restoreLabel = stringResource(R.string.edit_bottom_bar_restore)
                val cdRestore = stringResource(R.string.edit_bottom_bar_restore_cd)
                val restoreInteractionSource = remember { MutableInteractionSource() }
                val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                val cdArchive = stringResource(R.string.edit_bottom_bar_archive_cd)
                val archiveInteractionSource = remember { MutableInteractionSource() }
                val unarchiveLabel = stringResource(R.string.edit_bottom_bar_unarchive)
                val cdUnarchive = stringResource(R.string.edit_bottom_bar_unarchive_cd)
                val unarchiveInteractionSource = remember { MutableInteractionSource() }
                val trashLabel = stringResource(R.string.common_move_to_trash)
                val cdTrash = stringResource(R.string.history_archive_move_to_trash_cd)
                val trashInteractionSource = remember { MutableInteractionSource() }
                val deleteForeverLabel = stringResource(R.string.edit_bottom_bar_delete_forever)
                val cdDeleteForever = stringResource(R.string.edit_bottom_bar_delete_forever_cd)
                val deleteForeverInteractionSource = remember { MutableInteractionSource() }
                val deleteForeverColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                val actionButtonSize = rememberResponsiveActionButtonSize()
                ButtonGroup(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onClearSelection,
                                modifier = Modifier.size(actionButtonSize).animateWidth(exitInteractionSource),
                                interactionSource = exitInteractionSource,
                                tooltipLabel = exitLabel,
                                colors = exitColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = exitLabel },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(exitLabel) },
                                onClick = {
                                    onClearSelection()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            Box(modifier = Modifier.size(actionButtonSize)) {
                                RememberFilledTonalIconButton(
                                    onClick = onSelectAll,
                                    enabled = selectableVisibleIds.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(actionButtonSize)
                                            .animateWidth(selectAllInteractionSource),
                                    interactionSource = selectAllInteractionSource,
                                    tooltipLabel = cdSelectAll,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "select_all",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdSelectAll },
                                    )
                                }
                                Badge(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .offset(x = 2.dp, y = (-2).dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text(
                                        text = selectedCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(cdSelectAll) },
                                onClick = {
                                    onSelectAll()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onUnselectAll,
                                enabled = selectedCount > 0,
                                modifier = Modifier.size(actionButtonSize).animateWidth(unselectAllInteractionSource),
                                interactionSource = unselectAllInteractionSource,
                                tooltipLabel = cdUnselectAll,
                                colors = unselectAllColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "deselect",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdUnselectAll },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(cdUnselectAll) },
                                onClick = {
                                    onUnselectAll()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    if (section == HistorySection.TRASH) {
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onRestoreSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(restoreInteractionSource),
                                    interactionSource = restoreInteractionSource,
                                    tooltipLabel = restoreLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "restore_from_trash",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdRestore },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(restoreLabel) },
                                    onClick = {
                                        onRestoreSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onArchiveSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(archiveInteractionSource),
                                    interactionSource = archiveInteractionSource,
                                    tooltipLabel = archiveLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "archive",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdArchive },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(archiveLabel) },
                                    onClick = {
                                        onArchiveSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                    } else {
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onUnarchiveSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(unarchiveInteractionSource),
                                    interactionSource = unarchiveInteractionSource,
                                    tooltipLabel = unarchiveLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "unarchive",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdUnarchive },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(unarchiveLabel) },
                                    onClick = {
                                        onUnarchiveSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onTrashSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(trashInteractionSource),
                                    interactionSource = trashInteractionSource,
                                    tooltipLabel = trashLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "delete_sweep",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdTrash },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(trashLabel) },
                                    onClick = {
                                        onTrashSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onDeleteForeverSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(deleteForeverInteractionSource),
                                interactionSource = deleteForeverInteractionSource,
                                tooltipLabel = deleteForeverLabel,
                                colors = deleteForeverColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "delete_forever",
                                    size = 20.dp,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdDeleteForever },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(deleteForeverLabel) },
                                onClick = {
                                    onDeleteForeverSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RetentionNotice(
    modifier: Modifier = Modifier,
    containerColor: Color = historyActiveLavender(),
    contentColor: Color = historyPrimaryPurple(),
) {
    Row(
        modifier =
            modifier
                .background(
                    containerColor,
                    MaterialTheme.shapes.large,
                ).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "schedule",
            size = 18.dp,
            tint = contentColor,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.history_trash_retention_notice),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HistorySwipeCard(
    model: NoteCardUiModel,
    section: HistorySection,
    daysLeft: Int?,
    onOpenNote: () -> Unit,
    onRestore: () -> Unit,
    onArchive: () -> Unit,
    onDeleteForever: () -> Unit,
    onUnarchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    activeInDetailPane: Boolean = false,
    swipeEnabled: Boolean = true,
    reminderNotificationsAllowed: Boolean = true,
    revealKey: Any? = null,
    activeRevealKey: Any? = null,
    onRevealStarted: (() -> Unit)? = null,
    onRevealClosed: (() -> Unit)? = null,
) {
    if (!swipeEnabled) {
        Box(modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                activeInDetailPane = activeInDetailPane,
                onLongClick = onLongClick,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
            )
            if (daysLeft != null) {
                TrashDaysLeftBadge(
                    daysLeft = daysLeft,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (selected) 42.dp else 10.dp,
                                end = 10.dp,
                            ),
                )
            }
        }
        return
    }

    val startActions =
        when (section) {
            HistorySection.ARCHIVE ->
                listOf(
                    SwipeRevealTile(
                        key = "unarchive",
                        labelRes = R.string.edit_bottom_bar_unarchive,
                        symbolName = "unarchive",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onUnarchive,
                    ),
                )
            HistorySection.TRASH ->
                listOf(
                    SwipeRevealTile(
                        key = "restore",
                        labelRes = R.string.edit_bottom_bar_restore,
                        symbolName = "restore_from_trash",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onRestore,
                    ),
                    SwipeRevealTile(
                        key = "archive",
                        labelRes = R.string.edit_bottom_bar_archive,
                        symbolName = "archive",
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onArchive,
                    ),
                )
        }
    val endActions =
        when (section) {
            HistorySection.ARCHIVE ->
                listOf(
                    SwipeRevealTile(
                        key = "trash",
                        labelRes = R.string.edit_bottom_bar_trash,
                        symbolName = "delete",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onMoveToTrash,
                    ),
                )
            HistorySection.TRASH ->
                listOf(
                    SwipeRevealTile(
                        key = "delete_forever",
                        labelRes = R.string.edit_bottom_bar_delete_forever,
                        symbolName = "delete_forever",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onDeleteForever,
                    ),
                )
        }
    MultiActionSwipeRevealCard(
        startActions = startActions,
        endActions = endActions,
        cardShape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
        revealKey = revealKey,
        activeRevealKey = activeRevealKey,
        onRevealStarted = onRevealStarted,
        onRevealClosed = onRevealClosed,
    ) {
        Box(Modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                activeInDetailPane = activeInDetailPane,
                onLongClick = onLongClick,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
            )
            if (daysLeft != null) {
                TrashDaysLeftBadge(
                    daysLeft = daysLeft,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (selected) 42.dp else 10.dp,
                                end = 10.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun TrashDaysLeftBadge(
    daysLeft: Int,
    modifier: Modifier = Modifier,
) {
    val label =
        if (daysLeft <= 0) {
            stringResource(R.string.history_expires_today)
        } else {
            pluralStringResource(R.plurals.history_days_left, daysLeft, daysLeft)
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (daysLeft <= 3) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .background(
                    if (daysLeft <= 3) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    MaterialTheme.shapes.extraExtraLarge,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun HistoryHeader(
    onBack: () -> Unit,
    title: String,
    primaryTextColor: Color,
    buttonBgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            color = buttonBgColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                RememberMaterialRoundedSymbol(
                    name = "arrow_back",
                    tint = iconColor,
                    size = 24.dp,
                    autoMirror = true,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    color = primaryTextColor,
                ),
        )
    }
}

@Composable
private fun HistorySegmentedControl(
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    containerBg: Color,
    activeBg: Color,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(5.dp),
        ) {
            val tabWidth = maxWidth / 2
            val indicatorOffset by animateDpAsState(
                targetValue = if (section == HistorySection.ARCHIVE) 0.dp else tabWidth,
                animationSpec =
                    if (reducedMotion) {
                        snap()
                    } else {
                        spring(stiffness = Spring.StiffnessMediumLow)
                    },
                label = "historyTabIndicatorOffset",
            )

            // Animated active pill indicator
            Box(
                modifier =
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(activeBg),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Archive Tab
                val isArchiveSelected = section == HistorySection.ARCHIVE
                val archiveColor by animateColorAsState(
                    targetValue = if (isArchiveSelected) activeColor else inactiveColor,
                    label = "archiveTabColor",
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSectionChange(HistorySection.ARCHIVE) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "archive",
                            tint = archiveColor,
                            size = 20.dp,
                        )
                        Text(
                            text = HistorySection.ARCHIVE.label(),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isArchiveSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = archiveColor,
                                ),
                        )
                    }
                }

                // Trash Tab
                val isTrashSelected = section == HistorySection.TRASH
                val trashColor by animateColorAsState(
                    targetValue = if (isTrashSelected) activeColor else inactiveColor,
                    label = "trashTabColor",
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSectionChange(HistorySection.TRASH) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete",
                            tint = trashColor,
                            size = 20.dp,
                        )
                        Text(
                            text = HistorySection.TRASH.label(),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isTrashSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = trashColor,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(
    section: HistorySection,
    outlineColor: Color,
    circleColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        if (section == HistorySection.ARCHIVE) {
            R.string.history_archive_empty_title
        } else {
            R.string.history_trash_empty_title
        }
    val subtitleRes =
        if (section == HistorySection.ARCHIVE) {
            R.string.history_archive_empty_subtitle
        } else {
            R.string.history_trash_empty_subtitle
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (section == HistorySection.ARCHIVE) {
            EmptyArchiveIllustration(
                outlineColor = outlineColor,
                circleColor = circleColor,
            )
        } else {
            EmptyTrashIllustration(
                outlineColor = outlineColor,
                circleColor = circleColor,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(titleRes),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = primaryTextColor,
                ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(subtitleRes),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = secondaryTextColor,
                ),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun HistorySection.label(): String =
    when (this) {
        HistorySection.ARCHIVE -> stringResource(R.string.history_section_archive)
        HistorySection.TRASH -> stringResource(R.string.history_section_trash)
    }

@Composable
private fun HistoryBulkActionHeader(
    section: HistorySection,
    itemCount: Int,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val countText =
            pluralStringResource(
                R.plurals.history_note_count,
                itemCount,
                itemCount,
            )
        Text(
            text = countText,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = historySecondaryText(),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.width(8.dp))

        val isArchive = section == HistorySection.ARCHIVE
        val label =
            if (isArchive) {
                stringResource(R.string.history_archive_move_all_to_trash)
            } else {
                stringResource(R.string.history_trash_empty_action)
            }

        val isDark = LocalIsDark.current
        val containerColor =
            if (isArchive) {
                if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF2E8F7)
            } else {
                if (isDark) MaterialTheme.colorScheme.errorContainer else Color(0xFFFDE8E8)
            }
        val contentColor =
            if (isArchive) {
                if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFA34EA5)
            } else {
                if (isDark) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error
            }

        Surface(
            onClick = onAction,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.height(36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "delete",
                    size = 18.dp,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                )
                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                )
            }
        }
    }
}
