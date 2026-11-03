package haddad.three.notas.ui.home

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.NotesFilter
import haddad.three.notas.data.SortDir
import haddad.three.notas.data.SortKey
import haddad.three.notas.data.ViewOptions
import haddad.three.notas.ui.components.toNoteCardUiModel

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStateFlowTest {
    @Test
    fun selection_changes_reuse_prepared_content_without_rebuilding_items() =
        runTest {
            var noteReads = 0
            val note1 = note(id = 1L, title = "First", pinned = true)
            val note2 = note(id = 2L, title = "Second", pinned = false)
            val prepared =
                HomeState(
                    items =
                        persistentListOf(
                            HomeListItem.NoteRow(
                                note = note1,
                                card = note1.toNoteCardUiModel(),
                            ),
                            HomeListItem.NoteRow(
                                note = note2,
                                card = note2.toNoteCardUiModel(),
                            ),
                        ),
                    totalActive = 2,
                    archivedMatches = persistentListOf(note(id = 10L, title = "Archived")),
                    trashedMatches = persistentListOf(note(id = 20L, title = "Trash")),
                )
            val notes =
                ObservedNotes(listOf(note1, note2)) {
                    noteReads++
                }
            val fixture = HomeFlowFixture(notes)
            val state = fixture.state(this)
            runCurrent()
            noteReads = 0
            val preparedItems = state.value.items
            val preparedArchived = state.value.archivedMatches
            val preparedTrashed = state.value.trashedMatches

            fixture.selected.value = setOf(1L)
            runCurrent()
            assertEquals(setOf(1L), state.value.selectedIds)
            assertTrue(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)

            fixture.selected.value = setOf(1L, 2L, 999L)
            runCurrent()
            assertEquals(setOf(1L, 2L), state.value.selectedIds)
            assertTrue(state.value.canPinSelected)
            assertSame(preparedItems, state.value.items)
            assertSame(preparedArchived, state.value.archivedMatches)
            assertSame(preparedTrashed, state.value.trashedMatches)

            fixture.selected.value = emptySet()
            runCurrent()
            assertFalse(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)
            assertSame(preparedItems, state.value.items)
            assertEquals(0, noteReads)
        }

    @Test
    fun content_changes_refresh_bulk_actions_and_prune_hidden_or_removed_notes() =
        runTest {
            val fixture = HomeFlowFixture(listOf(note(id = 1L), note(id = 2L)))
            fixture.selected.value = setOf(1L, 2L)
            val state = fixture.state(this)
            runCurrent()
            assertTrue(state.value.canPinSelected)

            fixture.notes.value = listOf(note(id = 1L, pinned = true))
            runCurrent()
            assertEquals(setOf(1L), state.value.selectedIds)
            assertFalse(state.value.canPinSelected)

            fixture.filter.value = NotesFilter(pinned = false)
            runCurrent()
            assertTrue(state.value.items.isEmpty())
            assertTrue(state.value.selectedIds.isEmpty())
            assertFalse(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)
        }

    @Test
    fun sorting_preserves_selection_and_bulk_action_flags() =
        runTest {
            val fixture =
                HomeFlowFixture(
                    listOf(
                        note(id = 1L, title = "Alpha"),
                        note(id = 2L, title = "Beta"),
                    ),
                )
            fixture.options.value = ViewOptions(sortKey = SortKey.ALPHABETICAL)
            fixture.selected.value = setOf(1L)
            val state = fixture.state(this)
            runCurrent()
            assertEquals(
                listOf(1L, 2L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )

            fixture.options.value = fixture.options.value.copy(sortDir = SortDir.DESC)
            runCurrent()
            assertEquals(
                listOf(2L, 1L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )
            assertEquals(setOf(1L), state.value.selectedIds)
            assertTrue(state.value.canPinSelected)
        }

    @Test
    fun search_facets_apply_to_all_sources_without_repeating_text_matching() =
        runTest {
            val fixture =
                HomeFlowFixture(
                    listOf(note(id = 1L, pinned = true), note(id = 2L, pinned = false)),
                )
            fixture.archived.value = listOf(note(id = 3L, pinned = true), note(id = 4L, pinned = false))
            fixture.trashed.value = listOf(note(id = 5L, pinned = true), note(id = 6L, pinned = false))
            fixture.filter.value = NotesFilter(text = "separate terms", pinned = true)
            fixture.selected.value = setOf(1L, 3L, 5L)
            val state = fixture.state(this)
            runCurrent()
            assertEquals(
                listOf(1L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )
            assertEquals(listOf(3L), state.value.archivedMatches.map { it.note.id })
            assertEquals(listOf(5L), state.value.trashedMatches.map { it.note.id })
            assertEquals(setOf(1L), state.value.selectedIds)

            fixture.filter.value = fixture.filter.value.copy(text = "")
            runCurrent()
            assertTrue(state.value.archivedMatches.isEmpty())
            assertTrue(state.value.trashedMatches.isEmpty())
        }

    @Test
    fun list_preparation_uses_a_background_dispatcher_by_default() =
        runTest {
            val collectorThread = Thread.currentThread()
            val notes =
                ObservedNotes(listOf(note(id = 1L))) {
                    assertNotSame(collectorThread, Thread.currentThread())
                }
            val state =
                homeStateFlow(
                    filter = flowOf(NotesFilter()),
                    notesSource = flowOf(notes),
                    allActiveNotes = flowOf(notes),
                    viewOptions = flowOf(ViewOptions()),
                    selectedIds = flowOf(setOf(1L)),
                    archivedSearchSource = flowOf(emptyList()),
                    trashedSearchSource = flowOf(emptyList()),
                ).first()

            assertFalse(state.loading)
            assertEquals(setOf(1L), state.selectedIds)
        }

    private class HomeFlowFixture(
        initialNotes: List<NoteWithItems>,
    ) {
        val filter = MutableStateFlow(NotesFilter())
        val notes = MutableStateFlow(initialNotes)
        val allActive = MutableStateFlow(initialNotes)
        val options = MutableStateFlow(ViewOptions())
        val selected = MutableStateFlow(emptySet<Long>())
        val archived = MutableStateFlow(emptyList<NoteWithItems>())
        val trashed = MutableStateFlow(emptyList<NoteWithItems>())

        @Suppress("ktlint:standard:function-expression-body")
        fun state(scope: TestScope): StateFlow<HomeState> {
            return homeStateFlow(
                filter = filter,
                notesSource = notes,
                allActiveNotes = allActive,
                viewOptions = options,
                selectedIds = selected,
                archivedSearchSource = archived,
                trashedSearchSource = trashed,
                computationDispatcher = StandardTestDispatcher(scope.testScheduler),
            ).stateIn(scope.backgroundScope, SharingStarted.Eagerly, HomeState())
        }
    }

    private class ObservedNotes(
        private val notes: List<NoteWithItems>,
        private val onRead: () -> Unit,
    ) : AbstractList<NoteWithItems>() {
        override val size: Int get() = notes.size

        override fun get(index: Int): NoteWithItems {
            onRead()
            return notes[index]
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun note(
        id: Long,
        title: String = "Note $id",
        pinned: Boolean = false,
        shoulderId: Long? = null,
    ): NoteWithItems {
        return NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    pinnedAt = if (pinned) 1_000L else null,
                    shoulderId = shoulderId,
                ),
            items = emptyList(),
        )
    }
}
