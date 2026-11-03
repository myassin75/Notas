package three.haddad.smartnotas.ui.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import three.haddad.smartnotas.data.AttachmentDao
import three.haddad.smartnotas.data.ChecklistItemDao
import three.haddad.smartnotas.data.ChecklistItemEntity
import three.haddad.smartnotas.data.NoteAttachmentEntity
import three.haddad.smartnotas.data.NoteDao
import three.haddad.smartnotas.data.NoteEntity
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteReminder
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.SearchHistoryEntry
import three.haddad.smartnotas.data.SearchPreferences
import three.haddad.smartnotas.data.ShoulderDao
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.data.ShoulderRepository
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val activeSearchResultsFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())
    private val archivedSearchResultsFlow = MutableStateFlow<List<NoteWithItems>>(emptyList())
    private val allShouldersFlow = MutableStateFlow<List<ShoulderEntity>>(emptyList())

    private lateinit var fakePreferences: FakeSearchPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferences = FakeSearchPreferences()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FakeSearchPreferences : SearchPreferences(FakePreferencesDataStore()) {
        private val _recent = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
        override val recentSearches: Flow<List<SearchHistoryEntry>> = _recent

        override suspend fun addSearchQuery(query: String) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return
            _recent.update { list ->
                val updated = list.filterNot { it.query.equals(trimmed, ignoreCase = true) }.toMutableList()
                updated.add(0, SearchHistoryEntry(trimmed, System.currentTimeMillis()))
                updated.take(10)
            }
        }

        override suspend fun removeSearchQuery(query: String) {
            _recent.update { list -> list.filterNot { it.query.equals(query, ignoreCase = true) } }
        }

        override suspend fun clearRecentSearches() {
            _recent.value = emptyList()
        }
    }

    private fun TestScope.createViewModel(): SearchViewModel {
        val fakeNoteDao =
            object : NoteDao {
                override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeScheduled(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(null)

                override suspend fun get(id: Long): NoteWithItems? = null

                override suspend fun getByUuid(uuid: String): NoteWithItems? = null

                override suspend fun getNoteEntityByUuid(uuid: String): NoteEntity? = null

                override suspend fun allNotes(): List<NoteWithItems> = emptyList()

                override suspend fun notesForOwner(ownerId: String): List<NoteWithItems> = emptyList()

                override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

                override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = activeSearchResultsFlow

                override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = archivedSearchResultsFlow

                override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

                override suspend fun insert(note: NoteEntity): Long = 1L

                override suspend fun update(note: NoteEntity) = Unit

                override suspend fun setTrashed(
                    id: Long,
                    trashed: Boolean,
                    updatedAt: Long,
                ) = Unit

                override suspend fun setArchived(
                    id: Long,
                    archived: Boolean,
                    updatedAt: Long,
                ) = Unit

                override suspend fun deleteById(id: Long) = Unit

                override suspend fun allNoteIds(): List<Long> = emptyList()

                override suspend fun countPictureUri(uri: String): Int = 0

                override suspend fun trashedNoteIds(): List<Long> = emptyList()

                override suspend fun archivedNoteIds(): List<Long> = emptyList()

                override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

                override suspend fun deleteAllNotes() = Unit

                override suspend fun emptyTrash() = Unit

                override suspend fun deleteTrashedOlderThan(cutoff: Long) = Unit
            }

        val fakeItemDao =
            object : ChecklistItemDao {
                override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = emptyList()

                override suspend fun getByUuid(uuid: String): ChecklistItemEntity? = null

                override suspend fun insert(item: ChecklistItemEntity): Long = 1L

                override suspend fun insertAll(items: List<ChecklistItemEntity>) = Unit

                override suspend fun update(item: ChecklistItemEntity) = Unit

                override suspend fun deleteById(id: Long) = Unit

                override suspend fun deleteForNote(noteId: Long) = Unit
            }

        val fakeAttachmentDao =
            object : AttachmentDao {
                override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = emptyList()

                override suspend fun insert(attachment: NoteAttachmentEntity): Long = 1L

                override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) = Unit

                override suspend fun getById(id: Long): NoteAttachmentEntity? = null

                override suspend fun getByUuid(uuid: String): NoteAttachmentEntity? = null

                override suspend fun countByUri(uri: String): Int = 0

                override suspend fun deleteById(id: Long) = Unit

                override suspend fun deleteForNote(noteId: Long) = Unit
            }

        val fakeShoulderDao =
            object : ShoulderDao {
                override fun observeAllShoulders(): Flow<List<ShoulderEntity>> = allShouldersFlow

                override suspend fun allShoulders(): List<ShoulderEntity> = emptyList()

                override suspend fun shouldersForOwner(ownerId: String): List<ShoulderEntity> = emptyList()

                override fun observeActiveShoulders(): Flow<List<ShoulderEntity>> = flowOf(emptyList())

                override suspend fun getShoulderById(id: Long): ShoulderEntity? = null

                override suspend fun getByUuid(uuid: String): ShoulderEntity? = null

                override suspend fun getByNormalizedName(normalizedName: String): ShoulderEntity? = null

                override suspend fun noteIdsForShoulder(shoulderId: Long): List<Long> = emptyList()

                override suspend fun insertShoulder(shoulder: ShoulderEntity): Long = 1L

                override suspend fun updateShoulder(shoulder: ShoulderEntity) = Unit

                override suspend fun deleteShoulder(shoulder: ShoulderEntity) = Unit

                override suspend fun setShoulderForNote(
                    noteId: Long,
                    shoulderId: Long?,
                ) = Unit
            }

        val noteRepository =
            NoteRepository(
                noteDao = fakeNoteDao,
                itemDao = fakeItemDao,
                attachmentDao = fakeAttachmentDao,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            )

        val shoulderRepository = ShoulderRepository(shoulderDao = fakeShoulderDao)

        val viewModel =
            SearchViewModel(
                noteRepository = noteRepository,
                shoulderRepository = shoulderRepository,
                searchPreferences = fakePreferences,
            )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.recentSearches.collect {}
        }

        return viewModel
    }

    private fun createNote(
        id: Long,
        title: String,
        body: String = "",
        kind: NoteKind = NoteKind.NOTE,
        updatedAt: Long = 1000L,
        reminderAt: Long? = null,
        reminders: List<NoteReminder> = emptyList(),
        shoulderId: Long? = null,
        completedAt: Long? = null,
        archived: Boolean = false,
        items: List<ChecklistItemEntity> = emptyList(),
    ): NoteWithItems =
        NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = kind,
                    title = title,
                    body = body,
                    colorIndex = 0,
                    trashed = false,
                    createdAt = updatedAt,
                    updatedAt = updatedAt,
                    reminderAt = reminderAt,
                    reminders = reminders,
                    shoulderId = shoulderId,
                    completedAt = completedAt,
                    archived = archived,
                ),
            items = items,
        )

    @Test
    fun initial_state_defaults_to_empty_query_and_zero_counts() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.query)
            assertEquals(0, state.allResultsCount)
            assertEquals(0, state.notesResultsCount)
            assertEquals(0, state.tasksResultsCount)
            assertEquals(0, state.eventsResultsCount)
            assertTrue(state.searchResults.isEmpty())
            assertEquals(0, state.filters.activeCount)
            assertEquals(SearchSortMode.RELEVANCE, state.sortMode)
        }

    @Test
    fun query_updates_are_debounced_properly() =
        runTest {
            val viewModel = createViewModel()
            val note = createNote(id = 1L, title = "Project planning")
            activeSearchResultsFlow.value = listOf(note)

            viewModel.updateQuery("proj")
            // Before debounce delay (300ms), results should remain empty
            advanceTimeBy(100L)
            assertTrue(
                viewModel.uiState.value.searchResults
                    .isEmpty(),
            )

            // After 300ms delay, results must be populated
            advanceTimeBy(250L)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(
                "Project planning",
                viewModel.uiState.value.searchResults[0]
                    .note.title,
            )
        }

    @Test
    fun category_counts_properly_classify_notes_tasks_events_without_double_counting() =
        runTest {
            val viewModel = createViewModel()
            val noteItem = createNote(id = 1L, title = "General note", kind = NoteKind.NOTE, reminderAt = null)
            val taskItem = createNote(id = 2L, title = "Groceries task", kind = NoteKind.LIST)
            val eventItem = createNote(id = 3L, title = "Doctor event", kind = NoteKind.NOTE, reminderAt = 1700000000L)

            activeSearchResultsFlow.value = listOf(noteItem, taskItem, eventItem)
            viewModel.submitSearch("doc")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(3, state.allResultsCount)
            assertEquals(1, state.notesResultsCount)
            assertEquals(1, state.tasksResultsCount)
            assertEquals(1, state.eventsResultsCount)
            assertEquals(state.allResultsCount, state.notesResultsCount + state.tasksResultsCount + state.eventsResultsCount)
        }

    @Test
    fun category_filtering_restricts_visible_results_to_chosen_type() =
        runTest {
            val viewModel = createViewModel()
            val noteItem = createNote(id = 1L, title = "Note item", kind = NoteKind.NOTE, reminderAt = null)
            val taskItem = createNote(id = 2L, title = "Task item", kind = NoteKind.LIST)
            val eventItem = createNote(id = 3L, title = "Event item", kind = NoteKind.NOTE, reminderAt = 1700000000L)

            activeSearchResultsFlow.value = listOf(noteItem, taskItem, eventItem)
            viewModel.submitSearch("item")
            advanceUntilIdle()

            // 0: All
            viewModel.updateCategoryIndex(0)
            assertEquals(3, viewModel.uiState.value.searchResults.size)

            // 1: Notes only
            viewModel.updateCategoryIndex(1)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(
                1L,
                viewModel.uiState.value.searchResults[0]
                    .note.id,
            )

            // 2: Tasks only
            viewModel.updateCategoryIndex(2)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(
                2L,
                viewModel.uiState.value.searchResults[0]
                    .note.id,
            )

            // 3: Events only
            viewModel.updateCategoryIndex(3)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(
                3L,
                viewModel.uiState.value.searchResults[0]
                    .note.id,
            )
        }

    @Test
    fun relevance_sorting_prioritizes_exact_match_then_prefix_then_body() =
        runTest {
            val viewModel = createViewModel()
            val itemC = createNote(id = 3L, title = "Other Topic", body = "Discussion in meeting")
            val itemA = createNote(id = 1L, title = "Meeting")
            val itemB = createNote(id = 2L, title = "Meeting with stakeholders")

            activeSearchResultsFlow.value = listOf(itemC, itemA, itemB)
            viewModel.submitSearch("Meeting")
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(3, results.size)
            assertEquals("Meeting", results[0].note.title)
            assertEquals("Meeting with stakeholders", results[1].note.title)
            assertEquals("Other Topic", results[2].note.title)
        }

    @Test
    fun newest_sorting_uses_entity_appropriate_timestamps() =
        runTest {
            val viewModel = createViewModel()
            val olderNote = createNote(id = 1L, title = "Older Note", updatedAt = 1000L)
            val newerNote = createNote(id = 2L, title = "Newer Note", updatedAt = 5000L)
            val eventLatest = createNote(id = 3L, title = "Latest Event", reminderAt = 9000L, updatedAt = 500L)

            activeSearchResultsFlow.value = listOf(olderNote, newerNote, eventLatest)
            viewModel.submitSearch("Note")
            viewModel.setSortMode(SearchSortMode.NEWEST)
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(3L, results[0].note.id) // 9000L
            assertEquals(2L, results[1].note.id) // 5000L
            assertEquals(1L, results[2].note.id) // 1000L
        }

    @Test
    fun oldest_sorting_uses_entity_appropriate_timestamps() =
        runTest {
            val viewModel = createViewModel()
            val olderNote = createNote(id = 1L, title = "Older Note", updatedAt = 1000L)
            val newerNote = createNote(id = 2L, title = "Newer Note", updatedAt = 5000L)
            val eventLatest = createNote(id = 3L, title = "Latest Event", reminderAt = 9000L, updatedAt = 500L)

            activeSearchResultsFlow.value = listOf(newerNote, eventLatest, olderNote)
            viewModel.submitSearch("Note")
            viewModel.setSortMode(SearchSortMode.OLDEST)
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(1L, results[0].note.id) // 1000L
            assertEquals(2L, results[1].note.id) // 5000L
            assertEquals(3L, results[2].note.id) // 9000L
        }

    @Test
    fun date_range_this_month_filters_by_current_calendar_month() =
        runTest {
            val viewModel = createViewModel()
            val nowCal = Calendar.getInstance()
            val currentMonthMillis = nowCal.timeInMillis

            val pastCal = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
            val pastMonthMillis = pastCal.timeInMillis

            val currentItem = createNote(id = 1L, title = "Current month note", updatedAt = currentMonthMillis)
            val pastItem = createNote(id = 2L, title = "Past month note", updatedAt = pastMonthMillis)

            activeSearchResultsFlow.value = listOf(currentItem, pastItem)
            viewModel.submitSearch("note")
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.searchResults.size)

            // Filter by This Month (dateRangeIndex = 1)
            viewModel.applyFilters(SearchFilters(dateRangeIndex = 1))
            advanceUntilIdle()

            val filtered = viewModel.uiState.value.searchResults
            assertEquals(1, filtered.size)
            assertEquals(1L, filtered[0].note.id)
        }

    @Test
    fun date_range_custom_respects_start_and_end_boundaries() =
        runTest {
            val viewModel = createViewModel()

            val cal = Calendar.getInstance()
            cal.set(2026, Calendar.OCTOBER, 5, 12, 0)
            val oct5Millis = cal.timeInMillis

            cal.set(2026, Calendar.OCTOBER, 1, 12, 0)
            val oct1Millis = cal.timeInMillis

            cal.set(2026, Calendar.OCTOBER, 10, 12, 0)
            val oct10Millis = cal.timeInMillis

            val itemOct1 = createNote(id = 1L, title = "Oct 1 note", updatedAt = oct1Millis)
            val itemOct5 = createNote(id = 2L, title = "Oct 5 note", updatedAt = oct5Millis)
            val itemOct10 = createNote(id = 3L, title = "Oct 10 note", updatedAt = oct10Millis)

            activeSearchResultsFlow.value = listOf(itemOct1, itemOct5, itemOct10)
            viewModel.submitSearch("note")
            advanceUntilIdle()

            // Filter: Oct 3 to Oct 7
            cal.set(2026, Calendar.OCTOBER, 3, 0, 0)
            val startMillis = cal.timeInMillis
            cal.set(2026, Calendar.OCTOBER, 7, 0, 0)
            val endMillis = cal.timeInMillis

            viewModel.applyFilters(
                SearchFilters(
                    dateRangeIndex = 2,
                    customStartDate = startMillis,
                    customEndDate = endMillis,
                ),
            )
            advanceUntilIdle()

            val filtered = viewModel.uiState.value.searchResults
            assertEquals(1, filtered.size)
            assertEquals(2L, filtered[0].note.id) // Only Oct 5 matches
        }

    @Test
    fun task_status_todo_filters_incomplete_tasks() =
        runTest {
            val viewModel = createViewModel()
            val todoTask =
                createNote(
                    id = 1L,
                    title = "Incomplete task",
                    kind = NoteKind.LIST,
                    completedAt = null,
                    items = listOf(ChecklistItemEntity(noteId = 1L, text = "Subtask", checked = false, sortOrder = 0.0)),
                )
            val doneTask =
                createNote(
                    id = 2L,
                    title = "Completed task",
                    kind = NoteKind.LIST,
                    completedAt = 123456L,
                    items = listOf(ChecklistItemEntity(noteId = 2L, text = "Done subtask", checked = true, sortOrder = 0.0)),
                )

            activeSearchResultsFlow.value = listOf(todoTask, doneTask)
            viewModel.submitSearch("task")
            advanceUntilIdle()

            // taskStatusIndex = 1 (To do)
            viewModel.applyFilters(SearchFilters(taskStatusIndex = 1))
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(1, results.size)
            assertEquals(1L, results[0].note.id)
        }

    @Test
    fun task_status_completed_filters_completed_tasks() =
        runTest {
            val viewModel = createViewModel()
            val todoTask =
                createNote(
                    id = 1L,
                    title = "Incomplete task",
                    kind = NoteKind.LIST,
                    completedAt = null,
                    items = listOf(ChecklistItemEntity(noteId = 1L, text = "Subtask", checked = false, sortOrder = 0.0)),
                )
            val doneTask =
                createNote(
                    id = 2L,
                    title = "Completed task",
                    kind = NoteKind.LIST,
                    completedAt = 123456L,
                    items = listOf(ChecklistItemEntity(noteId = 2L, text = "Done subtask", checked = true, sortOrder = 0.0)),
                )

            activeSearchResultsFlow.value = listOf(todoTask, doneTask)
            viewModel.submitSearch("task")
            advanceUntilIdle()

            // taskStatusIndex = 2 (Completed)
            viewModel.applyFilters(SearchFilters(taskStatusIndex = 2))
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(1, results.size)
            assertEquals(2L, results[0].note.id)
        }

    @Test
    fun shoulder_filter_matches_assigned_shoulder_id() =
        runTest {
            val viewModel = createViewModel()
            val workItem = createNote(id = 1L, title = "Work note", shoulderId = 10L)
            val personalItem = createNote(id = 2L, title = "Personal note", shoulderId = 20L)
            val unassignedItem = createNote(id = 3L, title = "Unassigned note", shoulderId = null)

            activeSearchResultsFlow.value = listOf(workItem, personalItem, unassignedItem)
            viewModel.submitSearch("note")
            advanceUntilIdle()

            // Filter shoulderId = 10L
            viewModel.applyFilters(SearchFilters(shoulderId = 10L))
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(1, results.size)
            assertEquals(1L, results[0].note.id)
        }

    @Test
    fun archived_toggle_combines_active_and_archived_sources() =
        runTest {
            val viewModel = createViewModel()
            val activeNote = createNote(id = 1L, title = "Active note", archived = false)
            val archivedNote = createNote(id = 2L, title = "Archived note", archived = true)

            activeSearchResultsFlow.value = listOf(activeNote)
            archivedSearchResultsFlow.value = listOf(archivedNote)

            // When includeArchived is false
            viewModel.submitSearch("note")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(
                1L,
                viewModel.uiState.value.searchResults[0]
                    .note.id,
            )

            // When includeArchived is true
            viewModel.applyFilters(SearchFilters(includeArchived = true))
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.searchResults.size)
        }

    @Test
    fun combined_filters_apply_logical_and_correctly() =
        runTest {
            val viewModel = createViewModel()
            val matchingTask =
                createNote(
                    id = 1L,
                    title = "Urgent task",
                    kind = NoteKind.LIST,
                    shoulderId = 77L,
                    completedAt = null,
                )
            val wrongShoulderTask =
                createNote(
                    id = 2L,
                    title = "Other task",
                    kind = NoteKind.LIST,
                    shoulderId = 99L,
                    completedAt = null,
                )
            val completedTask =
                createNote(
                    id = 3L,
                    title = "Done task",
                    kind = NoteKind.LIST,
                    shoulderId = 77L,
                    completedAt = 12345L,
                )

            activeSearchResultsFlow.value = listOf(matchingTask, wrongShoulderTask, completedTask)
            viewModel.submitSearch("task")
            advanceUntilIdle()

            viewModel.applyFilters(
                SearchFilters(
                    typeIndex = 2, // Tasks
                    taskStatusIndex = 1, // To do
                    shoulderId = 77L,
                ),
            )
            advanceUntilIdle()

            val results = viewModel.uiState.value.searchResults
            assertEquals(1, results.size)
            assertEquals(1L, results[0].note.id)
        }

    @Test
    fun filter_reset_restores_defaults_and_active_count_zero() =
        runTest {
            val viewModel = createViewModel()
            val filters =
                SearchFilters(
                    typeIndex = 1,
                    dateRangeIndex = 1,
                    taskStatusIndex = 1,
                    shoulderId = 12L,
                    includeArchived = true,
                )
            assertEquals(5, filters.activeCount)

            viewModel.applyFilters(filters)
            assertEquals(5, viewModel.uiState.value.filters.activeCount)

            viewModel.applyFilters(SearchFilters())
            assertEquals(0, viewModel.uiState.value.filters.activeCount)
            assertEquals(0, viewModel.uiState.value.filters.typeIndex)
            assertFalse(viewModel.uiState.value.filters.includeArchived)
        }

    @Test
    fun submit_search_adds_query_to_recent_searches_with_deduplication() =
        runTest {
            val viewModel = createViewModel()

            viewModel.submitSearch("kotlin")
            advanceUntilIdle()
            viewModel.submitSearch("android")
            advanceUntilIdle()
            viewModel.submitSearch("kotlin")
            advanceUntilIdle()

            val recent = viewModel.recentSearches.value
            assertEquals(2, recent.size)
            // Most recent query "kotlin" must be first
            assertEquals("kotlin", recent[0].query)
            assertEquals("android", recent[1].query)
        }

    @Test
    fun remove_recent_search_deletes_specific_query() =
        runTest {
            val viewModel = createViewModel()

            viewModel.submitSearch("apple")
            advanceUntilIdle()
            viewModel.submitSearch("banana")
            advanceUntilIdle()

            viewModel.removeRecentSearch("apple")
            advanceUntilIdle()

            val recent = viewModel.recentSearches.value
            assertEquals(1, recent.size)
            assertEquals("banana", recent[0].query)
        }

    @Test
    fun clear_recent_searches_empties_history() =
        runTest {
            val viewModel = createViewModel()

            viewModel.submitSearch("one")
            advanceUntilIdle()
            viewModel.submitSearch("two")
            advanceUntilIdle()

            viewModel.clearRecentSearches()
            advanceUntilIdle()

            assertTrue(viewModel.recentSearches.value.isEmpty())
        }

    @Test
    fun blank_query_clears_search_results_and_counts() =
        runTest {
            val viewModel = createViewModel()
            val note = createNote(id = 1L, title = "Shopping list", kind = NoteKind.LIST)
            activeSearchResultsFlow.value = listOf(note)

            viewModel.submitSearch("shop")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals(1, viewModel.uiState.value.allResultsCount)

            viewModel.updateQuery("")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.searchResults
                    .isEmpty(),
            )
            assertEquals(0, viewModel.uiState.value.allResultsCount)
            assertEquals(0, viewModel.uiState.value.notesResultsCount)
            assertEquals(0, viewModel.uiState.value.tasksResultsCount)
            assertEquals(0, viewModel.uiState.value.eventsResultsCount)
        }

    @Test
    fun active_filter_count_computes_correct_number_of_non_default_filters() {
        val emptyFilters = SearchFilters()
        assertEquals(0, emptyFilters.activeCount)

        val oneFilter = emptyFilters.copy(typeIndex = 2)
        assertEquals(1, oneFilter.activeCount)

        val twoFilters = oneFilter.copy(dateRangeIndex = 1)
        assertEquals(2, twoFilters.activeCount)

        val threeFilters = twoFilters.copy(taskStatusIndex = 1)
        assertEquals(3, threeFilters.activeCount)

        val fourFilters = threeFilters.copy(shoulderId = 88L)
        assertEquals(4, fourFilters.activeCount)

        val fiveFilters = fourFilters.copy(includeArchived = true)
        assertEquals(5, fiveFilters.activeCount)
    }

    @Test
    fun buildHighlightedString_returns_plain_text_when_query_empty_or_no_match() {
        val plain = buildHighlightedString("Hello World", "")
        assertEquals("Hello World", plain.text)
        assertTrue(plain.spanStyles.isEmpty())

        val noMatch = buildHighlightedString("Hello World", "xyz")
        assertEquals("Hello World", noMatch.text)
        assertTrue(noMatch.spanStyles.isEmpty())
    }

    @Test
    fun buildHighlightedString_adds_spans_for_matching_substrings() {
        val highlighted = buildHighlightedString("Buy milk and oat milk", "milk")
        assertEquals("Buy milk and oat milk", highlighted.text)
        // Two occurrences of "milk" should be highlighted
        assertEquals(2, highlighted.spanStyles.size)
        assertEquals(4, highlighted.spanStyles[0].start)
        assertEquals(8, highlighted.spanStyles[0].end)
        assertEquals(17, highlighted.spanStyles[1].start)
        assertEquals(21, highlighted.spanStyles[1].end)
    }

    @Test
    fun buildSnippetAroundMatch_handles_various_string_lengths_and_queries() {
        // Blank text
        assertEquals("", buildSnippetAroundMatch("", "query"))

        // Short text returns full trimmed text
        assertEquals("Short text", buildSnippetAroundMatch("Short text", "text"))

        // Long text with match
        val longText = "Beginning of the very long text that goes on for a while until we find the keyword right here in the middle of a sentence."
        val snippet = buildSnippetAroundMatch(longText, "keyword", maxChars = 40)
        assertTrue(snippet.contains("keyword"))
    }

    @Test
    fun filter_reset_completely_restores_all_default_fields() {
        val customFilters =
            SearchFilters(
                typeIndex = 3,
                dateRangeIndex = 2,
                customStartDate = 1000L,
                customEndDate = 2000L,
                taskStatusIndex = 2,
                shoulderId = 42L,
                includeArchived = true,
            )
        assertEquals(5, customFilters.activeCount)

        val resetFilters = SearchFilters()
        assertEquals(0, resetFilters.activeCount)
        assertEquals(0, resetFilters.typeIndex)
        assertEquals(0, resetFilters.dateRangeIndex)
        assertEquals(null, resetFilters.customStartDate)
        assertEquals(null, resetFilters.customEndDate)
        assertEquals(0, resetFilters.taskStatusIndex)
        assertEquals(null, resetFilters.shoulderId)
        assertFalse(resetFilters.includeArchived)
    }
}
